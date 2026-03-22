package com.example.hdb.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class ProjectPlanResponse {
    
    private String displayText;
    private CoreElements coreElements;
    private Meta meta;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoreElements {
        private String mainCharacter;
        private String background;
        private String style;
        private String ratio;
        private String purpose;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private List<StoryOption> storyOptions;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoryOption {
        private String id;
        private String title;
        private String description;
    }
    
    public static ProjectPlanResponse fromJson(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            ProjectPlanResponse response = mapper.readValue(json, ProjectPlanResponse.class);
            log.info("Successfully parsed ProjectPlanResponse: {}", response);
            return response;
        } catch (Exception e) {
            log.error("Failed to parse ProjectPlanResponse from JSON: {}", json, e);
            log.info("Using fallback response");
            return createFallbackResponse();
        }
    }
    
    public static ProjectPlanResponse createFallbackResponse() {
        return ProjectPlanResponse.builder()
                .displayText("입력한 아이디어를 바탕으로 3가지 기획안을 제안합니다.")
                .coreElements(CoreElements.builder()
                        .mainCharacter("주요 인물")
                        .background("배경 설정")
                        .style("스타일")
                        .ratio("화면 비율")
                        .purpose("목적")
                        .build())
                .meta(Meta.builder()
                        .storyOptions(List.of(
                                StoryOption.builder()
                                        .id("A")
                                        .title("기획안 A")
                                        .description("제품 중심의 기획안")
                                        .build(),
                                StoryOption.builder()
                                        .id("B")
                                        .title("기획안 B")
                                        .description("감성 중심의 기획안")
                                        .build(),
                                StoryOption.builder()
                                        .id("C")
                                        .title("기획안 C")
                                        .description("기능 시연 중심의 기획안")
                                        .build()
                        ))
                        .build())
                .build();
    }
}
