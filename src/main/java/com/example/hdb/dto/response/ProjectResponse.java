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
    private String title;
    private String idea;
    private String style;
    private String ratio;
    private String planningStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProjectResponse from(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .idea(project.getIdea())
                .style(project.getStyle())
                .ratio(project.getRatio())
                .planningStatus(project.getPlanningStatus().name())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
