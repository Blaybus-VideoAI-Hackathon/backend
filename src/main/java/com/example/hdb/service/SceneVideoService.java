package com.example.hdb.service;

import com.example.hdb.dto.response.SceneVideoResponse;

import java.util.List;

public interface SceneVideoService {
    
    /**
     * Scene 영상 생성
     * @param projectId 프로젝트 ID
     * @param sceneId 씬 ID
     * @param loginId 사용자 ID
     * @param duration 영상 길이 (초)
     * @return 생성된 영상 정보
     */
    SceneVideoResponse generateVideo(Long projectId, Long sceneId, String loginId, Integer duration);
    
    /**
     * Scene 영상 목록 조회
     * @param projectId 프로젝트 ID
     * @param sceneId 씬 ID
     * @param loginId 사용자 ID
     * @return 영상 목록
     */
    List<SceneVideoResponse> getVideos(Long projectId, Long sceneId, String loginId);
    
    /**
     * 프로젝트 전체 영상 목록 조회
     * @param projectId 프로젝트 ID
     * @param loginId 사용자 ID
     * @return 프로젝트 내 모든 영상 목록
     */
    List<SceneVideoResponse> getProjectVideos(Long projectId, String loginId);
}
