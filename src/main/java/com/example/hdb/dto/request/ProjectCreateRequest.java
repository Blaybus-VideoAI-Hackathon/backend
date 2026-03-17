package com.example.hdb.dto.request;

import com.example.hdb.enums.PlanningStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectCreateRequest {
    
    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;
    
    @NotBlank(message = "프로젝트 제목을 입력해주세요.")
    private String title;
    
    @NotBlank(message = "스타일을 선택해주세요.")
    private String style;
    
    @NotBlank(message = "비율을 선택해주세요.")
    private String ratio;
    
    private String purpose;
    
    private Integer duration;
    
    private String ideaText;
    
    @Builder.Default
    private PlanningStatus planningStatus = PlanningStatus.DRAFT;
}
