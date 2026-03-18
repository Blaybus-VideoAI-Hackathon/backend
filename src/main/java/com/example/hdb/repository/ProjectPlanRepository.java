package com.example.hdb.repository;

import com.example.hdb.entity.ProjectPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectPlanRepository extends JpaRepository<ProjectPlan, Long> {

    List<ProjectPlan> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<ProjectPlan> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);

    boolean existsByProjectIdAndVersion(Long projectId, Integer version);
}
