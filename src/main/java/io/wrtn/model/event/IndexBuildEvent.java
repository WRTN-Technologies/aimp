package io.wrtn.model.event;

public class IndexBuildEvent {

    private String projectId;
    private String indexName;
    private int shardId;

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

    public int getShardId() {
        return shardId;
    }

    public void setShardId(int shardId) {
        this.shardId = shardId;
    }

    @Override
    public String toString() {
        return "IndexBuildEvent{" +
            "projectId='" + projectId + '\'' +
            ", indexName='" + indexName + '\'' +
            ", shardId=" + shardId +
            '}';
    }
}
