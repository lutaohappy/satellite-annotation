package com.annotation.satelliteannotationbackend.dto;

/**
 * 货车分析请求 DTO
 */
public class TruckAnalysisRequestDTO {

    private String requestName;       // 请求名称
    private Double startLat;          // 起点纬度
    private Double startLon;          // 起点经度
    private Double endLat;            // 终点纬度
    private Double endLon;            // 终点经度
    private Long roadNetworkId;       // 路网 ID
    private TruckParametersDTO truck; // 货车参数

    // Getters and Setters

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

    public Long getRoadNetworkId() {
        return roadNetworkId;
    }

    public void setRoadNetworkId(Long roadNetworkId) {
        this.roadNetworkId = roadNetworkId;
    }

    public TruckParametersDTO getTruck() {
        return truck;
    }

    public void setTruck(TruckParametersDTO truck) {
        this.truck = truck;
    }
}
