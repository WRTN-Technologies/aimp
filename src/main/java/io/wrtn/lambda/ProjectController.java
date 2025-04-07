package io.wrtn.lambda;

import io.wrtn.dto.*;
import io.wrtn.infra.aws.ApiGateway;
import io.wrtn.infra.aws.DynamoDB;
import io.wrtn.infra.aws.SQS;
import io.wrtn.model.command.ControlCommand;
import io.wrtn.model.index.Index;
import io.wrtn.model.project.Project;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.StatusCode;

import io.wrtn.util.Validation;
import java.util.ArrayList;
import java.util.List;

import static io.wrtn.util.Constants.CommandType.PROJECT_CREATE;
import static io.wrtn.util.Constants.CommandType.PROJECT_DELETE;
import static io.wrtn.util.Constants.ProjectStatus.*;
import static io.wrtn.util.TimeUtil.getCurrentUnixEpoch;

public final class ProjectController {

    private final DynamoDB ddbClient;
    private final SQS sqsClient;
    private final ApiGateway apiGatewayClient;
    private final Validation validation;

    public ProjectController(DynamoDB ddbClient, SQS sqsClient,
        ApiGateway apiGatewayClient, Validation validation) {
        this.ddbClient = ddbClient;
        this.sqsClient = sqsClient;
        this.apiGatewayClient = apiGatewayClient;
        this.validation = validation;
    }

    public ProjectListResponse listProject() {
        return new ProjectListResponse((ddbClient.listProjectsConsistent()));
    }

    public ProjectCreateResponse createProject(ProjectCreateRequest request)
        throws GlobalExceptionHandler {
        validation.validateProjectCreate(request);

        Project project = new Project(request);

        // Put project metadata to DynamoDB
        if (ddbClient.createProjectIfNotExists(project) != null) {
            // Project already exists
            throw new GlobalExceptionHandler("Project " + project.getId() + " already exists",
                StatusCode.RESOURCE_ALREADY_EXISTS);
        }

        // Send command for asynchronous project creation
        ControlCommand command = new ControlCommand();
        command.setType(PROJECT_CREATE);
        command.setProjectId(project.getId());
        command.setRateLimit(request.getRateLimit());
        sqsClient.sendControlCommand(command);

        return new ProjectCreateResponse(project);
    }

    public ProjectUpdateResponse updateProject(String projectId, ProjectUpdateRequest request)
        throws GlobalExceptionHandler {
        validation.validateProjectUpdate(request);

        // Update project rate limit
        apiGatewayClient.updateProjectRateLimit(projectId, request.getRateLimit());

        // Update project metadata
        Project project = new Project();
        project.setId(projectId);
        project.setRateLimit(request.getRateLimit());
        project.setUpdatedAt(getCurrentUnixEpoch());

        Project updatedProject = ddbClient.updateProjectIfActive(project);
        if (updatedProject == null) {
            // This project has been deleted by another context
            throw new GlobalExceptionHandler("Project " + project.getId() + " not found",
                StatusCode.NOT_FOUND);
        } else if (updatedProject.isConditionCheckFailed()) {
            if (!updatedProject.getProjectStatus().equals(PROJECT_STATUS_ACTIVE)) {
                throw new GlobalExceptionHandler("Invalid project status: "
                    + updatedProject.getProjectStatus(), StatusCode.SERVER_ERROR);
            }
        }

        return new ProjectUpdateResponse(updatedProject);
    }

    public ProjectDescribeResponse describeProject(Project project) {
        List<Index> indexes = ddbClient.listIndexesByProjectIdConsistent(project.getId());
        List<IndexResponse> indexResponses = new ArrayList<>();
        for (Index index : indexes) {
            IndexResponse indexResponse = new IndexResponse(index);
            indexResponses.add(indexResponse);
        }

        return new ProjectDescribeResponse(project, indexResponses);
    }

    public void deleteProject(Project project)
        throws GlobalExceptionHandler {
        if (project.getProjectStatus().equals(PROJECT_STATUS_ACTIVE)) {
            // Update project metadata
            project.setProjectStatus(PROJECT_STATUS_DELETING);
            project.setUpdatedAt(getCurrentUnixEpoch());
            Project updatedProject = ddbClient.updateProjectIfActive(project);
            if (updatedProject == null) {
                // This project has been deleted by another context
                throw new GlobalExceptionHandler(
                    "Project " + project.getId() + " not found",
                    StatusCode.NOT_FOUND);
            } else if (updatedProject.isConditionCheckFailed()) {
                if (!updatedProject.getProjectStatus().equals(PROJECT_STATUS_DELETING)) {
                    throw new GlobalExceptionHandler("Invalid project status: "
                        + updatedProject.getProjectStatus(), StatusCode.SERVER_ERROR);
                }
            }

            // Send project deletion command to control queue
            ControlCommand command = new ControlCommand();
            command.setType(PROJECT_DELETE);
            command.setProjectId(project.getId());
            sqsClient.sendControlCommand(command);
        } else if (!project.getProjectStatus().equals(PROJECT_STATUS_DELETING)) {
            throw new GlobalExceptionHandler(
                "Project is not deletable, current status " + project.getProjectStatus(),
                StatusCode.BAD_REQUEST);
        }
    }
}
