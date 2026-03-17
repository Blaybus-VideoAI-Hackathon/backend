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
    
    @Override
    public ProjectResponse createProject(ProjectCreateRequest request) {
        log.info("Creating project with userId: {}, title: {}", request.getUserId(), request.getTitle());
        
        // 사용자 존재 확인
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        Project project = Project.builder()
                .user(user)
                .title(request.getTitle())
                .style(request.getStyle())
                .ratio(request.getRatio())
                .purpose(request.getPurpose())
                .duration(request.getDuration())
                .ideaText(request.getIdeaText())
                .planningStatus(request.getPlanningStatus())
                .build();
        
        Project savedProject = projectRepository.save(project);
        log.info("Project created successfully with id: {}", savedProject.getId());
        
        return ProjectResponse.from(savedProject);
    }
    
    @Override
    public List<ProjectResponse> getProjectsByUserId(Long userId) {
        log.info("Getting projects for userId: {}", userId);
        
        List<Project> projects = projectRepository.findByUserIdOrderByCreatedAtDesc(userId);
        
        return projects.stream()
                .map(ProjectResponse::from)
                .collect(Collectors.toList());
    }
    
    @Override
    public ProjectResponse getProjectById(Long projectId) {
        log.info("Getting project by id: {}", projectId);
        
        // TODO: 실제 사용자 인증 구현 후 userId 파라미터 추가
        // 현재는 임시로 projectId만으로 조회 (권한 검증 필요)
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
        return ProjectResponse.from(project);
    }
    
    @Override
    public ProjectResponse getProjectByIdAndUserId(Long projectId, Long userId) {
        log.info("Getting project by id: {} for userId: {}", projectId, userId);
        
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        
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
        
        // OpenAI API 호출로 아이디어 생성
        String ideaJson = openAIService.generateIdea(
            request.getUserInput(), 
            project.getStyle(), 
            project.getRatio()
        );
        
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
