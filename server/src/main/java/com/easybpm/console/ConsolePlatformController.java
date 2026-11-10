package com.easybpm.console;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easybpm.audit.AuditService;
import com.easybpm.auth.AuthPrincipal;
import com.easybpm.auth.ConsoleUser;
import com.easybpm.auth.ConsoleUserMapper;
import com.easybpm.auth.CurrentUser;
import com.easybpm.auth.JwtService;
import com.easybpm.auth.PasswordService;
import com.easybpm.common.ApiResponse;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.Ids;
import com.easybpm.engine.BpmInstance;
import com.easybpm.engine.BpmInstanceMapper;
import com.easybpm.flow.FlowDefinition;
import com.easybpm.flow.FlowDefinitionMapper;
import com.easybpm.tenant.Tenant;
import com.easybpm.tenant.TenantContext;
import com.easybpm.tenant.TenantCredential;
import com.easybpm.tenant.TenantCredentialMapper;
import com.easybpm.tenant.TenantMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台管理员能力：租户全生命周期管理 + 切换租户上下文。
 *
 * <p>平台管理员挂在独立的「platform」租户下（tenant_code=platform），
 * 通过 switch 签发目标租户的临时 token 进入租户视角，前端保留原 token 可一键返回。
 * bpm_tenant / bpm_tenant_credential 不参与租户插件隔离，可直接查询；
 * 其余业务表统计需 TenantContext.callAs 切换上下文。
 */
@RestController
@RequestMapping("/api/platform")
public class ConsolePlatformController {

    /** 平台租户编码，与 DemoDataInitializer 保持一致 */
    public static final String PLATFORM_TENANT_CODE = "platform";
    /** 正则用全限定名，避免与 jakarta.validation.constraints.Pattern 冲突 */
    private static final java.util.regex.Pattern CODE_PATTERN =
            java.util.regex.Pattern.compile("^[a-z][a-z0-9-]{1,31}$");

    private final TenantMapper tenantMapper;
    private final TenantCredentialMapper credentialMapper;
    private final ConsoleUserMapper consoleUserMapper;
    private final FlowDefinitionMapper flowDefinitionMapper;
    private final BpmInstanceMapper instanceMapper;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final AuditService audit;

    public ConsolePlatformController(TenantMapper tenantMapper, TenantCredentialMapper credentialMapper,
                                     ConsoleUserMapper consoleUserMapper, FlowDefinitionMapper flowDefinitionMapper,
                                     BpmInstanceMapper instanceMapper, PasswordService passwordService,
                                     JwtService jwtService, AuditService audit) {
        this.tenantMapper = tenantMapper;
        this.credentialMapper = credentialMapper;
        this.consoleUserMapper = consoleUserMapper;
        this.flowDefinitionMapper = flowDefinitionMapper;
        this.instanceMapper = instanceMapper;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.audit = audit;
    }

    @GetMapping("/tenants")
    public ApiResponse<List<TenantView>> tenants() {
        requirePlatformAdmin();
        List<Tenant> tenants = tenantMapper.selectList(
                new LambdaQueryWrapper<Tenant>().orderByDesc(Tenant::getCreatedAt));
        return ApiResponse.ok(tenants.stream().map(this::toView).toList());
    }

    @PostMapping("/tenants")
    public ApiResponse<TenantView> create(@Valid @RequestBody CreateTenantRequest request) {
        requirePlatformAdmin();
        String code = request.code().trim();
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "租户编码需以小写字母开头，仅含小写字母、数字和中划线，长度 2-32");
        }
        if (tenantMapper.selectOne(new LambdaQueryWrapper<Tenant>().eq(Tenant::getCode, code)) != null) {
            throw new BizException(ErrorCode.CONFLICT, "租户编码已存在：" + code);
        }

        LocalDateTime now = LocalDateTime.now();
        Tenant tenant = new Tenant();
        tenant.setId(Ids.id());
        tenant.setCode(code);
        tenant.setName(request.name().trim());
        tenant.setStatus("ACTIVE");
        tenant.setRemark(request.remark());
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        tenantMapper.insert(tenant);

        // 初始租户管理员。bpm_console_user 不参与租户插件自动隔离，tenantId 必须显式写入
        String adminUsername = blankToNull(request.adminUsername());
        if (adminUsername != null) {
            String adminPassword = blankToNull(request.adminPassword());
            if (adminPassword == null || adminPassword.length() < 6) {
                throw new BizException(ErrorCode.BAD_REQUEST, "已填写初始管理员账号时，密码至少 6 位");
            }
            if (consoleUserMapper.selectOne(new LambdaQueryWrapper<ConsoleUser>()
                    .eq(ConsoleUser::getTenantId, tenant.getId())
                    .eq(ConsoleUser::getUsername, adminUsername)) != null) {
                throw new BizException(ErrorCode.CONFLICT, "该租户下已存在同名控制台账号：" + adminUsername);
            }
            ConsoleUser admin = new ConsoleUser();
            admin.setId(Ids.id());
            admin.setTenantId(tenant.getId());
            admin.setUsername(adminUsername);
            admin.setPasswordHash(passwordService.hash(adminPassword));
            admin.setDisplayName(request.adminDisplayName() == null || request.adminDisplayName().isBlank()
                    ? tenant.getName() + "管理员" : request.adminDisplayName().trim());
            admin.setRole("TENANT_ADMIN");
            admin.setStatus("ACTIVE");
            admin.setCreatedAt(LocalDateTime.now());
            consoleUserMapper.insert(admin);
        }

        audit.record("TENANT_CREATE", "TENANT", tenant.getId(),
                "新建租户「" + tenant.getName() + "」(" + code + ")"
                        + (adminUsername == null ? "" : "，初始管理员=" + adminUsername));
        return ApiResponse.ok(toView(tenant));
    }

    @PostMapping("/tenants/{id}")
    public ApiResponse<TenantView> update(@PathVariable String id, @Valid @RequestBody UpdateTenantRequest request) {
        requirePlatformAdmin();
        Tenant tenant = requireTenant(id);
        if (request.name() != null && !request.name().isBlank()) {
            tenant.setName(request.name().trim());
        }
        if (request.remark() != null) {
            tenant.setRemark(request.remark());
        }
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(tenant);
        audit.record("TENANT_UPDATE", "TENANT", tenant.getId(),
                "编辑租户「" + tenant.getName() + "」(" + tenant.getCode() + ")");
        return ApiResponse.ok(toView(tenant));
    }

    @PostMapping("/tenants/{id}/disable")
    public ApiResponse<TenantView> disable(@PathVariable String id) {
        requirePlatformAdmin();
        Tenant tenant = requireTenant(id);
        if (PLATFORM_TENANT_CODE.equals(tenant.getCode())) {
            throw new BizException(ErrorCode.CONFLICT, "平台租户不允许停用");
        }
        tenant.setStatus("DISABLED");
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(tenant);
        audit.record("TENANT_DISABLE", "TENANT", tenant.getId(),
                "停用租户「" + tenant.getName() + "」(" + tenant.getCode() + ")，控制台登录与 OpenAPI 已拒绝");
        return ApiResponse.ok(toView(tenant));
    }

    @PostMapping("/tenants/{id}/enable")
    public ApiResponse<TenantView> enable(@PathVariable String id) {
        requirePlatformAdmin();
        Tenant tenant = requireTenant(id);
        tenant.setStatus("ACTIVE");
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(tenant);
        audit.record("TENANT_ENABLE", "TENANT", tenant.getId(),
                "启用租户「" + tenant.getName() + "」(" + tenant.getCode() + ")");
        return ApiResponse.ok(toView(tenant));
    }

    /**
     * 切换租户上下文：签发目标租户的临时 token（角色仍为 PLATFORM_ADMIN），
     * 前端保存原 token，可随时返回平台视角。
     */
    @PostMapping("/tenants/{id}/switch")
    public ApiResponse<SwitchResult> switchTenant(@PathVariable String id) {
        requirePlatformAdmin();
        Tenant tenant = requireTenant(id);
        if (!"ACTIVE".equals(tenant.getStatus())) {
            throw new BizException(ErrorCode.TENANT_DISABLED, "租户已停用，无法进入");
        }
        AuthPrincipal principal = CurrentUser.require();
        AuthPrincipal switched = new AuthPrincipal(principal.consoleUserId(), tenant.getId(),
                principal.displayName(), "PLATFORM_ADMIN", null);
        audit.record("TENANT_SWITCH", "TENANT", tenant.getId(),
                "切换进入租户「" + tenant.getName() + "」(" + tenant.getCode() + ")，以平台管理员身份操作");
        return ApiResponse.ok(new SwitchResult(jwtService.issue(switched), jwtService.ttlSeconds(),
                tenant.getCode(), tenant.getName(), principal.displayName()));
    }

    private void requirePlatformAdmin() {
        if (!CurrentUser.require().isPlatformAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要平台管理员权限");
        }
    }

    private Tenant requireTenant(String id) {
        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "租户不存在");
        }
        return tenant;
    }

    private TenantView toView(Tenant tenant) {
        // bpm_console_user 不参与租户插件自动隔离，统计必须显式按 tenantId 过滤
        Long consoleUsers = consoleUserMapper.selectCount(new LambdaQueryWrapper<ConsoleUser>()
                .eq(ConsoleUser::getTenantId, tenant.getId()));
        Long flows = TenantContext.callAs(tenant.getId(),
                () -> flowDefinitionMapper.selectCount(new LambdaQueryWrapper<FlowDefinition>()
                        .eq(FlowDefinition::getStatus, "PUBLISHED")));
        Long running = TenantContext.callAs(tenant.getId(),
                () -> instanceMapper.selectCount(new LambdaQueryWrapper<BpmInstance>()
                        .eq(BpmInstance::getStatus, "RUNNING")));
        Long credentials = credentialMapper.selectCount(new LambdaQueryWrapper<TenantCredential>()
                .eq(TenantCredential::getTenantId, tenant.getId()));
        return new TenantView(tenant.getId(), tenant.getCode(), tenant.getName(), tenant.getStatus(),
                tenant.getRemark(), consoleUsers, credentials, flows, running, tenant.getCreatedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateTenantRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{1,31}$", message = "编码仅限小写字母、数字和中划线")
            String code,
            @NotBlank @Size(max = 128) String name,
            String remark,
            String adminUsername,
            String adminPassword,
            String adminDisplayName) {
    }

    public record UpdateTenantRequest(@Size(max = 128) String name, String remark) {
    }

    public record TenantView(String id, String code, String name, String status, String remark,
                             Long consoleUserCount, Long credentialCount, Long publishedFlowCount,
                             Long runningInstanceCount, LocalDateTime createdAt) {
    }

    public record SwitchResult(String token, long expiresIn, String tenantCode, String tenantName,
                               String displayName) {
    }
}
