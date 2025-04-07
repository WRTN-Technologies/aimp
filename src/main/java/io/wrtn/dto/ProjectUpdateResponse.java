package io.wrtn.dto;

import io.wrtn.model.project.Project;

public class ProjectUpdateResponse {

    private ProjectResponse project;

    public ProjectUpdateResponse(Project project) {
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
        return "ProjectUpdateResponse{" +
            "projectResponse=" + project +
            '}';
    }
}
