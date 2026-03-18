package com.example.hdb.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@Entity
@Table(name = "scene_images")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Column(nullable = false)
    private Integer imageNumber;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(length = 1000)
    private String imagePrompt;

    @Column(length = 100)
    private String openaiImageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ImageStatus {
        GENERATING("생성 중"),
        READY("준비 완료"),
        FAILED("실패");

        private final String description;

        ImageStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
