package io.wrtn.infra.aws;

import static io.wrtn.infra.aws.Constants.AWS_REGION;

import static io.wrtn.util.Constants.Config.*;
import static io.wrtn.util.JsonParser.gson;

import java.util.*;

import io.wrtn.model.command.ControlCommand;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;
import io.wrtn.util.Threads;

public final class SQS {

    SqsAsyncClient sqsClient = SqsAsyncClient.builder()
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

    private void sendMessage(String queueUrl, String message, int delaySeconds) {
        sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(message)
            .delaySeconds(delaySeconds)
            .build()).join();
    }

    public void sendControlCommand(ControlCommand controlCommand) {
        sendMessage(CONTROL_QUEUE_URL, gson.toJson(controlCommand), 0);
    }

    public void sendControlCommandWithDelay(ControlCommand controlCommand, int seconds) {
        sendMessage(CONTROL_QUEUE_URL, gson.toJson(controlCommand), seconds);
    }

    public long getMessageCount(String queueUrl) {
        Map<QueueAttributeName, String> attributes = sqsClient.getQueueAttributes(
                GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES).build()).join()
            .attributes();
        return Long.parseLong(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
    }

    public void close() {
        sqsClient.close();
    }
}
