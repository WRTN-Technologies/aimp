package io.wrtn.infra.aws;

import static io.wrtn.infra.aws.Constants.AWS_REGION;
import static io.wrtn.infra.aws.Constants.Batch.INDEX_BUILDER_JOB_DEFINITION_NAME;
import static io.wrtn.util.JsonParser.gson;

import java.util.Map;
import io.wrtn.model.event.IndexBuildEvent;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.batch.BatchAsyncClient;
import software.amazon.awssdk.services.batch.model.*;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.StatusCode;
import io.wrtn.util.Threads;

public class Batch {

    BatchAsyncClient batchClient = BatchAsyncClient.builder()
        .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
        .asyncConfiguration(
            b -> b.advancedOption(SdkAdvancedAsyncClientOption
                    .FUTURE_COMPLETION_EXECUTOR,
                Threads.getIOExecutor()
            )
        )
        .httpClientBuilder(AwsCrtAsyncHttpClient.builder())
        .region(Region.of(AWS_REGION))
        .build();

    public void submitIndexBuilderJob(IndexBuildEvent event,
        String indexClass, String indexBuilderJobNamePrefix,
        String indexBuilderJobQueueName) throws GlobalExceptionHandler {
        try {
            Map<String, String> eventParams = Map.of("event", gson.toJson(event));

            batchClient.submitJob(SubmitJobRequest.builder()
                .jobDefinition(INDEX_BUILDER_JOB_DEFINITION_NAME)
                .jobName(buildIndexBuilderJobName(indexBuilderJobNamePrefix, event))
                .jobQueue(indexBuilderJobQueueName)
                .parameters(eventParams)
                .retryStrategy(RetryStrategy.builder().attempts(1).build())
                .build()).join();
        } catch (BatchException e) {
            throw new GlobalExceptionHandler(e.getMessage(), StatusCode.SERVER_ERROR);
        }
    }

    public void close() {
        batchClient.close();
    }

    private String buildIndexBuilderJobName(String prefix, IndexBuildEvent event) {
        return prefix + "-" + event.getProjectId() + "-" + event.getIndexName() + "-" + event.getShardId();
    }
}
