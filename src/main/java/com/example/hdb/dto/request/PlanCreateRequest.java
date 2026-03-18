package com.example.hdb.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlanCreateRequest {
    
    @NotBlank(message = "기획 내용은 필수입니다.")
    private String userPrompt;
}
