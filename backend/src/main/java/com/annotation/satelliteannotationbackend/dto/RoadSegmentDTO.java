package com.annotation.satelliteannotationbackend.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 路段详情 DTO
 */
public class RoadSegmentDTO {

    private Integer sequence;           // 序号
    private String name;                // 路段名称/道路名称
    private Double distance;            // 路段长度 (米)
    private Double duration;            // 路段用时 (秒)
    private Double startLon;            // 起点经度
    private Double startLat;            // 起点纬度
    private Double endLon;              // 终点经度
    private Double endLat;              // 终点纬度
    private String instruction;         // 导航指示
    private String roadMode;            // 道路模式 (road, ferry, etc.)
    private String modifier;            // 转弯方向 (left, right, slight, sharp, etc.)
    private String maneuverType;        // 操作类型 (turn, roundabout, etc.)
    private Double bearingBefore;       // 进入方向角度
    private Double bearingAfter;        // 离开方向角度
    private String restrictions;        // 限制信息 (限高、限宽等)

    // Getters and Setters

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }

    public Double getDuration() {
        return duration;
    }

    public void setDuration(Double duration) {
        this.duration = duration;
    }

    public Double getStartLon() {
        return startLon;
    }

    public void setStartLon(Double startLon) {
        this.startLon = startLon;
    }

    public Double getStartLat() {
        return startLat;
    }

    public void setStartLat(Double startLat) {
        this.startLat = startLat;
    }

    public Double getEndLon() {
        return endLon;
    }

    public void setEndLon(Double endLon) {
        this.endLon = endLon;
    }

    public Double getEndLat() {
        return endLat;
    }

    public void setEndLat(Double endLat) {
        this.endLat = endLat;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getRoadMode() {
        return roadMode;
    }

    public void setRoadMode(String roadMode) {
        this.roadMode = roadMode;
    }

    public String getModifier() {
        return modifier;
    }

    public void setModifier(String modifier) {
        this.modifier = modifier;
    }

    public String getManeuverType() {
        return maneuverType;
    }

    public void setManeuverType(String maneuverType) {
        this.maneuverType = maneuverType;
    }

    public Double getBearingBefore() {
        return bearingBefore;
    }

    public void setBearingBefore(Double bearingBefore) {
        this.bearingBefore = bearingBefore;
    }

    public Double getBearingAfter() {
        return bearingAfter;
    }

    public void setBearingAfter(Double bearingAfter) {
        this.bearingAfter = bearingAfter;
    }

    public String getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(String restrictions) {
        this.restrictions = restrictions;
    }
}
