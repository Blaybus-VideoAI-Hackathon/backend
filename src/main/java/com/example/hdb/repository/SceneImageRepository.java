package com.example.hdb.repository;

import com.example.hdb.entity.SceneImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SceneImageRepository extends JpaRepository<SceneImage, Long> {

    List<SceneImage> findBySceneIdOrderByImageNumberAsc(Long sceneId);

    Optional<SceneImage> findFirstBySceneIdOrderByImageNumberDesc(Long sceneId);

    List<SceneImage> findByStatus(SceneImage.ImageStatus status);
}
