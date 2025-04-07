package io.wrtn.lambda;

import static io.wrtn.util.Constants.HttpMethod.DELETE;
import static io.wrtn.util.Constants.HttpMethod.GET;
import static io.wrtn.util.Constants.HttpMethod.PATCH;
import static io.wrtn.util.Constants.HttpMethod.POST;
import static io.wrtn.util.HttpUtil.setCommonResponseHeaders;
import static io.wrtn.util.JsonParser.gson;
import static io.wrtn.util.JsonParser.parseRequestBody;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.wrtn.dto.BulkUpsertRequest;
import io.wrtn.dto.DeleteRequest;
import io.wrtn.dto.MessageResponse;
import io.wrtn.dto.IndexCreateRequest;
import io.wrtn.dto.IndexCreateResponse;
import io.wrtn.dto.IndexDescribeResponse;
import io.wrtn.dto.IndexListResponse;
import io.wrtn.dto.IndexUpdateRequest;
import io.wrtn.dto.ProjectCreateRequest;
import io.wrtn.dto.ProjectCreateResponse;
import io.wrtn.dto.ProjectDescribeResponse;
import io.wrtn.dto.ProjectListResponse;
import io.wrtn.dto.ProjectUpdateRequest;
import io.wrtn.dto.ProjectUpdateResponse;
import io.wrtn.dto.IndexUpdateResponse;
import io.wrtn.dto.UpsertRequest;
import io.wrtn.infra.aws.ApiGateway;
import io.wrtn.infra.aws.DynamoDB;
import io.wrtn.infra.aws.EFS;
import io.wrtn.infra.aws.Lambda;
import io.wrtn.infra.aws.S3;
import io.wrtn.infra.aws.SQS;
import io.wrtn.infra.aws.SecretsManager;
import io.wrtn.model.index.Index;
import io.wrtn.model.project.Project;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.GlobalLogger;
import io.wrtn.util.StatusCode;
import io.wrtn.util.Validation;
import java.util.Arrays;
import java.util.Map;

public class RequestController implements
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    final DynamoDB ddbClient;
    final S3 s3Client;
    final Lambda lambdaClient;
    final EFS efsClient;
    final SecretsManager secretsManager;
    final SQS sqsClient;
    final ApiGateway apiGatewayClient;
    private final ProjectController projectController;
    private final IndexController indexController;
    private final IndexWriter indexWriter;
    private final Validation validation;
    private static final MessageResponse messageResponse = new MessageResponse();

    public RequestController() {
        this.ddbClient = new DynamoDB();
        this.s3Client = new S3();
        this.lambdaClient = new Lambda();
        this.efsClient = new EFS(s3Client);
        this.sqsClient = new SQS();
        this.secretsManager = new SecretsManager();
        this.apiGatewayClient = new ApiGateway();
        this.validation = new Validation(secretsManager);
        this.projectController = new ProjectController(ddbClient, sqsClient, apiGatewayClient,
            validation);
        this.indexController = new IndexController(s3Client, ddbClient, sqsClient, validation);
        this.indexWriter = new IndexWriter(s3Client, efsClient, validation);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
        APIGatewayProxyRequestEvent httpRequest, Context context) {

        APIGatewayProxyResponseEvent httpResponse = new APIGatewayProxyResponseEvent();

        try {
            GlobalLogger.initialize(false);

            setCommonResponseHeaders(httpResponse);
            httpResponse.setStatusCode(StatusCode.SUCCESS.getStatusCode());

            // Project request path: /v1alpha2/projects/:projectId
            validation.validateContentType(httpRequest.getHeaders());

            String method = httpRequest.getHttpMethod().toUpperCase();
            String apiKey = httpRequest.getHeaders().get("x-api-key");

            if (httpRequest.getPathParameters() == null) {
                // Validate admin API key
                validation.validateAdminApiKey(apiKey);
                switch (method) {
                    case GET -> {
                        ProjectListResponse response = projectController.listProject();
                        httpResponse.setBody(gson.toJson(response));
                    }
                    case POST -> {
                        ProjectCreateResponse response = projectController.createProject(
                            parseRequestBody(httpRequest.getBody(), ProjectCreateRequest.class)
                        );

                        httpResponse.setBody(gson.toJson(response));
                    }
                    default -> throw new GlobalExceptionHandler(
                        "Invalid http method : " + method + " " + httpRequest.getPath(),
                        StatusCode.BAD_REQUEST);
                }
            } else {
                String projectId = httpRequest.getPathParameters().get("projectId");
                String indexName = httpRequest.getPathParameters().get("indexName");
                if (indexName == null) {
                    Project project = ddbClient.getProjectConsistent(projectId);
                    if (project == null) {
                        throw new GlobalExceptionHandler("Project " + projectId + " is not found",
                            StatusCode.NOT_FOUND);
                    }

                    if (httpRequest.getPath().endsWith(projectId)) {
                        // Validate admin API key
                        validation.validateAdminApiKey(httpRequest.getHeaders().get("x-api-key"));
                        switch (method) {
                            case GET -> {
                                ProjectDescribeResponse response = projectController.describeProject(
                                    project);
                                httpResponse.setBody(gson.toJson(response));
                            }
                            case DELETE -> {
                                projectController.deleteProject(project);
                                httpResponse.setStatusCode(StatusCode.ACCEPTED.getStatusCode());
                                messageResponse.setMessage("Project " + project.getProjectName()
                                    + " is queued for deletion");
                                httpResponse.setBody(gson.toJson(messageResponse));
                            }
                            case PATCH -> {
                                ProjectUpdateResponse response =
                                    projectController.updateProject(projectId, parseRequestBody(
                                        httpRequest.getBody(), ProjectUpdateRequest.class));

                                httpResponse.setBody(gson.toJson(response));
                            }
                            default -> throw new GlobalExceptionHandler(
                                "Invalid http method : " + method + " " + httpRequest.getPath(),
                                StatusCode.BAD_REQUEST);
                        }
                    } else {
                        // Validate project API key
                        validation.validateProjectApiKey(apiKey, project.getProjectApiKey());
                        switch (method) {
                            case GET -> {
                                IndexListResponse response = indexController.listIndex(projectId);
                                httpResponse.setBody(gson.toJson(response));
                            }
                            case POST -> {
                                IndexCreateRequest indexCreateRequest = parseRequestBody(
                                    httpRequest.getBody(), IndexCreateRequest.class);
                                IndexCreateResponse response = indexController.createIndex(
                                    indexCreateRequest, projectId, apiKey);
                                httpResponse.setStatusCode(StatusCode.ACCEPTED.getStatusCode());
                                httpResponse.setBody(gson.toJson(response));
                            }
                            default -> throw new GlobalExceptionHandler(
                                "Invalid http method : " + method + " " + httpRequest.getPath(),
                                StatusCode.BAD_REQUEST);
                        }
                    }
                } else {
                    if (httpRequest.getPath().endsWith(indexName)) {
                        Index index = ddbClient.getIndexConsistent(projectId, indexName);
                        if (index == null) {
                            throw new GlobalExceptionHandler(
                                "Index " + indexName + " not found",
                                StatusCode.NOT_FOUND);
                        }
                        // Validate project API key
                        validation.validateProjectApiKey(apiKey, index.getProjectApiKey());

                        switch (method) {
                            case GET -> {
                                IndexDescribeResponse response = indexController.describeIndex(
                                    index);
                                httpResponse.setBody(gson.toJson(response));
                            }
                            case DELETE -> {
                                indexController.deleteIndex(index);
                                httpResponse.setStatusCode(StatusCode.ACCEPTED.getStatusCode());
                                messageResponse.setMessage("Index " + index.getIndexName()
                                    + " of project " + index.getProjectId()
                                    + " is queued for deleting");
                                httpResponse.setBody(gson.toJson(messageResponse));
                            }
                            case PATCH -> {
                                IndexUpdateRequest request = gson.fromJson(httpRequest.getBody(),
                                    IndexUpdateRequest.class);
                                IndexUpdateResponse response = indexController.updateIndex(
                                    request, index);
                                httpResponse.setBody(gson.toJson(response));
                            }
                            default -> throw new GlobalExceptionHandler(
                                "Invalid http method : " + method + " " + httpRequest.getPath(),
                                StatusCode.BAD_REQUEST);
                        }
                    } else {
                        Index index = ddbClient.getIndex(projectId, indexName);
                        if (index == null) {
                            throw new GlobalExceptionHandler(
                                "Index " + indexName + " not found",
                                StatusCode.NOT_FOUND);
                        }
                        // Validate project API key
                        validation.validateProjectApiKey(apiKey, index.getProjectApiKey());

                        String[] parts = httpRequest.getPath().split("/");
                        String endpoint = parts[parts.length - 1];

                        if (!method.equals(POST)) {
                            GlobalLogger.debug(httpRequest.getPath());
                            if (!(method.equals(GET) && endpoint.equals("bulk-upsert"))) {
                                throw new GlobalExceptionHandler(
                                    method + " + " + endpoint + " is not allowed",
                                    StatusCode.METHOD_NOT_ALLOWED);
                            }
                        }
                        switch (endpoint) {
                            case "upsert":
                                indexWriter.upsertDocuments(
                                    parseRequestBody(httpRequest.getBody(), UpsertRequest.class),
                                    index, httpRequest.getBody());
                                httpResponse.setStatusCode(StatusCode.ACCEPTED.getStatusCode());
                                messageResponse.setMessage("Upsert request is accepted");
                                httpResponse.setBody(gson.toJson(messageResponse));

                                // for poc, to be removed
                                httpResponse.setHeaders(Map.of("numDocs",
                                    String.valueOf(index.getNumDocs()), "request_id",
                                    context.getAwsRequestId(), "Content-Type", "application/json"));
                                break;
                            case "delete":
                                indexWriter.deleteDocuments(
                                    parseRequestBody(httpRequest.getBody(), DeleteRequest.class),
                                    index);
                                httpResponse.setStatusCode(StatusCode.ACCEPTED.getStatusCode());
                                messageResponse.setMessage("Delete request is accepted");
                                httpResponse.setBody(gson.toJson(messageResponse));
                                break;
                            case "bulk-upsert":
                                if (method.equals(GET)) {
                                    httpResponse.setBody(
                                        gson.toJson(s3Client.createPresignedUrl(index)));
                                } else {
                                    indexWriter.bulkUpsertDocuments(
                                        parseRequestBody(httpRequest.getBody(),
                                            BulkUpsertRequest.class), index);
                                    httpResponse.setStatusCode(StatusCode.ACCEPTED.getStatusCode());
                                    messageResponse.setMessage("Bulk upsert request is accepted");
                                    httpResponse.setBody(gson.toJson(messageResponse));
                                }
                                break;
                            default:
                                throw new GlobalExceptionHandler(
                                    "Invalid path: " + httpRequest.getPath(),
                                    StatusCode.BAD_REQUEST);
                        }
                    }
                }
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
