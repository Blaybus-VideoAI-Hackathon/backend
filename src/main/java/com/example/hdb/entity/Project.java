package com.example.hdb.entity;

import com.example.hdb.enums.PlanningStatus;
import com.example.hdb.entity.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false, length = 200)
    private String title;
    
    @Column(length = 100)
    private String style;
    
    @Column(length = 20)
    private String ratio;
    
    @Column(length = 500)
    private String purpose;
    
    @Column
    private Integer duration;
    
    @Column(columnDefinition = "TEXT")
    private String ideaText;
    
    @Column(columnDefinition = "TEXT")
    private String coreElements;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanningStatus planningStatus;
    
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Scene> scenes;
    
    // 기획/생성 상태를 위한 새로운 필드
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.PLANNING;
    
    // 최종 병합 영상 URL
    @Column(name = "final_video_url", length = 500)
    private String finalVideoUrl;
}
