package com.annotation.satelliteannotationbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 卫星影像实体
 */
@Entity
@Table(name = "images")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String filePath;

    private String crs;  // 坐标系

    // 影像范围 (EPSG:3857)
    private Double minX;
    private Double minY;
    private Double maxX;
    private Double maxY;

    private Long fileSize;  // 文件大小 (bytes)

    private Integer width;  // 影像宽度 (像素)
    private Integer height; // 影像高度 (像素)
    private Integer numBands; // 波段数

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User user;

    private Long projectId;  // 所属项目 ID

    private String batchId;  // 上传批次 ID

    private Boolean hasGeoreference;  // 是否有地理参考信息

    private String adjustmentParams;  // 调整参数 (JSON 格式)

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getCrs() { return crs; }
    public void setCrs(String crs) { this.crs = crs; }
    public Double getMinX() { return minX; }
    public void setMinX(Double minX) { this.minX = minX; }
    public Double getMinY() { return minY; }
    public void setMinY(Double minY) { this.minY = minY; }
    public Double getMaxX() { return maxX; }
    public void setMaxX(Double maxX) { this.maxX = maxX; }
    public Double getMaxY() { return maxY; }
    public void setMaxY(Double maxY) { this.maxY = maxY; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public Integer getNumBands() { return numBands; }
    public void setNumBands(Integer numBands) { this.numBands = numBands; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public Boolean getHasGeoreference() { return hasGeoreference; }
    public void setHasGeoreference(Boolean hasGeoreference) { this.hasGeoreference = hasGeoreference; }
    public String getAdjustmentParams() { return adjustmentParams; }
    public void setAdjustmentParams(String adjustmentParams) { this.adjustmentParams = adjustmentParams; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
