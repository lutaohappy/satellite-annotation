package com.annotation.satelliteannotationbackend.dto;

import java.time.LocalDateTime;

/**
 * 路网数据 DTO
 */
public class RoadNetworkDTO {

    private Long id;
    private String name;
    private String region;
    private Double minLat;
    private Double minLon;
    private Double maxLat;
    private Double maxLon;
    private Integer totalRoads;
    private LocalDateTime downloadDate;
    private LocalDateTime createdAt;
    private String downloadedByName;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getDownloadedByName() {
        return downloadedByName;
    }

    public void setDownloadedByName(String downloadedByName) {
        this.downloadedByName = downloadedByName;
    }

    public static RoadNetworkDTO fromEntity(com.annotation.satelliteannotationbackend.entity.RoadNetwork entity) {
        RoadNetworkDTO dto = new RoadNetworkDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setRegion(entity.getRegion());
        dto.setMinLat(entity.getMinLat());
        dto.setMinLon(entity.getMinLon());
        dto.setMaxLat(entity.getMaxLat());
        dto.setMaxLon(entity.getMaxLon());
        dto.setTotalRoads(entity.getTotalRoads());
        dto.setDownloadDate(entity.getDownloadDate());
        dto.setCreatedAt(entity.getCreatedAt());
        if (entity.getDownloadedBy() != null) {
            dto.setDownloadedByName(entity.getDownloadedBy().getUsername());
        }
        return dto;
    }
}
