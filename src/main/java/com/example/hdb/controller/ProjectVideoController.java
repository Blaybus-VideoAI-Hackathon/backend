package com.example.hdb.controller;

import com.example.hdb.dto.common.ApiResponse;
import com.example.hdb.dto.request.VideoMergeRequest;
import com.example.hdb.dto.response.ProjectVideoResponse;
import com.example.hdb.service.ProjectVideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects/{projectId}/videos")
@RequiredArgsConstructor
public class ProjectVideoController extends BaseController {
    
    private final ProjectVideoService projectVideoService;
    
    @Operation(summary = "프로젝트 영상 병합", description = "프로젝트 내 Scene 영상들을 순서대로 병합하여 최종 영상을 생성합니다.")
    @PostMapping("/merge")
    public ResponseEntity<ApiResponse<ProjectVideoResponse>> mergeProjectVideos(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            @Valid @RequestBody VideoMergeRequest request,
            Authentication authentication) {
        
        log.info("Merging videos for project: {}", projectId);
        
        String loginId = resolveLoginId(authentication);
        ProjectVideoResponse response = projectVideoService.mergeProjectVideos(projectId, loginId, request);
        
        return ResponseEntity.ok(ApiResponse.success("최종 영상 병합 완료", response));
    }
    
    @Operation(summary = "최종 병합 영상 조회", description = "프로젝트의 최종 병합 영상을 조회합니다.")
    @GetMapping("/final")
    public ResponseEntity<ApiResponse<ProjectVideoResponse>> getFinalVideo(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId,
            Authentication authentication) {
        
        log.info("Getting final video for project: {}", projectId);
        
        String loginId = resolveLoginId(authentication);
        ProjectVideoResponse response = projectVideoService.getFinalVideo(projectId, loginId);
        
        return ResponseEntity.ok(ApiResponse.success("최종 영상 조회 성공", response));
    }
}
