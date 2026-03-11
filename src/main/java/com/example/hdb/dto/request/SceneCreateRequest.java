package com.example.hdb.dto.request;

import com.example.hdb.entity.SceneStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SceneCreateRequest {
    
    @NotNull(message = "Project ID is required")
    private Long projectId;
    
    @NotNull(message = "Scene order is required")
    @Min(value = 1, message = "Scene order must be at least 1")
    private Integer sceneOrder;
    
    @NotBlank(message = "Scene title is required")
    @Size(max = 200, message = "Scene title must be less than 200 characters")
    private String title;
    
    @Size(max = 2000, message = "Description must be less than 2000 characters")
    private String description;
    
    private String coreElements;
    private String optionalElements;
    
    @Size(max = 2000, message = "Image prompt must be less than 2000 characters")
    private String imagePrompt;
    
    @Size(max = 2000, message = "Video prompt must be less than 2000 characters")
    private String videoPrompt;
    
    private SceneStatus status;
}
