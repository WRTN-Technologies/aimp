package io.wrtn.dto;

import io.wrtn.model.project.Project;

public class ProjectCreateResponse {

    private ProjectResponse project;

    public ProjectCreateResponse(Project project) {
        this.project = new ProjectResponse(project);
    }

    public ProjectResponse getProject() {
        return project;
    }

    public void setProject(ProjectResponse project) {
        this.project = project;
    }

    @Override
    public String toString() {
        return "ProjectCreateResponse{" +
            "projectResponse=" + project +
            '}';
    }
}
