package com.example.hdb.repository;

import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SceneRepository extends JpaRepository<Scene, Long> {
    List<Scene> findByProjectId(Long projectId);
    List<Scene> findByProjectIdOrderBySceneOrder(Long projectId);
    List<Scene> findByProjectIdOrderBySceneOrderAsc(Long projectId);
    List<Scene> findByStatus(SceneStatus status);
    List<Scene> findByProjectIdAndStatus(Long projectId, SceneStatus status);
    
    boolean existsByProjectIdAndSceneOrder(Long projectId, Integer sceneOrder);
    
    Optional<Scene> findByIdAndProjectUserId(Long sceneId, Long userId);
    
    @Modifying
    @Query("DELETE FROM Scene s WHERE s.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);
}
