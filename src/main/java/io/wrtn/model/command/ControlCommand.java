package io.wrtn.model.command;

import io.wrtn.model.index.CloneSourceInfo;

public class ControlCommand {

    private String type;
    private String projectId;
    private String indexName;
    private String indexClass;
    private int shardId;
    private CloneSourceInfo cloneSourceInfo;
    private Double rateLimit;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getIndexClass() {
        return indexClass;
    }

    public void setIndexClass(String indexClass) {
        this.indexClass = indexClass;
    }

    public int getShardId() {
        return shardId;
    }

    public void setShardId(int shardId) {
        this.shardId = shardId;
    }

    public CloneSourceInfo getCloneSourceInfo() {
        return cloneSourceInfo;
    }

    public void setCloneSourceInfo(CloneSourceInfo cloneSourceInfo) {
        this.cloneSourceInfo = cloneSourceInfo;
    }

    public Double getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(Double rateLimit) {
        this.rateLimit = rateLimit;
    }

    @Override
    public String toString() {
        return "ControlCommand{" +
            "type='" + type + '\'' +
            ", projectId='" + projectId + '\'' +
            ", indexName='" + indexName + '\'' +
            ", shardId=" + shardId +
            ", cloneSourceInfo=" + cloneSourceInfo +
            ", rateLimit=" + rateLimit +
            '}';
    }
}
