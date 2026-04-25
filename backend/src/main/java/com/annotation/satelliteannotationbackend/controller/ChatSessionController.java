package com.annotation.satelliteannotationbackend.controller;

import com.annotation.satelliteannotationbackend.dto.ApiResponse;
import com.annotation.satelliteannotationbackend.dto.ChatSessionDTO;
import com.annotation.satelliteannotationbackend.service.ChatSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 聊天会话 Controller
 */
@RestController
@RequestMapping("/api/chat-sessions")
@CrossOrigin(origins = "*")
public class ChatSessionController {

    @Autowired
    private ChatSessionService chatSessionService;

    /**
     * 获取会话列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse> getSessions(
            @RequestHeader(value = "X-Device-Id", required = false, defaultValue = "unknown") String deviceId) {
        try {
            List<ChatSessionDTO> sessions = chatSessionService.getSessions(deviceId);
            return ResponseEntity.ok(ApiResponse.success(sessions));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 保存会话
     */
    @PostMapping
    public ResponseEntity<ApiResponse> saveSession(
            @RequestBody ChatSessionDTO session,
            @RequestHeader(value = "X-Device-Id", required = false, defaultValue = "unknown") String deviceId) {
        try {
            ChatSessionDTO saved = chatSessionService.saveSession(session, deviceId);
            return ResponseEntity.ok(ApiResponse.success(saved));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse> deleteSession(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-Device-Id", required = false, defaultValue = "unknown") String deviceId) {
        try {
            boolean deleted = chatSessionService.deleteSession(sessionId, deviceId);
            if (deleted) {
                return ResponseEntity.ok(ApiResponse.success(null));
            } else {
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取单个会话
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse> getSession(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-Device-Id", required = false, defaultValue = "unknown") String deviceId) {
        try {
            return chatSessionService.getSession(sessionId, deviceId)
                .map(session -> ResponseEntity.ok(ApiResponse.success(session)))
                .orElse(ResponseEntity.ok(ApiResponse.error("会话不存在")));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }
}
