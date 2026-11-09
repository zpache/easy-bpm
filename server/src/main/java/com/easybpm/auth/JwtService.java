package com.easybpm.auth;

import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 控制台 JWT。自包含签发，无需 Redis 会话，便于独立部署。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlMillis;

    public JwtService(@Value("${easybpm.security.jwt-secret}") String secret,
                      @Value("${easybpm.security.token-ttl-hours:12}") long ttlHours) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("easybpm.security.jwt-secret 至少需要 32 个字符");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlHours * 3600_000L;
    }

    public String issue(AuthPrincipal principal) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(principal.consoleUserId())
                .claim("tid", principal.tenantId())
                .claim("name", principal.displayName())
                .claim("role", principal.role())
                .claim("uid", principal.externalUserId())
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(key)
                .compact();
    }

    public AuthPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return new AuthPrincipal(
                    claims.getSubject(),
                    claims.get("tid", String.class),
                    claims.get("name", String.class),
                    claims.get("role", String.class),
                    claims.get("uid", String.class));
        } catch (Exception e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
    }

    public long ttlSeconds() {
        return ttlMillis / 1000;
    }
}
