package com.example.hdb.dto.openai;

import com.example.hdb.dto.common.OptionalElements;
import lombok.*;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SceneDesignResponse {
    private OptionalElements optionalElements;
    private String imagePrompt;
    private String videoPrompt;
}
