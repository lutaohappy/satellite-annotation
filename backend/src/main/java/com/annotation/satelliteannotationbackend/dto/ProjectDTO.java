package com.annotation.satelliteannotationbackend.dto;

import java.time.LocalDateTime;

/**
 * 项目数据传输对象
 */
public class ProjectDTO {
    private Long id;
    private String name;
    private String description;
    private Long userId;
    private String crs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long annotationCount;  // 标注数量

    public ProjectDTO() {}

    public ProjectDTO(ProjectDTO project) {
        this.id = project.id;
        this.name = project.name;
        this.description = project.description;
        this.userId = project.userId;
        this.crs = project.crs;
        this.createdAt = project.createdAt;
        this.updatedAt = project.updatedAt;
        this.annotationCount = project.annotationCount;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCrs() { return crs; }
    public void setCrs(String crs) { this.crs = crs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getAnnotationCount() { return annotationCount; }
    public void setAnnotationCount(Long annotationCount) { this.annotationCount = annotationCount; }
}
