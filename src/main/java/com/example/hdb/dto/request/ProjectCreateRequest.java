package com.example.hdb.dto.request;

import com.example.hdb.entity.PlanningStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectCreateRequest {
    
    @NotBlank(message = "Project title is required")
    @Size(max = 200, message = "Project title must be less than 200 characters")
    private String title;
    
    @Size(max = 2000, message = "Idea must be less than 2000 characters")
    private String idea;
    
    @Size(max = 100, message = "Style must be less than 100 characters")
    private String style;
    
    @Size(max = 20, message = "Ratio must be less than 20 characters")
    private String ratio;
    
    private PlanningStatus planningStatus;
}
