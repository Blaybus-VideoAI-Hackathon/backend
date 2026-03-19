package com.example.hdb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "씬 수정 요청")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SceneEditRequest {
    
    @Schema(description = "수정할 선택 요소")
    private String optionalElements;
    
    @Schema(description = "수정할 이미지 프롬프트")
    private String imagePrompt;
    
    @Schema(description = "수정할 영상 프롬프트")
    private String videoPrompt;
}
