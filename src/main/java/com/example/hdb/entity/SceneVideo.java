package com.example.hdb.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(name = "scene_videos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SceneVideo extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Column(length = 500)
    private String videoUrl;

    @Column(length = 100)
    private String openaiVideoId;
    
    @Column(length = 100)
    private String klingTaskId;

    @Column(length = 1000)
    private String videoPrompt;
    
    @Column(name = "duration")
    private Integer duration;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private VideoStatus status;

    public enum VideoStatus {
        GENERATING("생성 중"),
        READY("준비 완료"),
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
