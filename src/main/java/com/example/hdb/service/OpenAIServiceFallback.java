package com.example.hdb.service;

import com.example.hdb.dto.response.IdeaGenerationResponse;
import com.example.hdb.dto.response.SceneGenerationResponse;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnMissingBean(OpenAIService.class)
public class OpenAIServiceFallback {
    
    public String generateIdea(String coreElements, String style, String ratio) {
        log.warn("OpenAI API Key가 설정되지 않아 Mock 응답을 반환합니다 - coreElements: {}, style: {}, ratio: {}", 
                coreElements, style, ratio);
        
        String mockResponse = String.format("""
            {
              "idea": "Mock 아이디어: %s 스타일의 %s 비율 비디오 (핵심: %s)"
            }
            """, style, ratio, coreElements);
            
        log.info("Mock 응답 생성 완료: {}", mockResponse);
        return mockResponse;
    }
    
    public String generateScenes(String idea, int sceneCount) {
        log.warn("OpenAI API Key가 설정되지 않아 Mock 응답을 반환합니다");
        StringBuilder scenes = new StringBuilder("[");
        
        for (int i = 1; i <= sceneCount; i++) {
            if (i > 1) scenes.append(",");
            scenes.append(String.format("""
                {
                  "sceneOrder": %d,
                  "summary": "Mock 씬 %d: %s",
                  "optionalElements": "선택적 요소 %d",
                  "imagePrompt": "이미지 프롬프트 %d",
                  "videoPrompt": "비디오 프롬프트 %d"
                }
                """, i, i, idea, i, i, i));
        }
        
        scenes.append("]");
        return scenes.toString();
    }
}
