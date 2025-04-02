package io.wrtn.model.index;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
public class DeletedIndex {

    private String id;
    private String projectId;
    private String indexName;
    private Integer numShards;
    private Long deletedAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @DynamoDbAttribute("projectId")
    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    @DynamoDbAttribute("indexName")
    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    @DynamoDbAttribute("numShards")
    public Integer getNumShards() {
        return numShards;
    }

    public void setNumShards(Integer numShards) {
        this.numShards = numShards;
    }

    @DynamoDbAttribute("deletedAt")
    public Long getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Long deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public String toString() {
        return "DeletedIndex{" +
            "id='" + id + '\'' +
            "projectId='" + projectId + '\'' +
            ", indexName='" + indexName + '\'' +
            ", numShards=" + numShards +
            ", deletedAt=" + deletedAt +
            '}';
    }
}
