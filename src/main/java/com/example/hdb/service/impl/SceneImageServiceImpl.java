package com.example.hdb.service.impl;

import com.example.hdb.dto.request.ImageEditCompleteRequest;
import com.example.hdb.dto.request.SceneImageEditAiRequest;
import com.example.hdb.dto.response.SceneImageResponse;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneImage;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneImageRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.service.OpenAIService;
import com.example.hdb.service.SceneImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SceneImageServiceImpl implements SceneImageService {

    private final SceneImageRepository sceneImageRepository;
    private final SceneRepository sceneRepository;
    private final ProjectRepository projectRepository;
    private final OpenAIService openAIService;

    @Override
    public SceneImageResponse generateImage(Long projectId, Long sceneId, String loginId) {
        log.info("=== SceneImageService.generateImage Started ===");
        log.info("projectId: {}, sceneId: {}, loginId: {}", projectId, sceneId, loginId);

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        validateProjectOwnership(scene.getProject(), loginId);

        String imagePrompt = buildRealImagePrompt(scene);
        if (imagePrompt == null || imagePrompt.isBlank()) {
            log.error("Scene image prompt is null or empty after normalization");
            throw new BusinessException(ErrorCode.SCENE_IMAGE_PROMPT_NOT_FOUND);
        }

        Integer nextImageNumber = sceneImageRepository.findFirstBySceneIdOrderByImageNumberDesc(sceneId)
                .map(SceneImage::getImageNumber)
                .orElse(0) + 1;

        log.info("Next image number: {}", nextImageNumber);
        log.info("Final image prompt: {}", imagePrompt);

        SceneImage sceneImage = SceneImage.builder()
                .scene(scene)
                .imageNumber(nextImageNumber)
                .imageUrl(null)
                .editedImageUrl(null)
                .imagePrompt(imagePrompt)
                .openaiImageId(null)
                .status(SceneImage.ImageStatus.GENERATING)
                .build();

        SceneImage savedImage = sceneImageRepository.save(sceneImage);
        log.info("SceneImage saved with GENERATING status: {}", savedImage.getId());

        try {
            log.info("=== CALLING OPENAI IMAGE API ===");
            String imageUrl = openAIService.generateImage(imagePrompt);

            if (imageUrl == null || imageUrl.isBlank()) {
                throw new RuntimeException("AI returned empty imageUrl");
            }

            savedImage.setImageUrl(imageUrl);
            savedImage.setStatus(SceneImage.ImageStatus.READY);
            SceneImage completedImage = sceneImageRepository.save(savedImage);

            log.info("REAL IMAGE GENERATED - sceneId: {}, imageId: {}, imageUrl: {}",
                    sceneId, completedImage.getId(), completedImage.getImageUrl());

            return toResponse(completedImage, false);

        } catch (Exception e) {
            log.error("=== IMAGE GENERATION FAILED, USING FALLBACK ===", e);

            String fallbackImageUrl = generateFallbackImageUrl(imagePrompt);
            savedImage.setImageUrl(fallbackImageUrl);
            savedImage.setStatus(SceneImage.ImageStatus.READY);
            SceneImage completedImage = sceneImageRepository.save(savedImage);

            log.warn("IMAGE FALLBACK COMPLETED - sceneId: {}, imageId: {}, fallbackUrl: {}",
                    sceneId, completedImage.getId(), completedImage.getImageUrl());

            return toResponse(completedImage, true);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SceneImageResponse> getImages(Long projectId, Long sceneId, String loginId) {
        log.info("Getting images for scene: {}, project: {}, user: {}", sceneId, projectId, loginId);

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        validateProjectOwnership(scene.getProject(), loginId);

        List<SceneImage> images = sceneImageRepository.findBySceneIdOrderByImageNumberAsc(sceneId);

        return images.stream()
                .map(image -> toResponse(image, isFallbackUrl(image.getImageUrl())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SceneImageResponse> getProjectImages(Long projectId, String loginId) {
        log.info("Getting all images for project: {}, user: {}", projectId, loginId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        validateProjectOwnership(project, loginId);

        List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);

        List<SceneImage> allImages = scenes.stream()
                .flatMap(scene -> sceneImageRepository.findBySceneIdOrderByImageNumberAsc(scene.getId()).stream())
                .collect(Collectors.toList());

        log.info("Found {} images for project: {}", allImages.size(), projectId);

        return allImages.stream()
                .map(image -> {
                    SceneImageResponse response = toResponse(image, isFallbackUrl(image.getImageUrl()));
                    response.setSceneId(image.getScene().getId());
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    public SceneImageResponse completeImageEdit(Long projectId, Long sceneId, Long imageId, String loginId, ImageEditCompleteRequest request) {
        log.info("Completing image edit for imageId: {}, editedImageUrl: {}", imageId, request.getEditedImageUrl());

        SceneImage sceneImage = sceneImageRepository.findByIdAndSceneId(imageId, sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));

        Scene scene = sceneImage.getScene();
        validateProjectOwnership(scene.getProject(), loginId);

        sceneImage.setEditedImageUrl(request.getEditedImageUrl());
        SceneImage savedImage = sceneImageRepository.save(sceneImage);

        log.info("Image edit completed successfully: {}", savedImage.getId());

        return toResponse(savedImage, isFallbackUrl(savedImage.getImageUrl()));
    }

    @Override
    public SceneImageResponse generateImageEditAi(Long projectId, Long sceneId, Long imageId, String loginId, SceneImageEditAiRequest request) {
        log.info("=== AI Image Edit Generation Started ===");
        log.info("projectId: {}, sceneId: {}, imageId: {}, userEditText: {}",
                projectId, sceneId, imageId, request.getUserEditText());

        SceneImage originalImage = sceneImageRepository.findByIdAndSceneId(imageId, sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));

        Scene scene = originalImage.getScene();
        validateProjectOwnership(scene.getProject(), loginId);

        String sourceImageUrl = originalImage.getEditedImageUrl() != null && !originalImage.getEditedImageUrl().isBlank()
                ? originalImage.getEditedImageUrl()
                : originalImage.getImageUrl();

        Integer maxImageNumber = sceneImageRepository.findMaxImageNumberBySceneId(sceneId);
        int nextImageNumber = (maxImageNumber != null ? maxImageNumber : 0) + 1;

        log.info("Source image URL: {}", sourceImageUrl);
        log.info("Next image number: {}", nextImageNumber);

        try {
            String editedImageUrl = generateEditedImageUrl(sourceImageUrl, request.getUserEditText());
            String newImagePrompt = buildEditedPrompt(originalImage.getImagePrompt(), request.getUserEditText());

            SceneImage newImage = SceneImage.builder()
                    .scene(scene)
                    .imageNumber(nextImageNumber)
                    .imageUrl(sourceImageUrl)
                    .editedImageUrl(editedImageUrl)
                    .imagePrompt(newImagePrompt)
                    .openaiImageId(null)
                    .status(SceneImage.ImageStatus.READY)
                    .build();

            SceneImage savedImage = sceneImageRepository.save(newImage);
            log.info("Saved new edited image with ID: {}", savedImage.getId());

            return toResponse(savedImage, false);

        } catch (Exception e) {
            log.warn("AI image edit failed, using fallback: {}", e.getMessage(), e);

            String fallbackEditedUrl = generateFallbackEditedUrl(sourceImageUrl, request.getUserEditText());
            String newImagePrompt = buildEditedPrompt(originalImage.getImagePrompt(), request.getUserEditText()) + " (fallback)";

            SceneImage newImage = SceneImage.builder()
                    .scene(scene)
                    .imageNumber(nextImageNumber)
                    .imageUrl(sourceImageUrl)
                    .editedImageUrl(fallbackEditedUrl)
                    .imagePrompt(newImagePrompt)
                    .openaiImageId(null)
                    .status(SceneImage.ImageStatus.READY)
                    .build();

            SceneImage savedImage = sceneImageRepository.save(newImage);
            log.info("Saved fallback edited image with ID: {}", savedImage.getId());

            return toResponse(savedImage, true);
        }
    }

    private void validateProjectOwnership(Project project, String loginId) {
        if (!project.getUser().getLoginId().equals(loginId)) {
            log.error("Unauthorized access: projectId={}, loginId={}, owner={}",
                    project.getId(), loginId, project.getUser().getLoginId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    private SceneImageResponse toResponse(SceneImage image, boolean fallbackUsed) {
        SceneImageResponse response = SceneImageResponse.builder()
                .id(image.getId())
                .sceneId(image.getScene().getId())
                .imageNumber(image.getImageNumber())
                .imageUrl(image.getImageUrl())
                .editedImageUrl(image.getEditedImageUrl())
                .imagePrompt(image.getImagePrompt())
                .status(image.getStatus().name())
                .statusDescription(image.getStatus().getDescription())
                .createdAt(image.getCreatedAt())
                .updatedAt(image.getUpdatedAt())
                .build();

        response.setFallbackUsed(fallbackUsed);
        return response;
    }

    /**
     * scene.imagePrompt가 generic하거나 비어 있으면 summary 기반으로 실제 프롬프트 재구성
     */
    private String buildRealImagePrompt(Scene scene) {
        String rawPrompt = scene.getImagePrompt();
        String summary = scene.getSummary() != null ? scene.getSummary().trim() : "";
        String optionalElements = scene.getOptionalElements() != null ? scene.getOptionalElements().trim() : "";

        boolean genericPrompt = rawPrompt == null
                || rawPrompt.isBlank()
                || rawPrompt.toLowerCase().contains("standard scene")
                || rawPrompt.toLowerCase().contains("basic lighting")
                || rawPrompt.toLowerCase().contains("basic composition");

        if (!genericPrompt) {
            return sanitizePrompt(rawPrompt);
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("A cute hamster scene, ");

        if (!summary.isBlank()) {
            prompt.append(summary).append(", ");
        }

        if (!optionalElements.isBlank() && !"{}".equals(optionalElements)) {
            prompt.append("scene design details: ").append(optionalElements).append(", ");
        }

        prompt.append("warm soft lighting, pastel background, cinematic composition, high detail, adorable fashion styling, 4k");

        return sanitizePrompt(prompt.toString());
    }

    private String sanitizePrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        return prompt.replace("null", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String buildEditedPrompt(String originalPrompt, String userEditText) {
        String base = originalPrompt != null && !originalPrompt.isBlank()
                ? originalPrompt
                : "A cute hamster fashion scene";

        return sanitizePrompt(base + ", edited with request: " + userEditText);
    }

    /**
     * 실제 AI 편집 연동 전까지는 sourceUrl 기반의 제어된 mock 편집 URL 생성
     */
    private String generateEditedImageUrl(String sourceUrl, String userEditText) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new RuntimeException("Source image URL is empty");
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        return String.format("%s?edited=%s&prompt=%s", sourceUrl, timestamp, userEditText.hashCode());
    }

    private String generateFallbackEditedUrl(String sourceUrl, String userEditText) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return String.format("https://fallback-image-service.com/edited/%d_%d.png",
                    System.currentTimeMillis(),
                    userEditText.hashCode());
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        return String.format("%s?fallback=%s&prompt=%s", sourceUrl, timestamp, userEditText.hashCode());
    }

    /**
     * picsum 대신 명확한 fallback URL 사용
     */
    private String generateFallbackImageUrl(String imagePrompt) {
        log.warn("IMAGE FALLBACK USED - imagePrompt: {}", imagePrompt);

        String timestamp = String.valueOf(System.currentTimeMillis());
        String promptHash = String.valueOf(imagePrompt.hashCode());

        return String.format("https://fallback-image-service.com/generated/%s_%s.png",
                timestamp, promptHash);
    }

    private boolean isFallbackUrl(String imageUrl) {
        if (imageUrl == null) {
            return false;
        }
        return imageUrl.contains("fallback-image-service.com") || imageUrl.contains("picsum.photos");
    }
}