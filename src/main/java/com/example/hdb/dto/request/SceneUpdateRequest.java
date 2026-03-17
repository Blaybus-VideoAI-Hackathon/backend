package com.example.hdb.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SceneUpdateRequest {
    
    @NotBlank(message = "씬 요약을 입력해주세요.")
    private String summary;
    
    private String optionalElements;
    
    private String imagePrompt;
    
    private String videoPrompt;
}
