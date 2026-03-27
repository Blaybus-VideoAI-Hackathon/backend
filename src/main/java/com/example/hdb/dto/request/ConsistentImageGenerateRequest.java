package com.example.hdb.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 일관성 있는 이미지 생성 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsistentImageGenerateRequest {
    
    @NotBlank(message = "Reference image URL is required")
    private String referenceImageUrl;
    
    @NotBlank(message = "Image prompt is required")
    private String imagePrompt;
}
