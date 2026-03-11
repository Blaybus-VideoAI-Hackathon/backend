package com.example.hdb.service;

import com.example.hdb.dto.request.SceneCreateRequest;
import com.example.hdb.dto.response.SceneResponse;

import java.util.List;

public interface SceneService {
    SceneResponse createScene(SceneCreateRequest request);
    SceneResponse getSceneById(Long id);
    List<SceneResponse> getScenesByProjectId(Long projectId);
    List<SceneResponse> getScenesByProjectIdOrderByOrder(Long projectId);
    List<SceneResponse> getScenesByStatus(String status);
    SceneResponse updateScene(Long id, SceneCreateRequest request);
    void deleteScene(Long id);
}
