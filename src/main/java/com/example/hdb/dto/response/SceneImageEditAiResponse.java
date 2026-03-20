package com.example.hdb.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "씬 이미지 AI 편집 응답")
public class SceneImageEditAiResponse {
    
    @Schema(description = "이미지 ID")
    private Long imageId;
    
    @Schema(description = "처리 결과 메시지")
    private String displayText;
    
    @Schema(description = "편집 제안 파라미터")
    private Map<String, Object> editSuggestions;
    
    @Schema(description = "업데이트된 이미지 프롬프트")
    private String updatedImagePrompt;
    
    @Schema(
        description = "편집 유형",
        allowableValues = {"brightness", "contrast", "tone", "crop", "filter"},
        example = "brightness"
    )
    private String editType;
    
    // 직접 builder 추가 (Lombok 보험용)
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Long imageId;
        private String displayText;
        private Map<String, Object> editSuggestions;
        private String updatedImagePrompt;
        private String editType;
        
        public Builder imageId(Long imageId) {
            this.imageId = imageId;
            return this;
        }
        
        public Builder displayText(String displayText) {
            this.displayText = displayText;
            return this;
        }
        
        public Builder editSuggestions(Map<String, Object> editSuggestions) {
            this.editSuggestions = editSuggestions;
            return this;
        }
        
        public Builder updatedImagePrompt(String updatedImagePrompt) {
            this.updatedImagePrompt = updatedImagePrompt;
            return this;
        }
        
        public Builder editType(String editType) {
            this.editType = editType;
            return this;
        }
        
        public SceneImageEditAiResponse build() {
            SceneImageEditAiResponse response = new SceneImageEditAiResponse();
            response.imageId = this.imageId;
            response.displayText = this.displayText;
            response.editSuggestions = this.editSuggestions;
            response.updatedImagePrompt = this.updatedImagePrompt;
            response.editType = this.editType;
            return response;
        }
    }
}
