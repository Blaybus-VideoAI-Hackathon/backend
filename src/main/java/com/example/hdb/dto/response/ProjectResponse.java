package com.example.hdb.dto.response;

import com.example.hdb.entity.Project;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponse {
    private Long id;
    private Long userId;
    private String title;
    private String style;
    private String ratio;
    private String purpose;
    private Integer duration;
    private String ideaText;
    private String coreElements;
    private String planningStatus;
    private String finalVideoUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProjectResponse from(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .userId(project.getUser() != null ? project.getUser().getId() : null)
                .title(project.getTitle())
                .style(project.getStyle())
                .ratio(project.getRatio())
                .purpose(project.getPurpose())
                .duration(project.getDuration())
                .ideaText(project.getIdeaText())
                .coreElements(project.getCoreElements())
                .planningStatus(project.getPlanningStatus().name())
                .finalVideoUrl(project.getFinalVideoUrl())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
