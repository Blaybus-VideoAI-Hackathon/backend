package com.example.hdb.dto.request;

import com.example.hdb.dto.common.CoreElements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmPlanRequest {
    
    @Valid
    @NotNull(message = "프로젝트 정보는 필수입니다")
    private ProjectInfo project;
    
    @NotBlank(message = "아이디어 텍스트는 필수입니다")
    private String ideaText;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectInfo {
        @NotBlank(message = "제목은 필수입니다")
        private String title;
        
        private String purpose;
        private String duration;
        private String ratio;
        private String style;
    }
}
