package io.wrtn.model.index;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean
public class CloneSourceInfo {

    private String sourceProjectId;
    private String sourceIndexName;
    private String sourceIndexVersionId;

    @DynamoDbAttribute("sourceProjectId")
    public String getSourceProjectId() {
        return sourceProjectId;
    }

    public void setSourceProjectId(String sourceProjectId) {
        this.sourceProjectId = sourceProjectId;
    }

    @DynamoDbAttribute("sourceIndexName")
    public String getSourceIndexName() {
        return sourceIndexName;
    }

    public void setSourceIndexName(String sourceIndexName) {
        this.sourceIndexName = sourceIndexName;
    }

    @DynamoDbAttribute("sourceIndexVersionId")
    public String getSourceIndexVersionId() {
        return sourceIndexVersionId;
    }

    public void setSourceIndexVersionId(String sourceIndexVersionId) {
        this.sourceIndexVersionId = sourceIndexVersionId;
    }

    @Override
    public String toString() {
        return "CloneSourceInfo{" +
            ", sourceProjectId='" + sourceProjectId + '\'' +
            ", sourceIndexName='" + sourceIndexName + '\'' +
            ", sourceIndexVersionId='" + sourceIndexVersionId + '\'' +
            '}';
    }

}
