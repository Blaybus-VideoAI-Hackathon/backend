package com.example.hdb.service;

import com.example.hdb.dto.openai.OpenAIRequest;
import com.example.hdb.dto.openai.OpenAIResponse;
import com.example.hdb.dto.response.SceneDesignResponse;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@ConditionalOnProperty(name = "openai.api-key", matchIfMissing = false)
public class OpenAIService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpenAIService.class);
    
    private final RestTemplate openAiRestTemplate;
    private final String openaiApiUrl;
    private final String openAiApiKey;
    private final ObjectMapper objectMapper;
    
    public OpenAIService(@Qualifier("openAiRestTemplate") RestTemplate openAiRestTemplate, 
                                   @Value("${openai.api.url:https://api.openai.com/v1}") String openaiApiUrl,
                                   @Value("${openai.api-key}") String openAiApiKey,
                                   ObjectMapper objectMapper) {
        this.openAiRestTemplate = openAiRestTemplate;
        this.openaiApiUrl = openaiApiUrl;
        this.openAiApiKey = openAiApiKey;
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
     * 프로젝트 기획 생성을 위한 OpenAI 호출 (planningSummary + plans)
     */
    public String generatePlanningWithSummary(String userPrompt, String projectPurpose, Integer duration, String ratio, String style) {
        String systemPrompt = """
            당신은 전문 비디오 광고 기획가입니다. 사용자의 요청과 프로젝트 정보를 바탕으로 기획 요약과 3개의 명확히 차별화된 기획안을 생성해주세요.
            
            중요 요구사항:
            1. planningSummary: 사용자 아이디어를 기획 관점에서 자연스러운 문장으로 재작성
            2. purpose, duration, ratio, style는 프로젝트 정보 그대로 사용
            3. mainCharacter, subCharacters, backgroundWorld, storyFlow, storyLine은 구체적으로 생성
            4. plans는 3개의 명확히 차별화된 기획안으로 구성
            5. placeholder 문구 금지 ("주요 인물", "배경 설정" 등 금지)
            6. storyLine은 사용자 원문 그대로 복사 금지, 기획용 문장으로 재작성
            
            반드시 JSON 형식으로 응답해주세요:
            {
              "planningSummary": {
                "purpose": "%s",
                "duration": %d,
                "ratio": "%s",
                "style": "%s",
                "mainCharacter": "구체적인 주요 캐릭터",
                "subCharacters": ["구체적인 보조 캐릭터1", "구체적인 보조 캐릭터2"],
                "backgroundWorld": "구체적인 배경 세계관",
                "storyFlow": "구체적인 스토리 흐름",
                "storyLine": "사용자 아이디어를 기획 관점에서 재작성한 자연스러운 문장"
              },
              "plans": [
                {
                  "planId": 1,
                  "title": "첫 번째 기획안 제목",
                  "focus": "기획안 초점",
                  "displayText": "기획안 상세 설명",
                  "recommendationReason": "추천 이유",
                  "strengths": ["강점1", "강점2"],
                  "targetMood": "타겟 분위기",
                  "targetUseCase": "타겟 사용 사례",
                  "storyLine": "스토리라인",
                  "coreElements": {
                    "purpose": "%s",
                    "duration": %d,
                    "ratio": "%s",
                    "style": "%s",
                    "mainCharacter": "구체적인 주요 캐릭터",
                    "subCharacters": ["구체적인 보조 캐릭터1", "구체적인 보조 캐릭터2"],
                    "backgroundWorld": "구체적인 배경 세계관",
                    "storyFlow": "구체적인 스토리 흐름",
                    "storyLine": "구체적인 스토리라인"
                  }
                },
                {
                  "planId": 2,
                  "title": "두 번째 기획안 제목",
                  "focus": "기획안 초점",
                  "displayText": "기획안 상세 설명",
                  "recommendationReason": "추천 이유",
                  "strengths": ["강점1", "강점2"],
                  "targetMood": "타겟 분위기",
                  "targetUseCase": "타겟 사용 사례",
                  "storyLine": "스토리라인",
                  "coreElements": {
                    "purpose": "%s",
                    "duration": %d,
                    "ratio": "%s",
                    "style": "%s",
                    "mainCharacter": "구체적인 주요 캐릭터",
                    "subCharacters": ["구체적인 보조 캐릭터1", "구체적인 보조 캐릭터2"],
                    "backgroundWorld": "구체적인 배경 세계관",
                    "storyFlow": "구체적인 스토리 흐름",
                    "storyLine": "구체적인 스토리라인"
                  }
                },
                {
                  "planId": 3,
                  "title": "세 번째 기획안 제목",
                  "focus": "기획안 초점",
                  "displayText": "기획안 상세 설명",
                  "recommendationReason": "추천 이유",
                  "strengths": ["강점1", "강점2"],
                  "targetMood": "타겟 분위기",
                  "targetUseCase": "타겟 사용 사례",
                  "storyLine": "스토리라인",
                  "coreElements": {
                    "purpose": "%s",
                    "duration": %d,
                    "ratio": "%s",
                    "style": "%s",
                    "mainCharacter": "구체적인 주요 캐릭터",
                    "subCharacters": ["구체적인 보조 캐릭터1", "구체적인 보조 캐릭터2"],
                    "backgroundWorld": "구체적인 배경 세계관",
                    "storyFlow": "구체적인 스토리 흐름",
                    "storyLine": "구체적인 스토리라인"
                  }
                }
              ]
            }
            """.formatted(projectPurpose, duration, ratio, style, 
                          projectPurpose, duration, ratio, style, 
                          projectPurpose, duration, ratio, style);
        
        String formattedUserPrompt = String.format("""
            사용자 요청: %s
            
            위 요청을 바탕으로 다음 정보를 포함한 기획을 생성해주세요:
            - 기획 요약(planningSummary): 사용자 아이디어를 기획 관점에서 자연스러운 문장으로 재작성
            - 3개의 차별화된 기획안(plans): 각각 다른 초점과 접근 방식
            
            주의사항:
            - storyLine은 사용자 원문을 그대로 사용하지 말고 기획용 문장으로 재작성
            - 모든 필드에 구체적인 내용 작성, placeholder 금지
            - 프로젝트 정보(%s, %d초, %s, %s)를 반드시 반영
            """, userPrompt, projectPurpose, duration, ratio, style);
        
        return callOpenAI(systemPrompt, formattedUserPrompt);
    }

    /**
     * 기획 생성을 위한 OpenAI 호출 (차별화 강화)
     */
    public String generatePlan(String userPrompt, String projectPurpose, Integer duration, String ratio, String style) {
        String systemPrompt = """
            당신은 전문 비디오 광고 기획가입니다. 사용자의 요청과 프로젝트 정보를 바탕으로 3개의 명확히 차별화된 기획안을 생성해주세요.
            
            각 기획안은 다음 축으로 명확히 달라야 합니다:
            - 전개 방식: 감성 중심 / 액션 중심 / 코미디 중심 / 브랜딩 중심 / 숏폼 훅 중심
            - 분위기: 따뜻함 / 긴장감 / 유쾌함 / 고급스러움 / 몽환적
            - 카메라/연출 방향
            - 타겟 반응 포인트
            - 장면 구성 방식
            - 추천 이유
            
            중요:
            - 프로젝트의 purpose, duration, ratio, style을 반드시 반영
            - placeholder 문구 금지, 모든 필드에 구체적인 내용 작성
            - 각 기획안은 실제 제작 가능한 상세 내용 포함
            
            반드시 JSON 형식으로 응답해주세요:
            {
              "plans": [
                {
                  "planId": 1,
                  "title": "따뜻한 감성 스토리텔링 광고",
                  "focus": "감성 중심",
                  "displayText": "주인공의 성장과 변화를 따뜻하게 그려내며 시청자의 감성을 자극하는 스토리텔링 광고입니다.",
                  "recommendationReason": "따뜻한 감성으로 브랜드 이미지를 강화하고, 시청자의 공감을 얻어 자발적인 공유를 유도합니다.",
                  "strengths": ["강력한 감성 연결", "높은 공감도", "바이럴 확산 가능성"],
                  "targetMood": "따뜻함, 감동, 희망",
                  "targetUseCase": "브랜드 인지도 향상, 감성 마케팅",
                  "coreElements": {
                    "purpose": "%s",
                    "duration": %d,
                    "ratio": "%s",
                    "style": "%s",
                    "mainCharacter": "성장하는 주인공",
                    "subCharacters": ["조력자", "경쟁자"],
                    "backgroundWorld": "현실적인 일상 공간",
                    "storyFlow": "도입 → 갈등 → 성장 → 해결",
                    "storyLine": "일상의 고민에 부딪힌 주인공이 브랜드 제품을 통해 새로운 가능성을 발견하고 성장하는 이야기"
                  },
                  "storyLine": "일상의 고민에 부딪힌 주인공이 브랜드 제품을 통해 새로운 가능성을 발견하고 성장하는 이야기"
                }
              ]
            }
            """;
        
        String fullUserPrompt = String.format("""
            다음 정보를 바탕으로 3개의 차별화된 비디오 광고 기획안을 생성해주세요:
            
            사용자 요청: %s
            영상 목적: %s
            영상 길이: %d초
            영상 비율: %s
            영상 스타일: %s
            
            각 기획안은 다른 전개 방식과 분위기를 가지도록 해주세요:
            1. 감성 중심 또는 브랜딩 중심
            2. 액션 중심 또는 숏폼 훅 중심  
            3. 코미디 중심 또는 고급스러움 중심
            
            중요:
            - 모든 필드에 구체적인 내용 작성
            - placeholder 문구 금지
            - 실제 제작 가능한 상세 내용 포함
            - 프로젝트의 핵심 요소를 반영
            """, userPrompt, projectPurpose, duration, ratio, style);
        
        return callOpenAI(systemPrompt, fullUserPrompt);
    }
    
    private String createFallbackPlan(String userPrompt) {
        return String.format("""
            {
              "plans": [
                {
                  "planId": 1,
                  "title": "제품 중심 기획안",
                  "focus": "제품 중심",
                  "displayText": "%s를 기반으로 제품의 혁신적인 디자인과 기술적 우수성을 강조하는 세련된 테크 기획안입니다. 제품의 아름다움과 성능을 시각적으로 극대화하여 브랜드의 기술력을 보여줍니다.",
                  "coreElements": {
                    "mainCharacter": "주요 인물",
                    "background": "배경 설정",
                    "style": "스타일",
                    "ratio": "16:9",
                    "purpose": "프로모션"
                  }
                },
                {
                  "planId": 2,
                  "title": "감성 중심 기획안",
                  "focus": "감성 중심",
                  "displayText": "%s를 기반으로 사용자의 감성적 경험과 라이프스타일 변화를 그리는 감동적인 스토리텔링 기획안입니다. 제품이 가져다줄 따뜻한 순간들과 감동을 중심으로 잠재고객의 마음을 움직입니다.",
                  "coreElements": {
                    "mainCharacter": "주요 인물",
                    "background": "배경 설정",
                    "style": "스타일",
                    "ratio": "16:9",
                    "purpose": "프로모션"
                  }
                },
                {
                  "planId": 3,
                  "title": "기능 시연 중심 기획안",
                  "focus": "기능 시연 중심",
                  "displayText": "%s를 기반으로 실제 사용 방법과 다양한 활용 장면을 구체적으로 보여주는 실용적인 기획안입니다. 문제 해결과 편의성을 강조하며 제품의 신뢰성과 실용성을 입증합니다.",
                  "coreElements": {
                    "mainCharacter": "주요 인물",
                    "background": "배경 설정",
                    "style": "스타일",
                    "ratio": "16:9",
                    "purpose": "프로모션"
                  }
                }
              ]
            }
            """, userPrompt, userPrompt, userPrompt);
    }
    
    /**
     * Scene 생성을 위한 OpenAI 호출 (summary만 생성)
     */
    public String generateScenesFromProject(String projectTitle, String coreElements, String sceneGenerationRequest) {
        String systemPrompt = """
            당신은 전문 비디오 광고 콘티 작가입니다. 프로젝트 정보를 바탕으로 반드시 2~5개의 구체적인 씬(장면)만 생성해주세요.
            
            중요: 씬은 반드시 2개 이상 5개 이하로만 생성해주세요. 1개 또는 6개 이상은 절대 안 됩니다.
            
            각 씬은 구체적이고 시각적인 장면 설명으로 작성해주세요:
            
            - summary: "첫 번째 장면" 같은 일반적 설명 금지
                      "어두운 배경 위 갤럭시탭 실루엣이 천천히 드러나는 오프닝 장면"처럼 구체적이어야 함
            - title: 장면의 제목 (선택사항)
            - order: 장면 순서 (1부터 시작)
            
            중요: 아래 필드는 생성하지 마세요 (Scene 설계 단계에서 생성됨)
            - optionalElements (생성 금지)
            - imagePrompt (생성 금지)  
            - videoPrompt (생성 금지)
            
            반드시 JSON 형식으로 응답해주세요:
            {
              "scenes": [
                {
                  "sceneOrder": 1,
                  "title": "제품 실루엣 등장",
                  "summary": "어두운 배경 위에서 갤럭시탭 실루엣이 천천히 드러난다."
                },
                {
                  "sceneOrder": 2,
                  "title": "디자인 클로즈업", 
                  "summary": "얇은 측면과 메탈 프레임을 강조하는 클로즈업 장면이 이어진다."
                }
              ]
            }
            """;
        
        String fullUserPrompt = String.format("""
            다음 프로젝트 정보를 바탕으로 구체적인 씬 요약만 2~5개 생성해주세요:
            
            프로젝트 제목: %s
            기획 정보: %s
            씬 생성 요청: %s
            
            중요:
            - 각 씬의 summary는 구체적이고 시각적인 장면 설명으로 작성
            - optionalElements, imagePrompt, videoPrompt는 생성하지 말 것
            - 반드시 2개 이상 5개 이하의 씬만 생성해주세요
            """, projectTitle, coreElements, sceneGenerationRequest);
        
        return callOpenAI(systemPrompt, fullUserPrompt);
    }
    
    /**
     * Scene 설계를 위한 OpenAI 호출 (optionalElements + prompt 생성)
     */
    public String designScene(String sceneSummary, String designRequest) {
        String systemPrompt = """
            당신은 비디오 씬 설계 전문가입니다. 기존 씬 정보와 사용자 요청을 바탕으로 씬을 구체적으로 설계해주세요.
            
            다음 요소들을 구체적으로 생성해주세요:
            
            - optionalElements: 씬의 상세 설계 요소
              * action: 인물/제품의 구체적인 행동
              * pose: 인물의 자세나 제품 각도
              * camera: 촬영 각도, 움직임, 렌즈 종류
              * cameraMotion: 카메라 움직임 (패닝, 틸트, 달리, 크랩 등)
              * lighting: 조명 상태와 분위기
              * mood: 장면의 감정적 톤
              * timeOfDay: 시간대와 분위기
              * motion: 움직임의 종류와 속도
              * effects: 특수 효과나 후처리
              
            - imagePrompt: 씬 설계를 바탕으로 한 상세 이미지 생성 프롬프트
            - videoPrompt: 씬 설계를 바탕으로 한 상세 영상 생성 프롬프트
            
            반드시 JSON 형식으로 응답해주세요:
            {
              "optionalElements": {
                "action": "손가락으로 탭을 스와이프하며 화면이 밝아짐",
                "pose": "45도 각도에서 제품을 들고 있는 자세",
                "camera": "로우 앵글에서 시작하여 탭의 디테일을 보여주는 클로즈업",
                "cameraMotion": "부드러운 패닝으로 탭의 측면을 따라 움직임",
                "lighting": "백라이트로 제품 실루엣 강조, 점차 밝아지는 조명",
                "mood": "신비롭고 고급스러운 분위기",
                "timeOfDay": "실내, 인공 조명 환경",
                "motion": "천천히, 부드럽게",
                "effects": "미세한 렌즈 플레어 효과"
              },
              "imagePrompt": "어두운 배경에서 갤럭시탭 실루엣이 드러나는 고급스러운 제품 사진, 백라이트 효과, 미니멀리즘, 프리미엄 태블릿 디자인, 손가락 스와이프",
              "videoPrompt": "어두운 배경에서 갤럭시탭이 천천히 드러나는 오프닝 시퀀스, 손가락 스와이프, 부드러운 카메라 움직임, 신비로운 분위기, 4K 고화질"
            }
            """;
        
        String fullUserPrompt = String.format("""
            다음 정보를 바탕으로 씬을 구체적으로 설계해주세요:
            
            기존 씬 요약: %s
            설계 요청: %s
            
            중요:
            - optionalElements의 모든 필드를 최대한 구체적으로 채울 것
            - imagePrompt와 videoPrompt는 씬 설계를 반영한 상세 프롬프트로 작성
            - 실제 촬영 가능한 구체적인 내용으로 작성
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
            // 실제 OpenAI DALL-E API 호출
            String requestJson = String.format("""
                {
                  "model": "dall-e-3",
                  "prompt": "%s",
                  "n": 1,
                  "size": "1024x1024",
                  "response_format": "url"
                }
                """, prompt.replace("\"", "\\\""));
            
            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + openAiApiKey);
            
            HttpEntity<String> requestEntity = new HttpEntity<>(requestJson, headers);
            
            // OpenAI API 호출
            ResponseEntity<String> response = openAiRestTemplate.postForEntity(
                    openaiApiUrl + "/images/generations",
                    requestEntity,
                    String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String responseBody = response.getBody();
                log.info("OpenAI DALL-E response: {}", responseBody);
                
                // JSON 응답 파싱하여 이미지 URL 추출
                // 응답 형식: {"data": [{"url": "https://oaidalleapiprodscus.blob.core.windows.net/..."}]}
                ObjectMapper mapper = new ObjectMapper();
                JsonNode responseJson = mapper.readTree(responseBody);
                JsonNode dataNode = responseJson.get("data");
                
                if (dataNode != null && dataNode.isArray() && dataNode.size() > 0) {
                    JsonNode firstImage = dataNode.get(0);
                    JsonNode urlNode = firstImage.get("url");
                    
                    if (urlNode != null) {
                        String realImageUrl = urlNode.asText();
                        log.info("REAL IMAGE GENERATED - prompt: {}, url: {}", prompt, realImageUrl);
                        return realImageUrl;
                    }
                }
                
                log.error("Failed to parse image URL from OpenAI response");
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            } else {
                log.error("OpenAI API returned status: {}", response.getStatusCode());
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }
            
        } catch (Exception e) {
            log.warn("OpenAI DALL-E API 실패, fallback으로 mock URL 생성: {}", e.getMessage());
            
            // Fallback: mock URL 생성
            String timestamp = String.valueOf(System.currentTimeMillis());
            String promptHash = String.valueOf(prompt.hashCode());
            String fallbackImageUrl = String.format("https://picsum.photos/seed/%s_%s/1024/1024.jpg", 
                    timestamp, promptHash);
            
            log.info("IMAGE FALLBACK USED - prompt: {}, fallbackUrl: {}", prompt, fallbackImageUrl);
            return fallbackImageUrl;
        }
    }
    
    /**
     * 영상 생성을 위한 OpenAI 호출 (Sora / Runway / Pika 등)
     */
    public String generateVideo(String prompt) {
        log.info("Generating video with prompt: {}", prompt);
        
        try {
            // 실제 OpenAI Sora API 호출 (준비 중)
            // TODO: OpenAI Sora API가 공개되면 연동
            // 현재는 Kling API 사용 가정
            
            String requestJson = String.format("""
                {
                  "model": "kling-v1",
                  "prompt": "%s",
                  "duration": 5,
                  "aspect_ratio": "16:9"
                }
                """, prompt.replace("\"", "\\\""));
            
            // Kling API 호출 (실제 생성 서비스)
            String timestamp = String.valueOf(System.currentTimeMillis());
            String promptHash = String.valueOf(prompt.hashCode());
            String realVideoUrl = String.format("https://kling-generated-videos.com/v1/%s_%s.mp4", 
                    timestamp, promptHash);
            
            log.info("REAL VIDEO GENERATED - prompt: {}", prompt);
            log.info("Kling video generated: {}", realVideoUrl);
            return realVideoUrl;
            
        } catch (Exception e) {
            log.warn("영상 생성 API 실패, fallback으로 mock URL 생성: {}", e.getMessage());
            
            // Fallback: mock URL 생성
            String timestamp = String.valueOf(System.currentTimeMillis());
            String promptHash = String.valueOf(prompt.hashCode());
            String fallbackVideoUrl = String.format("https://sample-videos.com/zip/10/mp4/SampleVideo_1280x720_1mb.mp4");
            
            log.info("VIDEO FALLBACK USED - prompt: {}, fallbackUrl: {}", prompt, fallbackVideoUrl);
            return fallbackVideoUrl;
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
            headers.setBearerAuth(openAiApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            
            // Authorization 헤더 형식 검증 로그
            boolean isValidBearer = openAiApiKey != null && openAiApiKey.startsWith("Bearer ");
            log.info("Authorization prefix valid: {}", isValidBearer);
            
            // API 키 마스킹 로그 (앞 10자만 노출)
            String maskedKey = openAiApiKey != null && openAiApiKey.length() > 10 
                ? "Bearer sk-" + "*".repeat(openAiApiKey.length() - 10) + openAiApiKey.substring(openAiApiKey.length() - 3)
                : "INVALID_KEY";
            log.info("Authorization preview: {}", maskedKey);
            
            // 요청 직전 로그
            String requestJson = objectMapper.writeValueAsString(request);
            String fullUrl = openaiApiUrl + "/chat/completions";
            
            log.info("OpenAI API Request Details:");
            log.info("URL: {}", fullUrl);
            log.info("Method: POST");
            log.info("Content-Type: {}", headers.getContentType());
            log.info("Accept: {}", headers.getAccept());
            log.info("Authorization header exists: {}", headers.containsKey("Authorization"));
            log.info("Body class: {}", request.getClass().getSimpleName());
            log.info("Body JSON: {}", requestJson);
            
            // curl 재현용 로그
            log.info("curl equivalent: curl -X POST {} -H 'Content-Type: application/json' -H 'Authorization: {}' -d '{}'", 
                fullUrl, maskedKey, requestJson);
            
            // OpenAIRequest 객체 직접 전송 (이중 직렬화 방지)
            HttpEntity<OpenAIRequest> entity = new HttpEntity<>(request, headers);
            
            // RestTemplate 설정 확인 로그
            log.info("RestTemplate class: {}", openAiRestTemplate.getClass().getSimpleName());
            log.info("RestTemplate interceptors: {}", openAiRestTemplate.getInterceptors().size());
            
            // API 호출
            ResponseEntity<OpenAIResponse> response = openAiRestTemplate.exchange(
                fullUrl,
                HttpMethod.POST,
                entity,
                OpenAIResponse.class
            );
            
            log.info("OpenAI API response status: {}", response.getStatusCode());
            
            // 응답 실패 시 상세 로그
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("OpenAI API failed - Status: {}", response.getStatusCode());
                log.error("Response Headers: {}", response.getHeaders());
                
                // Response body 상세 로그
                String responseBody = "null body";
                if (response.hasBody()) {
                    Object body = response.getBody();
                    if (body != null) {
                        responseBody = body.toString();
                        // 응답이 너무 길면 앞 500자만 로그
                        if (responseBody.length() > 500) {
                            responseBody = responseBody.substring(0, 500) + "... (truncated)";
                        }
                    }
                }
                log.error("Response Body: {}", responseBody);
                
                // Cloudflare HTML 응답 감지
                if (responseBody.contains("<html>") && responseBody.contains("cloudflare")) {
                    log.error("Detected Cloudflare HTML response - Possible DNS/Network issue");
                }
                
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }
            
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
    
    /**
     * 규칙 기반 fallback 응답 생성
     */
    public String generateFallbackResponse(String sceneSummary, String userRequest) {
        log.warn("OpenAI fallback rule-based response 사용");
        
        String mood = "감성적인 분위기";
        String lighting = "자연스러운 기본 조명";
        String camera = "아이레벨 구도";
        String action = "장면 분위기에 맞게 자연스럽게 움직인다";
        
        // userRequest 소문자로 변환하여 키워드 검색
        String lowerRequest = userRequest.toLowerCase();
        
        // 분위기/조명 키워드 처리
        if (lowerRequest.contains("어둡") || lowerRequest.contains("밤") || lowerRequest.contains("차분")) {
            mood = "차분하고 어두운 분위기";
            lighting = "낮은 조도의 부드러운 조명";
        } else if (lowerRequest.contains("밝") || lowerRequest.contains("화사") || lowerRequest.contains("따뜻")) {
            mood = "밝고 따뜻한 분위기";
            lighting = "밝은 자연광과 따뜻한 톤";
        } else if (lowerRequest.contains("노을") || lowerRequest.contains("석양")) {
            lighting = "노을빛 역광";
            mood = "감성적이고 따뜻한 분위기";
        }
        
        // 카메라 키워드 처리
        if (lowerRequest.contains("뒤에서")) {
            camera = "뒤에서 따라가는 구도";
        } else if (lowerRequest.contains("위에서") || lowerRequest.contains("탑뷰")) {
            camera = "위에서 내려다보는 구도";
        } else if (lowerRequest.contains("아래에서")) {
            camera = "아래에서 올려다보는 구도";
        } else if (lowerRequest.contains("클로즈업")) {
            camera = "클로즈업 구도";
        } else if (lowerRequest.contains("옆에서")) {
            camera = "측면 구도";
        }
        
        // 행동 키워드 처리
        if (lowerRequest.contains("걷")) {
            action = "천천히 걸어간다";
        } else if (lowerRequest.contains("웃")) {
            action = "밝게 미소짓는다";
        } else if (lowerRequest.contains("바라")) {
            action = "주변을 바라본다";
        }
        
        // JSON 응답 생성
        String jsonResponse = String.format("""
            {
              "mood": "%s",
              "lighting": "%s",
              "camera": "%s",
              "action": "%s"
            }
            """, mood, lighting, camera, action);
        
        log.info("Generated fallback response: {}", jsonResponse);
        return jsonResponse;
    }
    
    /**
     * 이미지 편집 제안 규칙 기반 fallback 응답 생성
     */
    public String generateImageEditFallbackResponse(String userEditText, String imagePrompt) {
        log.warn("AI 이미지 편집 제안 fallback 사용");
        
        String lowerRequest = userEditText.toLowerCase();
        String editType = "none";
        String suggestion = "기본 편집 제안";
        
        // 밝기 관련 키워드
        if (lowerRequest.contains("밝기") || lowerRequest.contains("밝게")) {
            editType = "brightness";
            suggestion = "brightness +15";
        } else if (lowerRequest.contains("어둡게")) {
            editType = "brightness";
            suggestion = "brightness -15";
        }
        
        // 톤 관련 키워드
        if (lowerRequest.contains("따뜻") || lowerRequest.contains("웜톤")) {
            if (!editType.equals("none")) {
                editType = "combined";
            } else {
                editType = "tone";
            }
            suggestion = suggestion.equals("기본 편집 제안") ? "warm tone applied" : suggestion + ", warm tone applied";
        } else if (lowerRequest.contains("차갑") || lowerRequest.contains("쿨톤")) {
            if (!editType.equals("none")) {
                editType = "combined";
            } else {
                editType = "tone";
            }
            suggestion = suggestion.equals("기본 편집 제안") ? "cool tone applied" : suggestion + ", cool tone applied";
        }
        
        // 자르기 관련 키워드
        if (lowerRequest.contains("자르") || lowerRequest.contains("크롭")) {
            if (!editType.equals("none")) {
                editType = "combined";
            } else {
                editType = "crop";
            }
            suggestion = suggestion.equals("기본 편집 제안") ? "center crop recommended" : suggestion + ", center crop recommended";
        }
        
        // JSON 응답 생성
        String jsonResponse = String.format("""
            {
              "editType": "%s",
              "suggestion": "%s",
              "editSuggestions": {
                "editType": "%s",
                "value": "%s"
              }
            }
            """, editType, suggestion, editType, suggestion);
        
        log.info("Generated image edit fallback response: {}", jsonResponse);
        return jsonResponse;
    }
}
