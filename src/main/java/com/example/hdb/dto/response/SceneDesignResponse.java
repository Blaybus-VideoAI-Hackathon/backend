package com.example.hdb.dto.response;

import com.example.hdb.dto.common.OptionalElements;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "씬 설계 응답")
public class SceneDesignResponse {
    
    @Schema(description = "씬 ID")
    private Long sceneId;
    
    @Schema(description = "씬 요약")
    private String summary;
    
    @Schema(description = "선택적 요소")
    private OptionalElements optionalElements;
    
    @Schema(description = "이미지 프롬프트")
    private String imagePrompt;
    
    @Schema(description = "영상 프롬프트")
    private String videoPrompt;
    
    @Schema(description = "처리 결과 메시지")
    private String displayText;
    
    @Schema(description = "수정 시간")
    private LocalDateTime updatedAt;
    
    // 직접 getter 추가 (Lombok 보험용)
    public OptionalElements getOptionalElements() {
        return optionalElements;
    }
    
    public String getImagePrompt() {
        return imagePrompt;
    }
    
    public String getVideoPrompt() {
        return videoPrompt;
    }
    
    public String getDisplayText() {
        return displayText;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    // 직접 builder 메서드 추가
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Long sceneId;
        private String summary;
        private OptionalElements optionalElements;
        private String imagePrompt;
        private String videoPrompt;
        private String displayText;
        private LocalDateTime updatedAt;
        
        public Builder sceneId(Long sceneId) {
            this.sceneId = sceneId;
            return this;
        }
        
        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }
        
        public Builder optionalElements(OptionalElements optionalElements) {
            this.optionalElements = optionalElements;
            return this;
        }
        
        public Builder imagePrompt(String imagePrompt) {
            this.imagePrompt = imagePrompt;
            return this;
        }
        
        public Builder videoPrompt(String videoPrompt) {
            this.videoPrompt = videoPrompt;
            return this;
        }
        
        public Builder displayText(String displayText) {
            this.displayText = displayText;
            return this;
        }
        
        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }
        
        public SceneDesignResponse build() {
            SceneDesignResponse response = new SceneDesignResponse();
            response.sceneId = this.sceneId;
            response.summary = this.summary;
            response.optionalElements = this.optionalElements;
            response.imagePrompt = this.imagePrompt;
            response.videoPrompt = this.videoPrompt;
            response.displayText = this.displayText;
            response.updatedAt = this.updatedAt;
            return response;
        }
    }
}
