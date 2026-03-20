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
import com.example.hdb.entity.Project;
import com.example.hdb.entity.ProjectStatus;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.User;
import com.example.hdb.enums.PlanningStatus;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneRepository;
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
@Transactional
public class ProjectServiceImpl implements ProjectService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProjectServiceImpl.class);
    
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final SceneRepository sceneRepository;
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
    
    public ApiResponse<SceneGenerationResponse> generateProjectScenes(Long projectId, SceneGenerationRequest request) {
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
        SceneGenerationResponse response = SceneGenerationResponse.builder()
            .scenes(java.util.List.of(scenesJson))
            .build();
        
        // 생성된 씬들을 DB에 저장
        for (int i = 0; i < response.getScenes().size(); i++) {
            Scene scene = Scene.builder()
                    .project(project)
                    .sceneOrder(i + 1)
                    .summary(response.getScenes().get(i))
                    .status(com.example.hdb.enums.SceneStatus.PENDING)
                    .build();
            
            sceneRepository.save(scene);
        }
        
        log.info("Project scenes generated successfully for projectId: {}", projectId);
        
        return ApiResponse.success("프로젝트 씬 생성 성공", response);
    }
}
