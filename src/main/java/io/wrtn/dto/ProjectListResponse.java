package io.wrtn.dto;

import io.wrtn.model.project.Project;

import java.util.ArrayList;
import java.util.List;

public class ProjectListResponse {

    List<ProjectResponse> projects;

    public ProjectListResponse(List<Project> projects) {
        this.projects = new ArrayList<>();

        for (Project project : projects) {
            this.projects.add(new ProjectResponse(project));
        }
    }

    public List<ProjectResponse> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectResponse> projects) {
        this.projects = projects;
    }

    @Override
    public String toString() {
        return "ProjectListResponse{" +
            "projects=" + projects +
            '}';
    }
}
