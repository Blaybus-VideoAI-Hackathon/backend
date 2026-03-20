package com.example.hdb.service;

import com.example.hdb.dto.request.SceneCreateRequest;
import com.example.hdb.dto.request.SceneDesignRegenerateRequest;
import com.example.hdb.dto.request.SceneDesignRequest;
import com.example.hdb.dto.request.SceneEditRequest;
import com.example.hdb.dto.request.SceneGenerateRequest;
import com.example.hdb.dto.request.SceneUpdateRequest;
import com.example.hdb.dto.response.SceneDesignResponse;
import com.example.hdb.dto.response.SceneResponse;

import java.util.List;

public interface SceneService {
    
    // ========== 기존 메서드 ==========
    SceneResponse createScene(SceneCreateRequest request);
    
    SceneResponse updateScene(Long sceneId, SceneUpdateRequest request);
    
    SceneResponse updateSceneAndCheckPermission(Long sceneId, Long userId, SceneUpdateRequest request);
    
    List<SceneResponse> getScenesByProjectId(Long projectId);
    
    // ========== 신규 메서드 (Scene 기능 확장) ==========
    // 프로젝트 기획 기반으로 Scene 자동 생성
    List<SceneResponse> generateScenes(Long projectId, String sceneGenerationRequest);
    
    // 특정 Scene 설계 (optional_elements, image_prompt, video_prompt 생성)
    SceneResponse designScene(Long projectId, Long sceneId, String loginId, SceneDesignRequest request);
    
    // 특정 Scene 설계 다시 추천받기 (summary 유지, variation 생성)
    SceneDesignResponse regenerateSceneDesign(Long projectId, Long sceneId, String loginId, SceneDesignRegenerateRequest request);
    
    // 특정 Scene 설계 조회
    SceneDesignResponse getSceneDesign(Long projectId, Long sceneId, String loginId);
    
    // 특정 Scene 수정 (optional_elements, image_prompt, video_prompt 업데이트)
    SceneResponse editScene(Long projectId, Long sceneId, String loginId, SceneEditRequest request);
    
    // 특정 Scene 삭제 (관련 데이터 함께 삭제)
    void deleteScene(Long projectId, Long sceneId, String loginId);
}
