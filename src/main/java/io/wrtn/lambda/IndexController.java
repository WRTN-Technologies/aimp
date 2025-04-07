package io.wrtn.lambda;

import io.wrtn.dto.*;
import io.wrtn.infra.aws.DynamoDB;
import io.wrtn.infra.aws.S3;
import io.wrtn.infra.aws.SQS;
import io.wrtn.model.command.ControlCommand;
import io.wrtn.model.index.CloneSourceInfo;
import io.wrtn.model.index.FieldConfig;
import io.wrtn.model.index.Index;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.PathBuilder;
import io.wrtn.util.StatusCode;
import io.wrtn.util.Validation;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import static io.wrtn.infra.aws.Constants.S3.INDEX_BUCKET;
import static io.wrtn.util.Constants.CommandType.*;
import static io.wrtn.util.Constants.Config.TEMPORARY_SHARD_ID;
import static io.wrtn.util.Constants.IndexStatus.*;
import static io.wrtn.util.TimeUtil.getCurrentUnixEpoch;

public final class IndexController {

    private final S3 s3Client;
    private final DynamoDB ddbClient;
    private final SQS sqsClient;
    private final Validation validation;

    public IndexController(S3 s3Client, DynamoDB ddbClient, SQS sqsClient, Validation validation) {
        this.s3Client = s3Client;
        this.ddbClient = ddbClient;
        this.sqsClient = sqsClient;
        this.validation = validation;
    }

    public IndexListResponse listIndex(String projectId) {
        return new IndexListResponse((ddbClient.listIndexesByProjectIdConsistent(projectId)));
    }

    public IndexCreateResponse createIndex(IndexCreateRequest request, String projectId,
        String projectApiKey) throws GlobalExceptionHandler {
        validation.validateIndexCreate(request);

        // Initialize index metadata
        Index index = new Index();
        index.initialize();
        index.setProjectId(projectId);
        index.setProjectApiKey(projectApiKey);
        index.setIndexName(request.getIndexName());
        index.setMappings(request.getMappings());
        index.setIndexClass(request.getIndexClass());

        if (request.getSourceIndexName() != null || request.getSourceProjectId() != null
            || request.getSourceProjectApiKey() != null) {
            if (request.getSourceIndexName() == null || request.getSourceProjectId() == null
                || request.getSourceProjectApiKey() == null) {
                throw new GlobalExceptionHandler(
                    "Source project id, api key, and index name are required",
                    StatusCode.INVALID_INPUT_VALUE);
            }

            String sourceProjectId = request.getSourceProjectId();
            String sourceIndexName = request.getSourceIndexName();

            Index sourceIndex = ddbClient.getIndexConsistent(sourceProjectId, sourceIndexName);
            if (sourceIndex == null) {
                throw new GlobalExceptionHandler(
                    "Source index " + sourceProjectId + "." + sourceIndexName + " is not found",
                    StatusCode.NOT_FOUND);
            } else if (!sourceIndex.getIndexStatus().equals(INDEX_STATUS_ACTIVE)) {
                throw new GlobalExceptionHandler(
                    "Source index " + sourceProjectId + "." + sourceIndexName + " is not active",
                    StatusCode.INVALID_INPUT_VALUE);
            }

            validation.validateProjectApiKey(request.getSourceProjectApiKey(),
                sourceIndex.getProjectApiKey());

            HeadObjectResponse sourceMeta = s3Client.tryHeadObject(INDEX_BUCKET,
                PathBuilder.buildMetaKey(sourceIndex, TEMPORARY_SHARD_ID));
            if (sourceMeta == null) {
                throw new GlobalExceptionHandler(
                    "Source index " + sourceProjectId + "." + sourceIndexName + " is not active",
                    StatusCode.INVALID_INPUT_VALUE);
            }
            CloneSourceInfo cloneSourceInfo = new CloneSourceInfo();
            cloneSourceInfo.setSourceProjectId(sourceProjectId);
            cloneSourceInfo.setSourceIndexName(sourceIndexName);
            cloneSourceInfo.setSourceIndexVersionId(sourceMeta.versionId());

            Map<String, FieldConfig> sourceMappings = sourceIndex.getMappings();
            Map<String, FieldConfig> newMappings = request.getMappings();
            if (newMappings == null) {
                index.setMappings(sourceMappings);
            } else {
                index.setMappings(validateAndMergeMappings(sourceMappings,
                    newMappings));
            }

            index.setCloneSourceInfo(cloneSourceInfo);
            index.setNumDocs(sourceIndex.getNumDocs());
            index.setDataUpdatedAt(sourceIndex.getDataUpdatedAt());
            index.setNumShards(sourceIndex.getNumShards());
        } else {
            // Validate index mappings only for non-cloned indexes
            validation.validateMappingsAndSetDefaults(request.getMappings());
        }

        // Put index metadata to DynamoDB
        if (ddbClient.createIndexIfNotExists(index) != null) {
            // Index name in this project already exists
            throw new GlobalExceptionHandler("Index already exists",
                StatusCode.RESOURCE_ALREADY_EXISTS);
        }

        // Send command for asynchronous index creation
        ControlCommand command = new ControlCommand();
        command.setType(INDEX_CREATE);
        command.setProjectId(index.getProjectId());
        command.setIndexName(index.getIndexName());
        command.setIndexClass(index.getIndexClass());
        command.setCloneSourceInfo(index.getCloneSourceInfo());
        sqsClient.sendControlCommand(command);

        return new IndexCreateResponse(index);
    }

    public IndexDescribeResponse describeIndex(Index index) {
        return new IndexDescribeResponse(index);
    }

    public void deleteIndex(Index index) throws GlobalExceptionHandler {
        if (index.getIndexStatus().equals(INDEX_STATUS_ACTIVE)) {
            index.setIndexStatus(INDEX_STATUS_DELETING);
            index.setUpdatedAt(getCurrentUnixEpoch());

            Index updatedIndex = ddbClient.updateIndexIfActive(index);
            if (updatedIndex == null) {
                // This index has been deleted by another context
                throw new GlobalExceptionHandler(
                    "Index " + index.getProjectId() + "." + index.getIndexName() + " not found",
                    StatusCode.NOT_FOUND);
            } else {
                String status = updatedIndex.getIndexStatus();
                if (!status.equals(INDEX_STATUS_DELETING)) {
                    // !ACTIVE && !DELETING
                    throw new GlobalExceptionHandler(
                        "Index is not deletable current status: " + status,
                        StatusCode.SERVER_ERROR);
                }
            }

            // Send command for asynchronous index deletion
            ControlCommand command = new ControlCommand();
            command.setType(INDEX_DELETE);
            command.setProjectId(index.getProjectId());
            command.setIndexName(index.getIndexName());
            sqsClient.sendControlCommand(command);
        } else if (!index.getIndexStatus().equals(INDEX_STATUS_DELETING)) {
            throw new GlobalExceptionHandler(
                "Index is not deletable, current status " + index.getIndexStatus(),
                StatusCode.BAD_REQUEST);
        }
    }

    public IndexUpdateResponse updateIndex(IndexUpdateRequest request, Index index)
        throws GlobalExceptionHandler {

        if (request.getMappings() == null) {
            throw new GlobalExceptionHandler("Mappings are required",
                StatusCode.INVALID_INPUT_VALUE);
        }

        Map<String, FieldConfig> oldMappings = index.getMappings();
        Map<String, FieldConfig> newMappings = request.getMappings();
        Map<String, FieldConfig> mergeMappings = validateAndMergeMappings(oldMappings, newMappings);

        Index newIndex = new Index();
        newIndex.setProjectId(index.getProjectId());
        newIndex.setIndexName(index.getIndexName());
        newIndex.setMappings(mergeMappings);
        newIndex.setUpdatedAt(getCurrentUnixEpoch());

        Index updatedIndex = ddbClient.updateIndexIfActive(newIndex);
        if (updatedIndex == null) {
            // This index has been deleted by another context
            throw new GlobalExceptionHandler(
                "Index " + index.getProjectId() + "." + index.getIndexName() + " not found",
                StatusCode.NOT_FOUND);
        } else if (updatedIndex.isConditionCheckFailed()) {
            if (!updatedIndex.getIndexStatus().equals(INDEX_STATUS_ACTIVE)) {
                throw new GlobalExceptionHandler(
                    "Invalid index status: " + updatedIndex.getIndexStatus(),
                    StatusCode.SERVER_ERROR);
            }
        }

        return new IndexUpdateResponse(updatedIndex);
    }

    public Map<String, FieldConfig> validateAndMergeMappings(
        Map<String, FieldConfig> oldMappings,
        Map<String, FieldConfig> newMappings) throws GlobalExceptionHandler {

        validation.validateMappingsAndSetDefaults(newMappings);

        Map<String, FieldConfig> mergedMappings = new HashMap<>(oldMappings);

        // If key exists in oldMappings, newMappings should have the same key, value
        for (String fieldName : oldMappings.keySet()) {
            if (newMappings.containsKey(fieldName)) {
                FieldConfig mergedConfig = validateAndMergeFieldConfigs(fieldName,
                    oldMappings.get(fieldName), newMappings.get(fieldName));
                mergedMappings.put(fieldName, mergedConfig);
            } else {
                throw new GlobalExceptionHandler(
                    String.format("Required mapping fieldName '%s' is missing in the update request",
                        fieldName), StatusCode.INVALID_INPUT_VALUE);
            }
        }

        // If key exists in newMappings but not in oldMappings, add it to mergedMappings
        for (Map.Entry<String, FieldConfig> entry : newMappings.entrySet()) {
            String fieldName = entry.getKey();
            if (!oldMappings.containsKey(fieldName)) {
                mergedMappings.put(fieldName, entry.getValue());
            }
        }

        return mergedMappings;
    }

    private FieldConfig validateAndMergeFieldConfigs(String fieldName, FieldConfig oldConfig,
        FieldConfig newConfig) throws GlobalExceptionHandler {
        // type
        if (!Objects.equals(oldConfig.getType(), newConfig.getType())) {
            throw new GlobalExceptionHandler(
                String.format("Type mismatch for fieldName '%s': expected '%s' but got '%s'",
                    fieldName, oldConfig.getType(), newConfig.getType()),
                StatusCode.INVALID_INPUT_VALUE);
        }

        // analyzers
        if (!Objects.equals(oldConfig.getAnalyzers(), newConfig.getAnalyzers())) {
            throw new GlobalExceptionHandler(
                String.format("Analyzers mismatch for fieldName '%s': expected '%s' but got '%s'",
                    fieldName, oldConfig.getAnalyzers(), newConfig.getAnalyzers()),
                StatusCode.INVALID_INPUT_VALUE);
        }

        // dimensions
        if (!Objects.equals(oldConfig.getDimensions(), newConfig.getDimensions())) {
            throw new GlobalExceptionHandler(
                String.format("Dimensions mismatch for fieldName '%s': expected '%d' but got '%d'",
                    fieldName, oldConfig.getDimensions(), newConfig.getDimensions()),
                StatusCode.INVALID_INPUT_VALUE);
        }

        // similarity
        if (!Objects.equals(oldConfig.getSimilarity(), newConfig.getSimilarity())) {
            throw new GlobalExceptionHandler(
                String.format("Similarity mismatch for fieldName '%s': expected '%s' but got '%s'",
                    fieldName, oldConfig.getSimilarity(), newConfig.getSimilarity()),
                StatusCode.INVALID_INPUT_VALUE);
        }

        // Create a new config for the current level
        FieldConfig mergedConfig = new FieldConfig();
        mergedConfig.setType(oldConfig.getType());
        mergedConfig.setAnalyzers(oldConfig.getAnalyzers());
        mergedConfig.setDimensions(oldConfig.getDimensions());
        mergedConfig.setSimilarity(oldConfig.getSimilarity());

        // objectMapping
        Map<String, FieldConfig> oldObjectMapping = oldConfig.getObjectMapping();
        Map<String, FieldConfig> newObjectMapping = newConfig.getObjectMapping();

        if (oldObjectMapping != null && newObjectMapping != null) {
            Map<String, FieldConfig> mergedObjectMapping = new HashMap<>();

            // Validate and merge existing keys
            for (String subFieldName : oldObjectMapping.keySet()) {
                if (newObjectMapping.containsKey(subFieldName)) {
                    FieldConfig mergedSubConfig = validateAndMergeFieldConfigs(
                        fieldName + "." + subFieldName,
                        oldObjectMapping.get(subFieldName),
                        newObjectMapping.get(subFieldName));
                    mergedObjectMapping.put(subFieldName, mergedSubConfig);
                } else {
                    throw new GlobalExceptionHandler(
                        String.format(
                            "Required nested mapping fieldName '%s.%s' is missing in the update request",
                            fieldName, subFieldName), StatusCode.INVALID_INPUT_VALUE);
                }
            }

            // Add new keys from newObjectMapping
            for (Map.Entry<String, FieldConfig> entry : newObjectMapping.entrySet()) {
                String subFieldName = entry.getKey();
                if (!oldObjectMapping.containsKey(subFieldName)) {
                    mergedObjectMapping.put(subFieldName, entry.getValue());
                }
            }

            mergedConfig.setObjectMapping(mergedObjectMapping);

        } else if (oldObjectMapping != null || newObjectMapping != null) {
            throw new GlobalExceptionHandler(
                String.format(
                    "Object mapping mismatch for fieldName '%s': one mapping is null while the other is not",
                    fieldName), StatusCode.INVALID_INPUT_VALUE);
        }

        return mergedConfig;
    }
}
