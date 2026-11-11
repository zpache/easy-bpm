package com.easybpm.sdk;

/**
 * 中台返回的业务错误（HTTP 非 2xx，或 2xx 但 code != 0）。
 */
public class EasyBpmException extends RuntimeException {

    private final int code;
    private final String traceId;
    private final int httpStatus;

    public EasyBpmException(int code, String message, String traceId, int httpStatus) {
        super(message == null || message.isBlank() ? "easy-bpm 调用失败（code=" + code + "）" : message);
        this.code = code;
        this.traceId = traceId;
        this.httpStatus = httpStatus;
    }

    /** 业务错误码，见 docs/integration.md 第 5 节。 */
    public int code() {
        return code;
    }

    /** 服务端 traceId，反馈问题时提供即可定位。 */
    public String traceId() {
        return traceId;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public boolean isAuthError() {
        return code == 40100 || code == 40101 || code == 40300 || code == 40301;
    }

    /** 任务已被处理（43003）：按成功处理，不要重试。 */
    public boolean isTaskAlreadyHandled() {
        return code == 43003;
    }

    public boolean isNotFound() {
        return code == 40400 || code == 41001 || code == 42001 || code == 43001;
    }

    @Override
    public String toString() {
        return "EasyBpmException{code=" + code + ", httpStatus=" + httpStatus
                + ", traceId=" + traceId + ", message=" + getMessage() + "}";
    }
}
