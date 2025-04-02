package io.wrtn.dto;

public class ProjectCreateRequest {

    private String projectName;
    private Double rateLimit;  // Request per second

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public Double getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(Double rateLimit) {
        this.rateLimit = rateLimit;
    }

    @Override
    public String toString() {
        return "ProjectCreateRequest{" +
            "projectName='" + projectName + '\'' +
            ", rateLimit=" + rateLimit +
            '}';
    }
}
