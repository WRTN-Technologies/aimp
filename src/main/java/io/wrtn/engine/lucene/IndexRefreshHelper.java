package io.wrtn.engine.lucene;

import static io.wrtn.engine.lucene.Constants.DOC_FIELD_ID;

import static io.wrtn.engine.lucene.Constants.DOC_FIELD_INTERNAL_DOCUMENT;
import static io.wrtn.engine.lucene.Constants.DOC_FIELD_INTERNAL_WAL_ID;
import static io.wrtn.infra.aws.Constants.S3.TEMP_BUCKET;
import static io.wrtn.util.Constants.CommandType.DOCUMENT_DELETE;
import static io.wrtn.util.Constants.CommandType.DOCUMENT_UPSERT;
import static io.wrtn.util.Constants.Config.EFS_MOUNT_PATH;
import static io.wrtn.util.Constants.Config.FS_TEMP_PATH;
import static io.wrtn.util.Constants.Config.TEMPORARY_SHARD_ID;
import static io.wrtn.util.JsonParser.gson;
import static io.wrtn.util.PathBuilder.buildFsShardPath;
import static io.wrtn.util.PathBuilder.buildFsWalPath;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.wrtn.engine.lucene.index.IndexReaderBuilder;
import io.wrtn.engine.lucene.index.IndexSearcherBuilder;
import io.wrtn.engine.lucene.index.IndexWriterBuilder;
import io.wrtn.engine.lucene.query.SearchQueryBuilder;
import io.wrtn.engine.lucene.query.SortBuilder;
import io.wrtn.engine.lucene.store.DirectoryBuilder;
import io.wrtn.engine.lucene.util.DocUtils;
import io.wrtn.infra.aws.EFS;
import io.wrtn.infra.aws.Lambda;
import io.wrtn.infra.aws.S3;
import io.wrtn.lambda.QueryRouter;
import io.wrtn.model.index.Index;
import io.wrtn.model.storage.StorageMetadata;
import io.wrtn.model.wal.PendingDeletes;
import io.wrtn.util.DocumentIterator;
import io.wrtn.util.GlobalLogger;
import io.wrtn.util.PathBuilder;
import io.wrtn.util.UUID;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import io.wrtn.model.wal.WalRecord;
import io.wrtn.model.document.Document;
import io.wrtn.model.document.RefreshedDocs;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.StatusCode;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public class IndexRefreshHelper {

    private Index index;
    private final String walPath;
    private final Directory directory;
    private final IndexWriter writer;
    private IndexReader reader;
    private IndexSearcher searcher;
    private Set<String> processedWalIdSet;
    private final Map<String, Integer> deletedDocIdMap;
    private final Map<String, List<String>> perWalDeletedDocIdMap;
    private final EFS efsClient;
    private final S3 s3Client;
    private final QueryRouter queryRouter;

    public IndexRefreshHelper(final Index index, EFS efsClient, S3 s3Client, Lambda lambdaClient)
        throws IOException, GlobalExceptionHandler {
        this.index = index;
        this.walPath = buildFsWalPath(EFS_MOUNT_PATH, index, TEMPORARY_SHARD_ID);
        this.directory = DirectoryBuilder.build(
            buildFsShardPath(FS_TEMP_PATH, index, TEMPORARY_SHARD_ID));
        this.writer = IndexWriterBuilder.build(directory, index.getMappings());
        this.reader = IndexReaderBuilder.build(writer);
        this.searcher = IndexSearcherBuilder.build(reader, new BM25Similarity());
        this.processedWalIdSet = new HashSet<>();
        this.deletedDocIdMap = new HashMap<>();
        this.perWalDeletedDocIdMap = new HashMap<>();
        this.efsClient = efsClient;
        this.s3Client = s3Client;
        this.queryRouter = new QueryRouter(s3Client, lambdaClient);
    }

    public void refreshIndex(Index index, StorageMetadata metadata)
        throws IOException, ExecutionException, InterruptedException, GlobalExceptionHandler, QueryNodeException {

        this.index = index;

        PendingDeletes pendingDeletes = efsClient.getPendingDeletes(walPath);

        // Get newly added WAL record paths
        Set<String> currentWalIdSet = new HashSet<>();
        List<Path> newWalPaths = new ArrayList<>();
        for (Path walPath : efsClient.listValidWalPaths(walPath, pendingDeletes.getWalIdMap())) {
            if (!processedWalIdSet.contains(walPath.getFileName().toString())) {
                newWalPaths.add(walPath);
            }
            currentWalIdSet.add(walPath.getFileName().toString());
        }

        // Delete documents in local index that are no longer needed
        processedWalIdSet.removeAll(currentWalIdSet);
        for (String staleWalId : processedWalIdSet) {
            writer.deleteDocuments(new Term(DOC_FIELD_INTERNAL_WAL_ID, staleWalId));
            for (String docId : perWalDeletedDocIdMap.get(staleWalId)) {
                deletedDocIdMap.merge(docId, -1, Integer::sum);
                if (deletedDocIdMap.get(docId) == 0) {
                    deletedDocIdMap.remove(docId);
                }
            }
            perWalDeletedDocIdMap.remove(staleWalId);
        }
        processedWalIdSet = currentWalIdSet;

        processWalRecords(efsClient.readWalRecords(newWalPaths), metadata);

        updateReader();
    }

    private void upsertDocuments(List<Map<String, JsonElement>> documents, String walId)
        throws GlobalExceptionHandler, IOException {

        List<String> deletedDocIds = new ArrayList<>();
        for (Map<String, JsonElement> document : documents) {
            org.apache.lucene.document.Document luceneDoc = new org.apache.lucene.document.Document();
            luceneDoc.add(new StringField(DOC_FIELD_INTERNAL_WAL_ID, walId, Store.NO));
            DocUtils.buildLuceneDocument(document, luceneDoc, null, index.getMappings());
            luceneDoc.add(new StoredField(DOC_FIELD_INTERNAL_DOCUMENT, gson.toJson(document)));
            String docId = document.get(DOC_FIELD_ID).getAsString();
            writer.updateDocument(new Term(DOC_FIELD_ID, docId), luceneDoc);
            deletedDocIdMap.merge(docId, 1, Integer::sum);
            deletedDocIds.add(docId);
        }

        perWalDeletedDocIdMap.put(walId, deletedDocIds);
    }

    private void deleteDocuments(List<String> ids, String walId) throws IOException {

        List<String> deletedDocIds = new ArrayList<>();
        for (String docId : ids) {
            writer.deleteDocuments(new Term(DOC_FIELD_ID, docId));
            deletedDocIdMap.merge(docId, 1, Integer::sum);
            deletedDocIds.add(docId);
        }

        perWalDeletedDocIdMap.put(walId, deletedDocIds);
    }

    /**
     * Processes data mutation commands
     *
     * @param newWalRecords the list of newly added data mutation WAL record
     * @throws GlobalExceptionHandler if something is failed in our end
     * @throws IOException            if filesystem/IO error occurs
     */
    private void processWalRecords(List<WalRecord> newWalRecords, StorageMetadata metadata)
        throws GlobalExceptionHandler, IOException, QueryNodeException {

        // Sort the command list in ascending order by sequence number
        newWalRecords.sort(Comparator.comparing(WalRecord::getSeqNo));

        Map<WalRecord, CompletableFuture<ResponseInputStream<GetObjectResponse>>> inputStreamMap = new HashMap<>();
        for (WalRecord walRecord : newWalRecords) {
            if (walRecord.getS3Payload()) {
                String key = PathBuilder.buildStorageKeyPath(walRecord.getProjectId(),
                    walRecord.getIndexName(), walRecord.getShardId(), walRecord.getId());
                inputStreamMap.put(walRecord, s3Client.getObjectStreamAsync(TEMP_BUCKET, key));
            }
        }

        // Write WAL records into local index
        for (WalRecord walRecord : newWalRecords) {
            GlobalLogger.info(
                "Processing WAL record: " + walRecord.getId() + " " + walRecord.getSeqNo());
            switch (walRecord.getType().toUpperCase()) {
                case DOCUMENT_UPSERT -> {
                    String walRecordId = walRecord.getId();
                    List<Map<String, JsonElement>> documents = new ArrayList<>();
                    if (walRecord.getS3Payload()) {
                        InputStream inputStream = inputStreamMap.get(walRecord).join();
                        DocumentIterator documentIterator = new DocumentIterator(inputStream);
                        while (documentIterator.hasNext()) {
                            Map<String, JsonElement> document = documentIterator.next();
                            documents.add(document);
                        }
                        documentIterator.close();
                        inputStream.close();
                    } else {
                        documents = walRecord.getDocuments();
                    }

                    if (documents.isEmpty()) {
                        throw new GlobalExceptionHandler(
                            "UPSERT must contain at least one document",
                            StatusCode.INVALID_INPUT_VALUE);
                    }

                    // Add document id if not present
                    for (Map<String, JsonElement> document : documents) {
                        if (!document.containsKey(DOC_FIELD_ID)) {
                            String randomId = UUID.shortenUuid(walRecordId,
                                documents.indexOf(document));
                            document.put(DOC_FIELD_ID, gson.toJsonTree(randomId));
                        }
                    }

                    upsertDocuments(documents, walRecord.getId());
                }
                case DOCUMENT_DELETE -> {
                    JsonObject filter = walRecord.getFilter();
                    List<String> ids;
                    if (filter != null && !filter.isEmpty()) {
                        Set<String> idSet = new HashSet<>();
                        if (metadata != null) {
                            // Get query results from storage
                            List<Document> docs = queryRouter.refreshHandler(index, metadata,
                                filter);
                            for (Document doc : docs) {
                                idSet.add(doc.getDoc().get(DOC_FIELD_ID).getAsString());
                            }
                        }

                        // Flush segments if necessary
                        updateReader();

                        Query filterQuery = SearchQueryBuilder.build(filter, index.getMappings());
                        TopDocs topDocs = searcher.search(filterQuery, Integer.MAX_VALUE);

                        for (ScoreDoc hit : topDocs.scoreDocs) {
                            JsonObject jsonDoc = toJsonDoc(
                                searcher.storedFields().document(hit.doc));
                            idSet.add(jsonDoc.get(DOC_FIELD_ID).getAsString());
                        }

                        ids = new ArrayList<>(idSet);
                    } else {
                        ids = walRecord.getIds();
                    }

                    deleteDocuments(ids, walRecord.getId());
                }
                default -> throw new GlobalExceptionHandler(
                    "Method must be one of ['DOCUMENT_UPSERT', 'DOCUMENT_DELETE'] \""
                        + "but input is " + walRecord.getType(), StatusCode.METHOD_NOT_ALLOWED);
            }
        }
    }

    public RefreshedDocs query(JsonObject jsonQuery, int size, boolean includeVectors,
        JsonArray sortArray, boolean trackScores, String[] fields)
        throws IOException, QueryNodeException, GlobalExceptionHandler, InterruptedException, ExecutionException {

        TopDocs topDocs;
        Query query;
        List<Document> docs;
        if (sortArray == null) {
            query = SearchQueryBuilder.build(jsonQuery, index.getMappings());
            topDocs = searcher.search(query, size);
            docs = DocUtils.fetchDocuments(topDocs, includeVectors, true, fields,
                searcher, index.getIndexName());

        } else {
            query = jsonQuery == null ? new MatchAllDocsQuery()
                : SearchQueryBuilder.build(jsonQuery, index.getMappings());
            Sort sort = SortBuilder.build(sortArray, index.getMappings());
            topDocs = searcher.search(query, size, sort, trackScores);
            docs = DocUtils.fetchDocuments(topDocs, includeVectors, trackScores, fields,
                searcher, index.getIndexName());

        }

        return new RefreshedDocs(docs.toArray(new Document[0]), deletedDocIdMap.keySet());
    }

    public RefreshedDocs fetch(String[] fetchIds, boolean includeVectors, String[] fields)
        throws IOException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        Term prototype = new Term(DOC_FIELD_ID, "");

        for (String id : fetchIds) {
            Term currentTerm = new Term(prototype.field(), id);
            builder.add(new TermQuery(currentTerm), Occur.SHOULD);
        }
        Query fetchQuery = builder.build();
        TopDocs topDocs = searcher.search(fetchQuery, fetchIds.length);

        List<Document> docs = DocUtils.fetchDocuments(topDocs, includeVectors, false, fields,
            searcher,
            index.getIndexName());

        return new RefreshedDocs(docs.toArray(new Document[0]), deletedDocIdMap.keySet());
    }

    public void close() throws IOException {
        reader.close();
        writer.close();
        directory.close();
    }

    private JsonObject toJsonDoc(org.apache.lucene.document.Document luceneDoc) {
        return gson.fromJson(luceneDoc.get(DOC_FIELD_INTERNAL_DOCUMENT), JsonObject.class);
    }

    private void updateReader() throws IOException {
        IndexReader updatedReader = IndexReaderBuilder.build(reader, writer);
        if (updatedReader != reader) {
            if (reader != null) {
                reader.close();
            }
            reader = updatedReader;
            searcher = IndexSearcherBuilder.build(reader, new BM25Similarity());
        }
    }
}