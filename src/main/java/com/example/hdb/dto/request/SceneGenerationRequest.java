package com.example.hdb.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SceneGenerationRequest {
    
    @NotBlank(message = "씬 아이디어를 입력해주세요.")
    private String sceneIdea;
    
    private String style;
    private String ratio;
}
