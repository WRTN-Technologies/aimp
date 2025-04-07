package io.wrtn.dto;

import io.wrtn.model.index.FieldConfig;
import java.util.Map;

public class IndexUpdateRequest {

    private Map<String, FieldConfig> mappings;

    public IndexUpdateRequest(Map<String, FieldConfig> mappings) {
        this.mappings = mappings;
    }

    public Map<String, FieldConfig> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, FieldConfig> mappings) {
        this.mappings = mappings;
    }

    @Override
    public String toString() {
        return "UpdateMappingRequest{" +
            "mappings=" + mappings +
            '}';
    }
}
