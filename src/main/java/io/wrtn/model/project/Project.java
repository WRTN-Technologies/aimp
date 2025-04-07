package io.wrtn.model.project;

import io.wrtn.dto.ProjectCreateRequest;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import io.wrtn.util.UUID;

import java.util.Objects;

import static io.wrtn.util.Constants.*;
import static io.wrtn.util.TimeUtil.getCurrentUnixEpoch;

@DynamoDbBean
public class Project {

    private String id;
    private String projectName;
    private String projectApiKey;
    private String projectStatus;
    private Long createdAt;
    private Long updatedAt;
    private boolean conditionCheckFailed;
    private Double rateLimit;

    public Project(ProjectCreateRequest request) {
        Objects.requireNonNull(request);
        this.id = UUID.generateShortUUID(Config.PROJECT_ID_LENGTH);
        this.projectName = request.getProjectName();
        this.rateLimit = request.getRateLimit();
        this.projectStatus = ProjectStatus.PROJECT_STATUS_CREATING;
        this.createdAt = getCurrentUnixEpoch();
        this.updatedAt = this.createdAt;
    }

    public Project() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @DynamoDbAttribute("projectName")
    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    @DynamoDbAttribute("projectApiKey")
    public String getProjectApiKey() {
        return projectApiKey;
    }

    public void setProjectApiKey(String projectApiKey) {
        this.projectApiKey = projectApiKey;
    }

    @DynamoDbAttribute("projectStatus")
    public String getProjectStatus() {
        return projectStatus;
    }

    public void setProjectStatus(String projectStatus) {
        this.projectStatus = projectStatus;
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

    @DynamoDbIgnore
    public boolean isConditionCheckFailed() {
        return conditionCheckFailed;
    }

    public void setConditionCheckFailed(boolean conditionCheckFailed) {
        this.conditionCheckFailed = conditionCheckFailed;
    }

    @DynamoDbAttribute("rateLimit")
    public Double getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(Double rateLimit) {
        this.rateLimit = rateLimit;
    }

    @Override
    public String toString() {
        return "Project{" +
            "id='" + id + '\'' +
            ", projectName='" + projectName + '\'' +
            ", projectApiKey='" + projectApiKey + '\'' +
            ", projectStatus='" + projectStatus + '\'' +
            ", createdAt=" + createdAt +
            ", updatedAt=" + updatedAt +
            ", conditionCheckFailed=" + conditionCheckFailed +
            ", rateLimit=" + rateLimit +
            '}';
    }
}
