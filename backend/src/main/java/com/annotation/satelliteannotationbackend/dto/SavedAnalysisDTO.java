package com.annotation.satelliteannotationbackend.dto;

import com.annotation.satelliteannotationbackend.entity.TruckAnalysisRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 已保存的分析结果 DTO
 */
public class SavedAnalysisDTO {

    private Long id;
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
    private Boolean isPassable;
    private List<RoadSegmentDTO> roadSegments;  // 路段列表
    private LocalDateTime createdAt;
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

    public Boolean getIsPassable() {
        return isPassable;
    }

    public void setIsPassable(Boolean isPassable) {
        this.isPassable = isPassable;
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

    public List<RoadSegmentDTO> getRoadSegments() {
        return roadSegments;
    }

    public void setRoadSegments(List<RoadSegmentDTO> roadSegments) {
        this.roadSegments = roadSegments;
    }

    /**
     * 从实体转换为 DTO
     */
    public static SavedAnalysisDTO fromEntity(TruckAnalysisRequest entity) {
        SavedAnalysisDTO dto = new SavedAnalysisDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getRequestName());
        dto.setStartPoint(String.format("%.4f, %.4f", entity.getStartLat(), entity.getStartLon()));
        dto.setEndPoint(String.format("%.4f, %.4f", entity.getEndLat(), entity.getEndLon()));
        dto.setStartLat(entity.getStartLat());
        dto.setStartLon(entity.getStartLon());
        dto.setEndLat(entity.getEndLat());
        dto.setEndLon(entity.getEndLon());

        // 设置货车参数
        TruckParametersDTO truckParams = new TruckParametersDTO();
        truckParams.setLength(entity.getTruckLength());
        truckParams.setWidth(entity.getTruckWidth());
        truckParams.setHeight(entity.getTruckHeight());
        truckParams.setWeight(entity.getTruckWeight());
        truckParams.setAxleWeight(entity.getTruckAxleWeight());
        truckParams.setWheelbase(entity.getWheelbase());
        dto.setTruckParams(truckParams);

        dto.setRouteGeoJson(entity.getRouteGeoJson());
        dto.setIsPassable(entity.getIsPassable());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // 解析禁行点 JSON
        if (entity.getViolationPoints() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                List<ViolationPointDTO> violations = mapper.readValue(
                    entity.getViolationPoints(),
                    new TypeReference<List<ViolationPointDTO>>() {}
                );
                dto.setViolations(violations);
            } catch (Exception e) {
                // 忽略解析错误
            }
        }

        // 解析转弯点 JSON
        if (entity.getTurnPoints() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                List<TurnPointDTO> turnPoints = mapper.readValue(
                    entity.getTurnPoints(),
                    new TypeReference<List<TurnPointDTO>>() {}
                );
                dto.setTurnPoints(turnPoints);
            } catch (Exception e) {
                // 忽略解析错误
            }
        }

        return dto;
    }

    /**
     * 从保存请求创建实体
     */
    public static TruckAnalysisRequest toEntity(SavedAnalysisRequestDTO request, Long userId) {
        TruckAnalysisRequest entity = new TruckAnalysisRequest();
        entity.setRequestName(request.getName());
        entity.setStartLat(request.getStartLat());
        entity.setStartLon(request.getStartLon());
        entity.setEndLat(request.getEndLat());
        entity.setEndLon(request.getEndLon());

        if (request.getTruckParams() != null) {
            entity.setTruckLength(request.getTruckParams().getLength());
            entity.setTruckWidth(request.getTruckParams().getWidth());
            entity.setTruckHeight(request.getTruckParams().getHeight());
            entity.setTruckWeight(request.getTruckParams().getWeight());
            entity.setTruckAxleWeight(request.getTruckParams().getAxleWeight());
            entity.setWheelbase(request.getTruckParams().getWheelbase());
        }

        entity.setRouteGeoJson(request.getRouteGeoJson());
        entity.setIsPassable(request.getViolations() == null || request.getViolations().isEmpty());

        // 保存禁行点为 JSON
        if (request.getViolations() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                entity.setViolationPoints(mapper.writeValueAsString(request.getViolations()));
            } catch (Exception e) {
                // 忽略序列化错误
            }
        }

        // 保存转弯点为 JSON
        if (request.getTurnPoints() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                entity.setTurnPoints(mapper.writeValueAsString(request.getTurnPoints()));
            } catch (Exception e) {
                // 忽略序列化错误
            }
        }

        return entity;
    }
}
