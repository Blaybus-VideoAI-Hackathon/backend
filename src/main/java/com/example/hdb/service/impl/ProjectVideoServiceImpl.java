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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectVideoServiceImpl implements ProjectVideoService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProjectVideoServiceImpl.class);

    private final ProjectRepository projectRepository;
    private final SceneRepository sceneRepository;
    private final SceneVideoRepository sceneVideoRepository;
    private final SceneVideoService sceneVideoService;

    @Value("${app.video.output.path:/tmp/videos}")
    private String videoOutputPath;

    @Value("${app.video.base-url:http://localhost:8080/videos}")
    private String videoBaseUrl;

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

                // 병합 전 Runway에서 최신 상태 동기화
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

            String finalVideoUrl = mergeVideosWithFFmpeg(videoUrls, projectId);

            project.setFinalVideoUrl(finalVideoUrl);
            projectRepository.save(project);

            log.info("=== PROJECT VIDEO MERGE COMPLETED ===");
            log.info("projectId={}, finalVideoUrl={}", projectId, finalVideoUrl);

            return ProjectVideoResponse.builder()
                    .projectId(projectId)
                    .finalVideoUrl(finalVideoUrl)
                    .status("READY")
                    .message("최종 영상 병합 완료")
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

    private String mergeVideosWithFFmpeg(List<String> videoUrls, Long projectId) throws IOException, InterruptedException {
        log.info("Merging {} videos with FFmpeg for projectId: {}", videoUrls.size(), projectId);

        Path outputDir = Paths.get(videoOutputPath);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        List<String> tempFiles = new ArrayList<>();
        List<String> concatList = new ArrayList<>();

        for (int i = 0; i < videoUrls.size(); i++) {
            String videoUrl = videoUrls.get(i);
            String tempFile = downloadVideoToTemp(videoUrl, projectId, i);
            if (tempFile != null) {
                tempFiles.add(tempFile);
                concatList.add("file '" + tempFile + "'");
            }
        }

        if (concatList.isEmpty()) {
            throw new RuntimeException("No valid video files to merge");
        }

        String concatFilePath = videoOutputPath + "/project-" + projectId + "-concat.txt";
        String concatContent = String.join("\n", concatList);
        Files.write(Paths.get(concatFilePath), concatContent.getBytes());

        String outputFileName = "project-" + projectId + "-final.mp4";
        String outputFilePath = videoOutputPath + "/" + outputFileName;

        String[] ffmpegCommand = {
                "ffmpeg",
                "-f", "concat",
                "-safe", "0",
                "-i", concatFilePath,
                "-c", "copy",
                "-y",
                outputFilePath
        };

        log.info("Executing FFmpeg command: {}", String.join(" ", ffmpegCommand));

        ProcessBuilder pb = new ProcessBuilder(ffmpegCommand);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("FFmpeg: {}", line);
            }
        }

        int exitCode = process.waitFor();

        cleanupTempFiles(tempFiles, concatFilePath);

        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg failed with exit code: " + exitCode);
        }

        String finalVideoUrl = videoBaseUrl + "/" + outputFileName;
        log.info("FFmpeg merge completed: {}", finalVideoUrl);

        return finalVideoUrl;
    }

    private String downloadVideoToTemp(String videoUrl, Long projectId, int index) {
        try {
            String fileName = "project-" + projectId + "-scene-" + index + ".mp4";
            String tempPath = videoOutputPath + "/" + fileName;
            log.info("Using video URL as temp file: {} -> {}", videoUrl, tempPath);
            return tempPath;
        } catch (Exception e) {
            log.error("Failed to download video: {}", videoUrl, e);
            return null;
        }
    }

    private void cleanupTempFiles(List<String> tempFiles, String concatFilePath) {
        for (String tempFile : tempFiles) {
            try {
                Files.deleteIfExists(Paths.get(tempFile));
            } catch (IOException e) {
                log.warn("Failed to delete temp file: {}", tempFile, e);
            }
        }

        try {
            Files.deleteIfExists(Paths.get(concatFilePath));
        } catch (IOException e) {
            log.warn("Failed to delete concat file: {}", concatFilePath, e);
        }
    }
}