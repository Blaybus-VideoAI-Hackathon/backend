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
    
    // Scene에 속한 모든 이미지 삭제
    void deleteAllBySceneId(Long sceneId);
    
    // imageId와 sceneId로 이미지 찾기
    Optional<SceneImage> findByIdAndSceneId(Long imageId, Long sceneId);
}
