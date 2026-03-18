package com.example.hdb.entity;

import com.example.hdb.enums.SceneStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "scenes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Scene extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    
    @Column(name = "scene_order", nullable = false)
    private Integer sceneOrder;
    
    @Column(nullable = false, length = 200)
    private String summary;
    
    @Column(columnDefinition = "TEXT")
    private String optionalElements;
    
    @Column(name = "image_prompt", columnDefinition = "TEXT")
    private String imagePrompt;
    
    @Column(name = "video_prompt", columnDefinition = "TEXT")
    private String videoPrompt;
    
    @Column(name = "image_url", length = 500)
    private String imageUrl;
    
    @Column(name = "video_url", length = 500)
    private String videoUrl;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SceneStatus status;
    
    // 이미지 관계 추가
    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SceneImage> images;
    
    // 영상 관계 추가
    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SceneVideo> videos;
}
