package io.wrtn.infra.aws;

import static io.wrtn.infra.aws.Constants.AWS_REGION;
import static io.wrtn.infra.aws.Constants.DynamoDB.*;
import static io.wrtn.util.Constants.IndexStatus.*;
import static io.wrtn.util.Constants.ProjectStatus.*;
import static io.wrtn.util.TimeUtil.getIndexBuilderFailureTimeout;

import io.wrtn.model.index.DeletedIndex;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.wrtn.model.index.Index;
import io.wrtn.model.project.Project;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.StatusCode;

public final class DynamoDB {

    DynamoDbClient client = DynamoDbClient.builder()
        .httpClientBuilder(AwsCrtHttpClient.builder())
        .region(Region.of(AWS_REGION))
        .build();

    DynamoDbEnhancedClient ddbClient = DynamoDbEnhancedClient.builder()
        .dynamoDbClient(
            // Configure an instance of the standard client.
            client)
        .build();
    DynamoDbTable<Project> projectTable =
        ddbClient.table(PROJECT_TABLE_NAME, TableSchema.fromBean(Project.class));
    DynamoDbTable<Index> indexTable =
        ddbClient.table(INDEX_TABLE_NAME, TableSchema.fromBean(Index.class));
    DynamoDbTable<DeletedIndex> deletedIndexTable =
        ddbClient.table(DELETED_INDEX_TABLE_NAME, TableSchema.fromBean(DeletedIndex.class));

    public Index createIndexIfNotExists(final Index index)
        throws GlobalExceptionHandler {
        Objects.requireNonNull(index);

        try {
            indexTable.putItem(PutItemEnhancedRequest.builder(Index.class)
                .item(index)
                .conditionExpression(Expression.builder()
                    .expression(
                        "attribute_not_exists(projectId) and attribute_not_exists(indexName)")
                    .build())
                .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
                .build());
            return null;
        } catch (ConditionalCheckFailedException e) {
            Index failedIndex = exceptionToIndex(e);
            if (failedIndex != null) {
                failedIndex.setConditionCheckFailed(true);
            }
            return failedIndex;
        } catch (DynamoDbException e) {
            throw new GlobalExceptionHandler(e.getMessage(), StatusCode.SERVER_ERROR);
        }
    }

    public void createDeletedIndex(final DeletedIndex deletedIndex)
        throws GlobalExceptionHandler {
        Objects.requireNonNull(deletedIndex);

        try {
            deletedIndexTable.putItem(PutItemEnhancedRequest.builder(DeletedIndex.class)
                .item(deletedIndex).build());
        } catch (DynamoDbException e) {
            throw new GlobalExceptionHandler(e.getMessage(), StatusCode.SERVER_ERROR);
        }
    }

    public Index getIndexConsistent(final String projectId, final String indexName)
        throws DynamoDbException, NullPointerException, GlobalExceptionHandler {

        if (projectId == null || projectId.isEmpty()) {
            throw new GlobalExceptionHandler("projectId can not be null/empty value",
                StatusCode.INVALID_INPUT_VALUE);
        } else if (indexName == null || indexName.isEmpty()) {
            throw new GlobalExceptionHandler("indexName can not be null/empty value",
                StatusCode.INVALID_INPUT_VALUE);
        }

        return indexTable.getItem(GetItemEnhancedRequest.builder()
            .key(Key.builder().partitionValue(projectId).sortValue(indexName).build())
            .consistentRead(true).build());
    }

    public Index getIndex(final String projectId, final String indexName)
        throws DynamoDbException, NullPointerException, GlobalExceptionHandler {

        if (projectId == null || projectId.isEmpty()) {
            throw new GlobalExceptionHandler("projectId can not be null/empty value",
                StatusCode.INVALID_INPUT_VALUE);
        } else if (indexName == null || indexName.isEmpty()) {
            throw new GlobalExceptionHandler("indexName can not be null/empty value",
                StatusCode.INVALID_INPUT_VALUE);
        }

        return indexTable.getItem(GetItemEnhancedRequest.builder()
            .key(Key.builder().partitionValue(projectId).sortValue(indexName).build())
            .build());
    }

    public Index updateIndexIfActive(final Index index)
        throws GlobalExceptionHandler {
        try {
            Index updatedIndex = indexTable.updateItem(
                UpdateItemEnhancedRequest.builder(Index.class)
                    .ignoreNulls(true)
                    .item(index)
                    .conditionExpression(
                        Expression.builder()
                            .expression("indexStatus = :indexStatusActive")
                            .expressionValues(Map.of(":indexStatusActive",
                                AttributeValue.builder().s(INDEX_STATUS_ACTIVE).build()))
                            .build())
                    .returnValuesOnConditionCheckFailure(
                        ReturnValuesOnConditionCheckFailure.ALL_OLD)
                    .build());
            if (updatedIndex != null) {
                updatedIndex.setConditionCheckFailed(false);
            }
            return updatedIndex;
        } catch (ConditionalCheckFailedException e) {
            Index failedIndex = exceptionToIndex(e);
            if (failedIndex != null) {
                failedIndex.setConditionCheckFailed(true);
            }
            return failedIndex;
        } catch (DynamoDbException e) {
            throw new GlobalExceptionHandler(e.getMessage(), StatusCode.SERVER_ERROR);
        }
    }

    public Index updateIndexIfCreating(final Index index)
        throws GlobalExceptionHandler {
        try {
            Index updatedIndex = indexTable.updateItem(
                UpdateItemEnhancedRequest.builder(Index.class)
                    .ignoreNulls(true)
                    .item(index)
                    .conditionExpression(
                        Expression.builder()
                            .expression("indexStatus = :indexStatusCreating")
                            .expressionValues(Map.of(":indexStatusCreating",
                                AttributeValue.builder().s(INDEX_STATUS_CREATING).build()))
                            .build())
                    .returnValuesOnConditionCheckFailure(
                        ReturnValuesOnConditionCheckFailure.ALL_OLD)
                    .build());
            if (updatedIndex != null) {
                updatedIndex.setConditionCheckFailed(false);
            }
            return updatedIndex;
        } catch (ConditionalCheckFailedException e) {
            Index failedIndex = exceptionToIndex(e);
            if (failedIndex != null) {
                failedIndex.setConditionCheckFailed(true);
            }
            return failedIndex;
        } catch (DynamoDbException e) {
            throw new GlobalExceptionHandler(e.getMessage(), StatusCode.SERVER_ERROR);
        }
    }

    public Index updateIndexIfWritable(Index index)
        throws GlobalExceptionHandler {
        try {
            long timeoutDiff = index.getWriteLockedAt() - getIndexBuilderFailureTimeout();
            Index updatedIndex = indexTable.updateItem(
                UpdateItemEnhancedRequest.builder(Index.class)
                    .ignoreNulls(true)
                    .item(index)
                    .conditionExpression(Expression.builder()
                        .expression("indexStatus = :indexStatusActive " +
                            "and (writeLocked = :writeLockedFalse or writeLockedAt < :timeoutDiff)")
                        .expressionValues(Map.of(":writeLockedFalse",
                            AttributeValue.builder().bool(false).build(),
                            ":indexStatusActive",
                            AttributeValue.builder().s(INDEX_STATUS_ACTIVE).build(),
                            ":timeoutDiff",
                            AttributeValue.builder().n(Long.toString(timeoutDiff)).build()))
                        .build())
                    .returnValuesOnConditionCheckFailure(
                        ReturnValuesOnConditionCheckFailure.ALL_OLD)
                    .build());

            if (updatedIndex != null) {
                updatedIndex.setConditionCheckFailed(false);
            }

            return updatedIndex;
        } catch (ConditionalCheckFailedException e) {
            Index failedIndex = exceptionToIndex(e);
            if (failedIndex != null) {
                failedIndex.setConditionCheckFailed(true);
            }
            return failedIndex;
        } catch (DynamoDbException e) {
            throw new GlobalExceptionHandler(e.getMessage(), StatusCode.SERVER_ERROR);
        }
    }

    public void updateIndex(Index index) {
        indexTable.updateItem(UpdateItemEnhancedRequest.builder(Index.class)
            .ignoreNulls(true)
            .item(index)
            .build());
    }

    public void tryUpdateIndex(Index index) {
        try {
            indexTable.updateItem(UpdateItemEnhancedRequest.builder(Index.class)
                .ignoreNulls(true)
                .item(index)
                .build());
        } catch (ResourceNotFoundException ignored) {
        }
    }

    public Index updateIndexIfWriteLocked(final Index index)
        throws GlobalExceptionHandler {
        try {
            Index updatedIndex = indexTable.updateItem(
                UpdateItemEnhancedRequest.builder(Index.class)
                    .ignoreNulls(true)
                    .item(index)
                    .conditionExpression(
                        Expression.builder()
                            .expression("writeLocked = :writeLockedTrue")
                            .expressionValues(Map.of(":writeLockedTrue",
                                AttributeValue.builder().bool(true).build()))
                            .build())
                    .build());
            if (updatedIndex != null) {
                updatedIndex.setConditionCheckFailed(false);
            }
            return updatedIndex;
        } catch (ConditionalCheckFailedException e) {
            Index failedIndex = exceptionToIndex(e);
            if (failedIndex != null) {
                failedIndex.setConditionCheckFailed(true);
            }
            return failedIndex;
        } catch (DynamoDbException e) {
            throw new GlobalExceptionHandler(e.getMessage(), StatusCode.SERVER_ERROR);
        }
    }

    public List<Index> listIndexesByProjectIdConsistent(final String projectId) {
        Objects.requireNonNull(projectId);
        return indexTable.query(QueryEnhancedRequest.builder()
            .consistentRead(true)
            .queryConditional(
                QueryConditional.keyEqualTo(Key.builder().partitionValue(projectId).build()))
            .build()).items().stream().toList();
    }

    public List<Index> listIndexesConsistent() {
        return indexTable.scan(ScanEnhancedRequest.builder()
            .consistentRead(true).build()
        ).items().stream().toList();
    }

    public List<Index> listActiveIndexes() {
        return indexTable.scan(ScanEnhancedRequest.builder().filterExpression(Expression.builder()
            .expression("indexStatus = :indexStatusActive")
            .expressionValues(Map.of(":indexStatusActive",
                AttributeValue.builder().s(INDEX_STATUS_ACTIVE).build()))
            .build()).build()).items().stream().toList();
    }

    public void deleteIndex(String projectId, String indexName) {
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(indexName);

        indexTable.deleteItem(Key.builder()
            .partitionValue(projectId)
            .sortValue(indexName)
            .build());
    }

    public List<Project> listProjectsConsistent() {
        return projectTable.scan(ScanEnhancedRequest.builder()
                .consistentRead(true).build()
        ).items().stream().toList();
    }

    public Project createProjectIfNotExists(final Project project)
        throws GlobalExceptionHandler {
        Objects.requireNonNull(project);

        try {
            projectTable.putItem(PutItemEnhancedRequest.builder(Project.class)
                .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
                .item(project)
                .conditionExpression(Expression.builder()
                    .expression("attribute_not_exists(id)").build())
                .build());
            return null;
        } catch (ConditionalCheckFailedException e) {
            Project failedProject = exceptionToProject(e);
            if (failedProject != null) {
                failedProject.setConditionCheckFailed(true);
            }
            return failedProject;
        } catch (DynamoDbException e) {
            throw new GlobalExceptionHandler(e.getMessage(), StatusCode.SERVER_ERROR);
        }
    }

    public Project getProjectConsistent(final String projectId) {
        Objects.requireNonNull(projectId);

        return projectTable.getItem(GetItemEnhancedRequest.builder()
            .key(Key.builder().partitionValue(projectId).build())
            .consistentRead(true).build());
    }

    public void deleteProject(final String projectId) {
        Objects.requireNonNull(projectId);

        projectTable.deleteItem(Key.builder()
            .partitionValue(projectId)
            .build());
    }

    public Project updateProjectIfCreating(final Project project)
        throws GlobalExceptionHandler {
        Objects.requireNonNull(project);

        try {
            Project updatedProject = projectTable.updateItem(
                UpdateItemEnhancedRequest.builder(Project.class)
                    .ignoreNulls(true)
                    .item(project)
                    .conditionExpression(
                        Expression.builder()
                            .expression("projectStatus = :projectStatusActive")
                            .expressionValues(Map.of(":projectStatusActive",
                                AttributeValue.builder().s(PROJECT_STATUS_CREATING).build()))
                            .build())
                    .returnValuesOnConditionCheckFailure(
                        ReturnValuesOnConditionCheckFailure.ALL_OLD)
                    .build());
            if (updatedProject != null) {
                updatedProject.setConditionCheckFailed(false);
            }
            return updatedProject;
        } catch (ConditionalCheckFailedException e) {
            Project failedProject = exceptionToProject(e);
            if (failedProject != null) {
                failedProject.setConditionCheckFailed(true);
            }
            return failedProject;
        } catch (DynamoDbException e) {
            throw new GlobalExceptionHandler(e.getMessage(), StatusCode.SERVER_ERROR);
        }
    }

    public Project updateProjectIfActive(final Project project)
        throws GlobalExceptionHandler {
        Objects.requireNonNull(project);

        try {
            Project updatedProject = projectTable.updateItem(
                UpdateItemEnhancedRequest.builder(Project.class)
                    .ignoreNulls(true)
                    .item(project)
                    .conditionExpression(
                        Expression.builder()
                            .expression("projectStatus = :projectStatusActive")
                            .expressionValues(Map.of(":projectStatusActive",
                                AttributeValue.builder().s(PROJECT_STATUS_ACTIVE).build()))
                            .build())
                    .returnValuesOnConditionCheckFailure(
                        ReturnValuesOnConditionCheckFailure.ALL_OLD)
                    .build());
            if (updatedProject != null) {
                updatedProject.setConditionCheckFailed(false);
            }
            return updatedProject;
        } catch (ConditionalCheckFailedException e) {
            Project failedProject = exceptionToProject(e);
            if (failedProject != null) {
                failedProject.setConditionCheckFailed(true);
            }
            return failedProject;
        } catch (DynamoDbException e) {
            throw new GlobalExceptionHandler(e.getMessage(), StatusCode.SERVER_ERROR);
        }
    }

    public void close() {
        client.close();
    }

    private Index exceptionToIndex(ConditionalCheckFailedException e) {
        return TableSchema.fromClass(Index.class).mapToItem(e.item());
    }

    private Project exceptionToProject(ConditionalCheckFailedException e) {
        return TableSchema.fromClass(Project.class).mapToItem(e.item());
    }
}