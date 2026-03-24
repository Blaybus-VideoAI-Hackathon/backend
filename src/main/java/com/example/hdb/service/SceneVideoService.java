package com.example.hdb.service;

import com.example.hdb.dto.response.SceneVideoResponse;
import com.example.hdb.entity.SceneVideo;

import java.util.List;

public interface SceneVideoService {

    SceneVideoResponse generateVideo(Long projectId, Long sceneId, String loginId, Integer duration);

    List<SceneVideoResponse> getVideos(Long projectId, Long sceneId, String loginId);

    List<SceneVideoResponse> getProjectVideos(Long projectId, String loginId);

    /**
     * 씬의 최신 영상을 Runway에서 상태 동기화 후 반환
     * 병합 전 각 씬의 videoUrl을 최신화하는 데 사용
     */
    SceneVideo syncAndGetLatestVideo(Long sceneId);
}