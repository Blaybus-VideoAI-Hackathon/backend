package com.example.hdb.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoreElementsRequest {
    
    @NotBlank(message = "핵심 요소를 입력해주세요.")
    private String coreElements;
}
