package io.wrtn.lambda;

import com.google.gson.JsonObject;
import io.wrtn.dto.FetchRequest;
import io.wrtn.dto.FetchResponse;
import io.wrtn.dto.QueryRequest;
import io.wrtn.dto.QueryResponse;
import io.wrtn.model.event.RefreshEvent;
import io.wrtn.model.storage.ComputeNode;
import io.wrtn.model.storage.StorageMetadata;
import io.wrtn.infra.aws.Lambda;
import io.wrtn.infra.aws.S3;
import io.wrtn.util.Threads;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import io.wrtn.model.document.RefreshedDocs;
import io.wrtn.model.event.QueryEvent;
import io.wrtn.model.document.Document;
import io.wrtn.model.index.Index;
import io.wrtn.model.storage.File;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.JsonParser;
import io.wrtn.util.PathBuilder;
import io.wrtn.util.StatusCode;

import java.util.Date;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import static io.wrtn.engine.lucene.Constants.DOC_FIELD_ID;
import static io.wrtn.engine.lucene.util.Validation.validateQuery;
import static io.wrtn.engine.lucene.util.Validation.validateSort;
import static io.wrtn.infra.aws.Constants.S3.INDEX_BUCKET;
import static io.wrtn.util.Constants.CommandType.DOCUMENT_FETCH;
import static io.wrtn.util.Constants.CommandType.DOCUMENT_QUERY;
import static io.wrtn.util.Constants.Config.*;
import static io.wrtn.util.Constants.Limits.MAX_DOCUMENTS_TO_FETCH;
import static io.wrtn.util.Constants.Limits.MAX_DOCUMENTS_TO_QUERY;
import static io.wrtn.util.DocumentSorter.mergeDocs;
import static io.wrtn.util.DocumentSorter.mergeWithRefreshedDocs;
import static io.wrtn.util.DocumentSorter.sortDocuments;
import static io.wrtn.util.JsonParser.gson;
import static io.wrtn.util.PathBuilder.buildQueryExecutorName;

public class QueryRouter {

    // TODO: Max entries to limit memory usage
    private static final Map<String, StorageMetadata> cachedMetaMap = new HashMap<>();

    private final S3 s3Client;
    private final Lambda lambdaClient;

    public QueryRouter(S3 s3Client, Lambda lambdaClient) {
        this.s3Client = s3Client;
        this.lambdaClient = lambdaClient;
    }

    public List<Document> refreshHandler(Index index, StorageMetadata meta, JsonObject query)
        throws GlobalExceptionHandler {

        assert index != null && meta != null && query != null;

        List<Document[]> docs = new ArrayList<>();
        if (index.getDataUpdatedAt() > 0) {
            List<QueryEvent> queryEvents = new ArrayList<>();

            for (ComputeNode node : meta.getComputeNodes()) {
                if (node.getSegmentIds().isEmpty()) {
                    continue;
                }

                File partitionMeta = meta.getFileMap().get(
                    PathBuilder.buildPartitionMetaName(node.getNodeId()));
                assert partitionMeta != null;

                QueryEvent event = new QueryEvent();
                event.setType(DOCUMENT_QUERY);
                event.setShardId(TEMPORARY_SHARD_ID);
                event.setProjectId(index.getProjectId());
                event.setIndexName(index.getIndexName());
                event.setIndexClass(index.getIndexClass());
                event.setMappings(index.getMappings());
                event.setQuery(query);
                event.setSize(Integer.MAX_VALUE);
                event.setIncludeVectors(false);
                event.setComputeNodeId(node.getNodeId());
                event.setMetaObjectKey(
                    PathBuilder.buildObjectKeyForRead(meta, partitionMeta.getName()));
                event.setMetaObjectVersionId(partitionMeta.getVersionId());
                event.setFields(new String[]{DOC_FIELD_ID});

                queryEvents.add(event);
            }

            // Invoke QueryExecutor concurrently
            docs = invokeQueryExecutorSyncParallel(queryEvents);
        }

        // Merge and sort documents by score

        return docs.stream()
            .flatMap(Arrays::stream)
            .limit(Integer.MAX_VALUE).toList();
    }

    public QueryResponse queryHandler(QueryRequest request, Index index)
        throws GlobalExceptionHandler {

        if (request.getSize() == null) {
            throw new GlobalExceptionHandler("Size must be provided",
                StatusCode.INVALID_INPUT_VALUE);
        } else if (request.getSize() > MAX_DOCUMENTS_TO_QUERY) {
            throw new GlobalExceptionHandler(
                "Total documents to query must be less than "
                    + MAX_DOCUMENTS_TO_QUERY, StatusCode.INVALID_INPUT_VALUE);
        } else if (request.getSize() <= 0) {
            throw new GlobalExceptionHandler(
                "Total documents to query must be greater than 0.",
                StatusCode.INVALID_INPUT_VALUE);
        } else if (request.getFields() != null && request.getFields().length < 1) {
            throw new GlobalExceptionHandler(
                "At least one field must be provided in the fields array",
                StatusCode.INVALID_INPUT_VALUE);
        }

        long queryStartMs = new Date().getTime();

        // Run query, sort builder for validation
        if (request.getSort() != null) {
            validateSort(request.getSort(), index.getMappings());
            if (request.getQuery() != null) {
                validateQuery(request.getQuery(), index.getMappings());
            }
        } else {
            if (request.getTrackScores()) {
                throw new GlobalExceptionHandler(
                    "Track scores is enabled but no sort field is provided",
                    StatusCode.INVALID_INPUT_VALUE);
            }
            validateQuery(request.getQuery(), index.getMappings());
        }

        List<Document[]> docs = new ArrayList<>();
        StorageMetadata meta = null;
        if (index.getDataUpdatedAt() > 0) {
            List<QueryEvent> queryEvents = new ArrayList<>();
            meta = getStorageMeta(index);

            for (ComputeNode node : meta.getComputeNodes()) {
                if (node.getSegmentIds().isEmpty()) {
                    continue;
                }

                File partitionMeta = meta.getFileMap().get(
                    PathBuilder.buildPartitionMetaName(node.getNodeId()));
                assert partitionMeta != null;

                QueryEvent event = new QueryEvent();
                event.setType(DOCUMENT_QUERY);
                event.setShardId(TEMPORARY_SHARD_ID);
                event.setProjectId(index.getProjectId());
                event.setIndexName(index.getIndexName());
                event.setIndexClass(index.getIndexClass());
                event.setMappings(index.getMappings());
                event.setQuery(request.getQuery());
                event.setSort(request.getSort());
                event.setFields(request.getFields());
                event.setTrackScores(request.getTrackScores());
                if (request.getConsistentRead()) {
                    event.setSize(request.getSize() * 2);
                } else {
                    event.setSize(request.getSize());
                }
                event.setIncludeVectors(request.getIncludeVectors());
                event.setComputeNodeId(node.getNodeId());
                event.setMetaObjectKey(
                    PathBuilder.buildObjectKeyForRead(meta, partitionMeta.getName()));
                event.setMetaObjectVersionId(partitionMeta.getVersionId());

                queryEvents.add(event);
            }

            // Invoke QueryExecutor concurrently
            docs = invokeQueryExecutorSyncParallel(queryEvents);
        }

        Future<InvokeResponse> refreshedDocsFuture = null;
        if (request.getConsistentRead()) {
            RefreshEvent event = new RefreshEvent();
            event.setType(DOCUMENT_QUERY);
            event.setIndex(index);
            event.setQuery(request.getQuery());
            event.setSize(request.getSize());
            event.setSort(request.getSort());
            event.setFields(request.getFields());
            event.setIncludeVectors(request.getIncludeVectors());
            event.setStorageMetadata(meta);
            refreshedDocsFuture =
                lambdaClient.invokeAsync(INDEX_REFRESHER_NAME, INDEX_REFRESHER_QUALIFIER, event);
        }

        // Merge and sort documents by score
        Document[] mergedDocs;
        try {
            if (request.getConsistentRead()) {
                assert refreshedDocsFuture != null;
                InvokeResponse resp = refreshedDocsFuture.get();
                lambdaClient.validateResponse(resp);
                RefreshedDocs refreshedDocs = JsonParser.parseRefreshedDocs(
                    resp.payload().asUtf8String());

                mergedDocs = sortDocuments(docs, refreshedDocs, index.getMappings(), request);
            } else {
                mergedDocs = sortDocuments(docs, null, index.getMappings(), request);
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new GlobalExceptionHandler("Failed to refresh documents: " + e.getMessage(),
                StatusCode.SERVER_ERROR);
        }

        Float maxScore;
        if (request.getSort() == null) {
            maxScore = (mergedDocs.length > 0 && mergedDocs[0].getScore() != null)
                ? mergedDocs[0].getScore()
                : -1.0f;
        } else {
            maxScore = Arrays.stream(mergedDocs)
                .map(Document::getScore)
                .filter(Objects::nonNull)
                .max(Float::compare)
                .orElse(-1.0f);
        }

        QueryResponse queryResult = new QueryResponse();
        queryResult.setTook(new Date().getTime() - queryStartMs);
        queryResult.setTotal(mergedDocs.length);
        if (mergedDocs.length > 0) {
            queryResult.setMaxScore(maxScore);
        }
        queryResult.setDocs(mergedDocs);

        return queryResult;
    }

    public FetchResponse fetchHandler(FetchRequest request, Index index)
        throws GlobalExceptionHandler {
        if (request.getIds() == null) {
            throw new GlobalExceptionHandler("ids must be provided",
                StatusCode.INVALID_INPUT_VALUE);
        } if (request.getIds().length == 0) {
            throw new GlobalExceptionHandler(
                "Total documents to fetch must be greater than 0",
                StatusCode.INVALID_INPUT_VALUE);
        } else if (request.getIds().length > MAX_DOCUMENTS_TO_FETCH) {
            throw new GlobalExceptionHandler(
                "Total documents to fetch must be less than "
                    + MAX_DOCUMENTS_TO_FETCH, StatusCode.INVALID_INPUT_VALUE);
        } else if (request.getFields() != null && request.getFields().length < 1) {
            throw new GlobalExceptionHandler(
                "At least one field must be provided in the fields array",
                StatusCode.INVALID_INPUT_VALUE);
        }

        long queryStartMs = new Date().getTime();

        List<Document[]> docs = new ArrayList<>();
        StorageMetadata meta = null;
        if (index.getDataUpdatedAt() > 0) {
            meta = getStorageMeta(index);
            List<QueryEvent> queryEvents = new ArrayList<>();

            for (ComputeNode node : meta.getComputeNodes()) {
                if (node.getSegmentIds().isEmpty()) {
                    continue;
                }

                File partitionMeta = meta.getFileMap().get(
                    PathBuilder.buildPartitionMetaName(node.getNodeId()));
                assert partitionMeta != null;

                QueryEvent event = new QueryEvent();
                event.setType(DOCUMENT_FETCH);
                event.setShardId(TEMPORARY_SHARD_ID);
                event.setProjectId(index.getProjectId());
                event.setIndexName(index.getIndexName());
                event.setIndexClass(index.getIndexClass());
                event.setMappings(index.getMappings());
                event.setIds(request.getIds());
                event.setIncludeVectors(request.getIncludeVectors());
                event.setFields(request.getFields());
                event.setComputeNodeId(node.getNodeId());
                event.setMetaObjectKey(
                    PathBuilder.buildObjectKeyForRead(meta, partitionMeta.getName()));
                event.setMetaObjectVersionId(partitionMeta.getVersionId());

                queryEvents.add(event);
            }

            // Invoke QueryExecutor concurrently
            docs = invokeQueryExecutorSyncParallel(queryEvents);
        }

        Future<InvokeResponse> refreshedDocsFuture = null;
        if (request.getConsistentRead()) {
            RefreshEvent event = new RefreshEvent();
            event.setType(DOCUMENT_FETCH);
            event.setIndex(index);
            event.setIds(request.getIds());
            event.setIncludeVectors(request.getIncludeVectors());
            event.setFields(request.getFields());
            event.setStorageMetadata(meta);
            refreshedDocsFuture =
                lambdaClient.invokeAsync(INDEX_REFRESHER_NAME, INDEX_REFRESHER_QUALIFIER, event);
        }

        Document[] mergedDocs;
        try {
            if (request.getConsistentRead()) {
                assert refreshedDocsFuture != null;
                InvokeResponse resp = refreshedDocsFuture.get();
                lambdaClient.validateResponse(resp);
                RefreshedDocs refreshedDocs = JsonParser.parseRefreshedDocs(
                    resp.payload().asUtf8String());
                mergedDocs = mergeWithRefreshedDocs(docs, refreshedDocs);
            } else {
                mergedDocs = mergeDocs(docs);
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new GlobalExceptionHandler("Failed to refresh documents: " + e.getMessage(),
                StatusCode.SERVER_ERROR);
        }

        FetchResponse fetchResult = new FetchResponse();
        fetchResult.setTook(new Date().getTime() - queryStartMs);
        fetchResult.setTotal(mergedDocs.length);
        fetchResult.setDocs(mergedDocs);

        return fetchResult;
    }

    private StorageMetadata getStorageMeta(Index index) throws GlobalExceptionHandler {
        String indexKey = index.getProjectId() + index.getIndexName();
        StorageMetadata meta = cachedMetaMap.get(indexKey);
        if (meta == null || meta.getDataUpdatedAt() < index.getDataUpdatedAt()) {
            ResponseBytes<GetObjectResponse> resp = s3Client.tryGetObject(INDEX_BUCKET,
                PathBuilder.buildMetaKey(index, TEMPORARY_SHARD_ID));
            if (resp == null) {
                throw new GlobalExceptionHandler("Index is not active",
                    StatusCode.NOT_FOUND);
            }

            meta = gson.fromJson(resp.asUtf8String(), StorageMetadata.class);

            cachedMetaMap.put(indexKey, meta);
        }

        return meta;
    }

    private List<Document[]> invokeQueryExecutorSyncParallel(List<QueryEvent> queryEvents)
        throws GlobalExceptionHandler {

        List<DocumentQueryTask> tasks = new ArrayList<>();
        for (QueryEvent queryEvent : queryEvents) {
            tasks.add(new DocumentQueryTask(queryEvent, lambdaClient));
        }

        List<Future<Document[]>> futures;
        try {
             futures = Threads.getIOExecutor().invokeAll(tasks);
        } catch (InterruptedException e) {
            throw new GlobalExceptionHandler("Failed to invoke executors: " + e.getMessage(),
                StatusCode.SERVER_ERROR);
        }

        // Gather documents from the result set
        List<Document[]> docs = new ArrayList<>();
        try {
            for (Future<Document[]> future : futures) {
                docs.add(future.get());
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new GlobalExceptionHandler("Failed to query documents: " + e.getMessage(),
                StatusCode.SERVER_ERROR);
        }

        return docs;
    }

    private static class DocumentQueryTask implements Callable<Document[]> {

        private final QueryEvent queryEvent;
        private final Lambda lambdaClient;

        public DocumentQueryTask(QueryEvent queryEvent, Lambda lambdaClient) {
            this.queryEvent = queryEvent;
            this.lambdaClient = lambdaClient;
        }

        @Override
        public Document[] call()
            throws GlobalExceptionHandler {

            InvokeResponse resp = lambdaClient.invokeSync(
                buildQueryExecutorName(
                    queryEvent.getComputeNodeId(), queryEvent.getIndexClass()),
                QUERY_EXECUTOR_QUALIFIER, queryEvent);

            lambdaClient.validateResponse(resp);

            return JsonParser.parseDocuments(resp.payload().asUtf8String());
        }
    }
}
