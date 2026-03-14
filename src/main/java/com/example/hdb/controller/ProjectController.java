package com.example.hdb.controller;

import com.example.hdb.dto.request.ProjectCreateRequest;
import com.example.hdb.dto.response.ApiResponse;
import com.example.hdb.dto.response.ProjectResponse;
import com.example.hdb.dto.response.SceneResponse;
import com.example.hdb.service.ProjectService;
import com.example.hdb.service.SceneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final SceneService sceneService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(@Valid @RequestBody ProjectCreateRequest request) {
        ProjectResponse response = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project created successfully", response));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(@PathVariable Long projectId) {
        ProjectResponse response = projectService.getProjectById(projectId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{projectId}/idea")
    public ResponseEntity<ApiResponse<Void>> inputIdea(@PathVariable Long projectId, @RequestBody Map<String, String> request) {
        String idea = request.get("idea");
        return ResponseEntity.ok(ApiResponse.success("Idea input successfully", null));
    }

    @GetMapping("/{projectId}/scenes")
    public ResponseEntity<ApiResponse<List<SceneResponse>>> getProjectScenes(@PathVariable Long projectId) {
        List<SceneResponse> scenes = sceneService.getScenesByProjectIdOrderByOrder(projectId);
        return ResponseEntity.ok(ApiResponse.success(scenes));
    }
}
