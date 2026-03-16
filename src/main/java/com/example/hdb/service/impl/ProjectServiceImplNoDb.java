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
@Slf4j
public class ProjectServiceImplNoDb implements ProjectService {

    @Override
    public ProjectResponse createProject(ProjectCreateRequest request) {
        log.info("No-DB: Creating project with title: {}", request.getTitle());
        
        Project mockProject = Project.builder()
                .id(System.currentTimeMillis())
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
        log.info("No-DB: Getting project with id: {}", id);
        
        Project mockProject = Project.builder()
                .id(id)
                .title("No-DB Project " + id)
                .idea("No-DB idea for project " + id)
                .style("no-db-style")
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
        log.info("No-DB: Getting projects with status: {}", status);
        
        List<ProjectResponse> projects = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Project mockProject = Project.builder()
                    .id((long) i)
                    .title("No-DB Project " + i)
                    .idea("No-DB idea for project " + i)
                    .style("no-db-style")
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
        log.info("No-DB: Updating project with id: {}", id);
        
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
        log.info("No-DB: Deleting project with id: {}", id);
        // No-DB implementation - just log the deletion
    }
}
