package com.annotation.satelliteannotationbackend.entity;

import com.annotation.satelliteannotationbackend.enums.TaskStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 路网下载任务实体
 */
@Entity
@Table(name = "road_network_download_tasks")
public class RoadNetworkDownloadTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_name", nullable = false, length = 255)
    private String taskName;

    @Column(name = "region", length = 255)
    private String region;

    @Column(name = "min_lat")
    private Double minLat;

    @Column(name = "min_lon")
    private Double minLon;

    @Column(name = "max_lat")
    private Double maxLat;

    @Column(name = "max_lon")
    private Double maxLon;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TaskStatus status;

    @Column(name = "progress")
    private Integer progress;

    @Column(name = "road_network_id")
    private Long roadNetworkId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User createdBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
