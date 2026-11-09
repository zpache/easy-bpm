package com.easybpm.tenant;

import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;

import java.util.function.Supplier;

/**
 * 当前请求的租户上下文。
 *
 * <p>这是多租户隔离的唯一入口：Web 层认证通过后写入，DAO 层据此追加 tenant_id 条件，
 * 业务代码不得从请求参数里读取 tenantId，避免越权。
 */
public final class TenantContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        HOLDER.set(tenantId);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static String require() {
        String tenantId = HOLDER.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少租户上下文，请检查接入凭证");
        }
        return tenantId;
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static <T> T callAs(String tenantId, Supplier<T> action) {
        String previous = HOLDER.get();
        HOLDER.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }

    public static void runAs(String tenantId, Runnable action) {
        callAs(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
