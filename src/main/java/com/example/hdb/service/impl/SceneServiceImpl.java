package com.example.hdb.service.impl;

import com.example.hdb.dto.request.SceneCreateRequest;
import com.example.hdb.dto.request.SceneUpdateRequest;
import com.example.hdb.dto.response.SceneResponse;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.Scene;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.service.SceneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SceneServiceImpl implements SceneService {
    
    private final SceneRepository sceneRepository;
    private final ProjectRepository projectRepository;
    
    @Override
    public SceneResponse createScene(SceneCreateRequest request) {
        log.info("Creating scene with projectId: {}, sceneOrder: {}", request.getProjectId(), request.getSceneOrder());
        
        // 프로젝트 존재 확인
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        // sceneOrder 중복 확인
        if (sceneRepository.existsByProjectIdAndSceneOrder(request.getProjectId(), request.getSceneOrder())) {
            throw new BusinessException(ErrorCode.SCENE_ORDER_DUPLICATE);
        }
        
        Scene scene = Scene.builder()
                .project(project)
                .sceneOrder(request.getSceneOrder())
                .summary(request.getSummary())
                .optionalElements(request.getOptionalElements())
                .imagePrompt(request.getImagePrompt())
                .videoPrompt(request.getVideoPrompt())
                .status(request.getStatus())
                .build();
        
        Scene savedScene = sceneRepository.save(scene);
        log.info("Scene created successfully with id: {}", savedScene.getId());
        
        return SceneResponse.from(savedScene);
    }
    
    @Override
    public SceneResponse updateScene(Long sceneId, SceneUpdateRequest request) {
        log.info("Updating scene with id: {}", sceneId);
        
        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        
        scene.setSummary(request.getSummary());
        scene.setOptionalElements(request.getOptionalElements());
        scene.setImagePrompt(request.getImagePrompt());
        scene.setVideoPrompt(request.getVideoPrompt());
        
        Scene updatedScene = sceneRepository.save(scene);
        log.info("Scene updated successfully with id: {}", updatedScene.getId());
        
        return SceneResponse.from(updatedScene);
    }
    
    @Override
    public SceneResponse updateSceneAndCheckPermission(Long sceneId, Long userId, SceneUpdateRequest request) {
        log.info("Updating scene with id: {} for userId: {}", sceneId, userId);
        
        Scene scene = sceneRepository.findByIdAndProjectUserId(sceneId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        
        scene.setSummary(request.getSummary());
        scene.setOptionalElements(request.getOptionalElements());
        scene.setImagePrompt(request.getImagePrompt());
        scene.setVideoPrompt(request.getVideoPrompt());
        
        Scene updatedScene = sceneRepository.save(scene);
        log.info("Scene updated successfully with id: {} for userId: {}", updatedScene.getId(), userId);
        
        return SceneResponse.from(updatedScene);
    }
    
    @Override
    public List<SceneResponse> getScenesByProjectId(Long projectId) {
        log.info("Getting scenes for projectId: {}", projectId);
        
        // 프로젝트 존재 확인
        if (!projectRepository.existsById(projectId)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        
        List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);
        
        return scenes.stream()
                .map(SceneResponse::from)
                .collect(Collectors.toList());
    }
}
