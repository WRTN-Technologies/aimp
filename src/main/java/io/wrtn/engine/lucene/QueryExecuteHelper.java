package io.wrtn.engine.lucene;

import static io.wrtn.engine.lucene.Constants.*;
import static io.wrtn.infra.aws.Constants.S3.INDEX_BUCKET;
import static io.wrtn.util.Constants.Config.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.wrtn.engine.lucene.query.SortBuilder;
import io.wrtn.engine.lucene.store.s3.buffer.fs.FSBuffer;
import io.wrtn.engine.lucene.store.s3.cache.fs.FSCache;
import io.wrtn.engine.lucene.store.s3.cache.fs.FSCacheConfig;
import io.wrtn.engine.lucene.store.s3.storage.s3.S3Storage;
import io.wrtn.engine.lucene.util.DocUtils;
import io.wrtn.infra.aws.S3;
import io.wrtn.model.storage.StorageMetadata;
import io.wrtn.util.PathBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import io.wrtn.engine.lucene.index.IndexReaderBuilder;
import io.wrtn.engine.lucene.index.IndexSearcherBuilder;
import io.wrtn.engine.lucene.store.DirectoryBuilder;
import io.wrtn.engine.lucene.store.s3.buffer.fs.FSBufferConfig;
import io.wrtn.engine.lucene.store.s3.storage.s3.S3StorageConfig;

import io.wrtn.model.document.Document;
import io.wrtn.model.index.FieldConfig;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.*;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import io.wrtn.engine.lucene.query.SearchQueryBuilder;
import io.wrtn.util.GlobalExceptionHandler;

public class QueryExecuteHelper {

    private final String indexName;
    private final Map<String, FieldConfig> mappings;
    private final Directory directory;
    private IndexReader reader;
    private IndexSearcher searcher;
    private final S3Storage storage;
    private final FSCache cache;
    private final FSBuffer buffer;

    private String snapshotVersionId;

    public QueryExecuteHelper(
        final String projectId,
        final String indexName,
        final Map<String, FieldConfig> mappings,
        final StorageMetadata snapshot,
        final String snapshotVersionId,
        final S3 s3Client
    ) throws IOException {
        this.indexName = indexName;
        this.mappings = mappings;
        this.snapshotVersionId = snapshotVersionId;

        storage = new S3Storage(new S3StorageConfig(INDEX_BUCKET, s3Client), snapshot);

        // Local FS cache
        cache = new FSCache(
            new FSCacheConfig(
                PathBuilder.buildFsCachePath(
                    FS_TEMP_PATH, projectId, indexName, TEMPORARY_SHARD_ID)),
            storage
        );

        // Local FS buffer
        buffer = new FSBuffer(
            new FSBufferConfig(PathBuilder.buildFsBufferPath(FS_TEMP_PATH, projectId, indexName,
                TEMPORARY_SHARD_ID)),
            storage
        );

        this.directory = DirectoryBuilder.build(storage, cache, buffer);
        this.reader = IndexReaderBuilder.build(directory);
        this.searcher = IndexSearcherBuilder.build(reader, new BM25Similarity());
    }

    public void updateIfChanged(StorageMetadata snapshot, String snapshotVersionId)
        throws IOException {
        Objects.requireNonNull(snapshot);

        this.snapshotVersionId = snapshotVersionId;
        storage.setMeta(snapshot);

        IndexReader updatedReader = IndexReaderBuilder.build(reader);
        if (updatedReader != reader) {
            if (reader != null) {
                reader.close();
            }
            reader = updatedReader;
        }

        searcher = IndexSearcherBuilder.build(reader, new BM25Similarity());
    }

    public Document[] query(JsonObject jsonQuery, int size, boolean includeVectors,
        JsonArray sortArray, boolean trackScores, String[] fields)
        throws IOException, QueryNodeException, GlobalExceptionHandler, InterruptedException, ExecutionException {

        TopDocs topDocs;
        Query query;
        if (sortArray == null || sortArray.isEmpty()) {
            query = SearchQueryBuilder.build(jsonQuery, mappings);
            topDocs = searcher.search(query, size);
            return fetchDocuments(topDocs, includeVectors, true, fields).toArray(new Document[0]);
        } else {
            query = jsonQuery == null ? new MatchAllDocsQuery()
                : SearchQueryBuilder.build(jsonQuery, mappings);
            Sort sort = SortBuilder.build(sortArray, mappings);
            topDocs = searcher.search(query, size, sort, trackScores);
            return fetchDocuments(topDocs, includeVectors, trackScores, fields).toArray(
                new Document[0]);
        }
    }

    public Document[] fetch(String[] fetchIds, boolean includeVectors, String[] fields)
        throws IOException, GlobalExceptionHandler {

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        Term prototype = new Term(DOC_FIELD_ID, "");

        for (String id : fetchIds) {
            Term currentTerm = new Term(prototype.field(), id);
            builder.add(new TermQuery(currentTerm), Occur.SHOULD);
        }
        Query fetchQuery = builder.build();
        TopDocs topDocs = searcher.search(fetchQuery, fetchIds.length);

        return fetchDocuments(topDocs, includeVectors, false, fields).toArray(new Document[0]);
    }

    public void close() throws IOException {
        reader.close();
        directory.close();
        cache.close();
        buffer.close();
    }

    public String getSnapshotVersionId() {
        return snapshotVersionId;
    }

    private List<Document> fetchDocuments(TopDocs topDocs, boolean includeVectors,
        boolean includeScores, String[] fields) throws IOException {
        return DocUtils.fetchDocuments(
            topDocs, includeVectors, includeScores, fields, searcher, indexName);
    }
}