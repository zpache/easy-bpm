package com.easybpm.auth.sso;

import com.easybpm.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台管理员：租户 SSO 配置与身份绑定管理。
 *
 * <p>刻意放在平台侧而不是租户侧：IdP 的 client_secret 决定"谁能登录进来"，
 * 若租户管理员也能改，就可能把认证指向自己的 IdP 完成账号接管。
 */
@RestController
@RequestMapping("/api/platform/tenants/{tenantId}/sso")
@Tag(name = "平台管理 - 租户 SSO")
public class ConsoleSsoAdminController {

    private final SsoProviderService providerService;

    public ConsoleSsoAdminController(SsoProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    @Operation(summary = "查询租户 SSO 配置（client_secret 掩码返回）")
    public ApiResponse<SsoProviderService.SsoConfigView> get(@PathVariable String tenantId) {
        return ApiResponse.ok(providerService.get(tenantId));
    }

    @PostMapping
    @Operation(summary = "新建或更新租户 SSO 配置")
    public ApiResponse<SsoProviderService.SsoConfigView> save(@PathVariable String tenantId,
                                                             @RequestBody SsoProviderService.SaveRequest request) {
        return ApiResponse.ok(providerService.save(tenantId, request));
    }

    @PostMapping("/test")
    @Operation(summary = "测试 issuer 连通性（只做 discovery）")
    public ApiResponse<SsoProviderService.TestResult> test(@PathVariable String tenantId,
                                                          @RequestBody TestRequest request) {
        return ApiResponse.ok(providerService.test(tenantId, request.issuer()));
    }

    @GetMapping("/identities")
    @Operation(summary = "已认证的企业身份列表（PENDING 为待绑定）")
    public ApiResponse<List<SsoIdentityView>> identities(@PathVariable String tenantId,
                                                         @RequestParam(required = false) String status) {
        return ApiResponse.ok(providerService.identities(tenantId, status).stream()
                .map(SsoIdentityView::of).toList());
    }

    @GetMapping("/candidates")
    @Operation(summary = "可绑定的控制台账号")
    public ApiResponse<List<SsoProviderService.CandidateView>> candidates(@PathVariable String tenantId) {
        return ApiResponse.ok(providerService.candidates(tenantId));
    }

    @PostMapping("/identities/{identityId}/bind")
    @Operation(summary = "把企业身份绑定到控制台账号")
    public ApiResponse<SsoIdentityView> bind(@PathVariable String tenantId, @PathVariable String identityId,
                                             @Valid @RequestBody SsoProviderService.BindRequest request) {
        return ApiResponse.ok(SsoIdentityView.of(providerService.bind(tenantId, identityId, request)));
    }

    @PostMapping("/identities/{identityId}/unbind")
    @Operation(summary = "解绑企业身份（保留记录用于审计）")
    public ApiResponse<SsoIdentityView> unbind(@PathVariable String tenantId, @PathVariable String identityId) {
        return ApiResponse.ok(SsoIdentityView.of(providerService.unbind(tenantId, identityId)));
    }

    public record TestRequest(String issuer) {
    }

    /** subject 对外只展示前 12 位，避免把完整 IdP 标识暴露在管理页截图里。 */
    public record SsoIdentityView(String id, String subject, String subjectShort, String email,
                                  String displayName, String status, String username,
                                  String consoleDisplayName, String lastAttemptAt, String lastLoginAt) {

        static SsoIdentityView of(SsoProviderService.IdentityView view) {
            String subject = view.subject();
            String shortSubject = subject == null || subject.length() <= 12
                    ? subject : subject.substring(0, 12) + "…";
            return new SsoIdentityView(view.id(), subject, shortSubject, view.email(), view.displayName(),
                    view.status(), view.username(), view.consoleDisplayName(),
                    view.lastAttemptAt() == null ? null : view.lastAttemptAt().toString(),
                    view.lastLoginAt() == null ? null : view.lastLoginAt().toString());
        }
    }
}
