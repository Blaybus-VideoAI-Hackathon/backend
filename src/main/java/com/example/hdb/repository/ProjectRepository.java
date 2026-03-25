package com.example.hdb.repository;

import com.example.hdb.entity.PlanningStatus;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByPlanningStatus(PlanningStatus planningStatus);

    List<Project> findByPlanningStatusOrderByCreatedAtDesc(PlanningStatus planningStatus);

    List<Project> findByUserId(Long userId);

    /**
     * 프로젝트 목록 조회 시 user 함께 로딩
     */
    @EntityGraph(attributePaths = {"user"})
    List<Project> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 프로젝트 목록 조회 시 user 함께 로딩
     */
    @EntityGraph(attributePaths = {"user"})
    List<Project> findByUserOrderByCreatedAtDesc(User user);

    /**
     * 프로젝트 상세 조회 시 user 함께 로딩
     * LazyInitializationException 방지용
     */
    @EntityGraph(attributePaths = {"user"})
    Optional<Project> findByIdWithUser(Long id);

    /**
     * 특정 유저의 특정 프로젝트 조회 시 user 함께 로딩
     */
    @EntityGraph(attributePaths = {"user"})
    Optional<Project> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);
}