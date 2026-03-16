package com.example.hdb.service.impl;

import com.example.hdb.dto.request.ProjectCreateRequest;
import com.example.hdb.dto.response.ProjectResponse;
import com.example.hdb.entity.PlanningStatus;
import com.example.hdb.entity.Project;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Profile("!no-db")
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;

    @Override
    public ProjectResponse createProject(ProjectCreateRequest request) {
        Project project = Project.builder()
                .title(request.getTitle())
                .idea(request.getIdea())
                .style(request.getStyle())
                .ratio(request.getRatio())
                .planningStatus(request.getPlanningStatus() != null ? request.getPlanningStatus() : PlanningStatus.DRAFT)
                .build();

        Project savedProject = projectRepository.save(project);
        return ProjectResponse.from(savedProject);
    }

    @Override
    public ProjectResponse getProjectById(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        return ProjectResponse.from(project);
    }

    @Override
    public List<ProjectResponse> getProjectsByStatus(String status) {
        PlanningStatus planningStatus;
        try {
            planningStatus = PlanningStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        
        List<Project> projects = projectRepository.findByPlanningStatusOrderByCreatedAtDesc(planningStatus);
        return projects.stream()
                .map(ProjectResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public ProjectResponse updateProject(Long id, ProjectCreateRequest request) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        project.setTitle(request.getTitle());
        project.setIdea(request.getIdea());
        project.setStyle(request.getStyle());
        project.setRatio(request.getRatio());
        if (request.getPlanningStatus() != null) {
            project.setPlanningStatus(request.getPlanningStatus());
        }

        Project updatedProject = projectRepository.save(project);
        return ProjectResponse.from(updatedProject);
    }

    @Override
    public void deleteProject(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        projectRepository.delete(project);
    }
}
