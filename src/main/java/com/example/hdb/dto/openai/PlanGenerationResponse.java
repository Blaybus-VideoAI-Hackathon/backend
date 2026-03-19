package com.example.hdb.dto.openai;

import lombok.*;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanGenerationResponse {
    private String title;
    private CoreElements coreElements;
    
    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoreElements {
        private String mainCharacter;
        private String background;
        private String mood;
        private String style;
        private String storyFlow;
    }
}
