package io.wrtn.lambda;

import static io.wrtn.util.Constants.CommandType.DOCUMENT_FETCH;
import static io.wrtn.util.Constants.CommandType.DOCUMENT_QUERY;
import static io.wrtn.util.JsonParser.exceptionGson;
import static io.wrtn.util.JsonParser.gson;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.wrtn.engine.lucene.IndexRefreshHelper;
import io.wrtn.infra.aws.EFS;
import io.wrtn.infra.aws.Lambda;
import io.wrtn.infra.aws.S3;
import io.wrtn.model.document.RefreshedDocs;
import io.wrtn.model.event.RefreshEvent;
import io.wrtn.model.index.Index;
import io.wrtn.util.GlobalLogger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.StatusCode;

public class IndexRefresher implements
    RequestHandler<Map<String, Object>, String> {

    private static final Map<String, IndexRefreshHelper> helperMap = new HashMap<>();

    final EFS efsClient;
    final S3 s3Client;
    final Lambda lambdaClient;

    public IndexRefresher() {
        this.s3Client = new S3();
        this.efsClient = new EFS(s3Client);
        this.lambdaClient = new Lambda();
    }

    @Override
    public String handleRequest(
        Map<String, Object> eventMap,
        Context context
    ) {

        GlobalLogger.initialize(false);
        RefreshEvent event = gson.fromJson(gson.toJson(eventMap), RefreshEvent.class);
        try {
            long start = System.currentTimeMillis();

            Index index = event.getIndex();
            String indexKey = index.getProjectId() + index.getIndexName();

            // Make search helper components
            IndexRefreshHelper helper = helperMap.get(indexKey);
            if (helper == null) {
                helper = new IndexRefreshHelper(
                    index,
                    efsClient,
                    s3Client,
                    lambdaClient
                );
                helperMap.put(indexKey, helper);
            }

            helper.refreshIndex(index, event.getStorageMetadata());

            RefreshedDocs docs;
            // Process request
            if (event.getType().equals(DOCUMENT_QUERY)) {
                docs = helper.query(event.getQuery(), event.getSize(),
                    event.getIncludeVectors(), event.getSort(),
                    event.getTrackScores(), event.getFields());
            } else if (event.getType().equals(DOCUMENT_FETCH)) {
                docs = helper.fetch(event.getIds(), event.getIncludeVectors(), event.getFields());
            } else {
                throw new GlobalExceptionHandler("Command must be one of query, fetch",
                    StatusCode.BAD_REQUEST);
            }

            long took = System.currentTimeMillis() - start;
            if (took >= 10000) {
                GlobalLogger.warn("Refreshing took " + took + "ms requestId: "
                    + context.getAwsRequestId());
                GlobalLogger.info("Request: " + event);
            }

            return gson.toJson(docs);

        } catch (GlobalExceptionHandler ge) {
            GlobalLogger.error("Exception: " + ge);
            GlobalLogger.error("Event: " + event);
            GlobalLogger.error(Arrays.toString(ge.getStackTrace()));
            throw new RuntimeException(exceptionGson.toJson(ge, GlobalExceptionHandler.class));
        } catch (Exception e) {
            GlobalLogger.error("Exception: " + e);
            GlobalLogger.error("Event: " + event.toString());
            GlobalLogger.error(Arrays.toString(e.getStackTrace()));
            throw new RuntimeException(
                exceptionGson.toJson(
                    new GlobalExceptionHandler(e.getMessage(), StatusCode.SERVER_ERROR),
                    GlobalExceptionHandler.class));
        }
    }
}
