package com.easybpm.auth;

import com.easybpm.common.ApiResponse;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.JsonCodec;
import com.easybpm.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 控制台登录态校验：/api/** 除登录接口外都必须带 Bearer Token。
 */
@Component
public class ConsoleAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ConsoleUserMapper userMapper;
    private final JsonCodec json;

    public ConsoleAuthFilter(JwtService jwtService, ConsoleUserMapper userMapper, JsonCodec json) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || path.equals("/api/auth/login")
                // SSO 授权跳转与回调依赖 IdP state/nonce/PKCE，且发生在登录之前
                || path.startsWith("/api/auth/sso/")
                || path.startsWith("/api/public/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "缺少登录凭证");
            }
            AuthPrincipal principal = jwtService.parse(header.substring(7).trim());
            TenantContext.set(principal.tenantId());

            ConsoleUser user = userMapper.selectById(principal.consoleUserId());
            if (user == null || !"ACTIVE".equals(user.getStatus())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "账号不可用，请重新登录");
            }
            CurrentUser.set(principal);
            chain.doFilter(request, response);
        } catch (BizException e) {
            writeError(response, e);
        } finally {
            TenantContext.clear();
            CurrentUser.clear();
        }
    }

    private void writeError(HttpServletResponse response, BizException e) throws IOException {
        response.setStatus(e.errorCode().httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json.write(ApiResponse.fail(e.errorCode(), e.getMessage())));
    }
}
