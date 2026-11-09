package com.easybpm.auth;

import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;

/**
 * 当前控制台用户。仅由 ConsoleAuthFilter 写入与清理。
 */
public final class CurrentUser {

    private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static void set(AuthPrincipal principal) {
        HOLDER.set(principal);
    }

    public static AuthPrincipal get() {
        return HOLDER.get();
    }

    public static AuthPrincipal require() {
        AuthPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录控制台");
        }
        return principal;
    }

    public static String requireApproverId() {
        return require().approverId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
