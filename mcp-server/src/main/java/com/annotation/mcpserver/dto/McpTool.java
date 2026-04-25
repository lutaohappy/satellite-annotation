package com.annotation.mcpserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;
import java.util.List;

/**
 * MCP 工具相关 DTO
 */
public class McpTool {

    /**
     * 工具定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolDefinition {
        private String name;
        private String description;
        private InputSchema inputSchema;
    }

    /**
     * 工具输入 Schema
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputSchema {
        private String type = "object";
        private Map<String, PropertySchema> properties;
        private List<String> required;
    }

    /**
     * 属性 Schema
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PropertySchema {
        private String type;  // string, number, boolean, object, array
        private String description;
        private Object defaultValue;
        private Map<String, Object> items;  // for array type
    }

    /**
     * 工具调用结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolResult {
        private boolean isSuccess;
        private Object content;
        private String error;
        private List<Resource> resources;
    }

    /**
     * 资源引用
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Resource {
        private String uri;
        private String mimeType;
        private Object data;
    }
}
