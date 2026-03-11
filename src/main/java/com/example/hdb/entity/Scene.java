package com.example.hdb.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "scenes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Scene {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    
    @Column(name = "scene_order", nullable = false)
    private Integer sceneOrder;
    
    @Column(nullable = false, length = 200)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(columnDefinition = "JSON")
    private String coreElements;
    
    @Column(columnDefinition = "JSON")
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
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
