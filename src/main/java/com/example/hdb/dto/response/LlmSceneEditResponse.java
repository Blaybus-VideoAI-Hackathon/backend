package com.example.hdb.dto.response;

import com.example.hdb.dto.common.OptionalElements;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmSceneEditResponse {
    private String displayText;
    private OptionalElements optionalElements;
    private String imagePrompt;
    private String videoPrompt;
}
