package com.annotation.mcpserver.controller;

import com.annotation.mcpserver.dto.McpMessage;
import com.annotation.mcpserver.service.McpServerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MCP 服务器 Controller
 * 提供 SSE 和 HTTP 传输支持
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp")
@CrossOrigin(origins = "*")
public class McpController {

    @Autowired
    private McpServerService mcpServerService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * MCP 消息端点（HTTP POST）
     * 处理所有 MCP 协议请求
     */
    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> handleMessage(@RequestBody Map<String, Object> request) {
        log.debug("Received MCP message: {}", request);

        try {
            // 转换为 Request 对象
            McpMessage.Request mcpRequest = objectMapper.convertValue(request, McpMessage.Request.class);

            // 处理请求
            McpMessage.Response response = mcpServerService.handleRequest(mcpRequest);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing MCP message: {}", e.getMessage(), e);

            McpMessage.ErrorObject error = new McpMessage.ErrorObject(
                -32603,
                "Internal error: " + e.getMessage(),
                null
            );
            McpMessage.Response response = new McpMessage.Response("2.0", null, null, error);

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "protocol", "mcp",
            "version", "1.0.0"
        ));
    }
}
