package com.annotation.mcpserver.config;

import com.annotation.mcpserver.registry.McpToolRegistry;
import com.annotation.mcpserver.tool.McpToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Map;

/**
 * MCP 工具自动配置
 * 自动注册所有实现 McpToolProvider 的 Bean
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(McpServerProperties.class)
public class McpAutoConfig {

    @Autowired
    private McpToolRegistry toolRegistry;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private McpServerProperties properties;

    /**
     * 初始化时自动注册所有工具
     */
    @PostConstruct
    public void initializeTools() {
        log.info("Initializing MCP tools...");

        // 获取所有 McpToolProvider Bean
        Map<String, McpToolProvider> toolBeans = applicationContext.getBeansOfType(McpToolProvider.class);

        for (McpToolProvider tool : toolBeans.values()) {
            try {
                // 检查工具是否在启用列表中（如果配置了启用列表）
                if (shouldEnableTool(tool)) {
                    toolRegistry.registerTool(tool);
                }
            } catch (Exception e) {
                log.error("Failed to register tool: {}", tool.getClass().getSimpleName(), e);
            }
        }

        log.info("MCP tools initialized. Total registered: {}", toolRegistry.getToolCount());
    }

    /**
     * 判断工具是否应该启用
     */
    private boolean shouldEnableTool(McpToolProvider tool) {
        String toolName = tool.getDefinition().getName();

        // 如果没有配置启用列表，默认启用所有工具
        if (properties.getEnabledTools() == null || properties.getEnabledTools().isEmpty()) {
            return true;
        }

        // 检查是否在启用列表中
        return properties.getEnabledTools().contains(toolName);
    }
}
