package com.example.hdb.service;

import com.example.hdb.dto.request.SceneCreateRequest;
import com.example.hdb.dto.request.SceneUpdateRequest;
import com.example.hdb.dto.response.SceneResponse;

import java.util.List;

public interface SceneService {
    
    SceneResponse createScene(SceneCreateRequest request);
    
    SceneResponse updateScene(Long sceneId, SceneUpdateRequest request);
    
    SceneResponse updateSceneAndCheckPermission(Long sceneId, Long userId, SceneUpdateRequest request);
    
    List<SceneResponse> getScenesByProjectId(Long projectId);
}
