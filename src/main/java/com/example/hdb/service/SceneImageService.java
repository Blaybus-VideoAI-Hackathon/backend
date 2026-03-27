package com.example.hdb.service;

import com.example.hdb.dto.request.ImageEditCompleteRequest;
import com.example.hdb.dto.request.SceneImageEditAiRequest;
import com.example.hdb.dto.response.SceneImageEditAiResponse;
import com.example.hdb.dto.response.SceneImageResponse;

import java.util.List;

public interface SceneImageService {

    /**
     * Scene 이미지 생성
     * @param projectId 프로젝트 ID
     * @param sceneId 씬 ID
     * @param loginId 사용자 ID
     * @return 생성된 이미지 정보
     */
    SceneImageResponse generateImage(Long projectId, Long sceneId, String loginId);

    /**
     * 일관성 있는 이미지 생성 (첫 번째 씬의 스타일을 유지)
     * @param projectId 프로젝트 ID
     * @param sceneId 씬 ID
     * @param referenceImageUrl 참조 이미지 URL
     * @param imagePrompt 이미지 프롬프트
     * @param loginId 사용자 ID
     * @return 생성된 이미지 정보
     */
    SceneImageResponse generateConsistentImage(Long projectId, Long sceneId, String referenceImageUrl, String imagePrompt, String loginId);

    /**
     * Scene 이미지 목록 조회
     * @param projectId 프로젝트 ID
     * @param sceneId 씬 ID
     * @param loginId 사용자 ID
     * @return 이미지 목록
     */
    List<SceneImageResponse> getImages(Long projectId, Long sceneId, String loginId);

    /**
     * 프로젝트 전체 이미지 목록 조회
     * @param projectId 프로젝트 ID
     * @param loginId 사용자 ID
     * @return 프로젝트 내 모든 이미지 목록
     */
    List<SceneImageResponse> getProjectImages(Long projectId, String loginId);

    /**
     * 이미지 편집 완료 처리
     * @param projectId 프로젝트 ID
     * @param sceneId 씬 ID
     * @param imageId 이미지 ID
     * @param loginId 사용자 ID
     * @param request 편집 완료 요청
     * @return 업데이트된 이미지 정보
     */
    SceneImageResponse completeImageEdit(Long projectId, Long sceneId, Long imageId, String loginId, ImageEditCompleteRequest request);

    /**
     * AI 이미지 편집 및 새 이미지 생성
     * @param projectId 프로젝트 ID
     * @param sceneId 씬 ID
     * @param imageId 원본 이미지 ID
     * @param loginId 사용자 ID
     * @param request AI 편집 요청
     * @return 새로 생성된 이미지 정보
     */
    SceneImageResponse generateImageEditAi(Long projectId, Long sceneId, Long imageId, String loginId, SceneImageEditAiRequest request);
}