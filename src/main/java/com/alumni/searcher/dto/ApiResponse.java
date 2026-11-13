package com.alumni.searcher.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String status, T data, String message, Map<String, String> errors) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", data, null, null);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>("error", null, message, null);
    }

    public static ApiResponse<Void> error(String message, Map<String, String> errors) {
        return new ApiResponse<>("error", null, message, errors);
    }
}
