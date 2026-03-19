package com.example.hdb.dto.openai;

import com.example.hdb.dto.common.OptionalElements;
import lombok.*;

import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SceneGenerationResponse {
    private List<SceneData> scenes;
    
    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SceneData {
        private Integer sceneOrder;
        private String summary;
        private OptionalElements optionalElements;
        private String imagePrompt;
        private String videoPrompt;
    }
}
