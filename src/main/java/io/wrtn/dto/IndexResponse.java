package io.wrtn.dto;

import io.wrtn.model.index.CloneSourceInfo;
import io.wrtn.model.index.FieldConfig;
import io.wrtn.model.index.Index;

import java.util.Map;

public class IndexResponse {

    private String projectId;
    private String indexName;
    private Map<String, FieldConfig> mappings;
    private int numDocs;
    private CloneSourceInfo cloneSourceInfo;
    private String indexStatus;
    private String indexClass;

    public IndexResponse(Index index) {
        this.projectId = index.getProjectId();
        this.indexName = index.getIndexName();
        this.cloneSourceInfo = index.getCloneSourceInfo();
        this.mappings = index.getMappings();
        this.numDocs = index.getNumDocs();
        this.indexStatus = index.getIndexStatus();
        this.indexClass = index.getIndexClass();
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

    public CloneSourceInfo getCloneSourceInfo() {
        return cloneSourceInfo;
    }

    public void setCloneSourceInfo(CloneSourceInfo cloneSourceInfo) {
        this.cloneSourceInfo = cloneSourceInfo;
    }

    public Map<String, FieldConfig> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, FieldConfig> mappings) {
        this.mappings = mappings;
    }

    public int getNumDocs() {
        return numDocs;
    }

    public void setNumDocs(int numDocs) {
        this.numDocs = numDocs;
    }

    public String getIndexStatus() {
        return indexStatus;
    }

    public void setIndexStatus(String indexStatus) {
        this.indexStatus = indexStatus;
    }

    public String getIndexClass() {
        return indexClass;
    }

    public void setIndexClass(String indexClass) {
        this.indexClass = indexClass;
    }

    @Override
    public String toString() {
        return "IndexResponse{" +
            "projectId='" + projectId + '\'' +
            ", indexName='" + indexName + '\'' +
            ", cloneSourceInfo=" + cloneSourceInfo +
            ", mappings=" + mappings +
            ", numDocs=" + numDocs +
            ", indexStatus='" + indexStatus + '\'' +
            ", indexClass='" + indexClass + '\'' +
            '}';
    }
}
