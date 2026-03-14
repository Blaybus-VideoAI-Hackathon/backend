package com.example.hdb.service.impl;

import com.example.hdb.dto.request.SceneCreateRequest;
import com.example.hdb.dto.response.SceneResponse;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneStatus;
import com.example.hdb.service.SceneService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Primary
@Slf4j
public class SceneServiceImplMock implements SceneService {

    @Override
    public SceneResponse createScene(SceneCreateRequest request) {
        log.info("Mock: Creating scene for project {}: {}", request.getProjectId(), request.getTitle());
        
        Project mockProject = Project.builder()
                .id(request.getProjectId())
                .title("Mock Project " + request.getProjectId())
                .build();
        
        Scene mockScene = Scene.builder()
                .id(1L)
                .project(mockProject)
                .sceneOrder(request.getSceneOrder())
                .title(request.getTitle())
                .description(request.getDescription())
                .summary(request.getSummary())
                .optionalElements(request.getOptionalElements())
                .imagePrompt(request.getImagePrompt())
                .videoPrompt(request.getVideoPrompt())
                .status(request.getStatus() != null ? request.getStatus() : SceneStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        return SceneResponse.from(mockScene);
    }

    @Override
    public SceneResponse getSceneById(Long id) {
        log.info("Mock: Getting scene with id: {}", id);
        
        Project mockProject = Project.builder()
                .id(1L)
                .title("Mock Project 1")
                .build();
        
        Scene mockScene = Scene.builder()
                .id(id)
                .project(mockProject)
                .sceneOrder(1)
                .title("Mock Scene " + id)
                .description("Mock description for scene " + id)
                .summary("Mock summary for scene " + id)
                .optionalElements("Mock optional elements")
                .imagePrompt("Mock image prompt")
                .videoPrompt("Mock video prompt")
                .status(SceneStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        return SceneResponse.from(mockScene);
    }

    @Override
    public List<SceneResponse> getScenesByProjectId(Long projectId) {
        log.info("Mock: Getting scenes for project: {}", projectId);
        
        List<SceneResponse> scenes = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Project mockProject = Project.builder()
                    .id(projectId)
                    .title("Mock Project " + projectId)
                    .build();
            
            Scene mockScene = Scene.builder()
                    .id((long) i)
                    .project(mockProject)
                    .sceneOrder(i)
                    .title("Mock Scene " + i)
                    .description("Mock description for scene " + i)
                    .summary("Mock summary for scene " + i)
                    .optionalElements("Mock optional elements")
                    .imagePrompt("Mock image prompt")
                    .videoPrompt("Mock video prompt")
                    .status(SceneStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            scenes.add(SceneResponse.from(mockScene));
        }
        
        return scenes;
    }

    @Override
    public List<SceneResponse> getScenesByProjectIdOrderByOrder(Long projectId) {
        return getScenesByProjectId(projectId);
    }

    @Override
    public List<SceneResponse> getScenesByStatus(String status) {
        log.info("Mock: Getting scenes with status: {}", status);
        
        List<SceneResponse> scenes = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Project mockProject = Project.builder()
                    .id(1L)
                    .title("Mock Project 1")
                    .build();
            
            Scene mockScene = Scene.builder()
                    .id((long) i)
                    .project(mockProject)
                    .sceneOrder(i)
                    .title("Mock Scene " + i)
                    .description("Mock description for scene " + i)
                    .summary("Mock summary for scene " + i)
                    .optionalElements("Mock optional elements")
                    .imagePrompt("Mock image prompt")
                    .videoPrompt("Mock video prompt")
                    .status(SceneStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            scenes.add(SceneResponse.from(mockScene));
        }
        
        return scenes;
    }

    @Override
    public SceneResponse updateScene(Long id, SceneCreateRequest request) {
        log.info("Mock: Updating scene with id: {}", id);
        
        Project mockProject = Project.builder()
                .id(request.getProjectId())
                .title("Mock Project " + request.getProjectId())
                .build();
        
        Scene mockScene = Scene.builder()
                .id(id)
                .project(mockProject)
                .sceneOrder(request.getSceneOrder())
                .title(request.getTitle())
                .description(request.getDescription())
                .summary(request.getSummary())
                .optionalElements(request.getOptionalElements())
                .imagePrompt(request.getImagePrompt())
                .videoPrompt(request.getVideoPrompt())
                .status(request.getStatus() != null ? request.getStatus() : SceneStatus.PENDING)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .build();
        
        return SceneResponse.from(mockScene);
    }

    @Override
    public void deleteScene(Long id) {
        log.info("Mock: Deleting scene with id: {}", id);
        // Mock implementation - just log the deletion
    }
}
