package com.annotation.satelliteannotationbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 路网数据实体
 * 存储从 OpenStreetMap 下载的道路网络数据
 */
@Entity
@Table(name = "road_networks")
public class RoadNetwork {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;              // 路网名称

    @Column(length = 255)
    private String region;            // 区域名称

    @Column(name = "min_lat")
    private Double minLat;            // bounding box - 最小纬度

    @Column(name = "min_lon")
    private Double minLon;            // 最小经度

    @Column(name = "max_lat")
    private Double maxLat;            // 最大纬度

    @Column(name = "max_lon")
    private Double maxLon;            // 最大经度

    @Column(columnDefinition = "GEOMETRY")
    private Object coverageArea;      // 覆盖范围 (PostGIS Geometry)

    @Column(name = "geojson_path", length = 500)
    private String geojsonPath;       // GeoJSON 文件存储路径

    @Column(name = "total_roads")
    private Integer totalRoads;       // 道路总数

    @Column(name = "download_date")
    private LocalDateTime downloadDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "downloaded_by")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User downloadedBy;        // 下载用户

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Object getCoverageArea() {
        return coverageArea;
    }

    public void setCoverageArea(Object coverageArea) {
        this.coverageArea = coverageArea;
    }

    public String getGeojsonPath() {
        return geojsonPath;
    }

    public void setGeojsonPath(String geojsonPath) {
        this.geojsonPath = geojsonPath;
    }

    public Integer getTotalRoads() {
        return totalRoads;
    }

    public void setTotalRoads(Integer totalRoads) {
        this.totalRoads = totalRoads;
    }

    public LocalDateTime getDownloadDate() {
        return downloadDate;
    }

    public void setDownloadDate(LocalDateTime downloadDate) {
        this.downloadDate = downloadDate;
    }

    public User getDownloadedBy() {
        return downloadedBy;
    }

    public void setDownloadedBy(User downloadedBy) {
        this.downloadedBy = downloadedBy;
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
