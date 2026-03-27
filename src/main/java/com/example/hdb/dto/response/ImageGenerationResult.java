package com.example.hdb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationResult {

    /**
     * 생성된 이미지 URL
     */
    private String imageUrl;

    /**
     * DALL-E 3가 실제 사용한 revised prompt
     * (캐릭터 일관성을 위해 재사용)
     */
    private String revisedPrompt;

    /**
     * ★ Leonardo AI 통합 추가
     * Generation ID (결과 추적용)
     */
    private String generationId;

    /**
     * ★ Leonardo AI 통합 추가
     * Seed 값 (일관성 유지용)
     */
    private String seed;

    /**
     * ★ Leonardo AI 통합 추가
     * 사용된 모델명
     */
    private String model;
}