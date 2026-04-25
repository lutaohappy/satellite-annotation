package com.annotation.mcpserver.tool;

import com.annotation.mcpserver.dto.McpTool;
import java.util.Map;

/**
 * MCP 工具接口
 * 所有工具必须实现此接口
 */
public interface McpToolProvider {

    /**
     * 获取工具定义
     */
    McpTool.ToolDefinition getDefinition();

    /**
     * 执行工具
     * @param args 工具参数
     * @return 执行结果
     */
    McpTool.ToolResult execute(Map<String, Object> args);
}
