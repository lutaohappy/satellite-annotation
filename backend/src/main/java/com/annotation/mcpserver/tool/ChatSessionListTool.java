package com.annotation.mcpserver.tool;

import com.annotation.mcpserver.dto.McpTool;
import com.annotation.satelliteannotationbackend.entity.ChatSession;
import com.annotation.satelliteannotationbackend.repository.ChatSessionRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天会话列表查询工具 - 查询数据库中所有对话记录
 */
@Component
public class ChatSessionListTool implements McpToolProvider {

    private final ChatSessionRepository chatSessionRepository;

    public ChatSessionListTool(ChatSessionRepository chatSessionRepository) {
        this.chatSessionRepository = chatSessionRepository;
    }

    @Override
    public McpTool.ToolDefinition getDefinition() {
        return McpTool.ToolDefinition.builder()
            .name("list_chat_sessions")
            .description("查询数据库中所有AI对话记录列表，包含会话标题、消息数量、创建时间等信息")
            .inputSchema(McpTool.InputSchema.builder()
                .type("object")
                .properties(Map.of())
                .required(List.of())
                .build()
            )
            .build();
    }

    @Override
    public McpTool.ToolResult execute(Map<String, Object> args) {
        List<ChatSession> sessions = chatSessionRepository.findAll();

        List<Map<String, Object>> result = new ArrayList<>();

        for (ChatSession session : sessions) {
            Map<String, Object> info = new HashMap<>();
            info.put("id", session.getId());
            info.put("title", session.getTitle());
            info.put("deviceId", session.getDeviceId());
            info.put("createdAt", session.getCreatedAt() != null ? session.getCreatedAt().toString() : null);
            info.put("updatedAt", session.getUpdatedAt() != null ? session.getUpdatedAt().toString() : null);

            // 估算消息数量（messages 字段存储为 JSON 数组字符串）
            int messageCount = countMessages(session.getMessages());
            info.put("messageCount", messageCount);

            result.add(info);
        }

        return McpTool.ToolResult.builder()
            .isSuccess(true)
            .content(Map.of(
                "count", result.size(),
                "sessions", result
            ))
            .build();
    }

    private int countMessages(String messagesJson) {
        if (messagesJson == null || messagesJson.isBlank()) {
            return 0;
        }
        // 简单统计 JSON 数组中的对象数量
        String trimmed = messagesJson.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return 0;
        }
        int count = 0;
        int i = 1; // skip '['
        while (i < trimmed.length()) {
            if (trimmed.charAt(i) == '{') {
                count++;
            }
            i++;
        }
        return count;
    }
}
