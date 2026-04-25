package com.annotation.satelliteannotationbackend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 聊天会话 DTO
 */
public class ChatSessionDTO {
    private String id;
    private String title;
    private List<ChatMessageDTO> messages;
    private String deviceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean synced;  // 标记是否已同步到服务端

    public static class ChatMessageDTO {
        private String role;
        private String content;
        private String time;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<ChatMessageDTO> getMessages() { return messages; }
    public void setMessages(List<ChatMessageDTO> messages) { this.messages = messages; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public boolean isSynced() { return synced; }
    public void setSynced(boolean synced) { this.synced = synced; }
}
