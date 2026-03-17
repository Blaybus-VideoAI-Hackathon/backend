package com.example.hdb.service;

import com.example.hdb.dto.request.ProjectCreateRequest;
import com.example.hdb.dto.response.ProjectResponse;

import java.util.List;

public interface ProjectService {
    ProjectResponse createProject(ProjectCreateRequest request);
    ProjectResponse getProjectById(Long id);
    List<ProjectResponse> getProjectsByStatus(String status);
    List<ProjectResponse> getProjectsByUserId(Long userId);
    ProjectResponse updateProject(Long id, ProjectCreateRequest request);
    void deleteProject(Long id);
}
