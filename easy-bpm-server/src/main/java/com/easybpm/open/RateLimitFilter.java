package com.easybpm.open;

import com.easybpm.common.ApiResponse;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.JsonCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAPI 限流（单机固定窗口）。
 *
 * <p>按 X-App-Key 维度限流（未带凭证的请求按来源 IP），窗口 60 秒。
 * 单实例内存实现，默认 600 次/分钟，0 表示关闭；多实例部署时可在网关层做全局限流。
 * 位于鉴权过滤器之前（order 5），对恶意高频请求在鉴权前即拦截。
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;

    private final JsonCodec json;
    private final int limitPerMinute;

    /** key -> [windowStart, count]。窗口滚动时原子替换。 */
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(JsonCodec json,
                           @Value("${easybpm.open.rate-limit-per-minute:600}") int limitPerMinute) {
        this.json = json;
        this.limitPerMinute = limitPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limitPerMinute <= 0
                || !request.getRequestURI().startsWith("/open/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = clientKey(request);
        long now = System.currentTimeMillis();
        long[] window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing[0] >= WINDOW_MS) {
                return new long[]{now, 1};
            }
            existing[1]++;
            return existing;
        });

        if (window[1] > limitPerMinute) {
            response.setStatus(ErrorCode.TOO_MANY_REQUESTS.httpStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Retry-After", String.valueOf(
                    Math.max(1, (WINDOW_MS - (now - window[0])) / 1000)));
            response.getWriter().write(json.write(ApiResponse.fail(
                    ErrorCode.TOO_MANY_REQUESTS, "超过限流阈值（" + limitPerMinute + " 次/分钟）")));
            return;
        }
        chain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String appKey = request.getHeader("X-App-Key");
        if (appKey != null && !appKey.isBlank()) {
            return "ak:" + appKey;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return "ip:" + ip;
    }
}
