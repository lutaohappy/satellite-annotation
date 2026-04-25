package com.annotation.mcpserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务器配置
 */
@Data
@ConfigurationProperties(prefix = "mcp.server")
public class McpServerProperties {

    /**
     * 服务器名称
     */
    private String name = "satellite-annotation-mcp";

    /**
     * 服务器版本
     */
    private String version = "1.0.0";

    /**
     * 服务器描述
     */
    private String description = "卫星影像标注系统 MCP 服务器";

    /**
     * 启用的工具列表
     */
    private List<String> enabledTools = new ArrayList<>();

    /**
     * 工具配置
     */
    private List<ToolConfig> tools = new ArrayList<>();

    /**
     * 工具配置
     */
    @Data
    public static class ToolConfig {
        /**
         * 工具名称
         */
        private String name;

        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 工具配置参数
         */
        private Map<String, Object> config = new java.util.HashMap<>();
    }
}
