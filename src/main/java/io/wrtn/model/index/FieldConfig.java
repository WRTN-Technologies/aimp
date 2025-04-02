package io.wrtn.model.index;

import java.util.List;
import java.util.Map;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

@DynamoDbBean
public class FieldConfig {

    private String type;
    private List<String> analyzers;
    private Integer dimensions;
    private String similarity;
    private Map<String, FieldConfig> objectMapping;

    @DynamoDbAttribute("type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @DynamoDbAttribute("analyzers")
    public List<String> getAnalyzers() {
        return analyzers;
    }

    public void setAnalyzers(List<String> analyzers) {
        this.analyzers = analyzers;
    }

    @DynamoDbAttribute("dimensions")
    public Integer getDimensions() {
        return dimensions;
    }

    public void setDimensions(Integer dimensions) {
        this.dimensions = dimensions;
    }

    @DynamoDbAttribute("similarity")
    public String getSimilarity() {
        return similarity;
    }

    public void setSimilarity(String similarity) {
        this.similarity = similarity;
    }

    @DynamoDbAttribute("objectMapping")
    public Map<String, FieldConfig> getObjectMapping() {
        return objectMapping;
    }

    public void setObjectMapping(Map<String, FieldConfig> objectMapping) {
        this.objectMapping = objectMapping;
    }

    @Override
    public String toString() {
        return "FieldConfig{" +
            "type='" + type + '\'' +
            ", analyzers='" + analyzers + '\'' +
            ", dimensions=" + dimensions +
            ", similarity='" + similarity + '\'' +
            ", objectMapping=" + objectMapping +
            '}';
    }
}
