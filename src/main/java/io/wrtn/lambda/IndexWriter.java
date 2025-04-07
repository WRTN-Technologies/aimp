package io.wrtn.lambda;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import io.wrtn.dto.BulkUpsertRequest;
import io.wrtn.dto.DeleteRequest;
import io.wrtn.dto.UpsertRequest;
import io.wrtn.infra.aws.EFS;
import io.wrtn.infra.aws.S3;
import io.wrtn.util.PathBuilder;
import io.wrtn.util.Validation;
import java.io.BufferedReader;
import java.io.IOException;
import io.wrtn.model.wal.WalRecord;
import io.wrtn.model.index.FieldConfig;
import io.wrtn.model.index.Index;

import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.StatusCode;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import static io.wrtn.engine.lucene.util.Validation.validateQuery;
import static io.wrtn.infra.aws.Constants.S3.TEMP_BUCKET;
import static io.wrtn.util.Constants.CommandType.DOCUMENT_DELETE;
import static io.wrtn.util.Constants.CommandType.DOCUMENT_UPSERT;
import static io.wrtn.util.Constants.Config.BULK_UPSERT_SIZE_LIMIT_MB;
import static io.wrtn.util.Constants.Config.EFS_MOUNT_PATH;
import static io.wrtn.util.Constants.Config.TEMPORARY_SHARD_ID;
import static io.wrtn.util.Constants.Config.WAL_PAYLOAD_SIZE_THRESHOLD;
import static io.wrtn.util.JsonParser.gson;
import static io.wrtn.util.TimeUtil.getCurrentUnixEpoch;

public class IndexWriter {

    private final S3 s3Client;
    private final EFS efsClient;
    private final Validation validation;

    public IndexWriter(S3 s3Client, EFS efsClient, Validation validation) {
        this.s3Client = s3Client;
        this.efsClient = efsClient;
        this.validation = validation;
    }

    public void upsertDocuments(UpsertRequest request, Index index, String requestBody)
        throws GlobalExceptionHandler {
        validateUpsertRequest(request, index.getMappings());

        List<Map<String, JsonElement>> documents = request.getDocuments();

        // Log this request to the corresponding WAL directory
        WalRecord walRecord = new WalRecord();
        walRecord.setId(io.wrtn.util.UUID.generateUUID());
        walRecord.setType(DOCUMENT_UPSERT);
        walRecord.setSeqNo(getCurrentUnixEpoch());
        walRecord.setProjectId(index.getProjectId());
        walRecord.setIndexName(index.getIndexName());
        walRecord.setShardId(TEMPORARY_SHARD_ID);
        walRecord.setS3Payload(false);
        walRecord.setDocuments(documents);
        walRecord.setPayloadBytes(requestBody.getBytes().length);
        if (requestBody.getBytes().length >= WAL_PAYLOAD_SIZE_THRESHOLD) {
            // Store payload in S3
            walRecord.setS3Payload(true);
            s3Client.putObject(TEMP_BUCKET,
                PathBuilder.buildStorageKeyPath(index, TEMPORARY_SHARD_ID, walRecord.getId()),
                requestBody);
            walRecord.setDocuments(null);
        }

        try {
            efsClient.syncWalRecord(
                PathBuilder.buildFsWalFilePath(EFS_MOUNT_PATH, index, TEMPORARY_SHARD_ID,
                    walRecord.getId()), walRecord);
            efsClient.syncDirectory(
                PathBuilder.buildFsWalPath(EFS_MOUNT_PATH, index, TEMPORARY_SHARD_ID));
        } catch (IOException ioe) {
            throw new GlobalExceptionHandler("Failed to write WAL record: " + ioe.getMessage(),
                StatusCode.SERVER_ERROR);
        }
    }

    public void bulkUpsertDocuments(BulkUpsertRequest request, Index index)
        throws GlobalExceptionHandler {
        validateBulkUpsertRequest(request);

        String objectKey = request.getObjectKey();
        HeadObjectResponse objectMetadata = s3Client.headObject(TEMP_BUCKET, objectKey);
        validateBulkUpsertPayload(objectMetadata);

        try {
            InputStream inputStream = s3Client.getObjectStream(TEMP_BUCKET, objectKey);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            JsonReader jsonReader = new JsonReader(bufferedReader);
            jsonReader.beginObject();
            if (jsonReader.hasNext()) {
                String name = jsonReader.nextName();
                if ("documents".equals(name)) {
                    Type documentType = new TypeToken<Map<String, JsonElement>>() {
                    }.getType();
                    jsonReader.beginArray();
                    while (jsonReader.hasNext()) {
                        Map<String, JsonElement> document = gson.fromJson(jsonReader, documentType);
                        validation.validateDocument(document, index.getMappings());
                    }
                    jsonReader.endArray();
                } else {
                    throw new GlobalExceptionHandler("One or more documents required",
                        StatusCode.INVALID_INPUT_VALUE);
                }
            } else {
                throw new GlobalExceptionHandler("Payload is empty",
                    StatusCode.INVALID_INPUT_VALUE);
            }

            if (jsonReader.hasNext()) {
                throw new GlobalExceptionHandler("Payload is malformed",
                    StatusCode.INVALID_INPUT_VALUE);
            }
            jsonReader.endObject();
            jsonReader.close();
            bufferedReader.close();
            inputStreamReader.close();
            inputStream.close();
        } catch (IOException | IllegalStateException e) {
            throw new GlobalExceptionHandler("Malformed documents: " + e.getMessage(),
                StatusCode.INVALID_INPUT_VALUE);
        }

        // Log this request to the corresponding WAL directory
        WalRecord walRecord = new WalRecord();
        walRecord.setId(Arrays.stream(objectKey.split("/")).toList().getLast());
        walRecord.setType(DOCUMENT_UPSERT);
        walRecord.setSeqNo(getCurrentUnixEpoch());
        walRecord.setProjectId(index.getProjectId());
        walRecord.setIndexName(index.getIndexName());
        walRecord.setShardId(TEMPORARY_SHARD_ID);
        walRecord.setS3Payload(true);
        walRecord.setDocuments(null);
        walRecord.setPayloadBytes(objectMetadata.contentLength());

        try {
            efsClient.syncWalRecord(
                PathBuilder.buildFsWalFilePath(EFS_MOUNT_PATH, index, TEMPORARY_SHARD_ID,
                    walRecord.getId()), walRecord);
            efsClient.syncDirectory(
                PathBuilder.buildFsWalPath(EFS_MOUNT_PATH, index, TEMPORARY_SHARD_ID));
        } catch (IOException ioe) {
            throw new GlobalExceptionHandler("Failed to write WAL record: " + ioe.getMessage(),
                StatusCode.SERVER_ERROR);
        }
    }

    private void validateBulkUpsertRequest(BulkUpsertRequest request)
        throws GlobalExceptionHandler {
        if (request.getObjectKey() == null) {
            throw new GlobalExceptionHandler("objectKey is required",
                StatusCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateBulkUpsertPayload(HeadObjectResponse objectMetadata)
        throws GlobalExceptionHandler {
        if (!objectMetadata.contentType().equalsIgnoreCase("application/json")) {
            throw new GlobalExceptionHandler("Invalid content type",
                StatusCode.INVALID_INPUT_VALUE);
        }

        if (objectMetadata.contentLength() > BULK_UPSERT_SIZE_LIMIT_MB) {
            throw new GlobalExceptionHandler(
                "Object size must be less than " + (BULK_UPSERT_SIZE_LIMIT_MB / 1024 / 1024)
                    + " MB",
                StatusCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateUpsertRequest(UpsertRequest request,
        Map<String, FieldConfig> mappings)
        throws GlobalExceptionHandler {
        List<Map<String, JsonElement>> documents = request.getDocuments();
        if (documents == null) {
            throw new GlobalExceptionHandler("documents is required",
                StatusCode.INVALID_INPUT_VALUE);
        } else if (documents.isEmpty()) {
            throw new GlobalExceptionHandler("One or more documents required",
                StatusCode.INVALID_INPUT_VALUE);
        }

        for (Map<String, JsonElement> document : documents) {
            validation.validateDocument(document, mappings);
        }
    }

    public void deleteDocuments(DeleteRequest request, Index index)
        throws GlobalExceptionHandler {
        validateDeleteRequest(request, index.getMappings());

        // Log this request to the corresponding WAL directory
        WalRecord walRecord = new WalRecord();
        walRecord.setId(io.wrtn.util.UUID.generateUUID());
        walRecord.setType(DOCUMENT_DELETE);
        walRecord.setSeqNo(getCurrentUnixEpoch());
        walRecord.setProjectId(index.getProjectId());
        walRecord.setIndexName(index.getIndexName());
        walRecord.setShardId(TEMPORARY_SHARD_ID);
        walRecord.setS3Payload(false);
        walRecord.setPayloadBytes(0);
        List<String> ids = request.getIds();
        if (ids != null) {
            // Delete by IDs
            walRecord.setIds(ids);
        } else {
            // Delete by filter
            walRecord.setFilter(request.getFilter());
        }

        try {
            efsClient.syncWalRecord(
                PathBuilder.buildFsWalFilePath(EFS_MOUNT_PATH, index, TEMPORARY_SHARD_ID,
                    walRecord.getId()), walRecord);
            efsClient.syncDirectory(
                PathBuilder.buildFsWalPath(EFS_MOUNT_PATH, index, TEMPORARY_SHARD_ID));
        } catch (IOException ioe) {
            throw new GlobalExceptionHandler("Failed to write WAL record: " + ioe.getMessage(),
                StatusCode.SERVER_ERROR);
        }
    }

    private void validateDeleteRequest(DeleteRequest request, Map<String, FieldConfig> mappings)
        throws GlobalExceptionHandler {
        List<String> ids = request.getIds();
        JsonObject filter = request.getFilter();

        if (ids == null && filter == null) {
            throw new GlobalExceptionHandler("One of ids/filter must be set to delete documents",
                StatusCode.INVALID_INPUT_VALUE);
        } else if (ids != null && filter != null) {
            throw new GlobalExceptionHandler("Either ids or filter must be set",
                StatusCode.INVALID_INPUT_VALUE);
        }

        if (ids != null) {
            // Delete by IDs
            if (ids.isEmpty()) {
                throw new GlobalExceptionHandler("ids list cannot be empty",
                    StatusCode.INVALID_INPUT_VALUE);
            }
        } else {
            // Delete by filter
            if (!validateQuery(filter, mappings)) {
                throw new GlobalExceptionHandler("filter query syntax is invalid: "
                    + "mapping not found",
                    StatusCode.INVALID_INPUT_VALUE);
            }
        }
    }
}
