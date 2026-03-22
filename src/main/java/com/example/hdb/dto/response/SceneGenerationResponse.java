package com.example.hdb.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Schema(description = "씬 생성 응답 (summary만)")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SceneGenerationResponse {
    
    @Schema(description = "생성된 씬 목록")
    private List<SceneSummaryDto> scenes;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SceneSummaryDto {
        
        @Schema(description = "씬 ID")
        private Long id;
        
        @Schema(description = "씬 순서")
        private Integer sceneOrder;
        
        @Schema(description = "씬 요약")
        private String summary;
        
        @Schema(description = "씬 상태")
        private String status;
    }
}
