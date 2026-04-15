package com.annotation.satelliteannotationbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 道路约束实体
 * 存储道路的限高、限重、限宽等约束信息
 */
@Entity
@Table(name = "road_constraints")
public class RoadConstraint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "road_network_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private RoadNetwork roadNetwork;

    @Column(name = "osm_way_id", length = 50)
    private String osmWayId;          // OSM 道路 ID

    @Column(name = "road_name", length = 255)
    private String roadName;          // 道路名称

    @Column(name = "max_height")
    private Double maxHeight;         // 限高 (米)

    @Column(name = "max_width")
    private Double maxWidth;          // 限宽 (米)

    @Column(name = "max_weight")
    private Double maxWeight;         // 限重 (吨)

    @Column(name = "max_axle_weight")
    private Double maxAxleWeight;     // 轴重限制 (吨)

    @Column(name = "min_length")
    private Double minLength;         // 长度限制 (米)

    @Column(name = "restriction_type", length = 50)
    private String restrictionType;   // 限制类型：no_trucks, weight_limit, height_limit 等

    @Column(name = "time_restriction", length = 100)
    private String timeRestriction;   // 时间限制（如"6:00-22:00"）

    @Column(columnDefinition = "GEOMETRY")
    private Object geometry;          // 道路几何 (PostGIS Geometry)

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

    public RoadNetwork getRoadNetwork() {
        return roadNetwork;
    }

    public void setRoadNetwork(RoadNetwork roadNetwork) {
        this.roadNetwork = roadNetwork;
    }

    public String getOsmWayId() {
        return osmWayId;
    }

    public void setOsmWayId(String osmWayId) {
        this.osmWayId = osmWayId;
    }

    public String getRoadName() {
        return roadName;
    }

    public void setRoadName(String roadName) {
        this.roadName = roadName;
    }

    public Double getMaxHeight() {
        return maxHeight;
    }

    public void setMaxHeight(Double maxHeight) {
        this.maxHeight = maxHeight;
    }

    public Double getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(Double maxWidth) {
        this.maxWidth = maxWidth;
    }

    public Double getMaxWeight() {
        return maxWeight;
    }

    public void setMaxWeight(Double maxWeight) {
        this.maxWeight = maxWeight;
    }

    public Double getMaxAxleWeight() {
        return maxAxleWeight;
    }

    public void setMaxAxleWeight(Double maxAxleWeight) {
        this.maxAxleWeight = maxAxleWeight;
    }

    public Double getMinLength() {
        return minLength;
    }

    public void setMinLength(Double minLength) {
        this.minLength = minLength;
    }

    public String getRestrictionType() {
        return restrictionType;
    }

    public void setRestrictionType(String restrictionType) {
        this.restrictionType = restrictionType;
    }

    public String getTimeRestriction() {
        return timeRestriction;
    }

    public void setTimeRestriction(String timeRestriction) {
        this.timeRestriction = timeRestriction;
    }

    public Object getGeometry() {
        return geometry;
    }

    public void setGeometry(Object geometry) {
        this.geometry = geometry;
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
