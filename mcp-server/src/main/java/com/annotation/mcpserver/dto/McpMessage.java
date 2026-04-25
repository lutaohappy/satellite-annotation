package com.annotation.mcpserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP 协议基础消息类型
 */
public class McpMessage {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Request {
        private String jsonrpc = "2.0";
        private String id;
        private String method;
        private Map<String, Object> params;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Response {
        private String jsonrpc = "2.0";
        private String id;
        private Object result;
        private ErrorObject error;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorObject {
        private int code;
        private String message;
        private Object data;
    }
}
