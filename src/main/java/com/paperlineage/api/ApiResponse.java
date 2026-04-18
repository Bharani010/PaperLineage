package com.paperlineage.api;

import java.util.Map;

public record ApiResponse<T>(T data, String error, Map<String, Object> meta) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(null, message, null);
    }
}
