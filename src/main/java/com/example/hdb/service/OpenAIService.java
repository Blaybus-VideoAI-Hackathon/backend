package com.example.hdb.service;

import com.example.hdb.dto.common.OptionalElements;
import com.example.hdb.dto.openai.OpenAIRequest;
import com.example.hdb.dto.openai.OpenAIResponse;
import com.example.hdb.dto.response.ImageGenerationResult;
import com.example.hdb.dto.response.SceneDesignResponse;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
@ConditionalOnProperty(name = "openai.api-key", matchIfMissing = false)
public class OpenAIService {

    private final RestTemplate openAiRestTemplate;
    private final String openaiApiUrl;
    private final String openAiApiKey;
    private final ObjectMapper objectMapper;

    public OpenAIService(
            @Qualifier("openAiRestTemplate") RestTemplate openAiRestTemplate,
            @Value("${openai.api.url:https://api.openai.com/v1}") String openaiApiUrl,
            @Value("${openai.api-key}") String openAiApiKey,
            ObjectMapper objectMapper
    ) {
        this.openAiRestTemplate = openAiRestTemplate;
        this.openaiApiUrl = openaiApiUrl;
        this.openAiApiKey = openAiApiKey;
        this.objectMapper = objectMapper;
    }

    // ──────────────────────────────────────────
    // 기획 생성
    // ──────────────────────────────────────────

    public String generatePlanningWithSummary(
            String userPrompt,
            String projectPurpose,
            Integer duration,
            String ratio,
            String style
    ) {
        String systemPrompt = """
                당신은 전문 숏폼 광고 기획자입니다.
                사용자의 요청을 바탕으로 3개의 확실히 다른 기획안을 생성하세요.

                중요한 규칙:
                1. 사용자의 요청을 구체적으로 반영할 것
                2. generic 표현 금지
                   - "성장하는 주인공"
                   - "조력자"
                   - "현실적인 일상 공간"
                   - "도전 과제"
                   같은 표현 금지
                3. 반드시 구체적인 캐릭터, 배경, 스토리라인 작성
                4. 3개 기획안은 접근법이 분명히 달라야 함
                   - 1안: 감성 중심
                   - 2안: 액션/역동성 중심
                   - 3안: 유머/반전 중심
                5. 프로젝트 공통 정보는 반드시 그대로 유지
                6. 반드시 한국어로 작성하고 영어 금지
                7. 구조화된 출력 금지, 연속된 서술형 스토리 문단으로 작성
                8. 리스트/항목 나열 금지
                9. storyLine은 최소 6문장 이상, 가능하면 8~10문장 정도로 작성
                10. 짧은 요약 금지, 실제 영상처럼 상상되는 긴 스토리 문단으로 작성
                11. "이야기", "장면", "순간" 같은 추상적 표현만 쓰지 말고,
                    실제 인물의 행동, 배경, 감정 변화, 분위기, 사건 전개를 구체적으로 묘사
                12. 시작 → 전개 → 감정 고조 → 마무리 흐름이 보이게 작성
                13. 15~30초 영상으로 자연스럽게 3~6개의 장면으로 분해할 수 있는 구조 포함
                14. 제목, 리스트, 항목 나열 금지
                15. 오직 하나의 긴 서술형 문단으로 작성

                형식:
                {
                  "plans": [
                    {
                      "planId": 1,
                      "title": "...",
                      "focus": "...",
                      "displayText": "...",
                      "recommendationReason": "...",
                      "strengths": ["...", "..."],
                      "targetUseCase": "...",
                      "storyLine": "최소 6문장 이상의 구체적인 서술형 스토리 문단 (인물 행동, 배경, 감정 변화, 사건 전개 포함)",
                      "coreElements": {
                        "purpose": "...",
                        "duration": 20,
                        "ratio": "9:16",
                        "style": "...",
                        "mainCharacter": "...",
                        "subCharacters": ["...", "..."],
                        "backgroundWorld": "...",
                        "storyFlow": "...",
                        "storyLine": "..."
                      }
                    }
                  ]
                }
                """;

        String formattedUserPrompt = String.format("""
                사용자 요청:
                %s

                프로젝트 정보:
                - purpose: %s
                - duration: %d초
                - ratio: %s
                - style: %s

                위 사용자 요청의 핵심 소재(캐릭터, 상황, 감성)를 반드시 실제 기획안에 반영하세요.
                
                중요 요구사항:
                - 짧게 요약하지 말고, 실제 영상처럼 상상되는 긴 스토리 문단으로 작성하라
                - storyLine은 최소 6문장 이상으로 작성하라
                - 인물의 구체적인 행동, 감정, 배경 묘사를 포함하라
                """, userPrompt, projectPurpose, duration, ratio, style);

        String response = callOpenAI(systemPrompt, formattedUserPrompt);

        log.info("=== Planning Generation Raw Response ===");
        log.info("User Prompt: {}", userPrompt);
        log.info("Project Info: purpose={}, duration={}, ratio={}, style={}", projectPurpose, duration, ratio, style);
        log.info("OpenAI Raw Response: {}", response);

        return response;
    }

    /**
     * 더 엄격한 포맷으로 정확히 3개의 기획안을 생성
     * (재시도용 - generatePlanningWithSummary가 3개를 못 만들었을 때 사용)
     */
    public String generatePlanningWithStrictFormat(
            String userPrompt,
            String projectPurpose,
            Integer duration,
            String ratio,
            String style
    ) {
        String systemPrompt = """
                당신은 전문 숏폼 광고 기획자입니다.
                
                ★★★ 중요: 반드시 정확히 3개의 기획안을 JSON 형식으로 생성하세요 ★★★
                
                규칙:
                1. 감성형, 액션형, 유머형 3가지 타입의 기획안 반드시 생성
                2. 각 기획안의 planId는 1, 2, 3 순서대로 설정
                3. plans 배열에는 정확히 3개의 객체만 포함
                4. storyLine은 최소 6문장 이상의 구체적인 서술형
                5. 구체적인 캐릭터, 배경, 사건을 명시
                6. 모든 텍스트는 한국어로 작성
                7. 반드시 JSON만 반환
                
                필수 JSON 구조:
                {
                  "plans": [
                    {
                      "planId": 1,
                      "title": "감성 버전: ...",
                      "focus": "감정이입과 공감",
                      "displayText": "...",
                      "recommendationReason": "...",
                      "strengths": ["...", "...", "..."],
                      "targetUseCase": "...",
                      "storyLine": "최소 6문장 이상 (제목 없음)",
                      "coreElements": {
                        "purpose": "%s",
                        "duration": %d,
                        "ratio": "%s",
                        "style": "%s",
                        "mainCharacter": "...",
                        "subCharacters": ["...", "..."],
                        "backgroundWorld": "...",
                        "storyFlow": "...",
                        "storyLine": "..."
                      }
                    },
                    {
                      "planId": 2,
                      "title": "액션 버전: ...",
                      "focus": "역동성과 에너지",
                      ...
                    },
                    {
                      "planId": 3,
                      "title": "유머 버전: ...",
                      "focus": "유머와 반전",
                      ...
                    }
                  ]
                }
                """.formatted(projectPurpose, duration, ratio, style);

        String formattedUserPrompt = String.format("""
                사용자 요청:
                %s
                
                프로젝트 정보:
                - purpose: %s
                - duration: %d초
                - ratio: %s
                - style: %s
                
                위 요청을 바탕으로 정확히 3개의 기획안(감성형, 액션형, 유머형)을 생성하세요.
                """, userPrompt, projectPurpose, duration, ratio, style);

        String response = callOpenAI(systemPrompt, formattedUserPrompt);

        log.info("=== STRICT FORMAT PLANNING GENERATION ===");
        log.info("User Prompt: {}", userPrompt);
        log.info("Project Info: purpose={}, duration={}, ratio={}, style={}", projectPurpose, duration, ratio, style);
        log.info("OpenAI Raw Response: {}", response);

        return response;
    }

    // ──────────────────────────────────────────
    // 기획안 분석
    // ──────────────────────────────────────────

    public String analyzeSelectedPlan(
            String storyLine,
            String projectPurpose,
            Integer duration,
            String ratio,
            String style
    ) {
        String systemPrompt = """
                당신은 전문 영상 프로듀서입니다.
                선택된 기획안의 전체 스토리라인을 분석해서 제작 가능한 구조 데이터를 생성하세요.

                규칙:
                1. projectCore는 실제 스토리라인 기반으로 생성
                2. scenePlan은 구체적인 장면 설명으로 생성
                3. placeholder 금지
                   - "분석된 주요 캐릭터"
                   - "선택된 기획안의 스토리라인"
                   - "분석된 첫 번째 장면"
                   같은 표현 금지
                4. 반드시 한국어로 작성하고 영어 금지
                5. 반드시 JSON만 반환

                형식:
                {
                  "projectCore": {
                    "purpose": "...",
                    "duration": 20,
                    "ratio": "9:16",
                    "style": "...",
                    "mainCharacter": "...",
                    "subCharacters": ["...", "..."],
                    "backgroundWorld": "...",
                    "storyFlow": "...",
                    "storyLine": "..."
                  },
                  "scenePlan": {
                    "recommendedSceneCount": 3,
                    "scenes": [
                      {
                        "sceneOrder": 1,
                        "summary": "...",
                        "sceneGoal": "...",
                        "emotionBeat": "...",
                        "estimatedDuration": 5
                      }
                    ]
                  }
                }
                """;

        String userPrompt = String.format("""
                storyLine:
                %s

                프로젝트 정보:
                - purpose: %s
                - duration: %d초
                - ratio: %s
                - style: %s

                위 스토리라인을 실제 장면으로 나눠 분석하세요.
                """, storyLine, projectPurpose, duration, ratio, style);

        String response = callOpenAI(systemPrompt, userPrompt);

        log.info("=== Plan Analysis Raw Response ===");
        log.info("Input StoryLine: {}", storyLine);
        log.info("OpenAI Raw Response: {}", response);

        return response;
    }

    // ──────────────────────────────────────────
    // 씬 설계
    // ──────────────────────────────────────────

    /**
     * 씬 설계: optionalElements의 effects는 LLM이 String으로 줄 수 있으므로
     * 파싱은 SceneServiceImpl에서 안전하게 처리함.
     * 여기서는 프롬프트에서 effects를 String으로 요청하여 LLM 혼란 방지.
     */
    public String designScene(String sceneSummary, String projectCore, String designRequest) {
        String systemPrompt = """
                당신은 영상 씬 설계 전문가입니다.
                입력된 씬 요약, 프로젝트 핵심 요소, 사용자 요청을 바탕으로
                씬 설계 JSON을 생성하세요.

                규칙:
                1. action, camera, lighting, mood는 반드시 채울 것
                2. generic 표현 금지
                   - "기본 설계", "표준", "A standard scene..."
                3. userDesignRequest를 실제 반영할 것
                4. ★★★ imagePrompt 작성 규칙 (매우 중요!) ★★★:
                   - 반드시 20단어 이내로 작성
                   - 단 하나의 짧은 문장만 사용
                   - 스토리 전개, 시간 흐름, 여러 동작 묘사 절대 금지
                   - 나쁜 예: "햇살이 비추는 농장에서 엄마 닭이 알을 품고 있다가 병아리들이 하나둘씩 걸어오고 서로 밀며 모여 앉는다"
                   - 좋은 예: "엄마 닭과 병아리들이 푸른 들판을 함께 걷는 모습"
                   - 좋은 예: "병아리들이 모이를 먹고 있는 장면"
                   - 좋은 예: "엄마 닭이 병아리들을 품에 안고 쉬는 모습"
                5. videoPrompt: 시간 흐름에 따른 동작 묘사 (긴 서술 가능)
                6. 반드시 한국어로 작성하고 영어 금지
                7. 반드시 JSON만 반환

                주의: effects는 단순 문자열로 작성하세요. (배열 아님)

                형식:
                {
                  "optionalElements": {
                    "action": "...",
                    "pose": "...",
                    "camera": "...",
                    "cameraMotion": "...",
                    "lighting": "...",
                    "mood": "...",
                    "timeOfDay": "...",
                    "effects": "soft glow, warm bokeh",
                    "backgroundCharacters": "...",
                    "environmentDetail": "..."
                  },
                  "imagePrompt": "짧은 한 문장 (20단어 이내)",
                  "videoPrompt": "긴 서술 가능"
                }
                """;

        String userPrompt = String.format("""
                sceneSummary: %s

                projectCore:
                %s

                userDesignRequest:
                %s
                """, sceneSummary, projectCore, designRequest);

        String response = callOpenAI(systemPrompt, userPrompt);

        log.info("=== Scene Design Raw Response ===");
        log.info("Scene Summary: {}", sceneSummary);
        log.info("Design Request: {}", designRequest);
        log.info("OpenAI Raw Response: {}", response);

        return response;
    }

    // ──────────────────────────────────────────
    // 씬 수정
    // ──────────────────────────────────────────

    public String editScene(
            String sceneSummary,
            String optionalElements,
            String imagePrompt,
            String videoPrompt,
            String editRequest
    ) {
        String systemPrompt = """
                당신은 비디오 씬 수정 전문가입니다.
                기존 씬 정보와 수정 요청을 반영한 새 설계 JSON을 생성하세요.

                주의: effects는 단순 문자열로 작성하세요. (배열 아님)

                반드시 JSON만 반환하세요.
                형식:
                {
                  "optionalElements": {
                    "action": "...",
                    "pose": "...",
                    "camera": "...",
                    "cameraMotion": "...",
                    "lighting": "...",
                    "mood": "...",
                    "timeOfDay": "...",
                    "effects": "soft glow",
                    "backgroundCharacters": "...",
                    "environmentDetail": "..."
                  },
                  "imagePrompt": "...",
                  "videoPrompt": "..."
                }
                """;

        String fullUserPrompt = String.format("""
                sceneSummary: %s
                optionalElements: %s
                imagePrompt: %s
                videoPrompt: %s
                editRequest: %s
                """, sceneSummary, optionalElements, imagePrompt, videoPrompt, editRequest);

        return callOpenAI(systemPrompt, fullUserPrompt);
    }

    // ──────────────────────────────────────────
    // 씬 목록 생성
    // ──────────────────────────────────────────

    public String generateScenes(String scenePlanJson, int sceneCount) {
        String systemPrompt = """
                당신은 영상 씬 구성 전문가입니다.
                입력된 scenePlan을 바탕으로 실제 저장 가능한 씬 목록만 생성하세요.

                규칙:
                1. 입력된 scenePlan의 scenes를 그대로 활용
                2. summary는 generic 표현 금지
                3. "첫 번째 장면", "두 번째 장면" 같은 표현 금지
                4. 반드시 JSON 배열만 반환

                형식:
                [
                  {
                    "sceneOrder": 1,
                    "summary": "구체적인 장면 설명"
                  }
                ]
                """;

        String userPrompt = String.format("""
                sceneCount: %d

                scenePlan JSON:
                %s
                """, sceneCount, scenePlanJson);

        return callOpenAI(systemPrompt, userPrompt);
    }

    // ──────────────────────────────────────────
    // 최종 프롬프트 생성
    // ──────────────────────────────────────────

    public String generateFinalPrompts(String sceneSummary, String projectCore, String optionalElements) {
        String systemPrompt = """
                당신은 AI 이미지/영상 프롬프트 엔지니어입니다.
                입력된 씬 요약, 프로젝트 핵심 정보, 연출 요소를 기반으로
                imagePrompt와 videoPrompt를 생성하세요.

                규칙:
                1. ★★★ imagePrompt 작성 규칙 (매우 중요!) ★★★:
                   - 반드시 20단어 이내로 작성
                   - 단 하나의 짧은 문장만 사용
                   - 스토리 전개, 시간 흐름, 여러 동작 묘사 절대 금지
                   - 나쁜 예: "아이가 청소를 싫어하다가 도구를 발견하고 신나게 청소하며 깨끗해진 방을 본다"
                   - 좋은 예: "마법 빗자루를 들고 신나게 방을 청소하는 아이"
                   - 좋은 예: "병아리들이 모이를 먹고 있는 장면"
                   - 좋은 예: "엄마 닭이 병아리들과 함께 쉬는 모습"
                2. videoPrompt: 시간 흐름에 따른 동작과 변화 묘사 (긴 서술 가능)
                3. "A standard scene" 같은 generic 문장 금지
                4. null, N/A 같은 표현 금지
                5. 반드시 한국어로 작성하고 영어 금지
                6. 반드시 JSON만 반환

                형식:
                {
                  "imagePrompt": "짧은 한 문장 (20단어 이내)",
                  "videoPrompt": "긴 서술 가능"
                }
                """;

        String userPrompt = String.format("""
                sceneSummary: %s

                projectCore:
                %s

                optionalElements:
                %s
                """, sceneSummary, projectCore, optionalElements);

        return callOpenAI(systemPrompt, userPrompt);
    }

    // ──────────────────────────────────────────
    // 이미지 생성 (DALL-E 3)
    // ──────────────────────────────────────────

    /**
     * DALL-E 3 이미지 생성
     * @return ImageGenerationResult (imageUrl + revisedPrompt)
     */
    public ImageGenerationResult generateImage(String prompt) {
        log.info("=== Image Generation Started ===");
        log.info("Image Prompt: {}", prompt);

        try {
            String safePrompt = prompt.replace("\"", "\\\"").replace("\n", " ").trim();

            String requestJson = String.format("""
                    {
                      "model": "dall-e-3",
                      "prompt": "%s",
                      "n": 1,
                      "size": "1024x1024",
                      "response_format": "url"
                    }
                    """, safePrompt);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + openAiApiKey);

            HttpEntity<String> requestEntity = new HttpEntity<>(requestJson, headers);

            ResponseEntity<String> response = openAiRestTemplate.postForEntity(
                    openaiApiUrl + "/images/generations",
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String responseBody = response.getBody();
                log.info("OpenAI DALL-E response: {}", responseBody);

                JsonNode responseJson = objectMapper.readTree(responseBody);
                JsonNode dataNode = responseJson.get("data");

                if (dataNode != null && dataNode.isArray() && dataNode.size() > 0) {
                    JsonNode firstItem = dataNode.get(0);

                    // URL 추출
                    JsonNode urlNode = firstItem.get("url");
                    String imageUrl = (urlNode != null && !urlNode.asText().isBlank())
                            ? urlNode.asText()
                            : null;

                    // revised_prompt 추출 (DALL-E 3가 실제 사용한 프롬프트)
                    JsonNode revisedNode = firstItem.get("revised_prompt");
                    String revisedPrompt = (revisedNode != null && !revisedNode.asText().isBlank())
                            ? revisedNode.asText()
                            : null;

                    if (imageUrl != null) {
                        log.info("REAL IMAGE GENERATED - url: {}", imageUrl);
                        log.info("REVISED PROMPT: {}", revisedPrompt);

                        return ImageGenerationResult.builder()
                                .imageUrl(imageUrl)
                                .revisedPrompt(revisedPrompt)
                                .build();
                    }
                }
            }

            throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OpenAI DALL-E 실패, fallback 이미지 사용: {}", e.getMessage());
            String timestamp = String.valueOf(System.currentTimeMillis());
            String promptHash = String.valueOf(prompt.hashCode());

            return ImageGenerationResult.builder()
                    .imageUrl(String.format("https://fallback-image-service.com/generated/%s_%s.jpg", timestamp, promptHash))
                    .revisedPrompt(null)
                    .build();
        }
    }

    // ──────────────────────────────────────────
    // 아이디어 생성 (보조)
    // ──────────────────────────────────────────

    public String generateIdea(String coreElements, String style, String ratio) {
        String systemPrompt = """
                당신은 창의적인 숏폼 영상 기획자입니다.
                사용자의 핵심 요소를 바탕으로 영상 아이디어를 생성하세요.

                반드시 JSON만 반환하세요.
                형식:
                {
                  "idea": "생성된 아이디어"
                }
                """;

        String userPrompt = String.format("""
                핵심 요소: %s
                스타일: %s
                화면 비율: %s
                """, coreElements, style, ratio);

        return callOpenAI(systemPrompt, userPrompt);
    }

    // ──────────────────────────────────────────
    // 씬 재설계 변형
    // ──────────────────────────────────────────

    public String generateSceneDesignWithVariation(String summary) {
        String systemPrompt = """
                당신은 비디오 씬 설계 전문가입니다.
                동일한 씬에 대해 다른 연출 버전의 설계를 생성하세요.

                반드시 JSON만 반환하세요.
                형식:
                {
                  "displayText": "...",
                  "imagePrompt": "...",
                  "videoPrompt": "..."
                }
                """;

        String userPrompt = String.format("""
                씬 요약:
                %s

                다른 연출 버전을 생성하세요.
                """, summary);

        String jsonResponse = callOpenAI(systemPrompt, userPrompt);

        try {
            SceneDesignResponse response = objectMapper.readValue(
                    JsonUtils.extractJsonSafely(jsonResponse),
                    SceneDesignResponse.class
            );
            log.info("Successfully parsed scene design variation");
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to parse scene design variation", e);
            return jsonResponse;
        }
    }

    // ──────────────────────────────────────────
    // 이미지 편집 제안
    // ──────────────────────────────────────────

    public String generateImageEditSuggestions(String userEditText, String currentImagePrompt) {
        String systemPrompt = """
                당신은 이미지 편집 전문가입니다.
                사용자의 편집 요청을 구조화된 JSON으로 변환하세요.

                반드시 JSON만 반환하세요.
                """;

        String userPrompt = String.format("""
                userEditText: %s
                currentImagePrompt: %s
                """, userEditText, currentImagePrompt);

        return callOpenAI(systemPrompt, userPrompt);
    }

    // ──────────────────────────────────────────
    // Fallback 메서드들 (OpenAI 호출 실패 시)
    // ──────────────────────────────────────────

    public String generateFallbackResponse(String sceneSummary, String userRequest) {
        log.warn("OpenAI fallback rule-based response 사용");

        String mood = "warm cozy";
        String lighting = "soft warm lighting";
        String camera = "medium shot";
        String action = "the hamster moves naturally through the scene";

        String lowerRequest = userRequest == null ? "" : userRequest.toLowerCase();

        if (lowerRequest.contains("어둡") || lowerRequest.contains("밤") || lowerRequest.contains("차분")) {
            mood = "calm dark mood";
            lighting = "low key warm lighting";
        } else if (lowerRequest.contains("밝") || lowerRequest.contains("화사") || lowerRequest.contains("따뜻")) {
            mood = "bright warm mood";
            lighting = "bright warm natural lighting";
        }

        if (lowerRequest.contains("가깝") || lowerRequest.contains("클로즈")) {
            camera = "close-up shot";
        } else if (lowerRequest.contains("멀") || lowerRequest.contains("와이드")) {
            camera = "wide shot";
        }

        if (lowerRequest.contains("옷") || lowerRequest.contains("변신")) {
            action = "the hamster changes clothes and reacts excitedly";
        }

        return String.format("""
                {
                  "action": "%s",
                  "pose": "natural pose",
                  "camera": "%s",
                  "cameraMotion": "slow push in",
                  "lighting": "%s",
                  "mood": "%s",
                  "timeOfDay": "afternoon",
                  "effects": "soft glow",
                  "backgroundCharacters": "",
                  "environmentDetail": "%s"
                }
                """, action, camera, lighting, mood, sceneSummary != null ? sceneSummary : "");
    }

    public String generateImageEditFallbackResponse(String userEditText, String imagePrompt) {
        log.warn("AI 이미지 편집 제안 fallback 사용");

        String lowerRequest = userEditText == null ? "" : userEditText.toLowerCase();
        String editType = "none";
        String suggestion = "no-op";

        if (lowerRequest.contains("밝기") || lowerRequest.contains("밝게")) {
            editType = "brightness";
            suggestion = "brightness +15";
        } else if (lowerRequest.contains("어둡게")) {
            editType = "brightness";
            suggestion = "brightness -15";
        }

        if (lowerRequest.contains("따뜻") || lowerRequest.contains("웜톤")) {
            suggestion = suggestion + ", warm tone";
        } else if (lowerRequest.contains("차갑") || lowerRequest.contains("쿨톤")) {
            suggestion = suggestion + ", cool tone";
        }

        return String.format("""
                {
                  "editType": "%s",
                  "suggestion": "%s",
                  "editSuggestions": {
                    "editType": "%s",
                    "value": "%s"
                  }
                }
                """, editType, suggestion, editType, suggestion);
    }

    // ──────────────────────────────────────────
    // OpenAI API 공통 호출
    // ──────────────────────────────────────────

    public String callOpenAI(String systemPrompt, String userPrompt) {
        try {
            log.info("Calling OpenAI API - systemPrompt.length={}, userPrompt.length={}",
                    systemPrompt.length(), userPrompt.length());

            OpenAIRequest request = OpenAIRequest.builder()
                    .model("gpt-4o-mini")
                    .messages(List.of(
                            java.util.Map.of("role", "system", "content", systemPrompt),
                            java.util.Map.of("role", "user", "content", userPrompt)
                    ))
                    .temperature(0.7)
                    .max_tokens(4000)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(openAiApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            String fullUrl = openaiApiUrl + "/chat/completions";
            log.info("OpenAI URL: {}", fullUrl);

            HttpEntity<OpenAIRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<OpenAIResponse> response = openAiRestTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    entity,
                    OpenAIResponse.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("OpenAI API failed - status: {}", response.getStatusCode());
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }

            OpenAIResponse openAIResponse = response.getBody();
            if (openAIResponse.getChoices() == null || openAIResponse.getChoices().isEmpty()) {
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }

            String content = openAIResponse.getChoices().get(0).getMessage().getContent();
            if (content == null || content.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }

            return content.trim();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call OpenAI API", e);
            throw new BusinessException(ErrorCode.LLM_SERVICE_ERROR);
        }
    }
}