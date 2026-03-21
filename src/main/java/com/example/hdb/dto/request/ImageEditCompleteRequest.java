package com.example.hdb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "이미지 편집 완료 요청")
public class ImageEditCompleteRequest {
    
    @Schema(description = "편집된 이미지의 최종 URL", example = "https://example.com/edited-image.jpg", required = true)
    private String editedImageUrl;
    
    @Schema(description = "편집 타입", 
           allowableValues = {"crop", "brightness", "combined"}, 
           required = true)
    private String editType;
    
    @Schema(description = "편집 파라미터 (JSON 형식)", required = true)
    private String editParameters;
}
