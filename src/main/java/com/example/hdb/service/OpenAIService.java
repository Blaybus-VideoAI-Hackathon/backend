package com.example.hdb.service;

import com.example.hdb.dto.openai.OpenAIRequest;
import com.example.hdb.dto.openai.OpenAIResponse;
import com.example.hdb.dto.response.SceneDesignResponse;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@ConditionalOnProperty(name = "openai.api-key", matchIfMissing = false)
public class OpenAIService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpenAIService.class);
    
    private final RestTemplate openAiRestTemplate;
    private final String openaiApiUrl;
    private final ObjectMapper objectMapper;
    
    public OpenAIService(RestTemplate openAiRestTemplate, 
                                   @Value("${openai.api.url:https://api.openai.com/v1}") String openaiApiUrl,
                                   ObjectMapper objectMapper) {
        this.openAiRestTemplate = openAiRestTemplate;
        this.openaiApiUrl = openaiApiUrl;
        this.objectMapper = objectMapper;
    }
    
    public String generateIdea(String coreElements, String style, String ratio) {
        String systemPrompt = "당신은 창의적인 비디오 콘텐츠 기획자입니다. 사용자의 핵심 요소를 바탕으로 흥미로운 비디오 아이디어를 생성해주세요.";
        
        String userPrompt = String.format("""
            다음 정보를 바탕으로 짧은 비디오 콘텐츠 아이디어를 생성해주세요:
            
            핵심 요소: %s
            스타일: %s
            화면 비율: %s
            
            응답은 JSON 형식으로 다음과 같이 제공해주세요:
            {
              "idea": "생성된 아이디어"
            }
            """, coreElements, style, ratio);
        
        return callOpenAI(systemPrompt, userPrompt);
    }
    
    public String generateScenes(String idea, int sceneCount) {
        String systemPrompt = "당신은 비디오 콘텐츠 구성 전문가입니다. 아이디어를 바탕으로 구체적인 씬(장면) 목록을 생성해주세요.";
        
        String userPrompt = String.format("""
            다음 비디오 아이디어를 바탕으로 %d개의 씬(장면)을 생성해주세요:
            
            아이디어: %s
            
            각 씬은 다음 형식의 JSON 배열로 제공해주세요:
            [
              {
                "sceneOrder": 1,
                "summary": "씬 요약",
                "optionalElements": "선택적 요소",
                "imagePrompt": "이미지 생성 프롬프트",
                "videoPrompt": "비디오 생성 프롬프트"
              },
              ...
            ]
            """, idea, sceneCount);
        
        return callOpenAI(systemPrompt, userPrompt);
    }
    
    // ========== 신규 메서드 (C단계 GPT 연동) ==========
    
    /**
     * 기획 생성을 위한 OpenAI 호출
     */
    public String generatePlan(String userPrompt) {
        String systemPrompt = """
            당신은 창의적인 비디오 콘텐츠 기획자입니다. 사용자의 요청을 바탕으로 흥미로운 비디오 기획을 생성해주세요.
            
            반드시 JSON 형식으로 응답해주세요:
            {
              "title": "기획 제목",
              "coreElements": {
                "mainCharacter": "주요 캐릭터",
                "background": "배경 설정",
                "mood": "분위기",
                "style": "스타일",
                "storyFlow": "이야기 흐름"
              }
            }
            """;
        
        String fullUserPrompt = String.format("""
            다음 사용자 요청을 바탕으로 비디오 기획을 생성해주세요:
            
            사용자 요청: %s
            
            위 요청에 맞는 창의적인 비디오 기획을 생성해주세요.
            """, userPrompt);
        
        return callOpenAI(systemPrompt, fullUserPrompt);
    }
    
    /**
     * Scene 생성을 위한 OpenAI 호출
     */
    public String generateScenesFromProject(String projectTitle, String coreElements, String sceneGenerationRequest) {
        String systemPrompt = """
            당신은 비디오 콘텐츠 구성 전문가입니다. 프로젝트 정보를 바탕으로 반드시 2~5개의 씬(장면)만 생성해주세요.
            
            중요: 씬은 반드시 2개 이상 5개 이하로만 생성해주세요. 1개 또는 6개 이상은 절대 안 됩니다.
            
            반드시 JSON 형식으로 응답해주세요:
            {
              "scenes": [
                {
                  "sceneOrder": 1,
                  "summary": "씬 요약",
                  "optionalElements": {
                    "action": "행동",
                    "mood": "분위기",
                    "camera": "카메라 각도"
                  },
                  "imagePrompt": "이미지 생성 프롬프트",
                  "videoPrompt": "비디오 생성 프롬프트"
                }
              ]
            }
            """;
        
        String fullUserPrompt = String.format("""
            다음 프로젝트 정보를 바탕으로 반드시 2~5개의 씬(장면)만 생성해주세요:
            
            프로젝트 제목: %s
            핵심 요소: %s
            씬 생성 요청: %s
            
            중요: 반드시 2개 이상 5개 이하의 씬만 생성해주세요.
            """, projectTitle, coreElements, sceneGenerationRequest);
        
        return callOpenAI(systemPrompt, fullUserPrompt);
    }
    
    /**
     * Scene 설계를 위한 OpenAI 호출
     */
    public String designScene(String sceneSummary, String designRequest) {
        String systemPrompt = """
            당신은 비디오 씬 설계 전문가입니다. 기존 씬 정보와 사용자 요청을 바탕으로 씬을 재설계해주세요.
            
            반드시 JSON 형식으로 응답해주세요:
            {
              "optionalElements": {
                "action": "행동",
                "mood": "분위기",
                "camera": "카메라 각도",
                "lighting": "조명"
              },
              "imagePrompt": "이미지 생성 프롬프트",
              "videoPrompt": "비디오 생성 프롬프트"
            }
            """;
        
        String fullUserPrompt = String.format("""
            다음 정보를 바탕으로 씬을 설계해주세요:
            
            기존 씬 요약: %s
            설계 요청: %s
            
            위 요청에 맞게 씬을 재설계해주세요.
            """, sceneSummary, designRequest);
        
        return callOpenAI(systemPrompt, fullUserPrompt);
    }
    
    /**
     * Scene 수정을 위한 OpenAI 호출
     */
    public String editScene(String sceneSummary, String optionalElements, String imagePrompt, String videoPrompt, String editRequest) {
        String systemPrompt = """
            당신은 비디오 씬 수정 전문가입니다. 기존 씬 정보와 사용자 수정 요청을 바탕으로 씬을 수정해주세요.
            
            반드시 JSON 형식으로 응답해주세요:
            {
              "optionalElements": {
                "action": "행동",
                "mood": "분위기",
                "camera": "카메라 각도",
                "lighting": "조명"
              },
              "imagePrompt": "이미지 생성 프롬프트",
              "videoPrompt": "비디오 생성 프롬프트"
            }
            """;
        
        String fullUserPrompt = String.format("""
            다음 정보를 바탕으로 씬을 수정해주세요:
            
            기존 씬 요약: %s
            기존 선택 요소: %s
            기존 이미지 프롬프트: %s
            기존 영상 프롬프트: %s
            수정 요청: %s
            
            위 요청에 맞게 씬을 수정해주세요.
            """, sceneSummary, optionalElements, imagePrompt, videoPrompt, editRequest);
        
        return callOpenAI(systemPrompt, fullUserPrompt);
    }
    
    /**
     * 이미지 생성을 위한 OpenAI 호출 (DALL-E)
     */
    public String generateImage(String prompt) {
        log.info("Generating image with prompt: {}", prompt);
        
        try {
            // TODO: 실제 OpenAI DALL-E API 연동
            // 현재는 stub으로 기본 URL 반환
            String stubImageUrl = "https://picsum.photos/seed/" + prompt.hashCode() + "/512/512.jpg";
            
            log.info("Stub image generated: {}", stubImageUrl);
            return stubImageUrl;
            
        } catch (Exception e) {
            log.error("Failed to generate image", e);
            throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
        }
    }
    
    /**
     * 영상 생성을 위한 OpenAI 호출 (Sora / Runway / Pika 등)
     */
    public String generateVideo(String prompt) {
        log.info("Generating video with prompt: {}", prompt);
        
        try {
            // TODO: 실제 영상 생성 API 연동 (OpenAI Sora, Runway, Pika 등)
            // 현재는 stub으로 기본 URL 반환
            String stubVideoUrl = "https://sample-videos.com/zip/10/mp4/SampleVideo_1280x720_1mb.mp4";
            
            log.info("Stub video generated: {}", stubVideoUrl);
            return stubVideoUrl;
            
        } catch (Exception e) {
            log.error("Failed to generate video", e);
            throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
        }
    }
    
    public String generateSceneDesignWithVariation(String summary) {
        String systemPrompt = "당신은 비디오 씬 설계 전문가입니다. 동일한 씬에 대해 다양한 버전의 설계를 제공해주세요.";
        
        String userPrompt = String.format("""
            다음 씬 요약에 대해 새로운 버전의 설계를 생성해주세요:
            
            씬 요약: %s
            
            기존과 다른 버전으로 아래 요소들을 새롭게 구성해주세요:
            - 행동 (action)
            - 포즈 (pose)
            - 구도/카메라 (camera)
            - 조명 (lighting)
            - 무드 (mood)
            - 시간대 (timeOfDay)
            
            응답은 JSON 형식으로 다음과 같이 제공해주세요:
            {
              "displayText": "행동: 주요 인물이 장면을 탐색함\\n포즈: 자연스러운 입장 자세\\n구도: 중간 거리 쇼트\\n조명: 장면에 맞는 조명\\n무드: 호기심 또는 기대감\\n시간대: 상황에 적합한 시간",
              "imagePrompt": "Character entering the scene with natural posture, medium shot, appropriate lighting, curious or expectant mood, suitable time of day",
              "videoPrompt": "Scene showing character entering with natural movement, medium distance view, proper lighting, atmosphere matching the scene context"
            }
            """, summary);
        
        String jsonResponse = callOpenAI(systemPrompt, userPrompt);
        
        try {
            // JSON 응답을 SceneDesignResponse로 파싱
            SceneDesignResponse response = objectMapper.readValue(jsonResponse, SceneDesignResponse.class);
            log.info("Successfully parsed scene design response: {}", response.getDisplayText());
            return jsonResponse;
        } catch (Exception e) {
            log.error("Failed to parse scene design response: {}", jsonResponse, e);
            // 파싱 실패 시 원본 JSON 반환
            return jsonResponse;
        }
    }
    
    public String generateImageEditSuggestions(String userEditText, String currentImagePrompt) {
        String systemPrompt = "당신은 이미지 편집 전문가입니다. 사용자의 자연어 요청을 구조화된 편집 파라미터로 변환해주세요.";
        
        String userPrompt = String.format("""
            사용자가 다음과 같이 이미지 편집을 요청했습니다:
            
            사용자 요청: %s
            현재 이미지 프롬프트: %s
            
            사용자 요청을 분석하여 아래 형식으로 편집 파라미터를 추천해주세요:
            
            1. 밝기 조절 요청: brightness 값 (0-100)
            2. 톤/분위기 변경: tone 값 (warm, cool, neutral, vivid, soft)
            3. 대비 조절: contrast 값 (-50 ~ +50)
            4. 강조 대상: emphasis (person, background, object)
            5. 수정된 프롬프트: updatedPrompt (이미지 재생성용)
            
            응답은 JSON 형식으로 제공해주세요:
            {
              "analysis": "사용자 요청 분석",
              "editSuggestions": {
                "brightness": 15,
                "tone": "warm",
                "contrast": 5,
                "emphasis": "main_subject"
              },
              "updatedPrompt": "Main subject with warm lighting and enhanced brightness"
            }
            """, userEditText, currentImagePrompt);
        
        return callOpenAI(systemPrompt, userPrompt);
    }
    
    private String callOpenAI(String systemPrompt, String userPrompt) {
        try {
            log.info("Calling OpenAI API - systemPrompt length: {}, userPrompt length: {}", 
                    systemPrompt.length(), userPrompt.length());
            log.debug("System prompt: {}", systemPrompt);
            log.debug("User prompt: {}", userPrompt);
            
            // OpenAIRequest DTO 사용
            OpenAIRequest request = OpenAIRequest.builder()
                    .model("gpt-4o-mini")
                    .messages(java.util.List.of(
                        java.util.Map.of("role", "system", "content", systemPrompt),
                        java.util.Map.of("role", "user", "content", userPrompt)
                    ))
                    .temperature(0.7)
                    .max_tokens(2000)
                    .build();
            
            log.info("OpenAI request prepared: model={}, messages={}", request.getModel(), request.getMessages().size());
            
            // HTTP 요청 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<OpenAIRequest> entity = new HttpEntity<>(request, headers);
            
            // 실제 전송될 JSON 로깅
            try {
                String actualRequestJson = objectMapper.writeValueAsString(request);
                log.info("OpenAI actual request json: {}", actualRequestJson);
            } catch (Exception e) {
                log.warn("Failed to serialize OpenAI request for logging", e);
            }
            
            // API 호출
            ResponseEntity<OpenAIResponse> response = openAiRestTemplate.exchange(
                openaiApiUrl + "/chat/completions",
                HttpMethod.POST,
                entity,
                OpenAIResponse.class
            );
            
            log.info("OpenAI API response status: {}", response.getStatusCode());
            log.info("OpenAI API response headers: {}", response.getHeaders());
            
            // 응답 처리
            OpenAIResponse openAIResponse = response.getBody();
            
            if (openAIResponse == null) {
                log.error("OpenAI API returned null response body");
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }
            
            log.info("OpenAI response ID: {}, Object: {}", openAIResponse.getId(), openAIResponse.getObject());
            log.debug("OpenAI raw response: {}", openAIResponse);
            
            if (openAIResponse.getChoices() == null || openAIResponse.getChoices().isEmpty()) {
                log.error("OpenAI API returned empty choices. Response: {}", openAIResponse);
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }
            
            String content = openAIResponse.getChoices().get(0).getMessage().getContent();
            
            if (content == null || content.trim().isEmpty()) {
                log.error("OpenAI API returned empty content. Response: {}", openAIResponse);
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }
            
            log.info("OpenAI API call successful - content length: {}", content.length());
            log.debug("OpenAI content: {}", content);
            
            return content.trim();
            
        } catch (BusinessException e) {
            // 이미 BusinessException이면 그대로 전파
            log.error("BusinessException in OpenAI API call: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to call OpenAI API", e);
            throw new BusinessException(ErrorCode.LLM_SERVICE_ERROR);
        }
    }
}
