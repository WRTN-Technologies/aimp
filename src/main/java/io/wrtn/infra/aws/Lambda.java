package io.wrtn.infra.aws;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.wrtn.util.GlobalLogger;
import java.util.concurrent.Future;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.StatusCode;

import io.wrtn.util.Threads;

import static io.wrtn.infra.aws.Constants.AWS_REGION;
import static io.wrtn.util.JsonParser.exceptionGson;
import static io.wrtn.util.JsonParser.gson;

public class Lambda {

    LambdaClient lambdaSyncClient = LambdaClient.builder()
        .region(Region.of(AWS_REGION))
        .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
        .httpClient(AwsCrtHttpClient.builder().maxConcurrency(2000).build())
        .overrideConfiguration(o -> o.retryStrategy(b -> b.maxAttempts(1)))
        .build();

    LambdaAsyncClient lambdaClient = LambdaAsyncClient.builder().asyncConfiguration(
            b -> b.advancedOption(SdkAdvancedAsyncClientOption
                    .FUTURE_COMPLETION_EXECUTOR,
                Threads.getIOExecutor()
            )
        )
        .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
        .region(Region.of(AWS_REGION))
        .httpClient(AwsCrtAsyncHttpClient.builder().maxConcurrency(2000).build())
        .overrideConfiguration(o -> o.retryStrategy(b -> b.maxAttempts(1)))
        .build();

    public int getLambdaTimeoutSeconds(String name) {
        return lambdaClient.getFunctionConfiguration(GetFunctionConfigurationRequest.builder()
            .functionName(name).build()).join().timeout();
    }

    public void invokeAsyncEvent(String name, String qualifier, Object payload) {
        lambdaClient.invoke(InvokeRequest.builder()
            .invocationType(InvocationType.EVENT)
            .functionName(name)
            .qualifier(qualifier)
            .payload(SdkBytes.fromUtf8String(gson.toJson(payload)))
            .build());
    }

    public Future<InvokeResponse> invokeAsync(String name, String qualifier, Object payload) {
        return lambdaClient.invoke(InvokeRequest.builder()
            .invocationType(InvocationType.REQUEST_RESPONSE)
            .functionName(name)
            .qualifier(qualifier)
            .payload(SdkBytes.fromUtf8String(gson.toJson(payload)))
            .build());
    }

    public Future<InvokeResponse> invokeAsync(String name, String qualifier) {
        return lambdaClient.invoke(InvokeRequest.builder()
            .invocationType(InvocationType.REQUEST_RESPONSE)
            .functionName(name)
            .qualifier(qualifier)
            .build());
    }

    public InvokeResponse invokeSync(String name, String qualifier, Object payload) {
        return lambdaSyncClient.invoke(InvokeRequest.builder()
            .invocationType(InvocationType.REQUEST_RESPONSE)
            .functionName(name)
            .qualifier(qualifier)
            .payload(SdkBytes.fromUtf8String(gson.toJson(payload)))
            .build());
    }

    public void validateResponse(InvokeResponse resp)
        throws GlobalExceptionHandler {

        // Check if the invocation was successful
        if (resp.statusCode() != StatusCode.SUCCESS.getStatusCode()) {
            throw new GlobalExceptionHandler("Lambda invocation failed",
                StatusCode.SERVER_ERROR);
        }

        // Check if the response contains an error
        if (resp.functionError() != null) {
            try {
                GlobalExceptionHandler error = exceptionGson.fromJson(
                    JsonParser.parseString(resp.payload().asUtf8String()).getAsJsonObject()
                        .get("errorMessage").getAsString(), GlobalExceptionHandler.class);
                throw new GlobalExceptionHandler(error.getMessage(), error.getErrorCode());
            } catch (JsonSyntaxException | IllegalArgumentException e) {
                GlobalLogger.error("Error parsing QueryExecutor error response: " + e.getMessage());
                throw new RuntimeException("Unhandled error occurred in QueryExecutor");
            }
        }
    }

    public void close() {
        lambdaClient.close();
    }
}
