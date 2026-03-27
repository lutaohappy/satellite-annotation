package com.annotation.satelliteannotationbackend.dto;

/**
 * 通用响应 DTO
 */
public class ApiResponse {
    private int code;
    private String message;
    private Object data;

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    public static ApiResponse success(Object data) {
        ApiResponse response = new ApiResponse();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static ApiResponse error(String message) {
        ApiResponse response = new ApiResponse();
        response.setCode(500);
        response.setMessage(message);
        return response;
    }
}
