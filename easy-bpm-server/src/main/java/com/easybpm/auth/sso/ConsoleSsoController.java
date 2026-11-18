package com.easybpm.auth.sso;

import com.easybpm.auth.ConsoleAuthService;
import com.easybpm.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;

/**
 * 控制台 SSO 登录入口（匿名）。
 *
 * <p>这些接口都在 ConsoleAuthFilter 的白名单内：授权与回调用的是 IdP 的
 * state/nonce/PKCE 与一次性 ticket，不依赖本地登录态。
 */
@RestController
@RequestMapping("/api/auth/sso")
@Tag(name = "控制台 SSO 登录")
public class ConsoleSsoController {

    private final SsoLoginService ssoLoginService;

    public ConsoleSsoController(SsoLoginService ssoLoginService) {
        this.ssoLoginService = ssoLoginService;
    }

    @GetMapping("/providers")
    @Operation(summary = "登录页探测租户已启用的单点登录方式")
    public ApiResponse<List<SsoLoginService.ProviderBrief>> providers(@RequestParam String tenantCode) {
        return ApiResponse.ok(ssoLoginService.providers(tenantCode));
    }

    @GetMapping("/authorize")
    @Operation(summary = "跳转到企业 IdP 授权页")
    public RedirectView authorize(@RequestParam String tenantCode,
                                 @RequestParam(required = false) String provider,
                                 @RequestParam(required = false) String redirect) {
        return new RedirectView(ssoLoginService.authorizeUrl(tenantCode, provider, redirect));
    }

    @GetMapping("/callback")
    @Operation(summary = "IdP 回调：校验身份后 302 回控制台")
    public RedirectView callback(@RequestParam(required = false) String code,
                                 @RequestParam(required = false) String state) {
        return new RedirectView(ssoLoginService.callback(code, state));
    }

    @PostMapping("/exchange")
    @Operation(summary = "用一次性 ticket 换取控制台登录态")
    public ApiResponse<ConsoleAuthService.LoginResult> exchange(@Valid @RequestBody ExchangeRequest request) {
        return ApiResponse.ok(ssoLoginService.exchange(request.ticket()));
    }

    public record ExchangeRequest(@NotBlank String ticket) {
    }
}
