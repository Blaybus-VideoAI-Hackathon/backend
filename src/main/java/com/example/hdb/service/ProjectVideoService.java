package com.example.hdb.service;

import com.example.hdb.dto.request.VideoMergeRequest;
import com.example.hdb.dto.response.ProjectVideoResponse;

public interface ProjectVideoService {
    
    /**
     * 프로젝트 영상 병합
     * @param projectId 프로젝트 ID
     * @param loginId 사용자 ID
     * @param request 병합 요청
     * @return 병합 결과
     */
    ProjectVideoResponse mergeProjectVideos(Long projectId, String loginId, VideoMergeRequest request);
    
    /**
     * 최종 병합 영상 조회
     * @param projectId 프로젝트 ID
     * @param loginId 사용자 ID
     * @return 최종 영상 정보
     */
    ProjectVideoResponse getFinalVideo(Long projectId, String loginId);
}
