package com.example.hdb.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdeaGenerationRequest {
    
    @NotBlank(message = "아이디어를 입력해주세요.")
    private String userInput;
    
    @NotBlank(message = "프로젝트 아이디어를 입력해주세요.")
    private String ideaText;
}
