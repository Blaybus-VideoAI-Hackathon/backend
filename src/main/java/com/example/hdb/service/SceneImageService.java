package com.example.hdb.service;

import com.example.hdb.dto.response.SceneImageResponse;

import java.util.List;

public interface SceneImageService {
    
    /**
     * Scene 이미지 생성
     * @param projectId 프로젝트 ID
     * @param sceneId 씬 ID
     * @param loginId 사용자 ID
     * @return 생성된 이미지 정보
     */
    SceneImageResponse generateImage(Long projectId, Long sceneId, String loginId);
    
    /**
     * Scene 이미지 목록 조회
     * @param projectId 프로젝트 ID
     * @param sceneId 씬 ID
     * @param loginId 사용자 ID
     * @return 이미지 목록
     */
    List<SceneImageResponse> getImages(Long projectId, Long sceneId, String loginId);
}
