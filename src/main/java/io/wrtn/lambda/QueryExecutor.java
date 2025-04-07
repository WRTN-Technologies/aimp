package io.wrtn.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import io.wrtn.engine.lucene.QueryExecuteHelper;

import io.wrtn.infra.aws.S3;
import io.wrtn.model.event.QueryEvent;
import io.wrtn.model.document.Document;
import io.wrtn.model.storage.File;
import io.wrtn.model.storage.StorageMetadata;
import io.wrtn.util.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.lambda.model.LambdaException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static io.wrtn.infra.aws.Constants.S3.INDEX_BUCKET;
import static io.wrtn.util.Constants.CommandType.DOCUMENT_FETCH;
import static io.wrtn.util.Constants.CommandType.DOCUMENT_QUERY;

import static io.wrtn.util.Constants.Config.FS_TEMP_PATH;
import static io.wrtn.util.Constants.Config.TEMPORARY_SHARD_ID;
import static io.wrtn.util.JsonParser.exceptionGson;
import static io.wrtn.util.JsonParser.gson;
import static org.apache.lucene.index.IndexFileNames.SEGMENTS;

public class QueryExecutor implements
    RequestHandler<Map<String, Object>, String> {

    // TODO: Max QueryExecuteHelper instances to limit memory usage
    private static final Map<String, QueryExecuteHelper> helperMap = new HashMap<>();
    final S3 s3Client;

    public QueryExecutor() {
        this.s3Client = new S3();
    }

    @Override
    public String handleRequest(
        Map<String, Object> queryEventMap,
        Context context
    ) {
        GlobalLogger.initialize(false);
        QueryEvent queryEvent = gson.fromJson(gson.toJson(queryEventMap), QueryEvent.class);
        try {
            long start = System.currentTimeMillis();

            String projectId = queryEvent.getProjectId();
            String indexName = queryEvent.getIndexName();
            String indexKey = projectId + indexName + queryEvent.getMetaObjectKey();

            // Make search helper components
            QueryExecuteHelper queryExecuteHelper = helperMap.get(indexKey);
            if (queryExecuteHelper == null) {
                StorageMetadata meta = gson.fromJson(s3Client.getVersionedObject(INDEX_BUCKET,
                        queryEvent.getMetaObjectKey(), queryEvent.getMetaObjectVersionId())
                    .asUtf8String(), StorageMetadata.class);

                // Write segments_N bytes to local disk
                for (Entry<String, File> entry : meta.getFileMap().entrySet()) {
                    String name = entry.getKey();
                    if (name.contains(SEGMENTS)) {
                        File segmentFile = entry.getValue();
                        Path localPath = Paths.get(PathBuilder.buildFsCachePath(
                            FS_TEMP_PATH, projectId, indexName, TEMPORARY_SHARD_ID) + name);
                        Files.createDirectories(localPath.getParent());
                        Files.write(localPath, segmentFile.getFullBytes());
                        break;
                    }
                }

                queryExecuteHelper = new QueryExecuteHelper(
                    projectId,
                    indexName,
                    queryEvent.getMappings(),
                    meta,
                    queryEvent.getMetaObjectVersionId(),
                    s3Client
                );
                helperMap.put(indexKey, queryExecuteHelper);
            } else {
                if (!queryEvent.getMetaObjectVersionId()
                    .equals(queryExecuteHelper.getSnapshotVersionId())) {
                    StorageMetadata meta = gson.fromJson(s3Client.getVersionedObject(INDEX_BUCKET,
                            queryEvent.getMetaObjectKey(), queryEvent.getMetaObjectVersionId())
                        .asUtf8String(), StorageMetadata.class);

                    queryExecuteHelper.updateIfChanged(meta, queryEvent.getMetaObjectVersionId());
                }
            }

            Document[] docs;
            // Process request
            if (queryEvent.getType().equals(DOCUMENT_QUERY)) {
                docs = queryExecuteHelper.query(queryEvent.getQuery(),
                    queryEvent.getSize(), queryEvent.getIncludeVectors(), queryEvent.getSort(),
                    queryEvent.getTrackScores(), queryEvent.getFields());

            } else if (queryEvent.getType().equals(DOCUMENT_FETCH)) {
                docs = queryExecuteHelper.fetch(queryEvent.getIds(),
                    queryEvent.getIncludeVectors(), queryEvent.getFields());

            } else {
                throw new GlobalExceptionHandler("Command must be one of query, fetch",
                    StatusCode.BAD_REQUEST);
            }

            long took = System.currentTimeMillis() - start;
            if (took >= 10000) {
                GlobalLogger.warn("Query execution took " + took + "ms requestId: "
                    + context.getAwsRequestId());
                GlobalLogger.info("Request: " + queryEvent);
            }

            return gson.toJson(docs);

        } catch (GlobalExceptionHandler ge) {
            GlobalLogger.error("Exception: " + ge);
            GlobalLogger.error("Event: " + queryEvent);
            GlobalLogger.error(Arrays.toString(ge.getStackTrace()));
            throw new RuntimeException(exceptionGson.toJson(ge, GlobalExceptionHandler.class));
        } catch (S3Exception s3e) {
            GlobalLogger.error("Event: " + queryEvent);
            GlobalLogger.error(Arrays.toString(s3e.getStackTrace()));
            GlobalLogger.error(s3e.getMessage());
            if (s3e.isThrottlingException()) {
                throw new RuntimeException(
                    exceptionGson.toJson(
                        new GlobalExceptionHandler(
                            "Please reduce your request rate.",
                            StatusCode.TOO_MANY_REQUESTS),
                        GlobalExceptionHandler.class));
            } else {
                throw new RuntimeException(
                    exceptionGson.toJson(
                        new GlobalExceptionHandler(
                            "Unknown error occurred.",
                            StatusCode.SERVER_ERROR),
                        GlobalExceptionHandler.class));
            }
        } catch (LambdaException le) {
            GlobalLogger.error("Event: " + queryEvent);
            GlobalLogger.error(Arrays.toString(le.getStackTrace()));

            if (le.isThrottlingException()) {
                throw new RuntimeException(
                    exceptionGson.toJson(
                        new GlobalExceptionHandler(
                            "Please reduce your request rate."
                                + le.getMessage(),
                            StatusCode.TOO_MANY_REQUESTS),
                        GlobalExceptionHandler.class));
            } else {
                throw new RuntimeException(
                    exceptionGson.toJson(
                        new GlobalExceptionHandler(
                            "Unknown error occurred.",
                            StatusCode.SERVER_ERROR),
                        GlobalExceptionHandler.class));
            }
        } catch (SdkException se) {
            GlobalLogger.error("Event: " + queryEvent);
            GlobalLogger.error(Arrays.toString(se.getStackTrace()));
            throw new RuntimeException(
                exceptionGson.toJson(
                    new GlobalExceptionHandler(
                        "Unknown error occurred.",
                        StatusCode.SERVER_ERROR),
                    GlobalExceptionHandler.class));

        } catch (Exception e) {
            GlobalLogger.error("Event: " + queryEvent);
            GlobalLogger.error(Arrays.toString(e.getStackTrace()));
            throw new RuntimeException(
                exceptionGson.toJson(
                    new GlobalExceptionHandler(e.getMessage(), StatusCode.SERVER_ERROR),
                    GlobalExceptionHandler.class));
        }
    }
}