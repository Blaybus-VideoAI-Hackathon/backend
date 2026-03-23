package com.example.hdb.service.impl;

import com.example.hdb.dto.runway.RunwayVideoRequest;
import com.example.hdb.dto.runway.RunwayVideoResponse;
import com.example.hdb.dto.response.SceneVideoResponse;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneImage;
import com.example.hdb.entity.SceneVideo;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneImageRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.repository.SceneVideoRepository;
import com.example.hdb.service.RunwayVideoApiService;
import com.example.hdb.service.SceneVideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SceneVideoServiceImpl implements SceneVideoService {

    private final SceneVideoRepository sceneVideoRepository;
    private final SceneRepository sceneRepository;
    private final SceneImageRepository sceneImageRepository;
    private final ProjectRepository projectRepository;
    private final RunwayVideoApiService runwayVideoApiService;

    @Override
    @Transactional
    public SceneVideoResponse generateVideo(Long projectId, Long sceneId, String loginId, Integer duration) {
        log.info("=== Video Generation Started ===");
        log.info("projectId={}, sceneId={}, loginId={}, duration={}", projectId, sceneId, loginId, duration);

        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

            Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

            if (!project.getUser().getLoginId().equals(loginId)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
            }

            int safeDuration = normalizeDuration(duration);
            String videoPrompt = sanitizeVideoPrompt(scene);
            String imageUrlForVideo = determineImageUrlForVideo(scene);

            // 이미지 URL이 없으면 text-to-video fallback 없이 예외 처리
            if (!isUsableImageUrl(imageUrlForVideo)) {
                log.error("=== IMAGE URL VALIDATION FAILED ===");
                log.error("sceneId={}", sceneId);
                log.error("imageUrlForVideo: {}", imageUrlForVideo);
                log.error("IMMEDIATE FAILURE - No image URL available for video generation");
                
                // SceneVideo 상태를 FAILED로 저장
                SceneVideo failedVideo = SceneVideo.builder()
                        .scene(scene)
                        .videoUrl(null)
                        .videoPrompt(videoPrompt)
                        .duration(safeDuration)
                        .openaiVideoId(null)
                        .klingTaskId(null)
                        .status(SceneVideo.VideoStatus.FAILED)
                        .build();
                sceneVideoRepository.save(failedVideo);
                
                throw new RuntimeException("No image URL available for video generation - sceneId: " + sceneId);
            }

            log.info("=== VIDEO GENERATION START ===");
            log.info("sceneId={}", sceneId);
            log.info("final imageUrlForVideo: {}", imageUrlForVideo);
            log.info("videoPrompt: {}", videoPrompt);
            log.info("duration: {}", safeDuration);

            SceneVideo sceneVideo = SceneVideo.builder()
                    .scene(scene)
                    .videoUrl(null)
                    .videoPrompt(videoPrompt)
                    .duration(safeDuration)
                    .openaiVideoId(null)
                    .klingTaskId(null)
                    .status(SceneVideo.VideoStatus.GENERATING)
                    .build();

            SceneVideo savedVideo = sceneVideoRepository.save(sceneVideo);
            log.info("SceneVideo saved with GENERATING status: id={}", savedVideo.getId());

            // 반드시 image-to-video 방식으로만 요청 (promptImage 필드 필수)
            log.info("=== CREATING RUNWAY REQUEST ===");
            log.info("model: gen4.5");
            log.info("promptText: {}", videoPrompt);
            log.info("promptImage: {}", imageUrlForVideo);
            log.info("ratio: 1280:720");
            log.info("duration: 5");
            
            RunwayVideoRequest request = RunwayVideoRequest.builder()
                    .promptText(videoPrompt)
                    .promptImage(imageUrlForVideo) // null이 아님을 보장
                    .duration(5)
                    .build();

            log.info("=== RUNWAY REQUEST CREATED ===");
            log.info("Request promptImage value: {}", request.getPromptImage());

            RunwayVideoResponse response = runwayVideoApiService.createTask(request);
            String taskId = response != null ? response.getId() : null;

            if (taskId == null || taskId.isBlank()) {
                savedVideo.setStatus(SceneVideo.VideoStatus.FAILED);
                sceneVideoRepository.save(savedVideo);
                throw new RuntimeException("Runway task_id is empty");
            }

            savedVideo.setKlingTaskId(taskId);
            sceneVideoRepository.save(savedVideo);
            log.info("Runway task created: {}", taskId);

            try {
                Thread.sleep(2500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            RunwayVideoResponse statusResponse = runwayVideoApiService.getTaskStatus(taskId);

            if (statusResponse != null && "COMPLETED".equals(statusResponse.getStatus())
                    && statusResponse.getVideoUrl() != null && !statusResponse.getVideoUrl().isBlank()) {
                savedVideo.setVideoUrl(statusResponse.getVideoUrl());
                savedVideo.setStatus(SceneVideo.VideoStatus.COMPLETED);
                savedVideo = sceneVideoRepository.save(savedVideo);

                log.info("REAL VIDEO GENERATED - url={}", savedVideo.getVideoUrl());
                return toResponse(savedVideo, scene.getSceneOrder(), true);
            }

            if (statusResponse != null && "FAILED".equals(statusResponse.getStatus())) {
                savedVideo.setStatus(SceneVideo.VideoStatus.FAILED);
                savedVideo = sceneVideoRepository.save(savedVideo);
                throw new RuntimeException("Runway video generation failed");
            }

            savedVideo.setStatus(SceneVideo.VideoStatus.GENERATING);
            savedVideo = sceneVideoRepository.save(savedVideo);
            return toResponse(savedVideo, scene.getSceneOrder(), true);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Video generation failed with unexpected error", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Async("videoGenerationExecutor")
    @Transactional
    public void processVideoGenerationAsync(Long videoId) {
        log.info("=== Async Video Processing Started === videoId={}", videoId);

        try {
            SceneVideo sceneVideo = sceneVideoRepository.findById(videoId)
                    .orElseThrow(() -> new RuntimeException("SceneVideo not found: " + videoId));

            if (sceneVideo.getStatus() != SceneVideo.VideoStatus.GENERATING) {
                log.warn("Skipping async processing because status is {}", sceneVideo.getStatus());
                return;
            }

            if (sceneVideo.getKlingTaskId() == null || sceneVideo.getKlingTaskId().isBlank()) {
                sceneVideo.setStatus(SceneVideo.VideoStatus.FAILED);
                sceneVideoRepository.save(sceneVideo);
                return;
            }

            RunwayVideoResponse statusResponse = runwayVideoApiService.getTaskStatus(sceneVideo.getKlingTaskId());

            if (statusResponse != null && "COMPLETED".equals(statusResponse.getStatus())
                    && statusResponse.getVideoUrl() != null && !statusResponse.getVideoUrl().isBlank()) {
                sceneVideo.setStatus(SceneVideo.VideoStatus.COMPLETED);
                sceneVideo.setVideoUrl(statusResponse.getVideoUrl());
            } else if (statusResponse != null && "FAILED".equals(statusResponse.getStatus())) {
                sceneVideo.setStatus(SceneVideo.VideoStatus.FAILED);
            } else {
                sceneVideo.setStatus(SceneVideo.VideoStatus.GENERATING);
            }

            sceneVideoRepository.save(sceneVideo);
            log.info("Async video processing finished - videoId={}, status={}", videoId, sceneVideo.getStatus());

        } catch (Exception e) {
            log.error("Async video generation failed: videoId={}", videoId, e);

            try {
                SceneVideo sceneVideo = sceneVideoRepository.findById(videoId)
                        .orElseThrow(() -> new RuntimeException("SceneVideo not found: " + videoId));
                sceneVideo.setStatus(SceneVideo.VideoStatus.FAILED);
                sceneVideoRepository.save(sceneVideo);
            } catch (Exception saveException) {
                log.error("Failed to mark video as FAILED: videoId={}", videoId, saveException);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SceneVideoResponse> getVideos(Long projectId, Long sceneId, String loginId) {
        log.info("=== Get Scene Videos === projectId={}, sceneId={}, loginId={}", projectId, sceneId, loginId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        if (!project.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        List<SceneVideo> videos = sceneVideoRepository.findBySceneIdOrderByCreatedAtDesc(sceneId);
        
        // GENERATING 상태인 영상들의 상태 동기화
        videos = videos.stream()
                .map(video -> {
                    SceneVideo syncedVideo = syncVideoStatusIfNeeded(video);
                    return syncedVideo != null ? syncedVideo : video;
                })
                .collect(Collectors.toList());

        return videos.stream()
                .map(video -> toResponse(video, scene.getSceneOrder(), false))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SceneVideoResponse> getProjectVideos(Long projectId, String loginId) {
        log.info("=== Project Videos Query Started === projectId={}, loginId={}", projectId, loginId);

        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

            if (!project.getUser().getLoginId().equals(loginId)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
            }

            List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);

            return scenes.stream()
                    .map(scene -> {
                        List<SceneVideo> sceneVideos = sceneVideoRepository.findBySceneIdOrderByCreatedAtDesc(scene.getId());
                        if (sceneVideos.isEmpty()) {
                            return null;
                        }

                        // GENERATING 상태인 영상들의 상태 동기화
                        sceneVideos = sceneVideos.stream()
                                .map(video -> {
                                    SceneVideo syncedVideo = syncVideoStatusIfNeeded(video);
                                    return syncedVideo != null ? syncedVideo : video;
                                })
                                .collect(Collectors.toList());

                        SceneVideo latestVideo = sceneVideos.get(0);
                        // 대표 영상은 최신 영상 기준으로 판단 (fallback-video-service.com 제거)
                        boolean representative = scene.getSceneOrder() == 1;
                        return toResponse(latestVideo, scene.getSceneOrder(), representative);
                    })
                    .filter(v -> v != null)
                    .collect(Collectors.toList());

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error querying project videos", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private int normalizeDuration(Integer duration) {
        return 5;
    }

    private String determineImageUrlForVideo(Scene scene) {
        log.info("=== Determining Image URL for Video Generation ===");
        log.info("sceneId={}", scene.getId());
        
        // 1. scene.editedImageUrl
        if (scene.getEditedImageUrl() != null && !scene.getEditedImageUrl().trim().isEmpty()) {
            log.info("SELECTED: scene.editedImageUrl - Priority 1");
            log.info("imageUrlForVideo: {}", scene.getEditedImageUrl());
            return scene.getEditedImageUrl();
        }

        try {
            // scene_images를 imageNumber asc로 조회 후 역순으로 처리 (최신부터)
            List<SceneImage> images = sceneImageRepository.findBySceneIdOrderByImageNumberAsc(scene.getId());
            log.info("Found {} SceneImage records for sceneId={}", images.size(), scene.getId());

            // 최신부터 처리하기 위해 역순으로 순회
            for (int i = images.size() - 1; i >= 0; i--) {
                SceneImage image = images.get(i);
                
                // 2. scene_images 최신 editedImageUrl
                if (image.getEditedImageUrl() != null && !image.getEditedImageUrl().trim().isEmpty()) {
                    log.info("SELECTED: sceneImage.editedImageUrl - Priority 2");
                    log.info("imageUrlForVideo: {}", image.getEditedImageUrl());
                    log.info("SceneImage id: {}, imageNumber: {}, createdAt: {}", 
                            image.getId(), image.getImageNumber(), image.getCreatedAt());
                    return image.getEditedImageUrl();
                }
            }

            // 최신부터 imageUrl 확인
            for (int i = images.size() - 1; i >= 0; i--) {
                SceneImage image = images.get(i);
                
                // 3. scene_images 최신 imageUrl
                if (image.getImageUrl() != null && !image.getImageUrl().trim().isEmpty()) {
                    log.info("SELECTED: sceneImage.imageUrl - Priority 3");
                    log.info("imageUrlForVideo: {}", image.getImageUrl());
                    log.info("SceneImage id: {}, imageNumber: {}, createdAt: {}", 
                            image.getId(), image.getImageNumber(), image.getCreatedAt());
                    return image.getImageUrl();
                }
            }

        } catch (Exception e) {
            log.error("Error while resolving image URL for video - sceneId={}", scene.getId(), e);
        }

        // 4. scene.imageUrl
        if (scene.getImageUrl() != null && !scene.getImageUrl().trim().isEmpty()) {
            log.info("SELECTED: scene.imageUrl - Priority 4");
            log.info("imageUrlForVideo: {}", scene.getImageUrl());
            return scene.getImageUrl();
        }

        log.error("NO IMAGE URL FOUND - sceneId={}", scene.getId());
        return null;
    }

    private boolean isUsableImageUrl(String imageUrl) {
        return imageUrl != null && !imageUrl.isBlank();
    }

    private String sanitizeVideoPrompt(Scene scene) {
        String prompt = scene.getVideoPrompt();

        if (prompt == null || prompt.trim().isEmpty()) {
            prompt = buildPromptFromScene(scene);
            log.warn("Scene videoPrompt was empty. Built fallback prompt: {}", prompt);
            return prompt;
        }

        prompt = prompt.replace("null", " ").replaceAll("\\s+", " ").trim();
        if (prompt.isBlank()) {
            prompt = buildPromptFromScene(scene);
        }
        return prompt;
    }

    private String buildPromptFromScene(Scene scene) {
        String summary = scene.getSummary() != null ? scene.getSummary() : "a cinematic food scene";
        return "Cinematic short video of " + summary + ", warm lighting, smooth camera motion, high quality";
    }

    @Transactional
    private SceneVideo syncVideoStatusIfNeeded(SceneVideo sceneVideo) {
        if (sceneVideo.getStatus() != SceneVideo.VideoStatus.GENERATING) {
            return sceneVideo;
        }

        if (sceneVideo.getKlingTaskId() == null || sceneVideo.getKlingTaskId().isBlank()) {
            log.warn("No taskId found for GENERATING video - videoId={}", sceneVideo.getId());
            return sceneVideo;
        }

        try {
            log.info("Syncing video status - videoId={}, taskId={}", sceneVideo.getId(), sceneVideo.getKlingTaskId());
            RunwayVideoResponse statusResponse = runwayVideoApiService.getTaskStatus(sceneVideo.getKlingTaskId());

            if (statusResponse != null) {
                String runwayStatus = statusResponse.getStatus();
                String videoUrl = statusResponse.getVideoUrl();

                if ("succeeded".equals(runwayStatus) && videoUrl != null && !videoUrl.isBlank()) {
                    sceneVideo.setStatus(SceneVideo.VideoStatus.COMPLETED);
                    sceneVideo.setVideoUrl(videoUrl);
                    sceneVideo = sceneVideoRepository.save(sceneVideo);
                    log.info("Video status updated to COMPLETED - videoId={}, url={}", sceneVideo.getId(), videoUrl);
                } else if ("failed".equals(runwayStatus) || "cancelled".equals(runwayStatus)) {
                    sceneVideo.setStatus(SceneVideo.VideoStatus.FAILED);
                    sceneVideo = sceneVideoRepository.save(sceneVideo);
                    log.info("Video status updated to FAILED - videoId={}, status={}", sceneVideo.getId(), runwayStatus);
                } else {
                    // starting / pending / processing / throttled / queued -> GENERATING 유지
                    log.debug("Video still processing - videoId={}, status={}", sceneVideo.getId(), runwayStatus);
                }
            }
        } catch (Exception e) {
            log.error("Failed to sync video status - videoId={}, taskId={}", sceneVideo.getId(), sceneVideo.getKlingTaskId(), e);
        }

        return sceneVideo;
    }

    private SceneVideoResponse toResponse(SceneVideo sceneVideo, Integer sceneOrder, boolean representative) {
        return new SceneVideoResponse(
                sceneVideo.getId(),
                sceneVideo.getScene().getId(),
                sceneOrder,
                sceneVideo.getDuration(),
                sceneVideo.getVideoUrl(),
                sceneVideo.getVideoPrompt(),
                sceneVideo.getStatus().name(),
                sceneVideo.getStatus().getDescription(),
                representative,
                sceneVideo.getCreatedAt(),
                sceneVideo.getUpdatedAt()
        );
    }
}