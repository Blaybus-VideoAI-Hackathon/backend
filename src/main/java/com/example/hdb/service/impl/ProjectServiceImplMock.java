package com.example.hdb.service.impl;

import com.example.hdb.dto.request.ProjectCreateRequest;
import com.example.hdb.dto.response.ProjectResponse;
import com.example.hdb.entity.PlanningStatus;
import com.example.hdb.entity.Project;
import com.example.hdb.service.ProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Profile("render")
@Slf4j
public class ProjectServiceImplMock implements ProjectService {

    @Override
    public ProjectResponse createProject(ProjectCreateRequest request) {
        log.info("Mock: Creating project with title: {}", request.getTitle());
        
        Project mockProject = Project.builder()
                .id(1L)
                .title(request.getTitle())
                .idea(request.getIdea())
                .style(request.getStyle())
                .ratio(request.getRatio())
                .planningStatus(request.getPlanningStatus() != null ? request.getPlanningStatus() : PlanningStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .scenes(new ArrayList<>())
                .build();
        
        return ProjectResponse.from(mockProject);
    }

    @Override
    public ProjectResponse getProjectById(Long id) {
        log.info("Mock: Getting project with id: {}", id);
        
        Project mockProject = Project.builder()
                .id(id)
                .title("Mock Project " + id)
                .idea("Mock idea for project " + id)
                .style("mock-style")
                .ratio("16:9")
                .planningStatus(PlanningStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .scenes(new ArrayList<>())
                .build();
        
        return ProjectResponse.from(mockProject);
    }

    @Override
    public List<ProjectResponse> getProjectsByStatus(String status) {
        log.info("Mock: Getting projects with status: {}", status);
        
        List<ProjectResponse> projects = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Project mockProject = Project.builder()
                    .id((long) i)
                    .title("Mock Project " + i)
                    .idea("Mock idea for project " + i)
                    .style("mock-style")
                    .ratio("16:9")
                    .planningStatus(PlanningStatus.DRAFT)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .scenes(new ArrayList<>())
                    .build();
            projects.add(ProjectResponse.from(mockProject));
        }
        
        return projects;
    }

    @Override
    public List<ProjectResponse> getProjectsByUserId(Long userId) {
        log.info("Mock: Getting projects for user: {}", userId);
        
        List<ProjectResponse> projects = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Project mockProject = Project.builder()
                    .id((long) i)
                    .title("Mock Project " + i + " for User " + userId)
                    .idea("Mock idea for project " + i)
                    .style("mock-style")
                    .ratio("16:9")
                    .planningStatus(PlanningStatus.DRAFT)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .scenes(new ArrayList<>())
                    .build();
            projects.add(ProjectResponse.from(mockProject));
        }
        
        return projects;
    }

    @Override
    public ProjectResponse updateProject(Long id, ProjectCreateRequest request) {
        log.info("Mock: Updating project with id: {}", id);
        
        Project mockProject = Project.builder()
                .id(id)
                .title(request.getTitle())
                .idea(request.getIdea())
                .style(request.getStyle())
                .ratio(request.getRatio())
                .planningStatus(request.getPlanningStatus() != null ? request.getPlanningStatus() : PlanningStatus.DRAFT)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .scenes(new ArrayList<>())
                .build();
        
        return ProjectResponse.from(mockProject);
    }

    @Override
    public void deleteProject(Long id) {
        log.info("Mock: Deleting project with id: {}", id);
        // Mock implementation - just log the deletion
    }
}
