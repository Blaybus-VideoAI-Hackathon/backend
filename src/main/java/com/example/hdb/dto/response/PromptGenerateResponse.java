package com.example.hdb.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "프롬프트 생성 응답")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptGenerateResponse {
    
    @Schema(description = "씬 ID")
    private Long sceneId;
    
    @Schema(description = "이미지 프롬프트")
    private String imagePrompt;
    
    @Schema(description = "영상 프롬프트")
    private String videoPrompt;
}
