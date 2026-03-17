package com.example.hdb.dto.response;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SceneGenerationResponse {
    private List<String> scenes;
}
