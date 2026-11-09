package com.easybpm.common;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。code 为业务码，httpStatus 决定 HTTP 状态，便于集成方按状态码做统一重试/告警。
 */
public enum ErrorCode {

    OK(0, HttpStatus.OK, "ok"),

    BAD_REQUEST(40000, HttpStatus.BAD_REQUEST, "请求参数不合法"),
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "未认证或凭证已失效"),
    SIGNATURE_INVALID(40101, HttpStatus.UNAUTHORIZED, "签名校验失败"),
    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "无权访问该资源"),
    NOT_FOUND(40400, HttpStatus.NOT_FOUND, "资源不存在"),
    CONFLICT(40900, HttpStatus.CONFLICT, "资源状态冲突"),
    TENANT_DISABLED(40301, HttpStatus.FORBIDDEN, "租户已停用"),

    FLOW_NOT_FOUND(41001, HttpStatus.NOT_FOUND, "流程定义不存在或未发布"),
    FLOW_SCHEMA_INVALID(41002, HttpStatus.BAD_REQUEST, "流程定义不合法"),
    INSTANCE_NOT_FOUND(42001, HttpStatus.NOT_FOUND, "审批实例不存在"),
    INSTANCE_NOT_RUNNING(42002, HttpStatus.CONFLICT, "审批实例已结束"),
    TASK_NOT_FOUND(43001, HttpStatus.NOT_FOUND, "审批任务不存在"),
    TASK_NOT_ASSIGNEE(43002, HttpStatus.FORBIDDEN, "当前用户不是该任务的处理人"),
    TASK_ALREADY_HANDLED(43003, HttpStatus.CONFLICT, "任务已被处理，请刷新后重试"),
    NO_APPROVER(44001, HttpStatus.BAD_REQUEST, "未匹配到审批人，请检查组织架构或流程配置"),

    INTERNAL(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务内部错误");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(int code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String message() {
        return message;
    }
}
