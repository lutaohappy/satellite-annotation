package com.annotation.mcpserver.service;

import com.annotation.mcpserver.dto.McpMessage;
import com.annotation.mcpserver.dto.McpTool;
import com.annotation.mcpserver.registry.McpToolRegistry;
import com.annotation.mcpserver.tool.McpToolProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP 服务器核心服务
 * 处理 MCP 协议请求和响应
 */
@Slf4j
@Service
public class McpServerService {

    @Autowired
    private McpToolRegistry toolRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 处理 MCP 请求
     */
    public McpMessage.Response handleRequest(McpMessage.Request request) {
        String method = request.getMethod();
        String id = request.getId();
        Map<String, Object> params = request.getParams();

        log.debug("Processing MCP request: method={}, id={}", method, id);

        try {
            Object result = switch (method) {
                case "initialize" -> handleInitialize(params);
                case "tools/list" -> handleToolsList();
                case "tools/call" -> handleToolsCall(params);
                default -> throw new UnsupportedOperationException("Unknown method: " + method);
            };

            return new McpMessage.Response("2.0", id, result, null);

        } catch (Exception e) {
            log.error("Error processing MCP request: {}", e.getMessage(), e);
            McpMessage.ErrorObject error = new McpMessage.ErrorObject(
                -32603,
                e.getMessage(),
                null
            );
            return new McpMessage.Response("2.0", id, null, error);
        }
    }

    /**
     * 处理 initialize 请求
     */
    private Map<String, Object> handleInitialize(Map<String, Object> params) {
        log.info("MCP client initializing...");

        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "satellite-annotation-mcp");
        serverInfo.put("version", "1.0.0");

        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("tools", Map.of(
            "listChanged", true
        ));

        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2025-03-26");
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);

        return result;
    }

    /**
     * 处理 tools/list 请求
     */
    private Map<String, Object> handleToolsList() {
        log.debug("Listing available tools");

        List<McpTool.ToolDefinition> tools = toolRegistry.listTools();

        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);

        return result;
    }

    /**
     * 处理 tools/call 请求
     */
    private Map<String, Object> handleToolsCall(Map<String, Object> params) throws Exception {
        String toolName = (String) params.get("name");
        Map<String, Object> args = (Map<String, Object>) params.get("arguments");

        log.info("Calling tool: {} with arguments: {}", toolName, args);

        if (toolName == null || toolName.isEmpty()) {
            throw new IllegalArgumentException("Tool name is required");
        }

        McpToolProvider tool = toolRegistry.getTool(toolName)
            .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + toolName));

        McpTool.ToolResult result = tool.execute(args);

        if (!result.isSuccess()) {
            throw new RuntimeException("Tool execution failed: " + result.getError());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("content", result.getContent());

        if (result.getResources() != null && !result.getResources().isEmpty()) {
            response.put("resources", result.getResources());
        }

        return response;
    }
}
