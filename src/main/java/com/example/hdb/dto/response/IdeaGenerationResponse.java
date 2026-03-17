package com.example.hdb.dto.response;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdeaGenerationResponse {
    private String coreElements;
    private String displayText;
}
