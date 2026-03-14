package com.example.hdb.dto.request;

import com.example.hdb.dto.common.CoreElements;
import com.example.hdb.dto.common.SceneInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmSceneDesignRequest {
    
    @Valid
    @NotNull(message = "Core elements는 필수입니다")
    private CoreElements coreElements;
    
    @Valid
    @NotNull(message = "Scene 정보는 필수입니다")
    private SceneInfo scene;
}
