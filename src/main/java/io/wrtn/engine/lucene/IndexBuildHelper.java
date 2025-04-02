package io.wrtn.engine.lucene;

import static io.wrtn.engine.lucene.Constants.*;
import static io.wrtn.infra.aws.Constants.S3.INDEX_BUCKET;
import static io.wrtn.infra.aws.Constants.S3.TEMP_BUCKET;
import static io.wrtn.util.Constants.CommandType.*;
import static io.wrtn.util.Constants.Config.*;
import static io.wrtn.util.PathBuilder.buildStorageShardPath;
import static io.wrtn.util.JsonParser.gson;

import com.google.gson.JsonElement;

import com.google.gson.JsonObject;
import io.wrtn.engine.lucene.query.SearchQueryBuilder;
import io.wrtn.engine.lucene.store.s3.S3Directory;
import io.wrtn.engine.lucene.store.s3.buffer.fs.FSBuffer;
import io.wrtn.engine.lucene.store.s3.buffer.fs.FSBufferConfig;
import io.wrtn.engine.lucene.store.s3.cache.fs.FSCache;
import io.wrtn.engine.lucene.store.s3.cache.fs.FSCacheConfig;
import io.wrtn.engine.lucene.store.s3.storage.s3.S3Storage;
import io.wrtn.engine.lucene.util.DocUtils;
import io.wrtn.infra.aws.S3;
import io.wrtn.engine.lucene.util.SegmentBalancer;
import io.wrtn.model.storage.File;
import io.wrtn.model.storage.ComputeNode;
import io.wrtn.model.storage.StorageMetadata;
import io.wrtn.util.DocumentIterator;
import io.wrtn.util.GlobalLogger;
import io.wrtn.util.PathBuilder;
import io.wrtn.util.SizeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;

import io.wrtn.engine.lucene.index.IndexWriterBuilder;
import io.wrtn.engine.lucene.store.DirectoryBuilder;
import io.wrtn.engine.lucene.store.s3.storage.s3.S3StorageConfig;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import io.wrtn.model.wal.WalRecord;
import io.wrtn.model.index.FieldConfig;
import io.wrtn.model.index.Index;
import io.wrtn.util.UUID;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.*;
import io.wrtn.util.StatusCode;
import io.wrtn.util.GlobalExceptionHandler;

import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.MMapDirectory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public class IndexBuildHelper {

    private final long BUFFER_FULL_THRESHOLD = (long) (RAM_BUFFER_SIZE_MB * 1024 * 1024 * 0.95);

    private final S3Directory directory;
    private final IndexWriter writer;
    private final Map<String, FieldConfig> mappings;

    private final S3Storage storage;
    private final FSCache cache;
    private final FSBuffer buffer;

    final S3 s3Client = new S3();
    private final MMapDirectory tempDirectory;

    private List<ComputeNode> computeNodes;
    private final List<ExecutorService> executors;

    public IndexBuildHelper(final Index index, final int shardId)
        throws IOException, ExecutionException, InterruptedException, GlobalExceptionHandler {

        executors = new ArrayList<>(INDEX_BUILDER_CONCURRENCY);
        for (int i = 0; i < INDEX_BUILDER_CONCURRENCY; i++) {
            executors.add(Executors.newSingleThreadExecutor());
        }

        String storageShardPath = buildStorageShardPath(index.getProjectId(), index.getIndexName(),
            shardId);

        StorageMetadata meta;
        if (index.getDataUpdatedAt() == 0) {
            // This index is empty
            meta = new StorageMetadata(storageShardPath, index.getComputeNodeIds());
            computeNodes = new ArrayList<>();
        } else {
            // Read storage meta from storage : {project}/{index}/{shard}/meta.json
            meta = gson.fromJson(
                s3Client.getObject(INDEX_BUCKET, PathBuilder.buildMetaKey(storageShardPath))
                    .asUtf8String(), StorageMetadata.class);

            // Initialize node mapping from storage
            computeNodes = meta.getComputeNodes();
            List<CompletableFuture<ResponseBytes<GetObjectResponse>>> futures = new ArrayList<>();
            File rootSegmentFile = null;
            for (File file : meta.getFileMap().values()) {
                if (file.getName().contains("segments")) {
                    // Root segments_N
                    rootSegmentFile = file;
                } else {
                    // Partition metadata
                    futures.add(s3Client.getVersionedObjectAsync(INDEX_BUCKET,
                        PathBuilder.buildObjectKeyForRead(meta, file.getName()),
                        file.getVersionId()));
                }
            }

            Objects.requireNonNull(rootSegmentFile);
            meta.getFileMap().clear();
            meta.getFileMap().put(rootSegmentFile.getName(), rootSegmentFile);
            for (CompletableFuture<ResponseBytes<GetObjectResponse>> future : futures) {
                StorageMetadata partitionMeta = gson.fromJson(future.get().asUtf8String(),
                    StorageMetadata.class);
                for (Entry<String, File> entry : partitionMeta.getFileMap().entrySet()) {
                    String name = entry.getKey();
                    File file = entry.getValue();
                    if (!name.contains("segments")) {
                        meta.getFileMap().put(name, file);
                    }
                }
            }
        }

        storage = new S3Storage(new S3StorageConfig(INDEX_BUCKET, s3Client), meta);

        cache = new FSCache(
            new FSCacheConfig(
                PathBuilder.buildFsCachePath(FS_TEMP_PATH, index, TEMPORARY_SHARD_ID)),
            storage
        );

        // Directly create buffer due to the limitation in GraalVM 21 foreign down call support
        buffer = new FSBuffer(
            new FSBufferConfig(
                PathBuilder.buildFsBufferPath(FS_TEMP_PATH, index, TEMPORARY_SHARD_ID)),
            storage
        );

        this.directory = DirectoryBuilder.build(storage, cache, buffer);
        this.writer = IndexWriterBuilder.build(directory, index.getMappings());
        this.mappings = index.getMappings();

        this.tempDirectory = new MMapDirectory(Paths.get(FS_TEMP_PATH));
    }

    public int getNumDocs() throws GlobalExceptionHandler, IOException {
        SegmentInfos si = SegmentInfos.readLatestCommit(directory);
        int numDocs = 0;
        for (SegmentCommitInfo sgi : si.asList()) {
            numDocs += (sgi.info.maxDoc() - sgi.getDelCount());
        }

        return numDocs;
    }

    public void close(Index index) throws IOException, ExecutionException, InterruptedException {
        // Final cleanup
        writer.close();

        // Commit metadata
        commitMetadata(index);

        directory.close();
        tempDirectory.close();
        buffer.close();
        cache.close();
        storage.close();

        for (ExecutorService executor : executors) {
            executor.shutdownNow();
        }
    }

    public void commit(Index index)
        throws IOException, ExecutionException, InterruptedException {
        // Commit data
        writer.commit();

        // Commit metadata
        commitMetadata(index);
    }

    private void commitMetadata(Index index)
        throws ExecutionException, InterruptedException, IOException {
        StorageMetadata currentMeta = storage.getMeta();
        SegmentInfos rootSegment = SegmentInfos.readLatestCommit(directory);
        int newNumComputeNodes = index.getComputeNodeIds().size();

        currentMeta.setComputeNodeIds(index.getComputeNodeIds());
        currentMeta.setDataUpdatedAt(index.getDataUpdatedAt());

        List<StorageMetadata> partitionMetas = new ArrayList<>();
        for (int i = 0; i < newNumComputeNodes; i++) {
            SegmentInfos partitionSegment = rootSegment.clone();
            partitionSegment.clear();
            StorageMetadata partitionMeta =
                new StorageMetadata(currentMeta, partitionSegment);
            partitionMetas.add(partitionMeta);
        }

        List<ComputeNode> newComputeNodes = SegmentBalancer.balanceSegments(
            computeNodes, index.getComputeNodeIds(), rootSegment.asList());

        // Fill out each partition metadata with segments
        List<CompletableFuture<PutObjectResponse>> futures = new ArrayList<>();
        StorageMetadata newMeta = new StorageMetadata(currentMeta);
        for (int i = 0; i < newNumComputeNodes; i++) {
            ComputeNode node = newComputeNodes.get(i);
            StorageMetadata partitionMeta = partitionMetas.get(i);

            for (SegmentCommitInfo segment : node.getSegments()) {
                partitionMeta.getSegmentInfos().add(segment);

                for (String name : segment.files()) {
                    File file = currentMeta.getFileMap().get(name);
                    partitionMeta.getFileMap().put(name, file);
                }

                partitionMeta.setTotalSize(
                    partitionMeta.getTotalSize() + segment.sizeInBytes());
                partitionMeta.getSegmentIds().add(segment.info.name);
            }

            SegmentInfos partitionSegment = partitionMeta.getSegmentInfos();
            partitionSegment.commit(tempDirectory);
            String segmentFileName = partitionSegment.getSegmentsFileName();
            File segmentFile = new File(segmentFileName,
                tempDirectory.fileLength(segmentFileName), "",
                currentMeta.getCurrentPrefixId());
            segmentFile.setFullBytes(Files.readAllBytes(
                tempDirectory.getDirectory().resolve(segmentFileName)));
            partitionMeta.getFileMap().put(segmentFileName, segmentFile);

            futures.add(s3Client.putObjectAsync(INDEX_BUCKET,
                PathBuilder.buildPartitionMetaKeyForWrite(newMeta, node.getNodeId()),
                partitionMeta));
        }

        // Complete partition metadata write
        for (int i = 0; i < newNumComputeNodes; i++) {
            PutObjectResponse response = futures.get(i).get();
            int nodeId = newComputeNodes.get(i).getNodeId();
            String name = PathBuilder.buildPartitionMetaName(nodeId);
            StorageMetadata partitionSnapshot = partitionMetas.get(i);
            // Total partition size is allocated as file size
            newMeta.getFileMap().put(name,
                new File(name, partitionSnapshot.getTotalSize(), response.versionId(),
                    newMeta.getCurrentPrefixId()));
        }

        // Delete partition metadata not used anymore
        Set<Integer> newComputeNodeIds = new HashSet<>(index.getComputeNodeIds());
        for (ComputeNode node : computeNodes) {
            if (!newComputeNodeIds.contains(node.getNodeId())) {
                // Delete partition metadata not used anymore
                s3Client.deleteObject(INDEX_BUCKET,
                    PathBuilder.buildPartitionMetaKeyForWrite(newMeta, node.getNodeId()));
            }
        }

        // Commit storage metadata
        String rootSegmentFileName = rootSegment.getSegmentsFileName();
        newMeta.getFileMap()
            .put(rootSegmentFileName, currentMeta.getFileMap().get(rootSegmentFileName));
        newMeta.setComputeNodes(newComputeNodes);

        // Maintain new node segment mapping in memory for future use
        computeNodes = newComputeNodes;

        s3Client.putObject(INDEX_BUCKET, PathBuilder.buildMetaKey(newMeta.getCurrentPrefix()),
            newMeta);
    }

    public long getStorageSize() {
        return storage.getStorageSize();
    }

    public void closeExecutors() {
        for (ExecutorService executor : executors) {
            executor.shutdownNow();
        }
    }

    /**
     * Processes data mutation commands
     *
     * @param walRecords the list of data mutation WAL record
     * @throws GlobalExceptionHandler if something is failed in our end
     * @throws IOException            if filesystem/IO error occurs
     */
    public List<WalRecord> processWalRecords(List<WalRecord> walRecords)
        throws GlobalExceptionHandler, IOException, ExecutionException, InterruptedException, QueryNodeException {

        // Sort the command list in ascending order by sequence number
        walRecords.sort(Comparator.comparing(WalRecord::getSeqNo));

        long totalPayloadBytes = 0;
        int index = 0;
        for (WalRecord walRecord : walRecords) {
            totalPayloadBytes += walRecord.getPayloadBytes();
            index++;
            if (totalPayloadBytes > BUFFER_FULL_THRESHOLD) {
                break;
            }
        }
        GlobalLogger.info("Total payload bytes: " + totalPayloadBytes + " buffer full threshold: "
            + BUFFER_FULL_THRESHOLD);

        List<WalRecord> targetWalRecords;
        if (index == walRecords.size()) {
            targetWalRecords = walRecords;
        } else {
            assert index < walRecords.size();
            targetWalRecords = walRecords.subList(0, index);
        }

        Map<WalRecord, CompletableFuture<ResponseInputStream<GetObjectResponse>>> inputStreamMap = new HashMap<>();
        for (WalRecord walRecord : targetWalRecords) {
            if (walRecord.getS3Payload()) {
                String key = PathBuilder.buildStorageKeyPath(walRecord.getProjectId(),
                    walRecord.getIndexName(), walRecord.getShardId(), walRecord.getId());
                inputStreamMap.put(walRecord, s3Client.getObjectStreamAsync(TEMP_BUCKET, key));
            }
        }

        List<Future<?>> futures = new ArrayList<>();
        List<List<Map<String, JsonElement>>> partitionDocs = new ArrayList<>(
            INDEX_BUILDER_CONCURRENCY);
        List<List<String>> partitionIds = new ArrayList<>(INDEX_BUILDER_CONCURRENCY);
        for (WalRecord walRecord : targetWalRecords) {
            switch (walRecord.getType().toUpperCase()) {
                case DOCUMENT_UPSERT -> {
                    int concurrency = (int) Math.min(
                        walRecord.getPayloadBytes() / SizeConverter.mbToB(
                            MIN_DOCS_SIZE_MB_FOR_INDEX_BUILD_CONCURRENCY),
                        INDEX_BUILDER_CONCURRENCY);
                    if (concurrency == 0) {
                        concurrency = 1;
                    }

                    for (int i = 0; i < concurrency; i++) {
                        partitionDocs.add(new ArrayList<>());
                    }

                    String walRecordId = walRecord.getId();
                    if (walRecord.getS3Payload()) {
                        InputStream inputStream = inputStreamMap.get(walRecord).join();
                        DocumentIterator documentIterator =
                            new DocumentIterator(inputStream);
                        while (documentIterator.hasNext()) {
                            Map<String, JsonElement> document = documentIterator.next();
                            if (!document.containsKey(DOC_FIELD_ID)) {
                                String randomId = UUID.shortenUuid(walRecordId,
                                    documentIterator.getCurrentIndex());
                                document.put(DOC_FIELD_ID, gson.toJsonTree(randomId));
                            }
                            partitionDocs.get(getWalPartitionId(document, concurrency))
                                .add(document);
                        }
                        documentIterator.close();
                        inputStream.close();
                    } else {
                        for (Map<String, JsonElement> document : walRecord.getDocuments()) {
                            if (!document.containsKey(DOC_FIELD_ID)) {
                                String randomId = UUID.shortenUuid(walRecordId,
                                    walRecord.getDocuments().indexOf(document));
                                document.put(DOC_FIELD_ID, gson.toJsonTree(randomId));
                            }
                            partitionDocs.get(getWalPartitionId(document, concurrency))
                                .add(document);
                        }
                    }

                    for (List<Map<String, JsonElement>> docs : partitionDocs) {
                        DocumentUpsertTask task = new DocumentUpsertTask(docs, writer, mappings);
                        futures.add(executors.get(partitionDocs.indexOf(docs)).submit(task));
                    }
                }
                case DOCUMENT_DELETE -> {
                    JsonObject filter = walRecord.getFilter();
                    if (filter != null && !filter.isEmpty()) {
                        Query filterQuery = SearchQueryBuilder.build(filter, mappings);
                        writer.deleteDocuments(filterQuery);
                    } else {
                        int concurrency = Math.min(
                            walRecord.getIds().size() / MIN_NUM_DOCS_FOR_INDEX_BUILD_CONCURRENCY,
                            INDEX_BUILDER_CONCURRENCY);
                        if (concurrency == 0) {
                            concurrency = 1;
                        }

                        for (int i = 0; i < concurrency; i++) {
                            partitionIds.add(new ArrayList<>());
                        }

                        for (String docId : walRecord.getIds()) {
                            partitionIds.get(getWalPartitionId(docId, concurrency)).add(docId);
                        }

                        for (List<String> ids : partitionIds) {
                            DocumentDeleteTask task = new DocumentDeleteTask(ids, writer);
                            futures.add(executors.get(partitionIds.indexOf(ids)).submit(task));
                        }
                    }
                }
                default -> throw new GlobalExceptionHandler(
                    "Method must be one of ['DOCUMENT_UPSERT', 'DOCUMENT_DELETE'] \""
                        + "but input is " + walRecord.getType(), StatusCode.METHOD_NOT_ALLOWED);
            }

            for (Future<?> future : futures) {
                future.get();
            }

            partitionDocs.clear();
            partitionIds.clear();
            futures.clear();
        }

        if (targetWalRecords.equals(walRecords)) {
            // The requested WAL records are processed as a whole
            return walRecords;
        } else {
            // The requested Wal records are processed partially
            assert targetWalRecords.size() < walRecords.size();

            List<WalRecord> processedWalRecords = new ArrayList<>(targetWalRecords);
            targetWalRecords.clear();

            return processedWalRecords;
        }
    }

    private int getWalPartitionId(Map<String, JsonElement> document, int numPartitions) {
        return Math.abs(document.get(DOC_FIELD_ID).getAsString().hashCode() % numPartitions);
    }

    private int getWalPartitionId(String id, int numPartitions) {
        return Math.abs(id.hashCode() % numPartitions);
    }

    private class DocumentUpsertTask implements Callable<Void> {

        private final List<Map<String, JsonElement>> documents;
        private final IndexWriter writer;
        private final Map<String, FieldConfig> mappings;

        public DocumentUpsertTask(List<Map<String, JsonElement>> documents,
            IndexWriter writer, Map<String, FieldConfig> mappings) {
            this.documents = documents;
            this.writer = writer;
            this.mappings = mappings;
        }

        @Override
        public Void call() throws GlobalExceptionHandler, IOException {
            for (Map<String, JsonElement> document : documents) {
                Document luceneDoc = new Document();
                DocUtils.buildLuceneDocument(document, luceneDoc, null, mappings);
                luceneDoc.add(new StoredField(DOC_FIELD_INTERNAL_DOCUMENT, gson.toJson(document)));
                writer.updateDocument(
                    new Term(DOC_FIELD_ID, document.get(DOC_FIELD_ID).getAsString()), luceneDoc);
            }

            return null;
        }
    }

    private class DocumentDeleteTask implements Callable<Void> {

        private final List<String> ids;
        private final IndexWriter writer;

        public DocumentDeleteTask(List<String> ids, IndexWriter writer) {
            this.ids = ids;
            this.writer = writer;
        }

        @Override
        public Void call() throws IOException {
            for (String docId : ids) {
                writer.deleteDocuments(new Term(DOC_FIELD_ID, docId));
            }

            return null;
        }
    }
}
