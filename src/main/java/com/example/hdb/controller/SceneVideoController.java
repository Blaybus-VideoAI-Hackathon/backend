package com.example.hdb.controller;

import com.example.hdb.dto.common.ApiResponse;
import com.example.hdb.dto.request.SceneVideoGenerateRequest;
import com.example.hdb.dto.response.SceneVideoResponse;
import com.example.hdb.service.SceneVideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
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
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId) {
        
        log.info("Getting all videos for project: {}", projectId);
        
        String loginId = "user1"; // 임시 fallback
        List<SceneVideoResponse> videos = sceneVideoService.getProjectVideos(projectId, loginId);
        
        return ResponseEntity.ok(ApiResponse.success("프로젝트 영상 목록 조회 성공", videos));
    }
    
    @Operation(
        summary = "씬 영상 생성", 
        description = "씬의 videoPrompt를 기반으로 영상을 생성합니다. 영상 길이를 선택할 수 있습니다.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "영상 생성 요청",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = SceneVideoGenerateRequest.class),
                examples = {
                    @ExampleObject(
                        name = "3초 영상",
                        value = "{\"duration\": 3}",
                        description = "3초 길이의 영상 생성"
                    ),
                    @ExampleObject(
                        name = "4초 영상", 
                        value = "{\"duration\": 4}",
                        description = "4초 길이의 영상 생성"
                    ),
                    @ExampleObject(
                        name = "5초 영상",
                        value = "{\"duration\": 5}",
                        description = "5초 길이의 영상 생성"
                    )
                }
            )
        )
    )
    @PostMapping("/{projectId}/scenes/{sceneId}/videos/generate")
    public ResponseEntity<ApiResponse<SceneVideoResponse>> generateVideo(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Parameter(description = "씬 ID") @PathVariable Long sceneId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false) @Valid SceneVideoGenerateRequest request,
            Authentication authentication) {
        
        log.info("Generating video for scene: {} in project: {}", sceneId, projectId);
        
        String loginId = resolveLoginId(authentication);
        
        // duration 파라미터 추출 (기본값: 3초)
        Integer duration = 3; // 기본값
        if (request != null && request.getDuration() != null) {
            duration = request.getDuration();
            // 유효성 체크 (3-5초)
            if (duration < 3 || duration > 5) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("영상 길이는 3초, 4초, 5초 중에서 선택 가능합니다."));
            }
        }
        
        SceneVideoResponse response = sceneVideoService.generateVideo(projectId, sceneId, loginId, duration);
        
        return ResponseEntity.ok(ApiResponse.success("영상 생성 성공", response));
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
