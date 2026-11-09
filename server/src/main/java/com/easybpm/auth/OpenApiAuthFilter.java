package com.easybpm.auth;

import com.easybpm.common.ApiResponse;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.JsonCodec;
import com.easybpm.tenant.Tenant;
import com.easybpm.tenant.TenantContext;
import com.easybpm.tenant.TenantCredential;
import com.easybpm.tenant.TenantService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * OpenAPI 鉴权。
 *
 * <p>推荐签名模式：X-App-Key + X-Timestamp + X-Signature，
 * 签名串 = METHOD + "\n" + requestURI + "\n" + timestamp + "\n" + body，HMAC-SHA256 十六进制。
 * 为方便快速联调，也支持 X-App-Secret 明文模式（仅建议内网/调试）。
 */
@Component
public class OpenApiAuthFilter extends OncePerRequestFilter {

    private static final long MAX_CLOCK_SKEW_SECONDS = 300;

    private final TenantService tenantService;
    private final JsonCodec json;

    public OpenApiAuthFilter(TenantService tenantService, JsonCodec json) {
        this.tenantService = tenantService;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/open/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        try {
            String appKey = cachedRequest.getHeader("X-App-Key");
            if (appKey == null || appKey.isBlank()) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "缺少 X-App-Key");
            }
            TenantCredential credential = tenantService.findCredential(appKey);
            if (credential == null || !"ACTIVE".equals(credential.getStatus())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "AppKey 无效或已停用");
            }
            if (credential.getExpireAt() != null && credential.getExpireAt().isBefore(java.time.LocalDateTime.now())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "接入凭证已过期");
            }

            Tenant tenant = tenantService.requireById(credential.getTenantId());
            verify(cachedRequest, credential);

            TenantContext.set(tenant.getId());
            tenantService.touchCredential(credential.getId());
            chain.doFilter(cachedRequest, response);
        } catch (BizException e) {
            writeError(response, e);
        } finally {
            TenantContext.clear();
        }
    }

    private void verify(CachedBodyHttpServletRequest request, TenantCredential credential) {
        String signature = request.getHeader("X-Signature");
        if (signature != null && !signature.isBlank()) {
            String timestamp = request.getHeader("X-Timestamp");
            if (timestamp == null || timestamp.isBlank()) {
                throw new BizException(ErrorCode.SIGNATURE_INVALID, "签名模式需要 X-Timestamp");
            }
            long ts;
            try {
                ts = Long.parseLong(timestamp);
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.SIGNATURE_INVALID, "X-Timestamp 必须是毫秒时间戳");
            }
            long skew = Math.abs(Instant.now().getEpochSecond() - ts / 1000);
            if (skew > MAX_CLOCK_SKEW_SECONDS) {
                throw new BizException(ErrorCode.SIGNATURE_INVALID, "请求时间戳超出 5 分钟窗口");
            }
            String payload = request.getMethod() + "\n" + request.getRequestURI() + "\n" + timestamp + "\n"
                    + new String(request.body(), StandardCharsets.UTF_8);
            String expected = hmacHex(credential.getAppSecret(), payload);
            if (!constantTimeEquals(expected, signature)) {
                throw new BizException(ErrorCode.SIGNATURE_INVALID, "签名不匹配");
            }
            return;
        }

        String secret = request.getHeader("X-App-Secret");
        if (secret == null || secret.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少 X-Signature 或 X-App-Secret");
        }
        if (!constantTimeEquals(credential.getAppSecret(), secret)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "AppSecret 不正确");
        }
    }

    private String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("签名计算失败", e);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response, BizException e) throws IOException {
        response.setStatus(e.errorCode().httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json.write(ApiResponse.fail(e.errorCode(), e.getMessage())));
    }
}
