package com.example.hdb.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class ProjectPlanResponse {
    
    private String title;
    private String theme;
    private String mainCharacter;
    private String background;
    private String timeOfDay;
    private String mood;
    private String style;
    private List<SceneInfo> scenes;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    public String toJson() {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    @Data
    @Builder
    @AllArgsConstructor
    public static class SceneInfo {
        private Integer sceneNumber;
        private String description;
        private String imagePrompt;
    }
}
