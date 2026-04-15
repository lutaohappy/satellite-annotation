package com.annotation.satelliteannotationbackend.enums;

/**
 * 下载任务状态枚举
 */
public enum TaskStatus {
    PENDING("等待下载"),
    DOWNLOADING("下载中"),
    PROCESSING("处理中"),
    COMPLETED("已完成"),
    FAILED("失败"),
    CANCELLED("已取消");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
