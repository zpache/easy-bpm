package com.easybpm.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.tenant.Tenant;
import com.easybpm.tenant.TenantService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ConsoleAuthService {

    private final TenantService tenantService;
    private final ConsoleUserMapper userMapper;
    private final PasswordService passwordService;
    private final JwtService jwtService;

    public ConsoleAuthService(TenantService tenantService, ConsoleUserMapper userMapper,
                              PasswordService passwordService, JwtService jwtService) {
        this.tenantService = tenantService;
        this.userMapper = userMapper;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
    }

    public LoginResult login(String tenantCode, String username, String password) {
        Tenant tenant = tenantService.requireByCode(tenantCode);
        // bpm_console_user 不参与租户插件自动隔离，手动按租户过滤
        ConsoleUser user = userMapper.selectOne(new LambdaQueryWrapper<ConsoleUser>()
                .eq(ConsoleUser::getTenantId, tenant.getId())
                .eq(ConsoleUser::getUsername, username));

        if (user == null || !passwordService.matches(password, user.getPasswordHash())) {
            // SSO 专属账号没有密码散列，直接提示走企业登录，避免用户反复试密码
            if (user != null && user.getPasswordHash() == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "该账号已启用企业单点登录，请点击「企业登录」");
            }
            throw new BizException(ErrorCode.UNAUTHORIZED, "租户编码、账号或密码不正确");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已停用");
        }

        LocalDateTime now = LocalDateTime.now();
        ConsoleUser update = new ConsoleUser();
        update.setId(user.getId());
        update.setLastLoginAt(now);
        userMapper.updateById(update);

        AuthPrincipal principal = new AuthPrincipal(user.getId(), tenant.getId(), user.getDisplayName(),
                user.getRole(), user.getExternalUserId());
        String token = jwtService.issue(principal);
        return new LoginResult(token, jwtService.ttlSeconds(), principal, tenant.getCode(), tenant.getName(),
                user.getUsername());
    }

    public LoginResult me(AuthPrincipal principal) {
        Tenant tenant = tenantService.requireById(principal.tenantId());
        return new LoginResult(null, jwtService.ttlSeconds(), principal, tenant.getCode(), tenant.getName(),
                null);
    }

    public record LoginResult(String token, long expiresIn, AuthPrincipal user, String tenantCode,
                              String tenantName, String username) {
    }
}
