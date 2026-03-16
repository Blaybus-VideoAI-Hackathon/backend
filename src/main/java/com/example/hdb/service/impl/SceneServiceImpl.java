package com.example.hdb.service.impl;

import com.example.hdb.dto.request.SceneCreateRequest;
import com.example.hdb.dto.response.SceneResponse;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneStatus;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.service.SceneService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Profile("!no-db")
public class SceneServiceImpl implements SceneService {

    private final SceneRepository sceneRepository;
    private final ProjectRepository projectRepository;

    @Override
    public SceneResponse createScene(SceneCreateRequest request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        Scene scene = Scene.builder()
                .project(project)
                .sceneOrder(request.getSceneOrder())
                .title(request.getTitle())
                .description(request.getDescription())
                .summary(request.getSummary())
                .optionalElements(request.getOptionalElements())
                .imagePrompt(request.getImagePrompt())
                .videoPrompt(request.getVideoPrompt())
                .status(request.getStatus() != null ? request.getStatus() : SceneStatus.PENDING)
                .build();

        Scene savedScene = sceneRepository.save(scene);
        return SceneResponse.from(savedScene);
    }

    @Override
    public SceneResponse getSceneById(Long id) {
        Scene scene = sceneRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        return SceneResponse.from(scene);
    }

    @Override
    public List<SceneResponse> getScenesByProjectId(Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        List<Scene> scenes = sceneRepository.findByProjectId(projectId);
        return scenes.stream()
                .map(SceneResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<SceneResponse> getScenesByProjectIdOrderByOrder(Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrder(projectId);
        return scenes.stream()
                .map(SceneResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<SceneResponse> getScenesByStatus(String status) {
        SceneStatus sceneStatus;
        try {
            sceneStatus = SceneStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        
        List<Scene> scenes = sceneRepository.findByStatus(sceneStatus);
        return scenes.stream()
                .map(SceneResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public SceneResponse updateScene(Long id, SceneCreateRequest request) {
        Scene scene = sceneRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        if (!scene.getProject().getId().equals(request.getProjectId())) {
            Project project = projectRepository.findById(request.getProjectId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
            scene.setProject(project);
        }

        scene.setSceneOrder(request.getSceneOrder());
        scene.setTitle(request.getTitle());
        scene.setDescription(request.getDescription());
        scene.setSummary(request.getSummary());
        scene.setOptionalElements(request.getOptionalElements());
        scene.setImagePrompt(request.getImagePrompt());
        scene.setVideoPrompt(request.getVideoPrompt());
        if (request.getStatus() != null) {
            scene.setStatus(request.getStatus());
        }

        Scene updatedScene = sceneRepository.save(scene);
        return SceneResponse.from(updatedScene);
    }

    @Override
    public void deleteScene(Long id) {
        Scene scene = sceneRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));
        
        sceneRepository.delete(scene);
    }
}
