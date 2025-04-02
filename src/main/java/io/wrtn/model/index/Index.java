package io.wrtn.model.index;

import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.Map;

import static io.wrtn.util.Constants.Config.DEFAULT_NUM_SHARDS_PER_INDEX;
import static io.wrtn.util.Constants.IndexStatus.INDEX_STATUS_CREATING;
import static io.wrtn.util.TimeUtil.getCurrentUnixEpoch;

@DynamoDbBean
public class Index {

    private String projectId;
    private String indexName;
    private Integer numShards;
    private String projectApiKey;
    private Map<String, FieldConfig> mappings;
    private Integer numDocs;
    private StorageInfo storageInfo;
    private List<Integer> computeNodeIds;
    private String indexStatus;
    private Boolean writeLocked;
    private Long writeLockedAt;
    private Long createdAt;
    private Long updatedAt;
    private Long dataUpdatedAt;
    private CloneSourceInfo cloneSourceInfo;
    private String indexClass;
    private boolean conditionCheckFailed;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("projectId")
    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    @DynamoDbSortKey
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

    @DynamoDbAttribute("projectApiKey")
    public String getProjectApiKey() {
        return projectApiKey;
    }

    public void setProjectApiKey(String projectApiKey) {
        this.projectApiKey = projectApiKey;
    }

    @DynamoDbAttribute("mappings")
    public Map<String, FieldConfig> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, FieldConfig> mappings) {
        this.mappings = mappings;
    }

    @DynamoDbAttribute("numDocs")
    public Integer getNumDocs() {
        return numDocs;
    }

    public void setNumDocs(Integer numDocs) {
        this.numDocs = numDocs;
    }

    @DynamoDbAttribute("storageInfo")
    public StorageInfo getStorageInfo() {
        return storageInfo;
    }

    public void setStorageInfo(StorageInfo storageInfo) {
        this.storageInfo = storageInfo;
    }

    @DynamoDbAttribute("computeNodeIds")
    public List<Integer> getComputeNodeIds() {
        return computeNodeIds;
    }

    public void setComputeNodeIds(List<Integer> computeNodeIds) {
        this.computeNodeIds = computeNodeIds;
    }

    @DynamoDbAttribute("indexStatus")
    public String getIndexStatus() {
        return indexStatus;
    }

    public void setIndexStatus(String indexStatus) {
        this.indexStatus = indexStatus;
    }

    @DynamoDbAttribute("writeLocked")
    public Boolean getWriteLocked() {
        return writeLocked;
    }

    public void setWriteLocked(Boolean writeLocked) {
        this.writeLocked = writeLocked;
    }

    @DynamoDbAttribute("writeLockedAt")
    public Long getWriteLockedAt() {
        return writeLockedAt;
    }

    public void setWriteLockedAt(Long writeLockedAt) {
        this.writeLockedAt = writeLockedAt;
    }

    @DynamoDbAttribute("createdAt")
    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("updatedAt")
    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @DynamoDbAttribute("dataUpdatedAt")
    public Long getDataUpdatedAt() {
        return dataUpdatedAt;
    }

    public void setDataUpdatedAt(Long dataUpdatedAt) {
        this.dataUpdatedAt = dataUpdatedAt;
    }

    @DynamoDbAttribute("cloneSourceInfo")
    public CloneSourceInfo getCloneSourceInfo() {
        return cloneSourceInfo;
    }

    public void setCloneSourceInfo(CloneSourceInfo cloneSourceInfo) {
        this.cloneSourceInfo = cloneSourceInfo;
    }

    @DynamoDbAttribute("indexClass")
    public String getIndexClass() {
        return indexClass;
    }

    public void setIndexClass(String indexClass) {
        this.indexClass = indexClass;
    }

    @DynamoDbIgnore
    public boolean isConditionCheckFailed() {
        return conditionCheckFailed;
    }

    public void setConditionCheckFailed(boolean conditionCheckFailed) {
        this.conditionCheckFailed = conditionCheckFailed;
    }

    @Override
    public String toString() {
        return "Index{" +
            "projectId='" + projectId + '\'' +
            ", indexName='" + indexName + '\'' +
            ", numShards=" + numShards +
            ", projectApiKey='" + projectApiKey + '\'' +
            ", mappings=" + mappings +
            ", numDocs=" + numDocs +
            ", storageInfo=" + storageInfo +
            ", computeNodeIds=" + computeNodeIds +
            ", indexStatus='" + indexStatus + '\'' +
            ", writeLocked=" + writeLocked +
            ", writeLockedAt=" + writeLockedAt +
            ", createdAt=" + createdAt +
            ", updatedAt=" + updatedAt +
            ", dataUpdatedAt=" + dataUpdatedAt +
            ", cloneSourceInfo=" + cloneSourceInfo +
            ", indexClass='" + indexClass + '\'' +
            ", conditionCheckFailed=" + conditionCheckFailed +
            '}';
    }

    public void initialize() {
        this.numShards = DEFAULT_NUM_SHARDS_PER_INDEX;
        this.numDocs = 0;
        this.computeNodeIds = new ArrayList<>();
        this.computeNodeIds.add(0);
        this.indexStatus = INDEX_STATUS_CREATING;
        this.writeLocked = false;
        this.writeLockedAt = 0L;
        this.createdAt = getCurrentUnixEpoch();
        this.updatedAt = this.createdAt;
        this.dataUpdatedAt = 0L;
    }
}
