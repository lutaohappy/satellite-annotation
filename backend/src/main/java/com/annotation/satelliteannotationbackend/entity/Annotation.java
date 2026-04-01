package com.annotation.satelliteannotationbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 标注要素实体
 */
@Entity
@Table(name = "annotations")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Annotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String type;  // Point, LineString, Polygon, Circle, Rectangle

    @Column(columnDefinition = "TEXT")
    private String geometry;  // GeoJSON Geometry

    @Column(columnDefinition = "TEXT")
    private String properties;  // GeoJSON Properties

    @Column(nullable = true)
    private String category;  // 标注分类

    private String symbolId;  // 符号 ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private Long projectId;  // 所属项目 ID

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
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getGeometry() { return geometry; }
    public void setGeometry(String geometry) { this.geometry = geometry; }
    public String getProperties() { return properties; }
    public void setProperties(String properties) { this.properties = properties; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSymbolId() { return symbolId; }
    public void setSymbolId(String symbolId) { this.symbolId = symbolId; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
