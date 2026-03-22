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
    
    // ========== loginId 기반 확장 메서드 (신규 추가) ==========
    // loginId 기반 프로젝트 생성
    Project createProjectByLoginId(String loginId, ProjectCreateRequest request);
    
    // loginId 기반 프로젝트 목록
    List<Project> getUserProjects(String loginId);
    
    // loginId 기반 프로젝트 상세 조회
    Project getProjectByLoginId(String loginId, Long projectId);
    
    // 프로젝트 상태 업데이트
    Project updateProjectStatus(Long projectId, ProjectStatus status);
    
    // 프로젝트 삭제
    void deleteProject(Long projectId, String loginId);
}
