package com.annotation.satelliteannotationbackend.dto;

import java.util.List;

/**
 * 禁行点 DTO
 */
public class ViolationPointDTO {

    private Double lat;               // 纬度
    private Double lon;               // 经度
    private String reason;            // 原因（限高、限重、限宽、转弯半径不足）
    private String detail;            // 详细信息
    private Double constraintValue;   // 约束值
    private Double truckValue;        // 车辆值
    private Double turnAngle;         // 转弯角度（度）- 仅转弯半径不足时有值
    private Double turnRadius;        // 实际转弯半径（米）- 仅转弯半径不足时有值

    // Getters and Setters

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Double getConstraintValue() {
        return constraintValue;
    }

    public void setConstraintValue(Double constraintValue) {
        this.constraintValue = constraintValue;
    }

    public Double getTruckValue() {
        return truckValue;
    }

    public void setTruckValue(Double truckValue) {
        this.truckValue = truckValue;
    }

    public Double getTurnAngle() {
        return turnAngle;
    }

    public void setTurnAngle(Double turnAngle) {
        this.turnAngle = turnAngle;
    }

    public Double getTurnRadius() {
        return turnRadius;
    }

    public void setTurnRadius(Double turnRadius) {
        this.turnRadius = turnRadius;
    }

    /**
     * 创建禁行点 DTO（通用）
     */
    public static ViolationPointDTO create(Double lat, Double lon, String reason, String detail) {
        ViolationPointDTO dto = new ViolationPointDTO();
        dto.setLat(lat);
        dto.setLon(lon);
        dto.setReason(reason);
        dto.setDetail(detail);
        return dto;
    }

    /**
     * 创建禁行点 DTO（转弯半径不足）
     */
    public static ViolationPointDTO createTurnRadiusViolation(Double lat, Double lon, String detail,
                                                               Double turnAngle, Double turnRadius) {
        ViolationPointDTO dto = create(lat, lon, "转弯半径不足", detail);
        dto.setTurnAngle(turnAngle);
        dto.setTurnRadius(turnRadius);
        return dto;
    }
}
