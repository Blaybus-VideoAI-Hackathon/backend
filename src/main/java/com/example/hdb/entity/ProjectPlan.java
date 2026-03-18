package com.example.hdb.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_plans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private Integer version;

    @Column(columnDefinition = "JSON")
    private String planData;

    @Column(nullable = false, length = 1000)
    private String userPrompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum PlanStatus {
        DRAFT("초안"),
        APPROVED("승인됨"),
        REVISION("수정됨");

        private final String description;

        PlanStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
