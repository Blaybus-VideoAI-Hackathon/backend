package com.example.hdb.controller;

import com.example.hdb.dto.request.SceneCreateRequest;
import com.example.hdb.dto.response.ApiResponse;
import com.example.hdb.dto.response.SceneResponse;
import com.example.hdb.service.SceneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/scenes")
@RequiredArgsConstructor
public class SceneController {

    private final SceneService sceneService;

    @PatchMapping("/{sceneId}")
    public ResponseEntity<ApiResponse<SceneResponse>> updateScene(
            @PathVariable Long sceneId, 
            @Valid @RequestBody SceneCreateRequest request) {
        SceneResponse response = sceneService.updateScene(sceneId, request);
        return ResponseEntity.ok(ApiResponse.success("Scene updated successfully", response));
    }
}
