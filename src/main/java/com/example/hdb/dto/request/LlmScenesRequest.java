package com.example.hdb.dto.request;

import com.example.hdb.dto.common.CoreElements;
import com.example.hdb.dto.common.StoryOption;
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
public class LlmScenesRequest {
    
    @Valid
    @NotNull(message = "Core elements는 필수입니다")
    private CoreElements coreElements;
    
    @Valid
    @NotNull(message = "선택된 기획안은 필수입니다")
    private StoryOption selectedStoryOption;
}
