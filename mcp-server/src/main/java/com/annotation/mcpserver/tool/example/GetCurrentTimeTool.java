package com.annotation.mcpserver.tool.example;

import com.annotation.mcpserver.dto.McpTool;
import com.annotation.mcpserver.tool.McpToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 示例工具：获取当前时间
 */
@Slf4j
@Component
public class GetCurrentTimeTool implements McpToolProvider {

    @Override
    public McpTool.ToolDefinition getDefinition() {
        return McpTool.ToolDefinition.builder()
            .name("get_current_time")
            .description("Get the current date and time")
            .inputSchema(McpTool.InputSchema.builder()
                .type("object")
                .properties(Map.of())
                .build()
            )
            .build();
    }

    @Override
    public McpTool.ToolResult execute(Map<String, Object> args) {
        log.info("Get current time tool called");

        String currentTime = LocalDateTime.now().toString();

        return McpTool.ToolResult.builder()
            .isSuccess(true)
            .content(Map.of(
                "current_time", currentTime,
                "timezone", "Asia/Shanghai"
            ))
            .build();
    }
}
