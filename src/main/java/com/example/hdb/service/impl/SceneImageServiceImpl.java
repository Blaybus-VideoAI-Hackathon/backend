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
import com.example.hdb.service.PlanningService;
import com.example.hdb.service.SceneImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class SceneImageServiceImpl implements SceneImageService {

    private static final String OPENAI_IMAGE_EDIT_URL = "https://api.openai.com/v1/images/edits";
    private static final String OPENAI_EDIT_MODEL = "gpt-image-1.5";

    private final SceneImageRepository sceneImageRepository;
    private final SceneRepository sceneRepository;
    private final ProjectRepository projectRepository;
    private final OpenAIService openAIService;
    private final PlanningService planningService;
    private final RestTemplate restTemplate;

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Value("${openai.api.key:${OPENAI_API_KEY:}}")
    private String openaiApiKey;

    // ──────────────────────────────────────────
    // 이미지 생성
    // ──────────────────────────────────────────

    @Override
    public SceneImageResponse generateImage(Long projectId, Long sceneId, String loginId) {
        log.info("=== SceneImageService.generateImage Started ===");
        log.info("projectId: {}, sceneId: {}, loginId: {}", projectId, sceneId, loginId);

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        validateProjectOwnership(scene.getProject(), loginId);

        String stylePrefix = buildStylePrefix(scene.getProject());

        String imagePrompt = buildRealImagePrompt(scene, stylePrefix);
        if (imagePrompt == null || imagePrompt.isBlank()) {
            log.error("Scene image prompt is null or empty after normalization");
            throw new BusinessException(ErrorCode.SCENE_IMAGE_PROMPT_NOT_FOUND);
        }

        Integer nextImageNumber = sceneImageRepository.findFirstBySceneIdOrderByImageNumberDesc(sceneId)
                .map(SceneImage::getImageNumber)
                .orElse(0) + 1;

        log.info("Next image number: {}", nextImageNumber);
        log.info("Style prefix: {}", stylePrefix);
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
            String tempImageUrl = openAIService.generateImage(imagePrompt);

            if (tempImageUrl == null || tempImageUrl.isBlank()) {
                throw new RuntimeException("AI returned empty imageUrl");
            }
            log.info("OpenAI temp URL: {}", tempImageUrl);

            String permanentUrl = uploadUrlToCloudinary(tempImageUrl, sceneId, nextImageNumber, "generated");
            log.info("Cloudinary permanent URL: {}", permanentUrl);

            savedImage.setImageUrl(permanentUrl);
            savedImage.setStatus(SceneImage.ImageStatus.READY);
            SceneImage completedImage = sceneImageRepository.save(savedImage);

            log.info("IMAGE SAVED WITH PERMANENT URL - sceneId: {}, imageId: {}, url: {}",
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

    // ──────────────────────────────────────────
    // 조회
    // ──────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SceneImageResponse> getImages(Long projectId, Long sceneId, String loginId) {
        log.info("Getting images for scene: {}, project: {}, user: {}", sceneId, projectId, loginId);

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        validateProjectOwnership(scene.getProject(), loginId);

        List<SceneImage> images = sceneImageRepository.findBySceneIdOrderByImageNumberAsc(sceneId);

        return images.stream()
                .map(image -> toResponse(image, isFallbackUrl(image.getImageUrl()) || isFallbackUrl(image.getEditedImageUrl())))
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
                    SceneImageResponse response = toResponse(
                            image,
                            isFallbackUrl(image.getImageUrl()) || isFallbackUrl(image.getEditedImageUrl())
                    );
                    response.setSceneId(image.getScene().getId());
                    return response;
                })
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────
    // 이미지 편집
    // ──────────────────────────────────────────

    @Override
    public SceneImageResponse completeImageEdit(Long projectId, Long sceneId, Long imageId,
                                                String loginId, ImageEditCompleteRequest request) {
        log.info("Completing image edit for imageId: {}", imageId);

        SceneImage sceneImage = sceneImageRepository.findByIdAndSceneId(imageId, sceneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));

        Scene scene = sceneImage.getScene();
        validateProjectOwnership(scene.getProject(), loginId);

        sceneImage.setEditedImageUrl(request.getEditedImageUrl());
        SceneImage savedImage = sceneImageRepository.save(sceneImage);

        log.info("Image edit completed successfully: {}", savedImage.getId());

        return toResponse(savedImage, isFallbackUrl(savedImage.getImageUrl()) || isFallbackUrl(savedImage.getEditedImageUrl()));
    }

    @Override
    public SceneImageResponse generateImageEditAi(Long projectId, Long sceneId, Long imageId,
                                                  String loginId, SceneImageEditAiRequest request) {
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

        if (sourceImageUrl == null || sourceImageUrl.isBlank()) {
            log.error("Source image URL is empty for imageId={}", imageId);
            throw new BusinessException(ErrorCode.IMAGE_NOT_FOUND);
        }

        Integer maxImageNumber = sceneImageRepository.findMaxImageNumberBySceneId(sceneId);
        int nextImageNumber = (maxImageNumber != null ? maxImageNumber : 0) + 1;

        log.info("Source image URL: {}", sourceImageUrl);
        log.info("Next image number: {}", nextImageNumber);

        try {
            String editPrompt = buildStrictEditPrompt(originalImage.getImagePrompt(), request.getUserEditText());
            log.info("OpenAI image edit prompt: {}", editPrompt);

            String editedImageUrl = editImageWithOpenAIAndUpload(
                    sourceImageUrl,
                    editPrompt,
                    sceneId,
                    nextImageNumber
            );

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
            log.info("Saved REAL edited image with ID: {}, editedImageUrl={}", savedImage.getId(), savedImage.getEditedImageUrl());

            return toResponse(savedImage, false);

        } catch (Exception e) {
            log.error("AI image edit failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // ──────────────────────────────────────────
    // PRIVATE: 실제 OpenAI 이미지 편집
    // ──────────────────────────────────────────

    private String editImageWithOpenAIAndUpload(String sourceImageUrl,
                                                String editPrompt,
                                                Long sceneId,
                                                int imageNumber) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            throw new RuntimeException("OPENAI_API_KEY is not configured");
        }

        byte[] sourceBytes = downloadImageBytes(sourceImageUrl);
        String extension = detectExtensionFromUrl(sourceImageUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(openaiApiKey);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource imageResource = new ByteArrayResource(sourceBytes) {
            @Override
            public String getFilename() {
                return "source-image." + extension;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", OPENAI_EDIT_MODEL);
        body.add("prompt", editPrompt);
        body.add("input_fidelity", "high");
        body.add("quality", "high");
        body.add("output_format", "png");
        body.add("size", "1024x1024");
        body.add("image[]", imageResource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                OPENAI_IMAGE_EDIT_URL,
                requestEntity,
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("OpenAI image edit failed: " + response.getStatusCode());
        }

        String uploadedUrl = extractAndUploadEditedImage(response.getBody(), sceneId, imageNumber);
        if (uploadedUrl == null || uploadedUrl.isBlank()) {
            throw new RuntimeException("Edited image upload failed");
        }

        log.info("Edited image uploaded to Cloudinary: {}", uploadedUrl);
        return uploadedUrl;
    }

    @SuppressWarnings("unchecked")
    private String extractAndUploadEditedImage(Map responseBody, Long sceneId, int imageNumber) {
        Object dataObj = responseBody.get("data");
        if (!(dataObj instanceof List<?> dataList) || dataList.isEmpty()) {
            throw new RuntimeException("OpenAI image edit response missing data");
        }

        Object firstObj = dataList.get(0);
        if (!(firstObj instanceof Map<?, ?> firstMapRaw)) {
            throw new RuntimeException("OpenAI image edit response invalid first item");
        }

        Map<String, Object> firstMap = (Map<String, Object>) firstMapRaw;

        Object b64 = firstMap.get("b64_json");
        if (b64 instanceof String b64Json && !b64Json.isBlank()) {
            byte[] imageBytes = Base64.getDecoder().decode(b64Json);
            return uploadBytesToCloudinary(imageBytes, sceneId, imageNumber, "edited", "png");
        }

        Object urlObj = firstMap.get("url");
        if (urlObj instanceof String url && !url.isBlank()) {
            return uploadUrlToCloudinary(url, sceneId, imageNumber, "edited");
        }

        throw new RuntimeException("OpenAI image edit response missing b64_json/url");
    }

    private byte[] downloadImageBytes(String imageUrl) {
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    imageUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    byte[].class
            );

            byte[] body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.length == 0) {
                throw new RuntimeException("Failed to download source image");
            }

            return body;
        } catch (Exception e) {
            throw new RuntimeException("Source image download failed", e);
        }
    }

    private String detectExtensionFromUrl(String imageUrl) {
        if (imageUrl == null) return "png";
        String lower = imageUrl.toLowerCase();
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "jpg";
        if (lower.contains(".webp")) return "webp";
        return "png";
    }

    private String buildStrictEditPrompt(String originalPrompt, String userEditText) {
        String basePrompt = (originalPrompt == null || originalPrompt.isBlank())
                ? "원본 장면"
                : originalPrompt.trim();

        return """
                다음 이미지를 편집해라.
                사용자의 수정 요청만 반영하고, 나머지 장면 구성, 인물, 배경, 구도, 스타일은 최대한 유지해라.
                
                원본 이미지 설명:
                %s
                
                수정 요청:
                %s
                
                규칙:
                1. 사용자의 수정 요청만 반영할 것
                2. 기존 인물, 배경, 구도, 스타일은 유지할 것
                3. 새로운 장면을 다시 창작하지 말 것
                4. 원본 이미지의 핵심 요소를 유지한 상태로 수정할 것
                """.formatted(basePrompt, userEditText);
    }

    // ──────────────────────────────────────────
    // PRIVATE: 공통 스타일 prefix 생성
    // ──────────────────────────────────────────

    private String buildStylePrefix(Project project) {
        StringBuilder prefix = new StringBuilder();
        prefix.append("IMPORTANT: maintain consistent character appearance and world throughout all scenes. ");

        try {
            var planAnalysis = planningService.getLatestPlanAnalysis(project.getId());
            if (planAnalysis != null && planAnalysis.getProjectCore() != null) {
                var core = planAnalysis.getProjectCore();

                if (core.getMainCharacter() != null && !core.getMainCharacter().isBlank()) {
                    prefix.append("main character: ").append(core.getMainCharacter())
                            .append(" (must look identical in every scene), ");
                }
                if (core.getSubCharacters() != null && !core.getSubCharacters().isEmpty()) {
                    prefix.append("sub characters: ")
                            .append(String.join(", ", core.getSubCharacters()))
                            .append(" (must look identical in every scene), ");
                }
                if (core.getBackgroundWorld() != null && !core.getBackgroundWorld().isBlank()) {
                    prefix.append("world setting: ").append(core.getBackgroundWorld()).append(", ");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load plan analysis for style prefix, using project info only", e);
        }

        if (project.getStyle() != null && !project.getStyle().isBlank()) {
            prefix.append("art style: ").append(project.getStyle())
                    .append(" (consistent style across all scenes), ");
        }
        if (project.getRatio() != null && !project.getRatio().isBlank()) {
            prefix.append("aspect ratio: ").append(project.getRatio()).append(", ");
        }

        prefix.append("same character design, same color palette, same art style in every scene");
        return prefix.toString().trim();
    }

    // ──────────────────────────────────────────
    // PRIVATE: Cloudinary 업로드
    // ──────────────────────────────────────────

    private String uploadUrlToCloudinary(String imageUrl, Long sceneId, int imageNumber, String suffix) {
        log.info("=== Uploading URL to Cloudinary ===");
        log.info("cloudName={}, imageUrl={}", cloudName, imageUrl);

        if (cloudName == null || cloudName.isBlank()) {
            log.warn("Cloudinary not configured, using original URL");
            return imageUrl;
        }

        try {
            String uploadUrl = String.format("https://api.cloudinary.com/v1_1/%s/image/upload", cloudName);

            long timestamp = System.currentTimeMillis() / 1000;
            String publicId = String.format("hdb/scene_%d_img_%d_%s_%d", sceneId, imageNumber, suffix, timestamp);

            String signatureInput = String.format("public_id=%s&timestamp=%d%s", publicId, timestamp, apiSecret);
            String signature = sha1Hex(signatureInput);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", imageUrl);
            body.add("api_key", apiKey);
            body.add("timestamp", String.valueOf(timestamp));
            body.add("public_id", publicId);
            body.add("signature", signature);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(uploadUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object secureUrl = response.getBody().get("secure_url");
                if (secureUrl != null && !secureUrl.toString().isBlank()) {
                    log.info("Cloudinary upload SUCCESS - url: {}", secureUrl);
                    return secureUrl.toString();
                }
            }

            throw new RuntimeException("Cloudinary upload failed");

        } catch (Exception e) {
            throw new RuntimeException("Cloudinary upload error", e);
        }
    }

    private String uploadBytesToCloudinary(byte[] imageBytes,
                                           Long sceneId,
                                           int imageNumber,
                                           String suffix,
                                           String extension) {
        log.info("=== Uploading BYTES to Cloudinary ===");
        log.info("cloudName={}, bytes={}", cloudName, imageBytes.length);

        if (cloudName == null || cloudName.isBlank()) {
            throw new RuntimeException("Cloudinary is not configured");
        }

        try {
            String uploadUrl = String.format("https://api.cloudinary.com/v1_1/%s/image/upload", cloudName);

            long timestamp = System.currentTimeMillis() / 1000;
            String publicId = String.format("hdb/scene_%d_img_%d_%s_%d", sceneId, imageNumber, suffix, timestamp);

            String signatureInput = String.format("public_id=%s&timestamp=%d%s", publicId, timestamp, apiSecret);
            String signature = sha1Hex(signatureInput);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource resource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "edited-image." + extension;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);
            body.add("api_key", apiKey);
            body.add("timestamp", String.valueOf(timestamp));
            body.add("public_id", publicId);
            body.add("signature", signature);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(uploadUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object secureUrl = response.getBody().get("secure_url");
                if (secureUrl != null && !secureUrl.toString().isBlank()) {
                    log.info("Cloudinary upload SUCCESS - url: {}", secureUrl);
                    return secureUrl.toString();
                }
            }

            throw new RuntimeException("Cloudinary byte upload failed");

        } catch (Exception e) {
            throw new RuntimeException("Cloudinary byte upload error", e);
        }
    }

    private String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-1 hashing failed", e);
        }
    }

    // ──────────────────────────────────────────
    // PRIVATE: 기타 헬퍼
    // ──────────────────────────────────────────

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

    private String buildRealImagePrompt(Scene scene, String stylePrefix) {
        String rawPrompt = scene.getImagePrompt();
        String summary = scene.getSummary() != null ? scene.getSummary().trim() : "";
        String optionalElements = scene.getOptionalElements() != null ? scene.getOptionalElements().trim() : "";

        boolean genericPrompt = rawPrompt == null
                || rawPrompt.isBlank()
                || rawPrompt.toLowerCase().contains("standard scene")
                || rawPrompt.toLowerCase().contains("basic lighting")
                || rawPrompt.toLowerCase().contains("basic composition");

        String scenePrompt;
        if (!genericPrompt) {
            scenePrompt = sanitizePrompt(rawPrompt);
        } else {
            StringBuilder prompt = new StringBuilder();
            if (!summary.isBlank()) {
                prompt.append(summary).append(", ");
            }
            if (!optionalElements.isBlank() && !"{}".equals(optionalElements)) {
                prompt.append("scene design: ").append(optionalElements).append(", ");
            }
            prompt.append("warm soft lighting, cinematic composition, high detail, 4k");
            scenePrompt = sanitizePrompt(prompt.toString());
        }

        if (stylePrefix != null && !stylePrefix.isBlank()) {
            return sanitizePrompt(stylePrefix + ", " + scenePrompt);
        }
        return scenePrompt;
    }

    private String sanitizePrompt(String prompt) {
        if (prompt == null) return "";
        return prompt.replace("null", " ").replaceAll("\\s+", " ").trim();
    }

    private String buildEditedPrompt(String originalPrompt, String userEditText) {
        String base = (originalPrompt != null && !originalPrompt.isBlank()) ? originalPrompt : "원본 장면";
        return sanitizePrompt(base + ", 수정 요청 반영: " + userEditText);
    }

    private String generateFallbackImageUrl(String imagePrompt) {
        log.warn("IMAGE FALLBACK USED - imagePrompt: {}", imagePrompt);
        return String.format("https://fallback-image-service.com/generated/%d_%d.png",
                System.currentTimeMillis(), imagePrompt.hashCode());
    }

    private boolean isFallbackUrl(String imageUrl) {
        if (imageUrl == null) return false;
        return imageUrl.contains("fallback-image-service.com")
                || imageUrl.contains("picsum.photos");
    }
}