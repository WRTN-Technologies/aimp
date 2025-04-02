package io.wrtn.dto;

import io.wrtn.model.project.Project;

import java.util.List;

public class ProjectDescribeResponse {

    ProjectResponse project;
    List<IndexResponse> indexes;

    public ProjectDescribeResponse(Project project, List<IndexResponse> indexResponses) {
        this.project = new ProjectResponse(project);
        this.indexes = indexResponses;
    }

    public ProjectResponse getProject() {
        return project;
    }

    public void setProject(ProjectResponse project) {
        this.project = project;
    }

    public List<IndexResponse> getIndexes() {
        return indexes;
    }

    public void setIndexes(List<IndexResponse> indexes) {
        this.indexes = indexes;
    }

    @Override
    public String toString() {
        return "ProjectDescribeResponse{" +
            "project=" + project +
            ", indexes=" + indexes +
            '}';
    }
}
