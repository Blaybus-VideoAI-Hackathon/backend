package com.example.hdb.service.impl;

import com.example.hdb.dto.common.ApiResponse;
import com.example.hdb.dto.request.ProjectCreateRequest;
import com.example.hdb.dto.request.SceneGenerationRequest;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.ProjectPlan;
import com.example.hdb.entity.ProjectStatus;
import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneImage;
import com.example.hdb.entity.SceneVideo;
import com.example.hdb.entity.User;
import com.example.hdb.enums.PlanningStatus;
import com.example.hdb.enums.SceneStatus;
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

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final SceneRepository sceneRepository;
    private final SceneImageRepository sceneImageRepository;
    private final SceneVideoRepository sceneVideoRepository;
    private final ProjectPlanRepository projectPlanRepository;
    private final OpenAIService openAIService;

    /**
     * loginId 기반 프로젝트 생성
     */
    @Override
    @Transactional
    public Project createProjectByLoginId(String loginId, ProjectCreateRequest request) {
        log.info("Creating project with loginId: {}, title: {}", loginId, request.getTitle());

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PlanningStatus planningStatus = request.getPlanningStatus() != null
                ? request.getPlanningStatus()
                : PlanningStatus.DRAFT;

        Project project = Project.builder()
                .user(user)
                .title(request.getTitle())
                .style(request.getStyle())
                .ratio(request.getRatio())
                .purpose(request.getPurpose())
                .duration(request.getDuration())
                .ideaText(request.getIdeaText())
                .coreElements(request.getCoreElements())
                .planningStatus(planningStatus)
                .build();

        Project savedProject = projectRepository.save(project);
        log.info("Project created successfully with id: {}", savedProject.getId());

        return savedProject;
    }

    /**
     * loginId 기반 프로젝트 목록 조회
     */
    @Override
    public List<Project> getUserProjects(String loginId) {
        log.info("getProjects 호출 - loginId={}", loginId);

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Project> projects = projectRepository.findByUserOrderByCreatedAtDesc(user);
        log.info("Found {} projects for loginId: {}", projects.size(), loginId);

        return projects;
    }

    /**
     * loginId 기반 프로젝트 상세 조회
     */
    @Override
    public Project getProjectByLoginId(String loginId, Long projectId) {
        log.info("Getting project by id: {}, loginId: {}", projectId, loginId);

        Project project = projectRepository.findByIdWithUser(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        if (loginId != null && !project.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        return project;
    }

    /**
     * 프로젝트 상태 업데이트
     */
    @Override
    @Transactional
    public Project updateProjectStatus(Long projectId, ProjectStatus status) {
        log.info("Updating project status: {} -> {}", projectId, status);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        project.setStatus(status);

        log.info("Project status updated successfully: {}", project.getStatus());
        return project;
    }

    /**
     * 프로젝트 씬 생성
     */
    @Override
    @Transactional
    public ApiResponse<List<String>> generateProjectScenes(Long projectId, SceneGenerationRequest request) {
        log.info("Generating project scenes for projectId: {}, sceneIdea: {}", projectId, request.getSceneIdea());

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        // 기존 씬 삭제
        sceneRepository.deleteByProjectId(projectId);

        // OpenAI 호출
        String scenesJson = openAIService.generateScenes(
                project.getCoreElements(),
                4
        );

        log.info("Generated scenes JSON for projectId={}: {}", projectId, scenesJson);

        // TODO: 실제 JSON 파싱 로직으로 교체 필요
        List<String> sceneSummaries = List.of(
                "첫 번째 장면",
                "두 번째 장면",
                "세 번째 장면",
                "네 번째 장면"
        );

        for (int i = 0; i < sceneSummaries.size(); i++) {
            Scene scene = Scene.builder()
                    .project(project)
                    .sceneOrder(i + 1)
                    .summary(sceneSummaries.get(i))
                    .status(SceneStatus.PENDING)
                    .build();

            sceneRepository.save(scene);
        }

        log.info("Project scenes generated successfully for projectId: {}", projectId);

        return ApiResponse.success("프로젝트 씬 생성 성공", sceneSummaries);
    }

    /**
     * 프로젝트 삭제
     */
    @Override
    @Transactional
    public void deleteProject(Long projectId, String loginId) {
        log.info("=== STARTING PROJECT DELETION ===");
        log.info("projectId={}, loginId={}", projectId, loginId);

        try {
            Project project = projectRepository.findByIdWithUser(projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

            if (!project.getUser().getLoginId().equals(loginId)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
            }

            List<Scene> scenes = sceneRepository.findByProjectId(projectId);
            log.info("Found {} scenes for projectId={}", scenes.size(), projectId);

            int totalDeletedSceneVideos = 0;
            int totalDeletedSceneImages = 0;

            for (Scene scene : scenes) {
                List<SceneVideo> sceneVideos = sceneVideoRepository.findBySceneIdOrderByCreatedAtDesc(scene.getId());
                if (!sceneVideos.isEmpty()) {
                    sceneVideoRepository.deleteAllBySceneId(scene.getId());
                    totalDeletedSceneVideos += sceneVideos.size();
                    log.debug("Deleted {} sceneVideos for sceneId={}", sceneVideos.size(), scene.getId());
                }

                List<SceneImage> sceneImages = sceneImageRepository.findBySceneIdOrderByImageNumberAsc(scene.getId());
                if (!sceneImages.isEmpty()) {
                    sceneImageRepository.deleteAllBySceneId(scene.getId());
                    totalDeletedSceneImages += sceneImages.size();
                    log.debug("Deleted {} sceneImages for sceneId={}", sceneImages.size(), scene.getId());
                }
            }

            if (!scenes.isEmpty()) {
                sceneRepository.deleteByProjectId(projectId);
                log.info("Deleted {} scenes for projectId={}", scenes.size(), projectId);
            }

            List<ProjectPlan> projectPlans = projectPlanRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
            if (!projectPlans.isEmpty()) {
                projectPlanRepository.deleteByProjectId(projectId);
                log.info("Deleted {} projectPlans for projectId={}", projectPlans.size(), projectId);
            }

            projectRepository.delete(project);

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