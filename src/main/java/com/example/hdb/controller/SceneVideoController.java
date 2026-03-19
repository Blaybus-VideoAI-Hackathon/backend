package com.example.hdb.controller;

import com.example.hdb.dto.common.ApiResponse;
import com.example.hdb.dto.response.SceneVideoResponse;
import com.example.hdb.service.SceneVideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/scenes/{sceneId}/videos")
@RequiredArgsConstructor
@Slf4j
public class SceneVideoController extends BaseController {
    
    private final SceneVideoService sceneVideoService;
    
    @Operation(summary = "씬 영상 생성", description = "씬의 videoPrompt를 기반으로 영상을 생성합니다.")
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<SceneVideoResponse>> generateVideo(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Parameter(description = "씬 ID") @PathVariable Long sceneId,
            Authentication authentication) {
        
        log.info("Generating video for scene: {} in project: {}", sceneId, projectId);
        
        String loginId = resolveLoginId(authentication);
        SceneVideoResponse response = sceneVideoService.generateVideo(projectId, sceneId, loginId);
        
        return ResponseEntity.ok(ApiResponse.success("영상 생성 성공", response));
    }
    
    @Operation(summary = "씬 영상 목록 조회", description = "씬에 생성된 모든 영상을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SceneVideoResponse>>> getVideos(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Parameter(description = "씬 ID") @PathVariable Long sceneId,
            Authentication authentication) {
        
        log.info("Getting videos for scene: {} in project: {}", sceneId, projectId);
        
        String loginId = resolveLoginId(authentication);
        List<SceneVideoResponse> videos = sceneVideoService.getVideos(projectId, sceneId, loginId);
        
        return ResponseEntity.ok(ApiResponse.success("영상 목록 조회 성공", videos));
    }
}
