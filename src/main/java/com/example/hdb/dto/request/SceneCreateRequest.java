package com.example.hdb.dto.request;

import com.example.hdb.enums.SceneStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SceneCreateRequest {
    
    @NotNull(message = "프로젝트 ID는 필수입니다.")
    private Long projectId;
    
    @NotNull(message = "씬 순서는 필수입니다.")
    private Integer sceneOrder;
    
    @NotBlank(message = "씬 요약을 입력해주세요.")
    private String summary;
    
    private String optionalElements;
    
    private String imagePrompt;
    
    private String videoPrompt;
    
    @Builder.Default
    private SceneStatus status = SceneStatus.PENDING;
}
