package com.annotation.satelliteannotationbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 货车分析请求实体
 * 存储货车通过性分析的申请和结果
 */
@Entity
@Table(name = "truck_analysis_requests")
public class TruckAnalysisRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_name", length = 255)
    private String requestName;       // 请求名称

    @Column(name = "start_lat", nullable = false)
    private Double startLat;          // 起点纬度

    @Column(name = "start_lon", nullable = false)
    private Double startLon;          // 起点经度

    @Column(name = "end_lat", nullable = false)
    private Double endLat;            // 终点纬度

    @Column(name = "end_lon", nullable = false)
    private Double endLon;            // 终点经度

    @Column(name = "truck_length")
    private Double truckLength;       // 车长 (米)

    @Column(name = "truck_width")
    private Double truckWidth;        // 车宽 (米)

    @Column(name = "truck_height")
    private Double truckHeight;       // 车高 (米)

    @Column(name = "truck_weight")
    private Double truckWeight;       // 总重 (吨)

    @Column(name = "truck_axle_weight")
    private Double truckAxleWeight;   // 轴重 (吨)

    @Column(name = "wheelbase")
    private Double wheelbase;         // 轴距 (米)

    @Column(name = "route_geojson", columnDefinition = "TEXT")
    private String routeGeoJson;      // 路线 GeoJSON

    @Column(name = "is_passable")
    private Boolean isPassable;       // 是否可通过

    @Column(name = "violation_points", columnDefinition = "TEXT")
    private String violationPoints;   // 禁行点 JSON

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "road_network_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private RoadNetwork roadNetwork;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User createdBy;

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

    public String getRequestName() {
        return requestName;
    }

    public void setRequestName(String requestName) {
        this.requestName = requestName;
    }

    public Double getStartLat() {
        return startLat;
    }

    public void setStartLat(Double startLat) {
        this.startLat = startLat;
    }

    public Double getStartLon() {
        return startLon;
    }

    public void setStartLon(Double startLon) {
        this.startLon = startLon;
    }

    public Double getEndLat() {
        return endLat;
    }

    public void setEndLat(Double endLat) {
        this.endLat = endLat;
    }

    public Double getEndLon() {
        return endLon;
    }

    public void setEndLon(Double endLon) {
        this.endLon = endLon;
    }

    public Double getTruckLength() {
        return truckLength;
    }

    public void setTruckLength(Double truckLength) {
        this.truckLength = truckLength;
    }

    public Double getTruckWidth() {
        return truckWidth;
    }

    public void setTruckWidth(Double truckWidth) {
        this.truckWidth = truckWidth;
    }

    public Double getTruckHeight() {
        return truckHeight;
    }

    public void setTruckHeight(Double truckHeight) {
        this.truckHeight = truckHeight;
    }

    public Double getTruckWeight() {
        return truckWeight;
    }

    public void setTruckWeight(Double truckWeight) {
        this.truckWeight = truckWeight;
    }

    public Double getTruckAxleWeight() {
        return truckAxleWeight;
    }

    public void setTruckAxleWeight(Double truckAxleWeight) {
        this.truckAxleWeight = truckAxleWeight;
    }

    public Double getWheelbase() {
        return wheelbase;
    }

    public void setWheelbase(Double wheelbase) {
        this.wheelbase = wheelbase;
    }

    public String getRouteGeoJson() {
        return routeGeoJson;
    }

    public void setRouteGeoJson(String routeGeoJson) {
        this.routeGeoJson = routeGeoJson;
    }

    public Boolean getIsPassable() {
        return isPassable;
    }

    public void setIsPassable(Boolean isPassable) {
        this.isPassable = isPassable;
    }

    public String getViolationPoints() {
        return violationPoints;
    }

    public void setViolationPoints(String violationPoints) {
        this.violationPoints = violationPoints;
    }

    public RoadNetwork getRoadNetwork() {
        return roadNetwork;
    }

    public void setRoadNetwork(RoadNetwork roadNetwork) {
        this.roadNetwork = roadNetwork;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
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
