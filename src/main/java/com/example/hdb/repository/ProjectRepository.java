package com.example.hdb.repository;

import com.example.hdb.entity.PlanningStatus;
import com.example.hdb.entity.Project;
import com.example.hdb.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByPlanningStatus(PlanningStatus planningStatus);
    List<Project> findByPlanningStatusOrderByCreatedAtDesc(PlanningStatus planningStatus);
    List<Project> findByUserId(Long userId);
    List<Project> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Project> findByUserOrderByCreatedAtDesc(User user);
    
    Optional<Project> findByIdAndUserId(Long id, Long userId);
    boolean existsByIdAndUserId(Long id, Long userId);
}
