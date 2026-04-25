package com.annotation.mcpserver.registry;

import com.annotation.mcpserver.dto.McpTool;
import com.annotation.mcpserver.tool.McpToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具注册表
 * 管理所有已注册的工具
 */
@Slf4j
@Component
public class McpToolRegistry {

    /**
     * 工具存储：name -> tool
     */
    private final Map<String, McpToolProvider> tools = new ConcurrentHashMap<>();

    /**
     * 工具列表缓存（用于快速响应 tools/list 请求）
     */
    private final List<McpTool.ToolDefinition> toolDefinitions = new ArrayList<>();

    /**
     * 注册工具
     */
    public void registerTool(McpToolProvider tool) {
        if (tool == null) {
            throw new IllegalArgumentException("Tool cannot be null");
        }

        McpTool.ToolDefinition definition = tool.getDefinition();
        if (definition == null || definition.getName() == null) {
            throw new IllegalArgumentException("Tool must have a name");
        }

        tools.put(definition.getName(), tool);
        toolDefinitions.add(definition);

        log.info("Registered MCP tool: {}", definition.getName());
    }

    /**
     * 注销工具
     */
    public void unregisterTool(String toolName) {
        if (toolName == null || toolName.isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }

        McpToolProvider removed = tools.remove(toolName);
        if (removed != null) {
            toolDefinitions.removeIf(t -> t.getName().equals(toolName));
            log.info("Unregistered MCP tool: {}", toolName);
        } else {
            log.warn("Tool not found: {}", toolName);
        }
    }

    /**
     * 获取工具
     */
    public Optional<McpToolProvider> getTool(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    /**
     * 获取所有工具定义列表
     */
    public List<McpTool.ToolDefinition> listTools() {
        return new ArrayList<>(toolDefinitions);
    }

    /**
     * 检查工具是否存在
     */
    public boolean hasTool(String toolName) {
        return tools.containsKey(toolName);
    }

    /**
     * 获取已注册工具数量
     */
    public int getToolCount() {
        return tools.size();
    }

    /**
     * 清空所有工具
     */
    public void clear() {
        tools.clear();
        toolDefinitions.clear();
        log.info("Cleared all MCP tools");
    }
}
