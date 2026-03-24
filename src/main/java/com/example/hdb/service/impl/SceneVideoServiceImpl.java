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

            int safeDuration = normalizeDuration(duration);
            String videoPrompt = sanitizeVideoPrompt(scene);
            String imageUrlForVideo = determineImageUrlForVideo(scene);

            if (!isUsableImageUrl(imageUrlForVideo)) {
                log.error("=== IMAGE URL VALIDATION FAILED ===");
                log.error("sceneId={}", sceneId);
                log.error("imageUrlForVideo: {}", imageUrlForVideo);
                log.error("IMMEDIATE FAILURE - No image URL available for video generation");

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

            log.info("=== CREATING RUNWAY REQUEST ===");
            log.info("model: gen4.5");
            log.info("promptText: {}", videoPrompt);
            log.info("promptImage: {}", imageUrlForVideo);
            log.info("ratio: 1280:720");
            log.info("duration: 5");

            RunwayVideoRequest request = RunwayVideoRequest.builder()
                    .promptText(videoPrompt)
                    .promptImage(imageUrlForVideo)
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
    @Transactional
    public List<SceneVideoResponse> getVideos(Long projectId, Long sceneId, String loginId) {
        log.info("=== Get Scene Videos === projectId={}, sceneId={}, loginId={}", projectId, sceneId, loginId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        List<SceneVideo> videos = sceneVideoRepository.findBySceneIdOrderByCreatedAtDesc(sceneId);

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
    @Transactional
    public List<SceneVideoResponse> getProjectVideos(Long projectId, String loginId) {
        log.info("=== Project Videos Query Started === projectId={}, loginId={}", projectId, loginId);

        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

            List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);

            return scenes.stream()
                    .map(scene -> {
                        List<SceneVideo> sceneVideos = sceneVideoRepository.findBySceneIdOrderByCreatedAtDesc(scene.getId());
                        if (sceneVideos.isEmpty()) {
                            return null;
                        }

                        sceneVideos = sceneVideos.stream()
                                .map(video -> {
                                    SceneVideo syncedVideo = syncVideoStatusIfNeeded(video);
                                    return syncedVideo != null ? syncedVideo : video;
                                })
                                .collect(Collectors.toList());

                        SceneVideo latestVideo = sceneVideos.get(0);
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

    @Override
    @Transactional
    public SceneVideo syncAndGetLatestVideo(Long sceneId) {
        List<SceneVideo> videos = sceneVideoRepository.findBySceneIdOrderByCreatedAtDesc(sceneId);
        if (videos.isEmpty()) {
            return null;
        }

        // 최신 영상부터 동기화 후 videoUrl 있으면 반환
        for (SceneVideo video : videos) {
            SceneVideo synced = syncVideoStatusIfNeeded(video);
            if (synced != null && synced.getVideoUrl() != null && !synced.getVideoUrl().isBlank()) {
                return synced;
            }
        }

        return null;
    }

    private int normalizeDuration(Integer duration) {
        return 5;
    }

    private String determineImageUrlForVideo(Scene scene) {
        log.info("=== Determining Image URL for Video Generation ===");
        log.info("sceneId={}", scene.getId());

        if (scene.getEditedImageUrl() != null && !scene.getEditedImageUrl().trim().isEmpty()) {
            log.info("SELECTED: scene.editedImageUrl - Priority 1");
            log.info("imageUrlForVideo: {}", scene.getEditedImageUrl());
            return scene.getEditedImageUrl();
        }

        try {
            List<SceneImage> images = sceneImageRepository.findBySceneIdOrderByImageNumberAsc(scene.getId());
            log.info("Found {} SceneImage records for sceneId={}", images.size(), scene.getId());

            for (int i = images.size() - 1; i >= 0; i--) {
                SceneImage image = images.get(i);

                if (image.getEditedImageUrl() != null && !image.getEditedImageUrl().trim().isEmpty()) {
                    log.info("SELECTED: sceneImage.editedImageUrl - Priority 2");
                    log.info("imageUrlForVideo: {}", image.getEditedImageUrl());
                    log.info("SceneImage id: {}, imageNumber: {}, createdAt: {}",
                            image.getId(), image.getImageNumber(), image.getCreatedAt());
                    return image.getEditedImageUrl();
                }
            }

            for (int i = images.size() - 1; i >= 0; i--) {
                SceneImage image = images.get(i);

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
        String summary = scene.getSummary() != null ? scene.getSummary() : "a cinematic scene";
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
            log.info("=== SYNCING VIDEO STATUS ===");
            log.info("videoId={}, taskId={}", sceneVideo.getId(), sceneVideo.getKlingTaskId());

            RunwayVideoResponse statusResponse = runwayVideoApiService.getTaskStatus(sceneVideo.getKlingTaskId());

            if (statusResponse != null) {
                String runwayStatus = statusResponse.getStatus();
                String videoUrl = statusResponse.getVideoUrl();

                log.info("Parsed runway status: {}", runwayStatus);
                log.info("Parsed videoUrl: {}", videoUrl);

                if ("COMPLETED".equals(runwayStatus) && videoUrl != null && !videoUrl.isBlank()) {
                    log.info("=== BEFORE SAVE ===");
                    log.info("sceneVideo.id: {}", sceneVideo.getId());
                    log.info("sceneVideo.status (before): {}", sceneVideo.getStatus());
                    log.info("sceneVideo.videoUrl (before): {}", sceneVideo.getVideoUrl());

                    sceneVideo.setStatus(SceneVideo.VideoStatus.COMPLETED);
                    sceneVideo.setVideoUrl(videoUrl);

                    sceneVideo = sceneVideoRepository.save(sceneVideo);

                    log.info("=== AFTER SAVE ===");
                    log.info("sceneVideo.status (after): {}", sceneVideo.getStatus());
                    log.info("sceneVideo.videoUrl (after): {}", sceneVideo.getVideoUrl());

                    sceneVideoRepository.flush();

                    SceneVideo dbVideo = sceneVideoRepository.findById(sceneVideo.getId()).orElse(null);
                    if (dbVideo != null) {
                        log.info("=== AFTER FLUSH & DB RELOAD ===");
                        log.info("dbVideo.status: {}", dbVideo.getStatus());
                        log.info("dbVideo.videoUrl: {}", dbVideo.getVideoUrl());
                        log.info("videoId={}, url={}", dbVideo.getId(), dbVideo.getVideoUrl());
                        sceneVideo = dbVideo;
                    } else {
                        log.error("FAILED TO RELOAD VIDEO FROM DB - videoId={}", sceneVideo.getId());
                    }

                    log.info("=== VIDEO STATUS UPDATED ===");
                    log.info("final sceneVideo.status: {}", sceneVideo.getStatus());
                    log.info("final sceneVideo.videoUrl: {}", sceneVideo.getVideoUrl());

                } else if ("FAILED".equals(runwayStatus)) {
                    log.info("=== BEFORE SAVE (FAILED) ===");
                    log.info("sceneVideo.id: {}", sceneVideo.getId());
                    log.info("sceneVideo.status (before): {}", sceneVideo.getStatus());

                    sceneVideo.setStatus(SceneVideo.VideoStatus.FAILED);
                    sceneVideo = sceneVideoRepository.save(sceneVideo);
                    sceneVideoRepository.flush();

                    log.info("=== VIDEO STATUS UPDATED (FAILED) ===");
                    log.info("final sceneVideo.status: {}", sceneVideo.getStatus());
                    log.info("videoId={}, status={}", sceneVideo.getId(), runwayStatus);
                } else {
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