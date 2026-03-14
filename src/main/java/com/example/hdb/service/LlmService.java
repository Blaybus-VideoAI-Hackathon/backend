package com.example.hdb.service;

import com.example.hdb.dto.request.LlmPlanRequest;
import com.example.hdb.dto.request.LlmSceneDesignRequest;
import com.example.hdb.dto.request.LlmSceneEditRequest;
import com.example.hdb.dto.request.LlmScenesRequest;
import com.example.hdb.dto.response.LlmPlanResponse;
import com.example.hdb.dto.response.LlmSceneDesignResponse;
import com.example.hdb.dto.response.LlmSceneEditResponse;
import com.example.hdb.dto.response.LlmScenesResponse;

public interface LlmService {
    LlmPlanResponse generatePlan(LlmPlanRequest request);
    LlmScenesResponse generateScenes(LlmScenesRequest request);
    LlmSceneDesignResponse designScene(LlmSceneDesignRequest request);
    LlmSceneEditResponse editScene(LlmSceneEditRequest request);
}
