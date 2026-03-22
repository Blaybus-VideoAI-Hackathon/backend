package com.example.hdb.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(name = "scene_images")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SceneImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Column(nullable = false)
    private Integer imageNumber;

    @Column(length = 500)
    private String imageUrl;
    
    @Column(name = "edited_image_url", length = 500)
    private String editedImageUrl;

    @Column(length = 1000)
    private String imagePrompt;

    @Column(length = 100)
    private String openaiImageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ImageStatus status;

    public enum ImageStatus {
        GENERATING("생성 중"),
        READY("준비 완료"),
        COMPLETED("생성 완료"),
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
