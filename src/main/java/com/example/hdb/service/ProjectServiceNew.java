package com.example.hdb.service;

import com.example.hdb.dto.request.ProjectCreateRequest;
import com.example.hdb.dto.response.ProjectResponse;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.ProjectStatus;
import com.example.hdb.entity.User;
import com.example.hdb.exception.BusinessException;
import com.example.hdb.exception.ErrorCode;
import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectServiceNew {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public Project createProject(String loginId, ProjectCreateRequest request) {
        // 사용자 존재 확인
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 프로젝트 생성
        Project project = Project.builder()
                .user(user)
                .title(request.getTitle())
                .style(request.getStyle())
                .ratio(request.getRatio())
                .status(ProjectStatus.PLANNING)
                .build();

        Project savedProject = projectRepository.save(project);
        
        log.info("프로젝트 생성 완료 - ID: {}, 제목: {}, 사용자: {}", 
                savedProject.getId(), savedProject.getTitle(), loginId);

        return savedProject;
    }

    public List<Project> getUserProjects(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Project> projects = projectRepository.findByUserOrderByCreatedAtDesc(user);
        
        log.info("사용자 프로젝트 조회 완료 - 사용자: {}, 프로젝트 수: {}", 
                loginId, projects.size());

        return projects;
    }

    public Project getProject(String loginId, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        // 권한 확인
        if (!project.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        log.info("프로젝트 상세 조회 완료 - ID: {}, 사용자: {}", projectId, loginId);

        return project;
    }

    public Project updateProjectStatus(Long projectId, ProjectStatus status) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        project.setStatus(status);
        Project updatedProject = projectRepository.save(project);

        log.info("프로젝트 상태 업데이트 - ID: {}, 상태: {}", 
                projectId, status);

        return updatedProject;
    }
}
