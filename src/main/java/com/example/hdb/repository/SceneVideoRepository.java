package com.example.hdb.repository;

import com.example.hdb.entity.SceneVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SceneVideoRepository extends JpaRepository<SceneVideo, Long> {

    List<SceneVideo> findBySceneIdOrderByCreatedAtDesc(Long sceneId);

    Optional<SceneVideo> findFirstBySceneIdOrderByCreatedAtDesc(Long sceneId);

    List<SceneVideo> findByStatus(SceneVideo.VideoStatus status);
}
