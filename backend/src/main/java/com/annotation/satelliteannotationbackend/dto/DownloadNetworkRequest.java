package com.annotation.satelliteannotationbackend.dto;

/**
 * 下载路网数据请求
 */
public class DownloadNetworkRequest {

    private String name;              // 路网名称
    private String region;            // 区域名称
    private Double minLat;            // 最小纬度
    private Double minLon;            // 最小经度
    private Double maxLat;            // 最大纬度
    private Double maxLon;            // 最大经度

    // Getters and Setters

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
}
