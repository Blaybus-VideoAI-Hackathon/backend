package com.example.hdb.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreElements {
    private String purpose;
    private String duration;
    private String ratio;
    private String style;
    private String mainCharacter;
    private List<String> subCharacters;
    private String backgroundWorld;
    private String storyFlow;
}
