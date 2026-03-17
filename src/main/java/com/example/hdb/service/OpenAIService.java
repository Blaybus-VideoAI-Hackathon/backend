package com.example.hdb.service;

import com.example.hdb.dto.openai.OpenAIRequest;
import com.example.hdb.dto.openai.OpenAIResponse;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@ConditionalOnProperty(name = "openai.api-key", matchIfMissing = false)
public class OpenAIService {
    
    private final RestTemplate openAiRestTemplate;
    private final String openaiApiUrl;
    
    public OpenAIService(RestTemplate openAiRestTemplate, 
                                   @Value("${openai.api.url:https://api.openai.com/v1}") String openaiApiUrl) {
        this.openAiRestTemplate = openAiRestTemplate;
        this.openaiApiUrl = openaiApiUrl;
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
    
    private String callOpenAI(String systemPrompt, String userPrompt) {
        try {
            log.debug("Calling OpenAI API with RestTemplate");
            
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
            
            // HTTP 요청 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<OpenAIRequest> entity = new HttpEntity<>(request, headers);
            
            // API 호출
            ResponseEntity<OpenAIResponse> response = openAiRestTemplate.exchange(
                openaiApiUrl + "/chat/completions",
                HttpMethod.POST,
                entity,
                OpenAIResponse.class
            );
            
            // 응답 처리
            OpenAIResponse openAIResponse = response.getBody();
            
            if (openAIResponse == null || 
                openAIResponse.getChoices() == null || 
                openAIResponse.getChoices().isEmpty()) {
                
                log.error("OpenAI API returned empty response");
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }
            
            String content = openAIResponse.getChoices().get(0).getMessage().getContent();
            
            if (content == null || content.trim().isEmpty()) {
                log.error("OpenAI API returned empty content");
                throw new BusinessException(ErrorCode.LLM_GENERATION_FAILED);
            }
            
            log.debug("OpenAI API call successful");
            return content.trim();
            
        } catch (BusinessException e) {
            // 이미 BusinessException이면 그대로 전파
            throw e;
        } catch (Exception e) {
            log.error("Failed to call OpenAI API", e);
            throw new BusinessException(ErrorCode.LLM_SERVICE_ERROR);
        }
    }
}
