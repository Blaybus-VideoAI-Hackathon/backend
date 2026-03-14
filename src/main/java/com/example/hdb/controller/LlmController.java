package com.example.hdb.controller;

import com.example.hdb.dto.request.*;
import com.example.hdb.dto.response.*;
import com.example.hdb.dto.response.ApiResponse;
import com.example.hdb.service.LlmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
@Tag(name = "LLM API", description = "AI 영상 생성 워크플로우 API")
public class LlmController {

    private final LlmService llmService;

    @PostMapping("/plan")
    @Operation(summary = "기획안 생성", description = "사용자 아이디어를 분석하여 core_elements를 정리하고 story option 3개를 생성합니다.")
    public ResponseEntity<ApiResponse<LlmPlanResponse>> generatePlan(@Valid @RequestBody LlmPlanRequest request) {
        LlmPlanResponse response = llmService.generatePlan(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("기획안이 성공적으로 생성되었습니다.", response));
    }

    @PostMapping("/scenes")
    @Operation(summary = "씬 목록 생성", description = "선택된 기획안을 기반으로 Scene 리스트를 생성합니다.")
    public ResponseEntity<ApiResponse<LlmScenesResponse>> generateScenes(@Valid @RequestBody LlmScenesRequest request) {
        LlmScenesResponse response = llmService.generateScenes(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Scene 목록이 성공적으로 생성되었습니다.", response));
    }

    @PostMapping("/scene/design")
    @Operation(summary = "씬 디자인", description = "특정 Scene에 대해 optional_elements를 추천하고 image_prompt/video_prompt를 생성합니다.")
    public ResponseEntity<ApiResponse<LlmSceneDesignResponse>> designScene(@Valid @RequestBody LlmSceneDesignRequest request) {
        LlmSceneDesignResponse response = llmService.designScene(request);
        return ResponseEntity.ok(ApiResponse.success("Scene 디자인이 완료되었습니다.", response));
    }

    @PostMapping("/scene/edit")
    @Operation(summary = "씬 수정", description = "사용자의 수정 요청을 반영하여 scene 정보를 갱신합니다.")
    public ResponseEntity<ApiResponse<LlmSceneEditResponse>> editScene(@Valid @RequestBody LlmSceneEditRequest request) {
        LlmSceneEditResponse response = llmService.editScene(request);
        return ResponseEntity.ok(ApiResponse.success("Scene이 성공적으로 수정되었습니다.", response));
    }
}
