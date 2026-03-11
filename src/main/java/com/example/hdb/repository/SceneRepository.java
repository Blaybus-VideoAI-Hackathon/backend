package com.example.hdb.repository;

import com.example.hdb.entity.Scene;
import com.example.hdb.entity.SceneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SceneRepository extends JpaRepository<Scene, Long> {
    List<Scene> findByProjectId(Long projectId);
    List<Scene> findByProjectIdOrderBySceneOrder(Long projectId);
    List<Scene> findByStatus(SceneStatus status);
    List<Scene> findByProjectIdAndStatus(Long projectId, SceneStatus status);
}
