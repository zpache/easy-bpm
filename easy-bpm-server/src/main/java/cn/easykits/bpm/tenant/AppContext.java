package cn.easykits.bpm.tenant;

/**
 * 当前请求的接入方应用上下文（OpenAPI 鉴权通过后写入）。
 *
 * <p>与 {@link TenantContext} 平行：tenantId 决定数据归哪个租户，appKey 决定流程来自哪个接入方。
 * 同一租户下可以有多个应用（多个 appKey），发起流程时由服务端从本上下文注入来源，
 * 不接受请求参数传入，避免接入方伪造来源。
 */
public final class AppContext {

    private static final ThreadLocal<String> APP_KEY = new ThreadLocal<>();
    private static final ThreadLocal<String> APP_NAME = new ThreadLocal<>();

    private AppContext() {
    }

    public static void set(String appKey, String appName) {
        APP_KEY.set(appKey);
        APP_NAME.set(appName);
    }

    /** 没有来源应用（控制台操作、内部任务）时返回 null，调用方按需容错。 */
    public static String getAppKey() {
        return APP_KEY.get();
    }

    public static String getAppName() {
        return APP_NAME.get();
    }

    public static void clear() {
        APP_KEY.remove();
        APP_NAME.remove();
    }
}
