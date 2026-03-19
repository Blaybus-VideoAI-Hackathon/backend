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
@Slf4j
@Transactional
public class ProjectServiceImpl implements ProjectService {
    
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final SceneRepository sceneRepository;
    private final OpenAIService openAIService;
    
    // ========== loginId 기반 메서드 (ProjectServiceNew에서 병합) ==========
    
    @Override
    public ProjectResponse createProject(ProjectCreateRequest request) {
        // 이 메서드는 더 이상 사용하지 않음 (loginId 기반으로 변경)
        throw new UnsupportedOperationException("이 메서드는 더 이상 사용되지 않습니다. loginId 기반 메서드를 사용하세요.");
    }
    
    /**
     * loginId 기반 프로젝트 생성 (신규 추가)
     */
    public Project createProjectByLoginId(String loginId, ProjectCreateRequest request) {
        log.info("Creating project with loginId: {}, title: {}", loginId, request.getTitle());
        
        // 사용자 존재 확인
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        // 프로젝트 생성
        Project project = Project.builder()
                .user(user)
                .title(request.getTitle())
                .style(request.getStyle())
                .ratio(request.getRatio())
                .purpose(request.getPurpose())
                .duration(request.getDuration())
                .ideaText(request.getIdeaText())
                .coreElements(request.getCoreElements())
                .planningStatus(request.getPlanningStatus())
                .status(ProjectStatus.PLANNING)
                .build();
        
        Project savedProject = projectRepository.save(project);
        log.info("Project created successfully with id: {}", savedProject.getId());
        
        return savedProject;
    }
    
    @Override
    public List<ProjectResponse> getProjectsByUserId(Long userId) {
        // userId 기반 메서드는 내부에서 loginId로 변환
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Project> projects = getUserProjects(user.getLoginId());
        
        return projects.stream()
                .map(ProjectResponse::from)
                .collect(Collectors.toList());
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
    
    @Override
    public ProjectResponse getProjectById(Long projectId) {
        log.info("Getting project by id: {}", projectId);
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        return ProjectResponse.from(project);
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

    // ========== 기존 userId 기반 메서드 (내부용) ==========
    
    @Override
    public ProjectResponse getProjectByIdAndUserId(Long projectId, Long userId) {
        // userId 기반 메서드는 내부에서 loginId로 변환
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Project project = getProjectByLoginId(user.getLoginId(), projectId);
        return ProjectResponse.from(project);
    }
    
    @Override
    public List<SceneResponse> getScenesByProjectId(Long projectId) {
        log.info("Getting scenes for projectId: {}", projectId);
        
        // 프로젝트 존재 확인
        if (!projectRepository.existsById(projectId)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        
        List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);
        
        return scenes.stream()
                .map(SceneResponse::from)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<SceneResponse> getScenesByProjectIdAndUserId(Long projectId, Long userId) {
        log.info("Getting scenes for projectId: {} and userId: {}", projectId, userId);
        
        // 프로젝트 권한 확인
        if (!projectRepository.existsByIdAndUserId(projectId, userId)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        
        List<Scene> scenes = sceneRepository.findByProjectIdOrderBySceneOrderAsc(projectId);
        
        return scenes.stream()
                .map(SceneResponse::from)
                .collect(Collectors.toList());
    }
    
    @Override
    public ProjectResponse updateCoreElements(Long projectId, CoreElementsRequest request) {
        log.info("Updating core elements for projectId: {}", projectId);
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        project.setCoreElements(request.getCoreElements());
        Project updatedProject = projectRepository.save(project);
        
        log.info("Core elements updated successfully for projectId: {}", projectId);
        return ProjectResponse.from(updatedProject);
    }
    
    @Override
    public ApiResponse<IdeaGenerationResponse> generateProjectIdea(Long projectId, IdeaGenerationRequest request) {
        log.info("Generating project idea for projectId: {}, userInput: {}", projectId, request.getUserInput());
        
        // 프로젝트 존재 확인
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        log.info("Project found - style: {}, ratio: {}", project.getStyle(), project.getRatio());
        
        try {
            // OpenAI API 호출로 아이디어 생성
            String ideaJson = openAIService.generateIdea(
                request.getUserInput(), 
                project.getStyle(), 
                project.getRatio()
            );
            
            log.info("OpenAI API response received: {}", ideaJson);
            
            // JSON 파싱 및 응답 생성 (임시)
            IdeaGenerationResponse response = IdeaGenerationResponse.builder()
                .coreElements(ideaJson)
                .displayText("아이디어 생성 완료")
                .build();
            
            // 프로젝트에 결과 저장
            project.setCoreElements(response.getCoreElements());
            projectRepository.save(project);
            
            log.info("Project idea generated successfully for projectId: {}", projectId);
            
            return ApiResponse.success("프로젝트 아이디어 생성 성공", response);
            
        } catch (Exception e) {
            log.error("Failed to generate project idea for projectId: {}", projectId, e);
            throw e;
        }
    }
    
    @Override
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
