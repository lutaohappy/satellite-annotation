package com.annotation.satelliteannotationbackend.dto;

/**
 * 影像调整参数
 */
public class AdjustmentParams {
    private Double brightness;
    private Double contrast;
    private Double gamma;
    private Boolean asNew;
    private String newName;

    public Double getBrightness() { return brightness; }
    public void setBrightness(Double brightness) { this.brightness = brightness; }
    public Double getContrast() { return contrast; }
    public void setContrast(Double contrast) { this.contrast = contrast; }
    public Double getGamma() { return gamma; }
    public void setGamma(Double gamma) { this.gamma = gamma; }
    public Boolean getAsNew() { return asNew; }
    public void setAsNew(Boolean asNew) { this.asNew = asNew; }
    public String getNewName() { return newName; }
    public void setNewName(String newName) { this.newName = newName; }
}
