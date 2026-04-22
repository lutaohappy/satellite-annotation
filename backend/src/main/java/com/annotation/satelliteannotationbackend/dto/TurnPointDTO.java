package com.annotation.satelliteannotationbackend.dto;

/**
 * 转弯点 DTO
 * 用于描述路径中的转弯点信息
 */
public class TurnPointDTO {

    private Double lat;               // 纬度
    private Double lon;               // 经度
    private Double turnAngle;         // 转弯角度（度，0-180）
    private Double turnRadius;        // 转弯半径（米）
    private Double bearingBefore;     // 进入方位角（度，0-360）
    private Double bearingAfter;      // 离开方位角（度，0-360）
    private String instruction;       // 转向说明（如"左转 45 度"）
    private Boolean isSharpTurn;      // 是否急转弯（角度>45 度）
    private Integer sequence;         // 转弯点序号

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

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public Boolean getIsSharpTurn() {
        return isSharpTurn;
    }

    public void setIsSharpTurn(Boolean isSharpTurn) {
        this.isSharpTurn = isSharpTurn;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    /**
     * 创建转弯点 DTO
     */
    public static TurnPointDTO create(Double lat, Double lon, Double turnAngle, Double turnRadius,
                                       Double bearingBefore, Double bearingAfter, Integer sequence) {
        TurnPointDTO dto = new TurnPointDTO();
        dto.setLat(lat);
        dto.setLon(lon);
        dto.setTurnAngle(turnAngle);
        dto.setTurnRadius(turnRadius);
        dto.setBearingBefore(bearingBefore);
        dto.setBearingAfter(bearingAfter);
        dto.setSequence(sequence);
        dto.setIsSharpTurn(turnAngle > 45.0);  // 大于 45 度视为急转弯
        dto.setInstruction(buildInstruction(turnAngle, bearingBefore, bearingAfter));
        return dto;
    }

    /**
     * 构建转向说明文字
     */
    private static String buildInstruction(Double turnAngle, Double bearingBefore, Double bearingAfter) {
        if (turnAngle < 5) {
            return "直行";
        }

        // 判断左转还是右转
        double bearingDiff = bearingAfter - bearingBefore;
        if (bearingDiff > 180) {
            bearingDiff -= 360;
        } else if (bearingDiff < -180) {
            bearingDiff += 360;
        }

        String direction = bearingDiff > 0 ? "右转" : "左转";
        String severity;

        if (turnAngle < 15) {
            severity = "微弯";
        } else if (turnAngle < 30) {
            severity = "缓弯";
        } else if (turnAngle < 60) {
            severity = "中弯";
        } else if (turnAngle < 90) {
            severity = "急弯";
        } else {
            severity = "锐角弯";
        }

        return String.format("%s %s%.0f 度", direction, severity, turnAngle);
    }
}
