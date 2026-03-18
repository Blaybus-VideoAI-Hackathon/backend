package com.example.hdb.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Entity
@Table(name = "scene_videos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Column(nullable = false, length = 500)
    private String videoUrl;

    @Column(length = 100)
    private String openaiVideoId;

    @Column(length = 1000)
    private String videoPrompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum VideoStatus {
        PENDING("대기 중"),
        PROCESSING("처리 중"),
        COMPLETED("완료"),
        FAILED("실패");

        private final String description;

        VideoStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
