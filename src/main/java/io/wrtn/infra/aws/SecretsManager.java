package io.wrtn.infra.aws;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import io.wrtn.util.Threads;

import static io.wrtn.infra.aws.Constants.AWS_REGION;

public final class SecretsManager {

    SecretsManagerAsyncClient secretsClient = SecretsManagerAsyncClient.builder()
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

    public String getSecretValue(String secretName) {
        return secretsClient.getSecretValue(GetSecretValueRequest.builder()
            .secretId(secretName).build()).join().secretString();
    }

    public void close() {
        secretsClient.close();
    }
}
