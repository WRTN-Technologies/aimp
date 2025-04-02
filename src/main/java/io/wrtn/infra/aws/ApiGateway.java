package io.wrtn.infra.aws;

import static io.wrtn.infra.aws.Constants.AWS_REGION;
import static io.wrtn.infra.aws.Constants.ApiGateway.API_GATEWAY_NAME;
import static io.wrtn.infra.aws.Constants.ApiGateway.API_GATEWAY_NAME_PRIVATE;
import static io.wrtn.infra.aws.Constants.ApiGateway.API_VERSION;
import static io.wrtn.infra.aws.Constants.ApiGateway.PROJECT_API_KEY_PREFIX;
import static io.wrtn.infra.aws.Constants.ApiGateway.PROJECT_USAGE_PLAN_PREFIX;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.ApiKey;
import software.amazon.awssdk.services.apigateway.model.ApiStage;
import software.amazon.awssdk.services.apigateway.model.CreateApiKeyRequest;
import software.amazon.awssdk.services.apigateway.model.CreateApiKeyResponse;
import software.amazon.awssdk.services.apigateway.model.CreateUsagePlanKeyRequest;
import software.amazon.awssdk.services.apigateway.model.CreateUsagePlanRequest;
import software.amazon.awssdk.services.apigateway.model.CreateUsagePlanResponse;
import software.amazon.awssdk.services.apigateway.model.DeleteApiKeyRequest;
import software.amazon.awssdk.services.apigateway.model.DeleteUsagePlanKeyRequest;
import software.amazon.awssdk.services.apigateway.model.DeleteUsagePlanRequest;
import software.amazon.awssdk.services.apigateway.model.GetApiKeysResponse;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.GetUsagePlansResponse;
import software.amazon.awssdk.services.apigateway.model.Op;
import software.amazon.awssdk.services.apigateway.model.PatchOperation;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.apigateway.model.ThrottleSettings;
import software.amazon.awssdk.services.apigateway.model.UpdateUsagePlanRequest;
import software.amazon.awssdk.services.apigateway.model.UsagePlan;
import software.amazon.awssdk.services.apigateway.paginators.GetApiKeysIterable;
import software.amazon.awssdk.services.apigateway.paginators.GetRestApisIterable;
import software.amazon.awssdk.services.apigateway.paginators.GetUsagePlansIterable;

public final class ApiGateway {

    ApiGatewayClient apiGatewayClient = ApiGatewayClient.builder()
        .region(Region.of(AWS_REGION))
        .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
        .build();

    public String createProjectApiKeyWithLimit(String projectId, double rateLimit) {

        List<String> apiIds = getApiIds();
        // AWS default account-level burst limit is the half of rate limit
        int burstLimit = (int) rateLimit / 2;

        CreateApiKeyResponse createApiKeyResponse =
            apiGatewayClient.createApiKey(CreateApiKeyRequest.builder()
                .name(buildProjectApiKeyName(projectId))
                .enabled(true).generateDistinctId(true).build());

        CreateUsagePlanResponse createUsagePlanResponse;
        if (apiIds.size() == 2) {
            createUsagePlanResponse =
                apiGatewayClient.createUsagePlan(CreateUsagePlanRequest.builder()
                    .apiStages(
                        ApiStage.builder().apiId(apiIds.getFirst()).stage(API_VERSION).build(),
                        ApiStage.builder().apiId(apiIds.getLast()).stage(API_VERSION).build()
                    )
                    .throttle(ThrottleSettings.builder().rateLimit(rateLimit).burstLimit(burstLimit)
                        .build())
                    .name(buildProjectUsagePlanName(projectId))
                    .build());
        } else {
            assert  apiIds.size() == 1;
            createUsagePlanResponse =
                apiGatewayClient.createUsagePlan(CreateUsagePlanRequest.builder()
                    .apiStages(
                        ApiStage.builder().apiId(apiIds.getFirst()).stage(API_VERSION).build()
                    )
                    .throttle(ThrottleSettings.builder().rateLimit(rateLimit).burstLimit(burstLimit)
                        .build())
                    .name(buildProjectUsagePlanName(projectId))
                    .build());
        }

        apiGatewayClient.createUsagePlanKey(CreateUsagePlanKeyRequest.builder()
            .usagePlanId(createUsagePlanResponse.id()).keyId(createApiKeyResponse.id())
            .keyType("API_KEY")
            .build());

        return createApiKeyResponse.value();
    }

    public void updateProjectRateLimit(String projectId, double newRateLimit) {
        // AWS default account-level burst limit is the half of rate limit
        int newBurstLimit = (int) newRateLimit / 2;
        apiGatewayClient.updateUsagePlan(UpdateUsagePlanRequest.builder()
            .usagePlanId(getUsagePlanId(projectId))
            .patchOperations(
                PatchOperation.builder()
                    .path("/throttle/rateLimit").value(Double.toString(newRateLimit)).op(Op.REPLACE).build(),
                PatchOperation.builder()
                    .path("/throttle/burstLimit").value(Integer.toString(newBurstLimit)).op(Op.REPLACE).build())
            .build());
    }

    public void deleteProjectApiKey(String projectId) {

        List<String> apiIds = getApiIds();
        String apiKeyId = getApiKeyId(projectId);
        String usagePlanId = getUsagePlanId(projectId);

        apiGatewayClient.deleteUsagePlanKey(DeleteUsagePlanKeyRequest.builder()
            .keyId(apiKeyId)
            .usagePlanId(usagePlanId)
            .build());

        if (apiIds.size() == 2) {
            apiGatewayClient.updateUsagePlan(UpdateUsagePlanRequest.builder()
                .usagePlanId(usagePlanId)
                .patchOperations(
                    PatchOperation.builder()
                        .path("/apiStages").value(apiIds.getFirst() + ":" + API_VERSION)
                        .op(Op.REMOVE).build(),
                    PatchOperation.builder()
                        .path("/apiStages").value(apiIds.getLast() + ":" + API_VERSION)
                        .op(Op.REMOVE).build()
                )
                .build());
        } else {
            assert apiIds.size() == 1;
            apiGatewayClient.updateUsagePlan(UpdateUsagePlanRequest.builder()
                .usagePlanId(usagePlanId)
                .patchOperations(
                    PatchOperation.builder()
                        .path("/apiStages").value(apiIds.getFirst() + ":" + API_VERSION)
                        .op(Op.REMOVE).build()
                )
                .build());
        }

        apiGatewayClient.deleteUsagePlan(DeleteUsagePlanRequest.builder()
            .usagePlanId(usagePlanId)
            .build());
        apiGatewayClient.deleteApiKey(DeleteApiKeyRequest.builder().apiKey(apiKeyId).build());
    }

    private List<String> getApiIds() {
        List<String> apiIds = new ArrayList<>();
        GetRestApisIterable restApisIterable = apiGatewayClient.getRestApisPaginator();
        for (GetRestApisResponse restApis : restApisIterable) {
            for (RestApi restApi : restApis.items()) {
                if (restApi.name().equals(API_GATEWAY_NAME)) {
                    apiIds.add(restApi.id());
                } else if (restApi.name().equals(API_GATEWAY_NAME_PRIVATE)) {
                    apiIds.add(restApi.id());
                }
            }

            if (apiIds.size() >= 2) {
                break;
            }
        }

        if (apiIds.isEmpty()) {
            throw new RuntimeException("API Gateway not found");
        }

        return apiIds;
    }

    public String getApiKeyId(String projectId) {
        String apiKeyId = null;
        GetApiKeysIterable apiKeysIterable = apiGatewayClient.getApiKeysPaginator();
        for (GetApiKeysResponse apiKeys : apiKeysIterable) {
            for (ApiKey apiKey : apiKeys.items()) {
                if (apiKey.name().equals(buildProjectApiKeyName(projectId))) {
                    apiKeyId = apiKey.id();
                    break;
                }
            }

            if (apiKeyId != null) {
                break;
            }
        }

        Objects.requireNonNull(apiKeyId, "API Key not found");

        return apiKeyId;
    }

    public String getUsagePlanId(String projectId) {
        String usagePlanId = null;
        GetUsagePlansIterable usagePlansIterable = apiGatewayClient.getUsagePlansPaginator();
        for (GetUsagePlansResponse usagePlans : usagePlansIterable) {
            for (UsagePlan usagePlan : usagePlans.items()) {
                if (usagePlan.name().equals(buildProjectUsagePlanName(projectId))) {
                    usagePlanId = usagePlan.id();
                    break;
                }
            }

            if (usagePlanId != null) {
                break;
            }
        }

        Objects.requireNonNull(usagePlanId, "Usage Plan not found");

        return usagePlanId;
    }

    private String buildProjectApiKeyName(String projectId) {
        return PROJECT_API_KEY_PREFIX + "-" + projectId;
    }

    private String buildProjectUsagePlanName(String projectId) {
        return PROJECT_USAGE_PLAN_PREFIX + "-" + projectId;
    }
}
