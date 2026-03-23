package com.example.hdb.service.impl;

import com.example.hdb.dto.common.ApiResponse;
import com.example.hdb.dto.request.CoreElementsRequest;
import com.example.hdb.dto.request.IdeaGenerationRequest;
import com.example.hdb.dto.request.ProjectCreateRequest;
import com.example.hdb.dto.response.IdeaGenerationResponse;
import com.example.hdb.dto.response.ProjectResponse;
import com.example.hdb.dto.response.SceneResponse;
import com.example.hdb.dto.request.SceneGenerationRequest;
import com.example.hdb.dto.response.SceneGenerationResponse;
import com.example.hdb.dto.response.SceneGenerationResponse.SceneSummaryDto;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.ProjectPlan;
import com.example.hdb.entity.ProjectStatus;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneImage;
import com.example.hdb.entity.SceneVideo;
import com.example.hdb.entity.User;
import com.example.hdb.enums.PlanningStatus;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectPlanRepository;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneImageRepository;
import com.example.hdb.repository.SceneRepository;
import com.example.hdb.repository.SceneVideoRepository;
import com.example.hdb.repository.UserRepository;
import com.example.hdb.service.OpenAIService;
import com.example.hdb.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceImpl implements ProjectService {
    
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final SceneRepository sceneRepository;
    private final SceneImageRepository sceneImageRepository;
    private final SceneVideoRepository sceneVideoRepository;
    private final ProjectPlanRepository projectPlanRepository;
    private final OpenAIService openAIService;
    
    // ========== loginId 기반 메서드 (ProjectServiceNew에서 병합) ==========
    
    /**
     * loginId 기반 프로젝트 생성 (신규 추가)
     */
    public Project createProjectByLoginId(String loginId, ProjectCreateRequest request) {
        log.info("Creating project with loginId: {}, title: {}", loginId, request.getTitle());
        
        // 사용자 존재 확인
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        Project project = Project.builder()
                .user(user)
                .title(request.getTitle())
                .style(request.getStyle())
                .ratio(request.getRatio())
                .purpose(request.getPurpose())
                .duration(request.getDuration())
                .ideaText(request.getIdeaText())
                .coreElements(request.getCoreElements())
                .planningStatus(PlanningStatus.COMPLETED)
                .build();
        
        Project savedProject = projectRepository.save(project);
        log.info("Project created successfully with id: {}", savedProject.getId());
        
        return savedProject;
    }
    
    /**
     * loginId 기반 프로젝트 목록 (신규 추가)
     */
    public List<Project> getUserProjects(String loginId) {
        log.info("getProjects 호출 - loginId={}", loginId);
        
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        List<Project> projects = projectRepository.findByUserOrderByCreatedAtDesc(user);
        log.info("Found {} projects for loginId: {}", projects.size(), loginId);
        
        return projects;
    }
    
    /**
     * loginId 기반 프로젝트 상세 조회 (신규 추가)
     */
    public Project getProjectByLoginId(String loginId, Long projectId) {
        log.info("Getting project by id: {}, loginId: {}", projectId, loginId);
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        // 권한 확인 (loginId가 있는 경우만)
        if (loginId != null && !project.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        return project;
    }
    
    /**
     * 프로젝트 상태 업데이트 (신규 추가)
     */
    public Project updateProjectStatus(Long projectId, ProjectStatus status) {
        log.info("Updating project status: {} -> {}", projectId, status);
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        project.setStatus(status);
        Project updatedProject = projectRepository.save(project);
        
        log.info("Project status updated successfully: {}", updatedProject.getStatus());
        return updatedProject;
    }
    
    public ApiResponse<List<String>> generateProjectScenes(Long projectId, SceneGenerationRequest request) {
        log.info("Generating project scenes for projectId: {}, sceneIdea: {}", projectId, request.getSceneIdea());
        
        // 프로젝트 존재 확인
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        // 기존 씬 삭제 (선택적)
        sceneRepository.deleteByProjectId(projectId);
        
        // OpenAI API 호출로 씬 생성
        String scenesJson = openAIService.generateScenes(
            project.getCoreElements(), 
            4 // 기본 4개 씬 생성
        );
        
        // JSON 파싱 및 응답 생성 (임시)
        List<String> sceneSummaries = java.util.List.of("첫 번째 장면", "두 번째 장면", "세 번째 장면", "네 번째 장면");
        
        // 생성된 씬들을 DB에 저장
        for (int i = 0; i < sceneSummaries.size(); i++) {
            Scene scene = Scene.builder()
                    .project(project)
                    .sceneOrder(i + 1)
                    .summary(sceneSummaries.get(i))
                    .status(com.example.hdb.enums.SceneStatus.PENDING)
                    .build();
            
            sceneRepository.save(scene);
        }
        
        log.info("Project scenes generated successfully for projectId: {}", projectId);
        
        return ApiResponse.success("프로젝트 씬 생성 성공", sceneSummaries);
    }
    
    @Override
    @Transactional
    public void deleteProject(Long projectId, String loginId) {
        log.info("=== STARTING PROJECT DELETION ===");
        log.info("projectId={}, loginId={}", projectId, loginId);
        
        try {
            // 프로젝트 조회
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
            
            // 권한 체크
            if (!project.getUser().getLoginId().equals(loginId)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
            }
            
            // 연관 데이터 삭제 순서: SceneVideo -> SceneImage -> Scene -> ProjectPlan -> Project
            
            // 1. Scene 목록 조회
            List<Scene> scenes = sceneRepository.findByProjectId(projectId);
            log.info("Found {} scenes for projectId={}", scenes.size(), projectId);
            
            int totalDeletedSceneVideos = 0;
            int totalDeletedSceneImages = 0;
            
            // 2. 각 Scene의 연관 데이터 삭제
            for (Scene scene : scenes) {
                // SceneVideo 삭제
                List<SceneVideo> sceneVideos = sceneVideoRepository.findBySceneIdOrderByCreatedAtDesc(scene.getId());
                if (!sceneVideos.isEmpty()) {
                    sceneVideoRepository.deleteAllBySceneId(scene.getId());
                    totalDeletedSceneVideos += sceneVideos.size();
                    log.debug("Deleted {} sceneVideos for sceneId={}", sceneVideos.size(), scene.getId());
                }
                
                // SceneImage 삭제
                List<SceneImage> sceneImages = sceneImageRepository.findBySceneIdOrderByImageNumberAsc(scene.getId());
                if (!sceneImages.isEmpty()) {
                    sceneImageRepository.deleteAllBySceneId(scene.getId());
                    totalDeletedSceneImages += sceneImages.size();
                    log.debug("Deleted {} sceneImages for sceneId={}", sceneImages.size(), scene.getId());
                }
            }
            
            // 3. Scene 삭제
            if (!scenes.isEmpty()) {
                sceneRepository.deleteByProjectId(projectId);
                log.info("Deleted {} scenes for projectId={}", scenes.size(), projectId);
            }
            
            // 4. ProjectPlan 삭제
            List<ProjectPlan> projectPlans = projectPlanRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
            if (!projectPlans.isEmpty()) {
                projectPlanRepository.deleteByProjectId(projectId);
                log.info("Deleted {} projectPlans for projectId={}", projectPlans.size(), projectId);
            }
            
            // 5. Project 삭제
            projectRepository.delete(project);
            
            // 삭제 결과 로그
            log.info("=== PROJECT DELETION COMPLETED ===");
            log.info("projectId: {}", projectId);
            log.info("scenes deleted: {}", scenes.size());
            log.info("sceneImages deleted: {}", totalDeletedSceneImages);
            log.info("sceneVideos deleted: {}", totalDeletedSceneVideos);
            log.info("projectPlans deleted: {}", projectPlans.size());
            log.info("project title: '{}'", project.getTitle());
            
        } catch (BusinessException e) {
            log.error("Business exception during project deletion - projectId={}", projectId, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during project deletion - projectId={}", projectId, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
