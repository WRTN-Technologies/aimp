package io.wrtn.dto;

import io.wrtn.model.index.FieldConfig;

import java.util.Map;

public class IndexCreateRequest {

    private String indexName;
    private Map<String, FieldConfig> mappings;
    private String sourceProjectId;
    private String sourceIndexName;
    private String sourceProjectApiKey;
    private String indexClass;

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public Map<String, FieldConfig> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, FieldConfig> mappings) {
        this.mappings = mappings;
    }

    public String getSourceIndexName() {
        return sourceIndexName;
    }

    public void setSourceIndexName(String sourceIndexName) {
        this.sourceIndexName = sourceIndexName;
    }

    public String getSourceProjectId() {
        return sourceProjectId;
    }

    public void setSourceProjectId(String sourceProjectId) {
        this.sourceProjectId = sourceProjectId;
    }

    public String getSourceProjectApiKey() {
        return sourceProjectApiKey;
    }

    public void setSourceProjectApiKey(String sourceProjectApiKey) {
        this.sourceProjectApiKey = sourceProjectApiKey;
    }

    public String getIndexClass() {
        return indexClass;
    }

    public void setIndexClass(String indexClass) {
        this.indexClass = indexClass;
    }

    @Override
    public String toString() {
        return "IndexCreateRequest{" +
            ", indexName='" + indexName + '\'' +
            ", mappings=" + mappings +
            ", sourceIndexName='" + sourceIndexName + '\'' +
            ", sourceProjectId='" + sourceProjectId + '\'' +
            ", sourceProjectApiKey='" + sourceProjectApiKey + '\'' +
            ", indexClass='" + indexClass + '\'' +
            '}';
    }
}
