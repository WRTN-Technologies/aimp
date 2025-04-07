package io.wrtn.dto;

import io.wrtn.model.project.Project;

public class ProjectResponse {

    private String id;
    private String projectName;
    private String apiKey;
    private Double rateLimit;
    private String status;

    public ProjectResponse(Project project) {
        this.id = project.getId();
        this.projectName = project.getProjectName();
        this.apiKey = project.getProjectApiKey();
        this.rateLimit = project.getRateLimit();
        this.status = project.getProjectStatus();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Double getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(Double rateLimit) {
        this.rateLimit = rateLimit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "ProjectResponse{" +
            "id='" + id + '\'' +
            ", projectName='" + projectName + '\'' +
            ", apiKey='" + apiKey + '\'' +
            ", rateLimit=" + rateLimit +
            ", status='" + status + '\'' +
            '}';
    }
}
