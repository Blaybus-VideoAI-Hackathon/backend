package com.example.hdb.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneInfo {
    private String sceneId;
    private Integer order;
    private String summary;
    private OptionalElements optionalElements;
    private String imagePrompt;
    private String videoPrompt;
}
