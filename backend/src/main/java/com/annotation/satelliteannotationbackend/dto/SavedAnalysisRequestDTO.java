package com.annotation.satelliteannotationbackend.dto;

import java.util.List;

/**
 * 保存分析结果请求 DTO
 */
public class SavedAnalysisRequestDTO {

    private String name;
    private String startPoint;
    private String endPoint;
    private Double startLat;
    private Double startLon;
    private Double endLat;
    private Double endLon;
    private TruckParametersDTO truckParams;
    private String routeGeoJson;
    private List<TurnPointDTO> turnPoints;
    private List<ViolationPointDTO> violations;
    private Double totalDistance;
    private Double estimatedTime;

    // Getters and Setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStartPoint() {
        return startPoint;
    }

    public void setStartPoint(String startPoint) {
        this.startPoint = startPoint;
    }

    public String getEndPoint() {
        return endPoint;
    }

    public void setEndPoint(String endPoint) {
        this.endPoint = endPoint;
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

    public TruckParametersDTO getTruckParams() {
        return truckParams;
    }

    public void setTruckParams(TruckParametersDTO truckParams) {
        this.truckParams = truckParams;
    }

    public String getRouteGeoJson() {
        return routeGeoJson;
    }

    public void setRouteGeoJson(String routeGeoJson) {
        this.routeGeoJson = routeGeoJson;
    }

    public List<TurnPointDTO> getTurnPoints() {
        return turnPoints;
    }

    public void setTurnPoints(List<TurnPointDTO> turnPoints) {
        this.turnPoints = turnPoints;
    }

    public List<ViolationPointDTO> getViolations() {
        return violations;
    }

    public void setViolations(List<ViolationPointDTO> violations) {
        this.violations = violations;
    }

    public Double getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(Double totalDistance) {
        this.totalDistance = totalDistance;
    }

    public Double getEstimatedTime() {
        return estimatedTime;
    }

    public void setEstimatedTime(Double estimatedTime) {
        this.estimatedTime = estimatedTime;
    }
}
