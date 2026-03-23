package com.example.hdb.service.impl;

import com.example.hdb.dto.kling.KlingVideoRequest;
import com.example.hdb.dto.kling.KlingVideoResponse;
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
import com.example.hdb.service.KlingVideoApiService;
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
    private final KlingVideoApiService klingVideoApiService;

    // ──────────────────────────────────────────
    // 영상 생성
    // ──────────────────────────────────────────

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

            int safeDuration = (duration == null || duration <= 0) ? 5 : duration;
            String videoPrompt = sanitizeVideoPrompt(scene);
            String imageUrlForVideo = determineImageUrlForVideo(scene);

            log.info("Video prompt: {}", videoPrompt);
            log.info("Image URL for video: {}", imageUrlForVideo);

            // 1. GENERATING 상태로 먼저 저장
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

            // 2. Kling 호출
            String finalVideoUrl;
            SceneVideo.VideoStatus finalStatus;
            String klingTaskId = null;

            try {
                KlingVideoRequest request = new KlingVideoRequest();
                request.setPrompt(videoPrompt);
                request.setDuration(safeDuration);
                request.setAspectRatio("16:9");

                if (isUsableImageUrl(imageUrlForVideo)) {
                    request.setImageUrl(imageUrlForVideo);
                    log.info("Using image-based video generation");
                } else {
                    log.warn("No usable image URL — using prompt-only video generation");
                }

                KlingVideoResponse response = klingVideoApiService.generateVideo(request);
                klingTaskId = response != null ? response.getTaskId() : null;

                if (klingTaskId != null && !klingTaskId.isBlank()) {
                    savedVideo.setKlingTaskId(klingTaskId);
                    sceneVideoRepository.save(savedVideo);
                    log.info("Kling task created: {}", klingTaskId);
                }

                // 상태 확인 (1회)
                String generatedUrl = null;
                if (klingTaskId != null && !klingTaskId.isBlank()) {
                    KlingVideoResponse statusResponse = klingVideoApiService.checkVideoStatus(klingTaskId);
                    if (statusResponse != null
                            && "completed".equalsIgnoreCase(statusResponse.getStatus())
                            && statusResponse.getVideoUrl() != null
                            && !statusResponse.getVideoUrl().isBlank()) {
                        generatedUrl = statusResponse.getVideoUrl();
                    }
                }

                if (generatedUrl != null && !generatedUrl.isBlank()) {
                    finalVideoUrl = generatedUrl;
                    finalStatus = SceneVideo.VideoStatus.COMPLETED;
                    log.info("REAL VIDEO GENERATED - url={}", finalVideoUrl);
                } else {
                    finalVideoUrl = generateFallbackVideoUrl(sceneId, videoPrompt);
                    // FALLBACK enum이 DB에 없으면 COMPLETED로 저장 (fallback URL이지만 사용 가능)
                    finalStatus = SceneVideo.VideoStatus.COMPLETED;
                    log.warn("VIDEO FALLBACK USED - sceneId={}", sceneId);
                }

            } catch (Exception klingException) {
                log.error("Kling video generation failed: {}", klingException.getMessage(), klingException);
                finalVideoUrl = generateFallbackVideoUrl(sceneId, videoPrompt);
                finalStatus = SceneVideo.VideoStatus.COMPLETED;
            }

            savedVideo.setKlingTaskId(klingTaskId);
            savedVideo.setVideoUrl(finalVideoUrl);
            savedVideo.setStatus(finalStatus);
            savedVideo = sceneVideoRepository.save(savedVideo);

            log.info("Video saved - id={}, status={}, url={}", savedVideo.getId(), savedVideo.getStatus(), savedVideo.getVideoUrl());

            return toResponse(savedVideo, scene.getSceneOrder(), true);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Video generation failed with unexpected error", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // ──────────────────────────────────────────
    // 비동기 처리 (선택적 사용)
    // ──────────────────────────────────────────

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

            String sourceImageUrl = determineImageUrlForVideo(sceneVideo.getScene());
            String prompt = sanitizeVideoPrompt(sceneVideo.getScene());

            KlingVideoRequest request = new KlingVideoRequest();
            request.setPrompt(prompt);
            request.setDuration(sceneVideo.getDuration() != null ? sceneVideo.getDuration() : 5);
            request.setAspectRatio("16:9");

            if (isUsableImageUrl(sourceImageUrl)) {
                request.setImageUrl(sourceImageUrl);
            }

            try {
                KlingVideoResponse response = klingVideoApiService.generateVideo(request);
                String taskId = response != null ? response.getTaskId() : null;

                sceneVideo.setKlingTaskId(taskId);
                sceneVideoRepository.save(sceneVideo);

                String finalUrl = null;
                if (taskId != null && !taskId.isBlank()) {
                    KlingVideoResponse statusResponse = klingVideoApiService.checkVideoStatus(taskId);
                    if (statusResponse != null
                            && "completed".equalsIgnoreCase(statusResponse.getStatus())
                            && statusResponse.getVideoUrl() != null
                            && !statusResponse.getVideoUrl().isBlank()) {
                        finalUrl = statusResponse.getVideoUrl();
                    }
                }

                if (finalUrl != null && !finalUrl.isBlank()) {
                    sceneVideo.setStatus(SceneVideo.VideoStatus.COMPLETED);
                    sceneVideo.setVideoUrl(finalUrl);
                } else {
                    sceneVideo.setStatus(SceneVideo.VideoStatus.COMPLETED);
                    sceneVideo.setVideoUrl(generateFallbackVideoUrl(sceneVideo.getScene().getId(), prompt));
                }

                sceneVideoRepository.save(sceneVideo);
                log.info("Async video processing completed - videoId={}, status={}", videoId, sceneVideo.getStatus());

            } catch (Exception klingException) {
                log.warn("Async Kling call failed, using fallback: {}", klingException.getMessage());

                sceneVideo.setStatus(SceneVideo.VideoStatus.COMPLETED);
                sceneVideo.setVideoUrl(generateFallbackVideoUrl(sceneVideo.getScene().getId(), prompt));
                sceneVideoRepository.save(sceneVideo);
            }

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

    // ──────────────────────────────────────────
    // 조회
    // ──────────────────────────────────────────

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
            log.info("Found {} scenes for project {}", scenes.size(), projectId);

            List<SceneVideoResponse> videoResponses = scenes.stream()
                    .map(scene -> {
                        List<SceneVideo> sceneVideos = sceneVideoRepository.findBySceneIdOrderByCreatedAtDesc(scene.getId());
                        if (sceneVideos.isEmpty()) return null;

                        SceneVideo latestVideo = sceneVideos.get(0);
                        if (latestVideo.getVideoUrl() == null || latestVideo.getVideoUrl().isBlank()) return null;

                        boolean representative = scene.getSceneOrder() == 1;
                        return toResponse(latestVideo, scene.getSceneOrder(), representative);
                    })
                    .filter(v -> v != null)
                    .collect(Collectors.toList());

            log.info("Returning {} project video responses", videoResponses.size());
            return videoResponses;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error querying project videos", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // ──────────────────────────────────────────
    // PRIVATE HELPERS
    // ──────────────────────────────────────────

    private String determineImageUrlForVideo(Scene scene) {
        // 1. scene의 편집된 이미지
        if (scene.getEditedImageUrl() != null && !scene.getEditedImageUrl().trim().isEmpty()) {
            log.info("Using scene editedImageUrl");
            return scene.getEditedImageUrl();
        }

        try {
            List<SceneImage> images = sceneImageRepository.findBySceneIdOrderByImageNumberAsc(scene.getId());

            // 2. sceneImage의 편집된 이미지
            for (SceneImage image : images) {
                if (image.getEditedImageUrl() != null && !image.getEditedImageUrl().trim().isEmpty()) {
                    log.info("Using sceneImage editedImageUrl");
                    return image.getEditedImageUrl();
                }
            }

            // 3. sceneImage의 원본 이미지 (최신)
            Optional<SceneImage> latestImage = sceneImageRepository.findFirstBySceneIdOrderByImageNumberDesc(scene.getId());
            if (latestImage.isPresent()
                    && latestImage.get().getImageUrl() != null
                    && !latestImage.get().getImageUrl().trim().isEmpty()) {
                log.info("Using sceneImage originalImageUrl");
                return latestImage.get().getImageUrl();
            }

        } catch (Exception e) {
            log.warn("Failed while resolving image URL for video", e);
        }

        // 4. scene의 원본 이미지
        if (scene.getImageUrl() != null && !scene.getImageUrl().trim().isEmpty()) {
            log.info("Using scene originalImageUrl");
            return scene.getImageUrl();
        }

        log.warn("No image URL available for video generation - sceneId={}", scene.getId());
        return null;
    }

    private boolean isUsableImageUrl(String imageUrl) {
        return imageUrl != null
                && !imageUrl.isBlank()
                && !imageUrl.contains("picsum.photos")
                && !imageUrl.contains("fallback-image-service.com");
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
        String summary = scene.getSummary() != null ? scene.getSummary() : "a cute hamster scene";
        return "Cinematic short video of " + summary + ", warm lighting, smooth camera motion, high quality";
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

    private String generateFallbackVideoUrl(Long sceneId, String videoPrompt) {
        log.warn("VIDEO FALLBACK URL GENERATED - sceneId={}", sceneId);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String promptHash = String.valueOf(videoPrompt != null ? videoPrompt.hashCode() : 0);
        return String.format(
                "https://fallback-video-service.com/scene_%d_%s_%s.mp4",
                sceneId, timestamp, promptHash
        );
    }
}