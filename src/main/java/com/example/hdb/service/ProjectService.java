package com.example.hdb.service;

import com.example.hdb.dto.common.ApiResponse;
import com.example.hdb.dto.request.CoreElementsRequest;
import com.example.hdb.dto.request.IdeaGenerationRequest;
import com.example.hdb.dto.request.ProjectCreateRequest;
import com.example.hdb.dto.response.IdeaGenerationResponse;
import com.example.hdb.dto.response.ProjectResponse;
import com.example.hdb.dto.response.SceneResponse;
import com.example.hdb.dto.request.SceneGenerationRequest;
import com.example.hdb.dto.response.SceneGenerationResponse;

import java.util.List;

public interface ProjectService {
    
    // 기본 CRUD
    ProjectResponse createProject(ProjectCreateRequest request);
    
    List<ProjectResponse> getProjectsByUserId(Long userId);
    
    ProjectResponse getProjectById(Long projectId);
    
    ProjectResponse getProjectByIdAndUserId(Long projectId, Long userId);
    
    List<SceneResponse> getScenesByProjectId(Long projectId);
    
    List<SceneResponse> getScenesByProjectIdAndUserId(Long projectId, Long userId);
    
    ProjectResponse updateCoreElements(Long projectId, CoreElementsRequest request);
    
    // AI 연동용 API
    ApiResponse<IdeaGenerationResponse> generateProjectIdea(Long projectId, IdeaGenerationRequest request);
    
    ApiResponse<SceneGenerationResponse> generateProjectScenes(Long projectId, SceneGenerationRequest request);
}
