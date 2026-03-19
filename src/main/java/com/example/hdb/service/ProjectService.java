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
import com.example.hdb.entity.Project;
import com.example.hdb.entity.ProjectStatus;

import java.util.List;

public interface ProjectService {
    
    // ========== 기존 메서드 (기존 방식 유지) ==========
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
    
    // ========== loginId 기반 확장 메서드 (신규 추가) ==========
    // loginId 기반 프로젝트 생성
    Project createProjectByLoginId(String loginId, ProjectCreateRequest request);
    
    // loginId 기반 프로젝트 목록
    List<Project> getUserProjects(String loginId);
    
    // loginId 기반 프로젝트 상세 조회
    Project getProjectByLoginId(String loginId, Long projectId);
    
    // 프로젝트 상태 업데이트
    Project updateProjectStatus(Long projectId, ProjectStatus status);
}
