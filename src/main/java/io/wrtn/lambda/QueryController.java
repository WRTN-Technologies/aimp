package io.wrtn.lambda;

import static io.wrtn.util.Constants.HttpMethod.POST;
import static io.wrtn.util.HttpUtil.setCommonResponseHeaders;
import static io.wrtn.util.JsonParser.gson;
import static io.wrtn.util.JsonParser.parseRequestBody;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.wrtn.dto.MessageResponse;
import io.wrtn.dto.FetchRequest;
import io.wrtn.dto.FetchResponse;
import io.wrtn.dto.QueryRequest;
import io.wrtn.dto.QueryResponse;
import io.wrtn.infra.aws.DynamoDB;
import io.wrtn.infra.aws.Lambda;
import io.wrtn.infra.aws.S3;
import io.wrtn.model.index.Index;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.GlobalLogger;
import io.wrtn.util.StatusCode;
import io.wrtn.util.Validation;
import java.util.Arrays;
import java.util.Map;

public class QueryController implements
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    final DynamoDB ddbClient;
    final S3 s3Client;
    final Lambda lambdaClient;

    private final QueryRouter queryRouter;
    private final Validation validation;
    private static final MessageResponse messageResponse = new MessageResponse();

    public QueryController() {
        this.ddbClient = new DynamoDB();
        this.s3Client = new S3();
        this.lambdaClient = new Lambda();
        this.queryRouter = new QueryRouter(s3Client, lambdaClient);
        this.validation = new Validation(null);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
        APIGatewayProxyRequestEvent httpRequest, Context context) {

        if (httpRequest.getRequestContext() == null) {
            return null;
        }

        APIGatewayProxyResponseEvent httpResponse = new APIGatewayProxyResponseEvent();

        try {
            setCommonResponseHeaders(httpResponse);
            httpResponse.setStatusCode(StatusCode.SUCCESS.getStatusCode());

            validation.validateContentType(httpRequest.getHeaders());

            String method = httpRequest.getHttpMethod().toUpperCase();
            if (!method.equals(POST)) {
                GlobalLogger.debug(httpRequest.getPath());
                throw new GlobalExceptionHandler(
                    method + " is not allowed", StatusCode.METHOD_NOT_ALLOWED);
            }

            String projectId = httpRequest.getPathParameters().get("projectId");
            String indexName = httpRequest.getPathParameters().get("indexName");

            Index index = ddbClient.getIndex(projectId, indexName);
            if (index == null) {
                throw new GlobalExceptionHandler(
                    "Index " + indexName + " not found",
                    StatusCode.NOT_FOUND);
            }

            // Validate project API key
            String apiKey = httpRequest.getHeaders().get("x-api-key");
            validation.validateProjectApiKey(apiKey, index.getProjectApiKey());

            String[] parts = httpRequest.getPath().split("/");
            String endpoint = parts[parts.length - 1];

            switch (endpoint) {
                case "query":
                    QueryResponse queryResult = queryRouter.queryHandler(
                        parseRequestBody(httpRequest.getBody(), QueryRequest.class),
                        index);
                    httpResponse.setBody(gson.toJson(queryResult));

                    // for poc, to be removed
                    httpResponse.setHeaders(Map.of("numDocs",
                        String.valueOf(index.getNumDocs()), "request_id",
                        context.getAwsRequestId(), "took",
                        String.valueOf(queryResult.getTook()), "Content-Type",
                        "application/json"));

                    if (queryResult.getTook() >= 10000) {
                        GlobalLogger.warn("Query took " + queryResult.getTook() + "ms" +
                            " requestId: " + context.getAwsRequestId());
                        GlobalLogger.info("Request: " + httpRequest.getBody());
                    }

                    break;
                case "fetch":
                    FetchResponse fetchResult = queryRouter.fetchHandler(
                        parseRequestBody(httpRequest.getBody(), FetchRequest.class),
                        index);
                    httpResponse.setBody(gson.toJson(fetchResult));

                    if (fetchResult.getTook() >= 10000) {
                        GlobalLogger.warn("Fetch took " + fetchResult.getTook() + "ms" +
                            " requestId: " + context.getAwsRequestId());
                        GlobalLogger.info("Request: " + httpRequest.getBody());
                    }

                    break;
                default:
                    throw new GlobalExceptionHandler(
                        "Invalid path: " + httpRequest.getPath(),
                        StatusCode.BAD_REQUEST);
            }

            return httpResponse;
        } catch (Exception e) {
            // for poc, to be removed
            httpResponse.setHeaders(Map.of("request_id",
                context.getAwsRequestId(), "Content-Type", "application/json"));

            if (e instanceof GlobalExceptionHandler ge) {
                if (ge.getErrorCode().getStatusCode() == StatusCode.SERVER_ERROR.getStatusCode()) {
                    GlobalLogger.error("Request: " + httpRequest.getBody());
                    GlobalLogger.error("Headers" + httpRequest.getHeaders());
                    GlobalLogger.error(e.getMessage());
                    GlobalLogger.error(Arrays.toString(e.getStackTrace()));
                }
                httpResponse.setStatusCode(ge.getErrorCode().getStatusCode());
                messageResponse.setMessage(e.getMessage());
                httpResponse.setBody(gson.toJson(messageResponse));
            } else {
                httpResponse.setStatusCode(StatusCode.SERVER_ERROR.getStatusCode());
                messageResponse.setMessage(StatusCode.SERVER_ERROR.getStatusDescription());
                httpResponse.setBody(gson.toJson(messageResponse));

                GlobalLogger.error("Request: " + httpRequest.getBody());
                GlobalLogger.error("Headers" + httpRequest.getHeaders());
                GlobalLogger.error(e.getMessage());
                GlobalLogger.error(Arrays.toString(e.getStackTrace()));
            }

            return httpResponse;
        }
    }
}
