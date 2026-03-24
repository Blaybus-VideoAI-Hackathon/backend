package com.example.hdb.service.impl;

import com.example.hdb.dto.request.VideoMergeRequest;
import com.example.hdb.dto.response.ProjectVideoResponse;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneVideo;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.repository.SceneVideoRepository;
import com.example.hdb.service.ProjectVideoService;
import com.example.hdb.service.SceneVideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ProjectVideoServiceImpl implements ProjectVideoService {

    private final ProjectRepository projectRepository;
    private final SceneRepository sceneRepository;
    private final SceneVideoRepository sceneVideoRepository;
    private final SceneVideoService sceneVideoService;

    @Override
    public ProjectVideoResponse mergeProjectVideos(Long projectId, String loginId, VideoMergeRequest request) {
        log.info("=== STARTING PROJECT VIDEO MERGE ===");
        log.info("projectId={}, loginId={}", projectId, loginId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        try {
            List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);

            if (scenes.isEmpty()) {
                throw new BusinessException(ErrorCode.SCENE_NOT_FOUND);
            }

            log.info("=== SCENE LIST FOR MERGE ===");
            for (Scene scene : scenes) {
                log.info("sceneId={}, sceneOrder={}, summary='{}'",
                        scene.getId(), scene.getSceneOrder(), scene.getSummary());
            }

            List<String> videoUrls = new ArrayList<>();
            List<String> missingScenes = new ArrayList<>();

            for (Scene scene : scenes) {
                log.info("=== SYNCING & CHECKING SCENE_VIDEOS ===");
                log.info("sceneId={}, sceneOrder={}", scene.getId(), scene.getSceneOrder());

                SceneVideo selectedVideo = sceneVideoService.syncAndGetLatestVideo(scene.getId());

                if (selectedVideo != null && selectedVideo.getVideoUrl() != null && !selectedVideo.getVideoUrl().isBlank()) {
                    videoUrls.add(selectedVideo.getVideoUrl());
                    log.info("SELECTED VIDEO FOR MERGE - sceneId={}, videoId={}, status={}, videoUrl={}",
                            scene.getId(), selectedVideo.getId(), selectedVideo.getStatus(), selectedVideo.getVideoUrl());
                } else {
                    String errorMsg = String.format("Scene %d (sceneId=%d, order=%d, summary='%s')",
                            scene.getSceneOrder(), scene.getId(), scene.getSceneOrder(), scene.getSummary());
                    missingScenes.add(errorMsg);
                    log.warn("NO MERGEABLE VIDEO FOUND - sceneId={}, sceneOrder={}", scene.getId(), scene.getSceneOrder());
                }
            }

            log.info("=== FINAL MERGE CANDIDATES ===");
            log.info("total videos found: {}", videoUrls.size());
            for (int i = 0; i < videoUrls.size(); i++) {
                log.info("mergeUrl[{}]: {}", i, videoUrls.get(i));
            }

            if (!missingScenes.isEmpty()) {
                if (request.getSkipMissingVideos() != null && request.getSkipMissingVideos()) {
                    log.warn("Skipping scenes without videos: {}", missingScenes);
                } else {
                    String errorMsg = "다음 Scene에 영상이 없습니다: " + String.join(", ", missingScenes);
                    log.error("MERGE FAILED - missing scenes: {}", missingScenes);
                    throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, errorMsg);
                }
            }

            if (videoUrls.isEmpty()) {
                throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, "병합할 영상이 없습니다.");
            }

            // FFmpeg 병합 대신 videoUrl 목록을 콤마로 연결해서 저장
            String allVideoUrls = String.join(",", videoUrls);

            project.setFinalVideoUrl(allVideoUrls);
            projectRepository.save(project);

            log.info("=== PROJECT VIDEO MERGE COMPLETED (URL LIST) ===");
            log.info("projectId={}, videoCount={}, urls={}", projectId, videoUrls.size(), allVideoUrls);

            return ProjectVideoResponse.builder()
                    .projectId(projectId)
                    .finalVideoUrl(allVideoUrls)
                    .status("READY")
                    .message("영상 목록 준비 완료 (" + videoUrls.size() + "개)")
                    .createdAt(project.getUpdatedAt())
                    .updatedAt(project.getUpdatedAt())
                    .build();

        } catch (BusinessException e) {
            log.error("Business exception during video merge - projectId={}", projectId, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during video merge - projectId={}", projectId, e);
            throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, "영상 병합에 실패했습니다: " + e.getMessage());
        }
    }

    @Override
    public ProjectVideoResponse getFinalVideo(Long projectId, String loginId) {
        log.info("Getting final video for projectId: {}, loginId: {}", projectId, loginId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        String finalVideoUrl = project.getFinalVideoUrl();
        String status = finalVideoUrl != null ? "READY" : "NOT_CREATED";
        String message = finalVideoUrl != null ? "최종 영상 있음" : "최종 영상 생성 전";

        return ProjectVideoResponse.builder()
                .projectId(projectId)
                .finalVideoUrl(finalVideoUrl)
                .status(status)
                .message(message)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}