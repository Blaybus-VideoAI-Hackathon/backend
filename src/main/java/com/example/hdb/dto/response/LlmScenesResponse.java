package com.example.hdb.dto.response;

import com.example.hdb.dto.common.SceneInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmScenesResponse {
    private String displayText;
    private List<SceneInfo> scenes;
}
