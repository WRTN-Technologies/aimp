package io.wrtn.infra.aws;

import io.wrtn.dto.BulkUpsertResponse;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.GlobalLogger;
import io.wrtn.util.StatusCode;
import io.wrtn.util.UUID;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import io.wrtn.model.index.Index;
import java.util.concurrent.ExecutionException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectVersionsPublisher;

import java.util.ArrayList;
import java.util.List;
import io.wrtn.util.Threads;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import static io.wrtn.infra.aws.Constants.AWS_REGION;
import static io.wrtn.infra.aws.Constants.S3.*;
import static io.wrtn.util.Constants.Config.BULK_UPSERT_SIZE_LIMIT_MB;
import static io.wrtn.util.Constants.Config.TEMPORARY_SHARD_ID;
import static io.wrtn.util.JsonParser.gson;
import static io.wrtn.util.PathBuilder.buildStorageIndexPath;
import static io.wrtn.util.PathBuilder.buildStorageKeyPath;
import static io.wrtn.util.PathBuilder.buildStorageShardPath;

public final class S3 {

    S3Client s3SyncClient = S3Client.builder()
        .httpClientBuilder(AwsCrtHttpClient.builder().maxConcurrency(5000))
        .region(Region.of(AWS_REGION))
        .build();

    S3AsyncClient s3Client = S3AsyncClient.crtBuilder()
        .futureCompletionExecutor(Threads.getIOExecutor())
        .region(Region.of(AWS_REGION)).build();

    S3Presigner s3Presigner = S3Presigner.builder()
        .s3Client(S3Client.builder().httpClientBuilder(AwsCrtHttpClient.builder())
            .build()).region(Region.of(AWS_REGION)).build();

    public boolean createIndexPrefixIfNotExists(String bucket, String projectId, String indexName) {
        String prefix = buildStorageIndexPath(projectId, indexName);
        String shardPrefix = buildStorageShardPath(projectId, indexName,
            TEMPORARY_SHARD_ID);
        List<S3Object> objects = listObjects(bucket, prefix);
        if (!objects.isEmpty()) {
            if (objects.size() == 1) {
                S3Object firstObject = objects.getFirst();
                if (!firstObject.key().equals(shardPrefix)) {
                    // Some unknown objects are in the prefix path
                    return false;
                }
            } else {
                // Some unknown objects are in the prefix path
                return false;
            }
        }

        s3Client.putObject(PutObjectRequest.builder()
            .bucket(bucket)
            .key(shardPrefix)
            .build(), AsyncRequestBody.empty()).join();

        return true;
    }

    public void deleteObjectsWithPrefix(String bucket, String prefix) {
        ListObjectsResponse resp = null;
        List<ObjectIdentifier> objects = new ArrayList<>();
        String nextMarker = null;
        while (resp == null || nextMarker != null) {
            // List all the objects
            if (nextMarker == null) {
                resp = s3Client.listObjects(ListObjectsRequest.builder()
                    .bucket(bucket)
                    .prefix(prefix).build()).join();
            } else {
                resp = s3Client.listObjects(ListObjectsRequest.builder()
                    .bucket(bucket)
                    .marker(nextMarker)
                    .prefix(prefix).build()).join();
            }

            for (S3Object object : resp.contents()) {
                objects.add(ObjectIdentifier.builder()
                    .key(object.key())
                    .build());
            }

            // Delete all the objects
            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder()
                    .objects(objects).build())
                .build());

            GlobalLogger.debug(
                objects.size() + " objects deleted, nextMarker" + resp.nextMarker());
            objects.clear();
            nextMarker = resp.nextMarker();
        }
    }

    public void deleteObjectVersionsWithPrefix(String bucket, String prefix) {
        ListObjectVersionsResponse resp = null;
        List<ObjectIdentifier> objects = new ArrayList<>();
        String nextKeyMarker = null;
        String nextVersionIdMarker = null;
        while (resp == null || nextKeyMarker != null) {
            // List all the object versions
            if (nextKeyMarker == null) {
                resp = s3Client.listObjectVersions(ListObjectVersionsRequest.builder()
                    .bucket(bucket)
                    .prefix(prefix).build()).join();
            } else {
                resp = s3Client.listObjectVersions(ListObjectVersionsRequest.builder()
                    .bucket(bucket)
                    .keyMarker(nextKeyMarker)
                    .versionIdMarker(nextVersionIdMarker)
                    .prefix(prefix).build()).join();
            }

            for (ObjectVersion object : resp.versions()) {
                objects.add(ObjectIdentifier.builder()
                    .key(object.key())
                    .versionId(object.versionId())
                    .build());
            }
            for (DeleteMarkerEntry object : resp.deleteMarkers()) {
                objects.add(ObjectIdentifier.builder()
                    .key(object.key())
                    .versionId(object.versionId())
                    .build());
            }

            // Delete all the object versions
            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder()
                    .objects(objects).build())
                .build());

            GlobalLogger.debug(
                objects.size() + " object versions deleted, nextKeyMarker" + resp.nextKeyMarker());
            objects.clear();
            nextKeyMarker = resp.nextKeyMarker();
            nextVersionIdMarker = resp.nextVersionIdMarker();
        }
    }

    public BulkUpsertResponse createPresignedUrl(Index index) {

        String objectKey = buildStorageKeyPath(index, TEMPORARY_SHARD_ID, UUID.generateUUID());

        PutObjectRequest objectRequest = PutObjectRequest.builder()
            .bucket(TEMP_BUCKET)
            .key(objectKey)
            .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(PRESIGNED_URL_EXPIRATION_IN_MINUTES))
            .putObjectRequest(objectRequest)
            .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        BulkUpsertResponse response = new BulkUpsertResponse();
        response.setUrl(presignedRequest.url().toExternalForm());
        response.setHttpMethod(presignedRequest.httpRequest().method().name());
        response.setObjectKey(objectKey);
        response.setSizeLimitBytes(BULK_UPSERT_SIZE_LIMIT_MB);

        return response;
    }

    // TODO: support for GZIP
    public ResponseInputStream<GetObjectResponse> getObjectStream(String bucket, String key) {
        return s3Client.getObject(GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build(), AsyncResponseTransformer.toBlockingInputStream()).join();
//        GZIPInputStream gzipStream = new GZIPInputStream(s3ObjectInputStream);

    }

    public CompletableFuture<ResponseInputStream<GetObjectResponse>> getObjectStreamAsync(
        String bucket, String key) {
        return s3Client.getObject(GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build(), AsyncResponseTransformer.toBlockingInputStream());
    }

    public HeadObjectResponse headObject(String bucket, String key) throws GlobalExceptionHandler {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key).build()).get();
        } catch (Exception e) {
            if (e.getCause() instanceof NoSuchKeyException) {
                throw new GlobalExceptionHandler("Object not found", StatusCode.INVALID_INPUT_VALUE);
            } else {
                throw new GlobalExceptionHandler("Failed to get object metadata",
                    StatusCode.SERVER_ERROR);
            }
        }
    }

    public HeadObjectResponse tryHeadObject(String bucket, String key)
        throws GlobalExceptionHandler {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key).build()).join();
        } catch (Exception e) {
            if (e.getCause() instanceof NoSuchKeyException) {
                return null;
            } else {
                throw new GlobalExceptionHandler("Failed to get object metadata",
                    StatusCode.SERVER_ERROR);
            }
        }
    }

    public List<S3Object> listObjects(String bucket, final String prefix) {
        ListObjectsV2Response resp = s3Client.listObjectsV2(ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(prefix).build()).join();
        return resp.contents();
    }

    public void readToFiles(String bucket, final List<String> keys, final List<Path> filePaths) {
        List<CompletableFuture<GetObjectResponse>> futures = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Path filePath = filePaths.get(i);
            futures.add(s3Client.getObject(req -> req.bucket(bucket).key(key),
                AsyncResponseTransformer.toFile(filePath)));
        }

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Path filePath = filePaths.get(i);
            CompletableFuture<GetObjectResponse> future = futures.get(i);
            try {
                future.join();
            } catch (NoSuchKeyException e) {
                GlobalLogger.info("Retry with object versionId: " + key);
                // The object may be deleted concurrently, try again with the version ID
                s3Client.getObject(req -> req.bucket(bucket).key(key)
                        .versionId(getVersionId(bucket, key)),
                    AsyncResponseTransformer.toFile(filePath));
            }
        }
    }

    public CompletableFuture<ResponseBytes<GetObjectResponse>> getVersionedObjectAsync(
        String bucket,
        String key, String versionId) {
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(key).versionId(versionId).build(),
            AsyncResponseTransformer.toBytes());
    }

    public ResponseBytes<GetObjectResponse> getVersionedObject(String bucket, String key,
        String versionId) {
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(key).versionId(versionId).build(),
            AsyncResponseTransformer.toBytes()).join();
    }

    public ResponseBytes<GetObjectResponse> getObject(String bucket, String key) {
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(key).build(),
            AsyncResponseTransformer.toBytes()).join();
    }

    public ResponseBytes<GetObjectResponse> tryGetObject(String bucket, String key) {
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket).key(key).build(),
                AsyncResponseTransformer.toBytes()).join();
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    public CompletableFuture<PutObjectResponse> putObjectAsync(String bucket, String key,
        final Object object) {
        return s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucket).key(key).build(),
            AsyncRequestBody.fromString(gson.toJson(object)));
    }

    public void putObject(String bucket, String key, final Object object) {
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucket).key(key).build(),
            AsyncRequestBody.fromString(gson.toJson(object)));
    }

    public DeleteObjectResponse deleteObject(String bucket, String key)
        throws ExecutionException, InterruptedException {
        return s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key).build()).get();
    }

    public CompletableFuture<DeleteObjectResponse> deleteObjectAsync(String bucket, String key) {
        return s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key).build());
    }

    public CompletableFuture<ResponseBytes<GetObjectResponse>> getObjectAsync(String bucket,
        String key) {
        return s3Client.getObject(
            GetObjectRequest.builder().bucket(bucket).key(key).build(),
            AsyncResponseTransformer.toBytes()
        );
    }

    public void putObject(String bucket, String key, String payload) {
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key).build(),
            AsyncRequestBody.fromString(payload)).join();
    }

    public S3AsyncClient getClient() {
        return s3Client;
    }

    public S3Client getSyncClient() {
        return s3SyncClient;
    }

    public void close() {
        s3SyncClient.close();
        s3Client.close();
        s3Presigner.close();
    }

    private String getVersionId(String bucket, String key) {
        ListObjectVersionsPublisher responses =
            s3Client.listObjectVersionsPaginator(
                r -> r.bucket(bucket).prefix(key).build());
        ArrayList<ObjectVersion> objectList = new ArrayList<>();
        responses.versions().subscribe(objectList::add).join();
        return objectList.getFirst().versionId();
    }
}
