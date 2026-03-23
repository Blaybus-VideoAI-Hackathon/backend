package com.example.hdb.controller;

import com.example.hdb.dto.common.ApiResponse;
import com.example.hdb.dto.response.SceneVideoResponse;
import com.example.hdb.service.SceneVideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
public class SceneVideoController extends BaseController {

    private final SceneVideoService sceneVideoService;

    @Operation(summary = "프로젝트 전체 영상 목록 조회", description = "프로젝트에 속한 모든 씬의 영상 목록을 조회합니다.")
    @GetMapping("/{projectId}/videos")
    public ResponseEntity<ApiResponse<List<SceneVideoResponse>>> getProjectVideos(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            Authentication authentication) {

        log.info("Getting all videos for project: {}", projectId);

        String loginId = resolveLoginId(authentication);
        List<SceneVideoResponse> videos = sceneVideoService.getProjectVideos(projectId, loginId);

        return ResponseEntity.ok(ApiResponse.success("프로젝트 영상 목록 조회 성공", videos));
    }

    @Operation(
            summary = "씬 영상 생성",
            description = "씬의 videoPrompt를 기반으로 5초 고정 영상을 생성합니다."
    )
    @PostMapping("/{projectId}/scenes/{sceneId}/videos/generate")
    public ResponseEntity<ApiResponse<SceneVideoResponse>> generateVideo(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Parameter(description = "씬 ID") @PathVariable Long sceneId,
            Authentication authentication) {

        log.info("Generating 5-second video for scene: {} in project: {}", sceneId, projectId);

        String loginId = resolveLoginId(authentication);

        SceneVideoResponse response = sceneVideoService.generateVideo(projectId, sceneId, loginId, 5);

        String message = "COMPLETED".equalsIgnoreCase(response.getStatus())
                ? "영상 생성 성공"
                : "영상 생성 요청 완료";

        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @Operation(summary = "씬 영상 목록 조회", description = "씬에 생성된 모든 영상을 조회합니다.")
    @GetMapping("/{projectId}/scenes/{sceneId}/videos")
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