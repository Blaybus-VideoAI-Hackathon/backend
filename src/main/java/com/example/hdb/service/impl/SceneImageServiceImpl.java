package com.example.hdb.service.impl;

import com.example.hdb.dto.request.ImageEditCompleteRequest;
import com.example.hdb.dto.request.SceneImageEditAiRequest;
import com.example.hdb.dto.response.ImageGenerationResult;
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
    // ★★★ 이미지 생성 (revised_prompt 기반 일관성 보장) ★★★
    // ──────────────────────────────────────────

    @Override
    public SceneImageResponse generateImage(Long projectId, Long sceneId, String loginId) {
        log.info("=== SceneImageService.generateImage Started ===");
        log.info("projectId: {}, sceneId: {}, loginId: {}", projectId, sceneId, loginId);

        Scene scene = sceneRepository.findByIdAndProjectId(sceneId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCENE_NOT_FOUND));

        validateProjectOwnership(scene.getProject(), loginId);

        // ★★★ 1단계: 프로젝트의 첫 번째 씬의 revised_prompt 찾기 ★★★
        String baseRevisedPrompt = findProjectBaseRevisedPrompt(projectId, sceneId);

        String consistentStylePrefix = buildConsistentStylePrefix(scene.getProject());
        String imagePrompt;

        if (baseRevisedPrompt == null) {
            // 첫 번째 씬 → 일반 프롬프트 사용
            imagePrompt = buildConsistentImagePrompt(scene, consistentStylePrefix);
            log.info("=== FIRST SCENE: Using original prompt ===");
        } else {
            // 두 번째 이후 씬 → revised_prompt 기반으로 씬 내용만 교체
            imagePrompt = buildPromptWithRevisedBase(scene, baseRevisedPrompt);
            log.info("=== SUBSEQUENT SCENE: Using revised prompt base ===");
        }

        if (imagePrompt == null || imagePrompt.isBlank()) {
            log.error("Scene image prompt is null or empty");
            throw new BusinessException(ErrorCode.SCENE_IMAGE_PROMPT_NOT_FOUND);
        }

        Integer nextImageNumber = sceneImageRepository.findFirstBySceneIdOrderByImageNumberDesc(sceneId)
                .map(SceneImage::getImageNumber)
                .orElse(0) + 1;

        log.info("Next image number: {}", nextImageNumber);
        log.info("Base revised prompt: {}", baseRevisedPrompt != null ? truncate(baseRevisedPrompt, 100) : "null");
        log.info("Final image prompt: {}", truncate(imagePrompt, 150));

        SceneImage sceneImage = SceneImage.builder()
                .scene(scene)
                .imageNumber(nextImageNumber)
                .imageUrl(null)
                .editedImageUrl(null)
                .imagePrompt(imagePrompt)
                .revisedPrompt(null)  // 생성 후 저장됨
                .openaiImageId(null)
                .status(SceneImage.ImageStatus.GENERATING)
                .build();

        SceneImage savedImage = sceneImageRepository.save(sceneImage);
        log.info("SceneImage saved with GENERATING status: {}", savedImage.getId());

        try {
            log.info("=== CALLING OPENAI IMAGE API ===");
            ImageGenerationResult result = openAIService.generateImage(imagePrompt);

            if (result.getImageUrl() == null || result.getImageUrl().isBlank()) {
                throw new RuntimeException("AI returned empty imageUrl");
            }

            log.info("Generated image URL: {}", result.getImageUrl());
            log.info("Revised prompt from DALL-E: {}", result.getRevisedPrompt());

            String permanentUrl = uploadUrlToCloudinary(result.getImageUrl(), sceneId, nextImageNumber, "generated");
            log.info("Cloudinary permanent URL: {}", permanentUrl);

            savedImage.setImageUrl(permanentUrl);
            savedImage.setRevisedPrompt(result.getRevisedPrompt());  // ★★★ revised_prompt 저장 ★★★
            savedImage.setStatus(SceneImage.ImageStatus.READY);
            SceneImage completedImage = sceneImageRepository.save(savedImage);

            log.info("IMAGE SAVED - sceneId: {}, imageId: {}, url: {}",
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

    /**
     * 프로젝트의 첫 번째 씬의 revised_prompt 찾기
     * - sceneOrder가 가장 작은 씬의 revised_prompt 반환
     * - 없으면 null (이 씬이 첫 번째)
     */
    private String findProjectBaseRevisedPrompt(Long projectId, Long currentSceneId) {
        try {
            Scene currentScene = sceneRepository.findById(currentSceneId).orElse(null);
            if (currentScene == null) {
                return null;
            }

            Integer currentOrder = currentScene.getSceneOrder();

            // 현재 씬보다 order가 작은 씬들 중 이미지가 있는 씬 찾기
            List<Scene> previousScenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);

            for (Scene scene : previousScenes) {
                if (scene.getSceneOrder() < currentOrder) {
                    // 이 씬의 첫 번째 이미지의 revised_prompt 찾기
                    List<SceneImage> images = sceneImageRepository.findBySceneIdOrderByImageNumberAsc(scene.getId());
                    if (!images.isEmpty() && images.get(0).getRevisedPrompt() != null) {
                        String revisedPrompt = images.get(0).getRevisedPrompt();
                        log.info("Found base revised_prompt from scene {} (order {})",
                                scene.getId(), scene.getSceneOrder());
                        return revisedPrompt;
                    }
                }
            }

            log.info("No base revised_prompt found - this is the first scene image");
            return null;

        } catch (Exception e) {
            log.warn("Failed to find base revised_prompt", e);
            return null;
        }
    }

    /**
     * revised_prompt를 기반으로 씬 내용만 교체한 프롬프트 생성
     *
     * 전략:
     * 1. revised_prompt에서 캐릭터/스타일 묘사 추출
     * 2. 현재 씬의 동작/상황만 결합
     */
    private String buildPromptWithRevisedBase(Scene scene, String baseRevisedPrompt) {
        String sceneSummary = scene.getSummary() != null ? scene.getSummary().trim() : "";

        // revised_prompt는 이미 DALL-E가 만든 매우 상세한 캐릭터 묘사
        // 이것을 그대로 사용하되, 마지막에 씬의 동작만 추가

        // revised_prompt에서 마지막 문장(일반적으로 동작/상황 묘사) 제거
        String characterDescription = extractCharacterDescription(baseRevisedPrompt);

        // 씬의 새로운 동작 추가
        String newPrompt = characterDescription + " " + sceneSummary;

        log.info("Built prompt from revised base:");
        log.info("  Character part: {}", truncate(characterDescription, 80));
        log.info("  Scene part: {}", sceneSummary);

        return sanitizePrompt(newPrompt);
    }

    /**
     * revised_prompt에서 캐릭터/스타일 묘사 부분 추출
     * (마지막 문장은 보통 동작/상황이므로 제거)
     */
    private String extractCharacterDescription(String revisedPrompt) {
        if (revisedPrompt == null || revisedPrompt.isBlank()) {
            return "";
        }

        // 마지막 문장 제거 (보통 동작/상황 묘사)
        // "A cute yellow kitten with blue eyes, fluffy fur. The kitten is sleeping on a pink blanket."
        // → "A cute yellow kitten with blue eyes, fluffy fur."

        String[] sentences = revisedPrompt.split("\\. ");
        if (sentences.length <= 1) {
            return revisedPrompt;  // 문장이 하나면 그대로 반환
        }

        // 마지막 문장 제외하고 결합
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < sentences.length - 1; i++) {
            result.append(sentences[i]);
            if (i < sentences.length - 2) {
                result.append(". ");
            }
        }

        return result.toString().trim();
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
    // ★★★ 핵심: 일관성 있는 스타일 Prefix 구축 ★★★
    // ──────────────────────────────────────────

    /**
     * 프로젝트 전체에서 캐릭터/배경/스타일 일관성을 보장하는 프롬프트 prefix 생성
     * 모든 씬에서 동일한 캐릭터 외형, 배경, 그림체를 유지하도록 강제
     */
    private String buildConsistentStylePrefix(Project project) {
        StringBuilder prefix = new StringBuilder();

        // 1. 일관성 강조 (최우선)
        prefix.append("CRITICAL: All scenes must maintain EXACT same character appearance, facial features, clothing, hair style, body proportions, and art style. ");
        prefix.append("Characters must be IDENTICAL across all scenes - same face, same outfit, same design. ");

        try {
            var planAnalysis = planningService.getLatestPlanAnalysis(project.getId());
            if (planAnalysis != null && planAnalysis.getProjectCore() != null) {
                var core = planAnalysis.getProjectCore();

                // 2. 메인 캐릭터 상세 정의
                if (core.getMainCharacter() != null && !core.getMainCharacter().isBlank()) {
                    prefix.append("Main character: ").append(core.getMainCharacter())
                            .append(" - MUST look EXACTLY the same in every scene (same face, same hair, same clothes, same body type). ");
                }

                // 3. 보조 캐릭터 상세 정의
                if (core.getSubCharacters() != null && !core.getSubCharacters().isEmpty()) {
                    prefix.append("Supporting characters: ")
                            .append(String.join(", ", core.getSubCharacters()))
                            .append(" - MUST maintain EXACT same appearance in all scenes. ");
                }

                // 4. 배경 세계관 고정
                if (core.getBackgroundWorld() != null && !core.getBackgroundWorld().isBlank()) {
                    prefix.append("World setting: ").append(core.getBackgroundWorld())
                            .append(" - consistent environment and atmosphere across all scenes. ");
                }

                // 5. 스토리라인 컨텍스트 (선택적)
                if (core.getStoryLine() != null && !core.getStoryLine().isBlank()) {
                    // 스토리라인에서 핵심 비주얼 요소 추출
                    prefix.append("Story context: ").append(truncate(core.getStoryLine(), 100)).append(". ");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load plan analysis for style prefix, using project info only", e);
        }

        // 6. 프로젝트 아트 스타일 강조
        if (project.getStyle() != null && !project.getStyle().isBlank()) {
            prefix.append("Art style: ").append(project.getStyle())
                    .append(" - MUST be consistent across ALL scenes (same drawing style, same color palette, same rendering technique). ");
        }

        // 7. 화면 비율
        if (project.getRatio() != null && !project.getRatio().isBlank()) {
            prefix.append("Aspect ratio: ").append(project.getRatio()).append(". ");
        }

        // 8. 최종 일관성 재강조
        prefix.append("REMEMBER: Same character design, same facial features, same outfits, same color scheme, same art style in EVERY scene. ");
        prefix.append("Do NOT change character appearance, facial structure, clothing, or visual style between scenes. ");

        String result = prefix.toString().trim();
        log.info("Built consistent style prefix (length={}): {}", result.length(), truncate(result, 200));
        return result;
    }

    /**
     * 씬별 프롬프트 + 일관성 prefix 결합
     */
    private String buildConsistentImagePrompt(Scene scene, String consistentStylePrefix) {
        String rawPrompt = scene.getImagePrompt();
        String summary = scene.getSummary() != null ? scene.getSummary().trim() : "";
        String optionalElements = scene.getOptionalElements() != null ? scene.getOptionalElements().trim() : "";

        // generic 프롬프트 감지
        boolean genericPrompt = rawPrompt == null
                || rawPrompt.isBlank()
                || rawPrompt.toLowerCase().contains("standard scene")
                || rawPrompt.toLowerCase().contains("basic lighting")
                || rawPrompt.toLowerCase().contains("basic composition");

        StringBuilder sceneSpecificPrompt = new StringBuilder();

        if (!genericPrompt) {
            sceneSpecificPrompt.append(sanitizePrompt(rawPrompt));
        } else {
            // generic한 경우 씬 정보로 구체화
            if (!summary.isBlank()) {
                sceneSpecificPrompt.append(summary);
            }
            if (!optionalElements.isBlank() && !"{}".equals(optionalElements)) {
                sceneSpecificPrompt.append(", scene elements: ").append(optionalElements);
            }
            sceneSpecificPrompt.append(", cinematic composition, detailed, high quality");
        }

        // ★★★ 최종 프롬프트: 일관성 prefix + 씬 구체 내용 ★★★
        String finalPrompt = consistentStylePrefix + " | Scene: " + sceneSpecificPrompt.toString();

        return sanitizePrompt(finalPrompt);
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

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}