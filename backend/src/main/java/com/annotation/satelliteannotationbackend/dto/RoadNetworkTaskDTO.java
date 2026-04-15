package com.annotation.satelliteannotationbackend.dto;

import com.annotation.satelliteannotationbackend.entity.RoadNetworkDownloadTask;
import com.annotation.satelliteannotationbackend.enums.TaskStatus;

import java.time.LocalDateTime;

/**
 * 下载任务 DTO
 */
public class RoadNetworkTaskDTO {

    private Long id;
    private String taskName;
    private String region;
    private Double minLat;
    private Double minLon;
    private Double maxLat;
    private Double maxLon;
    private TaskStatus status;
    private Integer progress;
    private Long roadNetworkId;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    public static RoadNetworkTaskDTO fromEntity(RoadNetworkDownloadTask task) {
        RoadNetworkTaskDTO dto = new RoadNetworkTaskDTO();
        dto.setId(task.getId());
        dto.setTaskName(task.getTaskName());
        dto.setRegion(task.getRegion());
        dto.setMinLat(task.getMinLat());
        dto.setMinLon(task.getMinLon());
        dto.setMaxLat(task.getMaxLat());
        dto.setMaxLon(task.getMaxLon());
        dto.setStatus(task.getStatus());
        dto.setProgress(task.getProgress());
        dto.setRoadNetworkId(task.getRoadNetworkId());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setStartedAt(task.getStartedAt());
        dto.setCompletedAt(task.getCompletedAt());
        dto.setCreatedAt(task.getCreatedAt());
        return dto;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Double getMinLat() {
        return minLat;
    }

    public void setMinLat(Double minLat) {
        this.minLat = minLat;
    }

    public Double getMinLon() {
        return minLon;
    }

    public void setMinLon(Double minLon) {
        this.minLon = minLon;
    }

    public Double getMaxLat() {
        return maxLat;
    }

    public void setMaxLat(Double maxLat) {
        this.maxLat = maxLat;
    }

    public Double getMaxLon() {
        return maxLon;
    }

    public void setMaxLon(Double maxLon) {
        this.maxLon = maxLon;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Long getRoadNetworkId() {
        return roadNetworkId;
    }

    public void setRoadNetworkId(Long roadNetworkId) {
        this.roadNetworkId = roadNetworkId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
