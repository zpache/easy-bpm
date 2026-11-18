package com.easybpm.common;

import org.slf4j.MDC;

/**
 * 统一响应体。traceId 便于集成方把一次调用与服务端日志对齐。
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.code(), ErrorCode.OK.message(), data, MDC.get("traceId"));
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null, MDC.get("traceId"));
    }
}
