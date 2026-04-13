package com.annotation.satelliteannotationbackend.dto;

/**
 * 货车参数 DTO
 */
public class TruckParametersDTO {

    private Double length;            // 车长 (米)
    private Double width;             // 车宽 (米)
    private Double height;            // 车高 (米)
    private Double weight;            // 总重 (吨)
    private Double axleWeight;        // 轴重 (吨)
    private Double wheelbase;         // 轴距 (米)

    // Getters and Setters

    public Double getLength() {
        return length;
    }

    public void setLength(Double length) {
        this.length = length;
    }

    public Double getWidth() {
        return width;
    }

    public void setWidth(Double width) {
        this.width = width;
    }

    public Double getHeight() {
        return height;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Double getAxleWeight() {
        return axleWeight;
    }

    public void setAxleWeight(Double axleWeight) {
        this.axleWeight = axleWeight;
    }

    public Double getWheelbase() {
        return wheelbase;
    }

    public void setWheelbase(Double wheelbase) {
        this.wheelbase = wheelbase;
    }

    /**
     * 计算最小转弯半径
     * 公式：R = L / sin(δ_max)
     * 假设最大转向角为 35 度
     */
    public Double calculateMinTurningRadius() {
        if (wheelbase == null || wheelbase <= 0) {
            return null;
        }
        // 典型货车最大转向角约 35 度
        double maxSteeringAngleRad = Math.toRadians(35);
        return wheelbase / Math.sin(maxSteeringAngleRad);
    }
}
