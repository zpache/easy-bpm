package com.easybpm.auth.sso;

import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极简 OIDC 客户端：discovery、授权跳转 URL、code 换 token、id_token 验签、userinfo。
 *
 * <p>刻意不引 spring-security-oauth2-client：那套会注册自己的 SecurityFilterChain，
 * 与本项目 {@code ConsoleAuthFilter} + {@code TenantContext} 的时序冲突，且只用到极少能力。
 * 这里只依赖 JDK HttpClient + Jackson，逻辑全可控。
 *
 * <p>生产限制：只支持 RSA 签名（RS256/384/512），这是企业 IdP 的默认算法；
 * 使用 ES256 的 IdP 需要扩展 {@link #verifyIdToken} 的密钥构造。
 */
@Component
public class OidcClient {

    private static final Logger log = LoggerFactory.getLogger(OidcClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long METADATA_TTL_MS = 10 * 60_000L;
    private static final long JWKS_TTL_MS = 60 * 60_000L;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper mapper;

    private final Map<String, CachedMetadata> metadataCache = new ConcurrentHashMap<>();
    private final Map<String, CachedJwks> jwksCache = new ConcurrentHashMap<>();

    public OidcClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** discovery 结果。issuer 未配置成标准形式（多/少尾斜杠）时自动容错。 */
    public OidcMetadata discover(String issuer) {
        String normalized = normalizeIssuer(issuer);
        CachedMetadata cached = metadataCache.get(normalized);
        if (cached != null && !cached.expired()) {
            return cached.metadata();
        }
        String url = normalized + "/.well-known/openid-configuration";
        Map<String, Object> body = getJson(url, null, "OIDC discovery 失败");
        OidcMetadata metadata = new OidcMetadata(
                normalizeIssuer(str(body.get("issuer"))),
                str(body.get("authorization_endpoint")),
                str(body.get("token_endpoint")),
                str(body.get("userinfo_endpoint")),
                str(body.get("jwks_uri")),
                str(body.get("end_session_endpoint")));
        if (metadata.authorizationEndpoint() == null || metadata.tokenEndpoint() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "IdP discovery 文档缺少 authorization_endpoint/token_endpoint");
        }
        if (!normalized.equals(metadata.issuer())) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "IdP 返回的 issuer（" + metadata.issuer() + "）与配置不一致，请检查 issuer 配置");
        }
        metadataCache.put(normalized, new CachedMetadata(metadata, System.currentTimeMillis() + METADATA_TTL_MS));
        return metadata;
    }

    /** 构造授权跳转 URL。state / nonce / PKCE challenge 全部由调用方生成。 */
    public String authorizationUrl(OidcMetadata metadata, String clientId, String scopes,
                                   String redirectUri, String state, String nonce, String codeChallenge) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", clientId);
        params.put("redirect_uri", redirectUri);
        params.put("scope", scopes == null || scopes.isBlank() ? "openid profile email" : scopes);
        params.put("state", state);
        params.put("nonce", nonce);
        params.put("code_challenge", codeChallenge);
        params.put("code_challenge_method", "S256");
        return metadata.authorizationEndpoint() + (metadata.authorizationEndpoint().contains("?") ? "&" : "?")
                + formEncode(params);
    }

    /** code 换 token。code_verifier 与授权时的 challenge 必须配对。 */
    public TokenResponse exchangeCode(OidcMetadata metadata, SsoProvider provider, String code,
                                      String codeVerifier, String redirectUri) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("client_id", provider.getClientId());
        form.put("client_secret", provider.getClientSecret());
        form.put("code_verifier", codeVerifier);

        HttpRequest request = HttpRequest.newBuilder(URI.create(metadata.tokenEndpoint()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
                .build();
        Map<String, Object> body = send(request, "换取 token 失败");
        String error = str(body.get("error"));
        if (error != null) {
            throw new BizException(ErrorCode.UNAUTHORIZED,
                    "IdP 拒绝换取 token：" + error + describe(body.get("error_description")));
        }
        String idToken = str(body.get("id_token"));
        if (idToken == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "IdP 未返回 id_token，无法确认身份");
        }
        return new TokenResponse(idToken, str(body.get("access_token")), str(body.get("token_type")));
    }

    /**
     * 校验 id_token：签名（JWKS）+ iss + aud + exp + nonce。
     * kid 未命中缓存时强制刷新一次 JWKS，覆盖 IdP 密钥轮换窗口。
     */
    public Map<String, Object> verifyIdToken(OidcMetadata metadata, SsoProvider provider,
                                             String idToken, String nonce) {
        Map<String, Object> header = jwtHeader(idToken);
        String alg = str(header.get("alg"));
        if (alg == null || !alg.startsWith("RS")) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "不支持的 id_token 签名算法：" + alg);
        }
        String kid = str(header.get("kid"));
        PublicKey key = rsaKey(metadata.jwksUri(), kid, false);
        if (key == null) {
            key = rsaKey(metadata.jwksUri(), kid, true);
        }
        if (key == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "IdP 公钥集中找不到 kid=" + kid);
        }

        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clockSkewSeconds(120)
                .build()
                .parseSignedClaims(idToken)
                .getPayload();

        String issuer = normalizeIssuer(claims.getIssuer());
        if (!normalizeIssuer(provider.getIssuer()).equals(issuer)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "id_token 的 issuer 与配置不一致");
        }
        Set<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(provider.getClientId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "id_token 的 audience 不包含当前 client_id");
        }
        Object tokenNonce = claims.get("nonce");
        if (nonce != null && (tokenNonce == null || !nonce.equals(tokenNonce.toString()))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "id_token 的 nonce 校验失败");
        }
        return new LinkedHashMap<>(claims);
    }

    /** userinfo。失败不阻断登录（id_token 已足够确认身份），返回空 Map 由调用方兜底。 */
    public Map<String, Object> userInfo(OidcMetadata metadata, String accessToken) {
        if (accessToken == null || metadata.userinfoEndpoint() == null) {
            return Map.of();
        }
        try {
            return getJson(metadata.userinfoEndpoint(), accessToken, "userinfo 获取失败");
        } catch (RuntimeException e) {
            log.warn("userinfo 调用失败，仅使用 id_token 的 claim：{}", e.getMessage());
            return Map.of();
        }
    }

    /** 配置页「测试连接」：只验证 discovery 可达且 issuer 自洽。 */
    public OidcMetadata test(String issuer) {
        metadataCache.remove(normalizeIssuer(issuer));
        return discover(issuer);
    }

    public record TokenResponse(String idToken, String accessToken, String tokenType) {
    }

    private record CachedMetadata(OidcMetadata metadata, long expireAt) {
        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    private record CachedJwks(List<Map<String, Object>> keys, long fetchedAt) {
    }

    /** 取 RSA 公钥。forceRefresh=true 时忽略缓存重新拉取。 */
    private PublicKey rsaKey(String jwksUri, String kid, boolean forceRefresh) {
        if (jwksUri == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "IdP discovery 文档缺少 jwks_uri");
        }
        CachedJwks cached = jwksCache.get(jwksUri);
        if (forceRefresh || cached == null || System.currentTimeMillis() - cached.fetchedAt() > JWKS_TTL_MS) {
            Map<String, Object> body = getJson(jwksUri, null, "获取 IdP 公钥失败");
            Object keys = body.get("keys");
            if (!(keys instanceof List<?> list)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "IdP 公钥集格式不正确");
            }
            List<Map<String, Object>> parsed = list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> {
                        Map<String, Object> map = new HashMap<>();
                        ((Map<?, ?>) item).forEach((k, v) -> map.put(String.valueOf(k), v));
                        return map;
                    })
                    .toList();
            cached = new CachedJwks(parsed, System.currentTimeMillis());
            jwksCache.put(jwksUri, cached);
        }
        for (Map<String, Object> key : cached.keys()) {
            String keyId = str(key.get("kid"));
            if (kid != null && !kid.equals(keyId)) {
                continue;
            }
            if (!"RSA".equals(str(key.get("kty")))) {
                continue;
            }
            try {
                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(str(key.get("n"))));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(str(key.get("e"))));
                return KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(modulus, exponent));
            } catch (Exception e) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "IdP 公钥解析失败：" + e.getMessage());
            }
        }
        return null;
    }

    private Map<String, Object> jwtHeader(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "id_token 格式不正确");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[0]);
            return mapper.readValue(new String(decoded, StandardCharsets.UTF_8),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "id_token 头部解析失败");
        }
    }

    private Map<String, Object> getJson(String url, String bearer, String message) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET();
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return send(builder.build(), message);
    }

    private Map<String, Object> send(HttpRequest request, String message) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new BizException(ErrorCode.UNAUTHORIZED,
                        message + "（HTTP " + response.statusCode() + "）" + abbreviate(response.body()));
            }
            return mapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {
            });
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL, message + "：请求被中断");
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL, message + "：" + e.getMessage());
        }
    }

    private static String formEncode(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    private static String normalizeIssuer(String issuer) {
        if (issuer == null) {
            return null;
        }
        String trimmed = issuer.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static String describe(Object value) {
        String text = str(value);
        return text == null || text.isBlank() ? "" : "：" + text;
    }

    private static String abbreviate(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ");
        return "：" + (compact.length() > 200 ? compact.substring(0, 200) + "..." : compact);
    }
}
