package com.annotation.mcpserver.tool.example;

import com.annotation.mcpserver.dto.McpTool;
import com.annotation.mcpserver.tool.McpToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 示例工具：回声
 * 用于测试和演示
 */
@Slf4j
@Component
public class EchoTool implements McpToolProvider {

    @Override
    public McpTool.ToolDefinition getDefinition() {
        return McpTool.ToolDefinition.builder()
            .name("echo")
            .description("Echo back the input message")
            .inputSchema(McpTool.InputSchema.builder()
                .type("object")
                .properties(Map.of(
                    "message", McpTool.PropertySchema.builder()
                        .type("string")
                        .description("The message to echo back")
                        .build()
                ))
                .required(java.util.List.of("message"))
                .build()
            )
            .build();
    }

    @Override
    public McpTool.ToolResult execute(Map<String, Object> args) {
        String message = (String) args.get("message");
        log.info("Echo tool called with message: {}", message);

        return McpTool.ToolResult.builder()
            .isSuccess(true)
            .content(Map.of(
                "echo", message,
                "timestamp", System.currentTimeMillis()
            ))
            .build();
    }
}
