package com.annotation.satelliteannotationbackend.dto;

import java.util.List;

/**
 * 通过性分析结果 DTO
 */
public class AnalysisResultDTO {

    private Boolean isPassable;           // 是否可通过
    private String routeGeoJson;          // 路线 GeoJSON
    private List<ViolationPointDTO> violations;  // 禁行点列表
    private List<TurnPointDTO> turnPoints;       // 转弯点列表
    private List<RoadSegmentDTO> roadSegments;   // 路段列表
    private Double totalDistance;         // 总距离 (公里)
    private Double estimatedTime;         // 预计时间 (分钟)
    private String summary;               // 分析摘要

    // Getters and Setters

    public Boolean getIsPassable() {
        return isPassable;
    }

    public void setIsPassable(Boolean isPassable) {
        this.isPassable = isPassable;
    }

    public String getRouteGeoJson() {
        return routeGeoJson;
    }

    public void setRouteGeoJson(String routeGeoJson) {
        this.routeGeoJson = routeGeoJson;
    }

    public List<ViolationPointDTO> getViolations() {
        return violations;
    }

    public void setViolations(List<ViolationPointDTO> violations) {
        this.violations = violations;
    }

    public List<TurnPointDTO> getTurnPoints() {
        return turnPoints;
    }

    public void setTurnPoints(List<TurnPointDTO> turnPoints) {
        this.turnPoints = turnPoints;
    }

    public List<RoadSegmentDTO> getRoadSegments() {
        return roadSegments;
    }

    public void setRoadSegments(List<RoadSegmentDTO> roadSegments) {
        this.roadSegments = roadSegments;
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

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public static AnalysisResultDTO passable(String routeGeoJson, Double distance, Double time,
                                              List<TurnPointDTO> turnPoints, List<RoadSegmentDTO> roadSegments) {
        AnalysisResultDTO result = new AnalysisResultDTO();
        result.setIsPassable(true);
        result.setRouteGeoJson(routeGeoJson);
        result.setTotalDistance(distance);
        result.setEstimatedTime(time);
        result.setTurnPoints(turnPoints);
        result.setRoadSegments(roadSegments);
        result.setSummary("路线可通过，无禁行限制");
        return result;
    }

    public static AnalysisResultDTO notPassable(String routeGeoJson, List<ViolationPointDTO> violations,
                                                  Double distance, Double time, List<TurnPointDTO> turnPoints, List<RoadSegmentDTO> roadSegments) {
        AnalysisResultDTO result = new AnalysisResultDTO();
        result.setIsPassable(false);
        result.setRouteGeoJson(routeGeoJson);
        result.setViolations(violations);
        result.setTotalDistance(distance);
        result.setEstimatedTime(time);
        result.setTurnPoints(turnPoints);
        result.setRoadSegments(roadSegments);
        result.setSummary(String.format("发现 %d 处禁行点，请注意绕行", violations.size()));
        return result;
    }
}
