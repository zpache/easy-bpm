package com.easybpm.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easybpm.auth.ConsoleUser;
import com.easybpm.auth.ConsoleUserMapper;
import com.easybpm.auth.PasswordService;
import com.easybpm.common.Ids;
import com.easybpm.flow.FlowDefinition;
import com.easybpm.flow.FlowDefinitionService;
import com.easybpm.org.OrgDept;
import com.easybpm.org.OrgDeptMapper;
import com.easybpm.org.OrgUser;
import com.easybpm.org.OrgUserMapper;
import com.easybpm.org.OrgUserRole;
import com.easybpm.org.OrgUserRoleMapper;
import com.easybpm.tenant.Tenant;
import com.easybpm.tenant.TenantContext;
import com.easybpm.tenant.TenantCredential;
import com.easybpm.tenant.TenantCredentialMapper;
import com.easybpm.tenant.TenantMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 演示数据。仅在 easybpm.demo.enabled=true 时执行，且全部按唯一键幂等写入。
 */
@Component
@ConditionalOnProperty(name = "easybpm.demo.enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final String TENANT_ID = "t-demo";
    private static final String TENANT_CODE = "demo";
    private static final String APP_KEY = "ak_demo";
    private static final String APP_SECRET = "sk_demo_2026";

    private final TenantMapper tenantMapper;
    private final TenantCredentialMapper credentialMapper;
    private final ConsoleUserMapper consoleUserMapper;
    private final OrgDeptMapper deptMapper;
    private final OrgUserMapper orgUserMapper;
    private final OrgUserRoleMapper roleMapper;
    private final PasswordService passwordService;
    private final FlowDefinitionService definitions;

    public DemoDataInitializer(TenantMapper tenantMapper, TenantCredentialMapper credentialMapper,
                               ConsoleUserMapper consoleUserMapper, OrgDeptMapper deptMapper,
                               OrgUserMapper orgUserMapper, OrgUserRoleMapper roleMapper,
                               PasswordService passwordService, FlowDefinitionService definitions) {
        this.tenantMapper = tenantMapper;
        this.credentialMapper = credentialMapper;
        this.consoleUserMapper = consoleUserMapper;
        this.deptMapper = deptMapper;
        this.orgUserMapper = orgUserMapper;
        this.roleMapper = roleMapper;
        this.passwordService = passwordService;
        this.definitions = definitions;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (Exception e) {
            log.error("演示数据初始化失败，不影响服务启动", e);
        }
    }

    private void seed() {
        LocalDateTime now = LocalDateTime.now();
        if (tenantMapper.selectById(TENANT_ID) == null) {
            Tenant tenant = new Tenant();
            tenant.setId(TENANT_ID);
            tenant.setCode(TENANT_CODE);
            tenant.setName("演示租户");
            tenant.setStatus("ACTIVE");
            tenant.setWebhookSecret("whsec_demo_2026");
            tenant.setRemark("easy-bpm 演示数据");
            tenant.setCreatedAt(now);
            tenant.setUpdatedAt(now);
            tenantMapper.insert(tenant);
        }

        if (credentialMapper.selectOne(new LambdaQueryWrapper<TenantCredential>()
                .eq(TenantCredential::getAppKey, APP_KEY)) == null) {
            TenantCredential credential = new TenantCredential();
            credential.setId(Ids.id());
            credential.setTenantId(TENANT_ID);
            credential.setName("演示应用");
            credential.setAppKey(APP_KEY);
            credential.setAppSecret(APP_SECRET);
            credential.setStatus("ACTIVE");
            credential.setCreatedAt(now);
            credentialMapper.insert(credential);
        }

        TenantContext.runAs(TENANT_ID, () -> {
            seedOrg(now);
            seedConsoleUsers(now);
            seedFlows();
        });
        log.info("easy-bpm 演示数据就绪：tenant={}, appKey={}, 控制台 admin/admin123", TENANT_CODE, APP_KEY);
    }

    private void seedOrg(LocalDateTime now) {
        dept("D-SALES", "销售部", "u1002", 1, now);
        dept("D-FIN", "财务部", "u1003", 2, now);
        dept("D-HR", "人事部", "u1004", 3, now);

        user("u1001", "张三", "D-SALES", "销售部", "u1002", "销售专员", now);
        user("u1002", "李四", "D-SALES", "销售部", "u1003", "销售总监", now);
        user("u1003", "王五", "D-FIN", "财务部", null, "财务总监", now);
        user("u1004", "赵六", "D-HR", "人事部", null, "人事经理", now);

        role("u1003", "FINANCE", now);
        role("u1004", "HR", now);
        role("u1001", "EMPLOYEE", now);
    }

    private void seedConsoleUsers(LocalDateTime now) {
        consoleUser("admin", "admin123", "系统管理员", "u1001", "TENANT_ADMIN", now);
        consoleUser("lisi", "admin123", "李四", "u1002", "MEMBER", now);
        consoleUser("wangwu", "admin123", "王五", "u1003", "MEMBER", now);
    }

    private void seedFlows() {
        if (definitions.find("leave") == null) {
            Map<String, Object> form = Map.of("fields", List.of(
                    Map.of("key", "days", "label", "请假天数", "type", "number", "required", true),
                    Map.of("key", "reason", "label", "请假事由", "type", "textarea", "required", true)));
            Map<String, Object> schema = Map.of("nodes", List.of(
                    Map.of("id", "start", "type", "START", "name", "发起人"),
                    Map.of("id", "n1", "type", "APPROVAL", "name", "直属主管审批", "props",
                            Map.of("approverType", "INITIATOR_LEADER", "leaderLevel", 1, "mode", "ANY")),
                    Map.of("id", "n2", "type", "APPROVAL", "name", "人事审批", "props",
                            Map.of("approverType", "ROLE", "roleCodes", List.of("HR"), "mode", "ANY")),
                    Map.of("id", "end", "type", "END", "name", "结束")));
            definitions.save(new FlowDefinitionService.SaveCommand("leave", "请假审批", "人事", "calendar",
                    "员工请假：直属主管 → 人事", form, schema), "system");
            definitions.publish("leave", "system", "初始化演示流程");
        }

        if (definitions.find("expense") == null) {
            Map<String, Object> form = Map.of("fields", List.of(
                    Map.of("key", "amount", "label", "报销金额", "type", "number", "required", true),
                    Map.of("key", "reason", "label", "报销事由", "type", "textarea", "required", true)));
            Map<String, Object> schema = Map.of("nodes", List.of(
                    Map.of("id", "start", "type", "START", "name", "发起人"),
                    Map.of("id", "n1", "type", "APPROVAL", "name", "直属主管审批", "props",
                            Map.of("approverType", "INITIATOR_LEADER", "leaderLevel", 1, "mode", "ANY")),
                    Map.of("id", "n2", "type", "APPROVAL", "name", "财务审批", "condition", "amount > 5000",
                            "props", Map.of("approverType", "ROLE", "roleCodes", List.of("FINANCE"), "mode", "ANY")),
                    Map.of("id", "end", "type", "END", "name", "结束")));
            definitions.save(new FlowDefinitionService.SaveCommand("expense", "费用报销", "财务", "money",
                    "金额超过 5000 元需财务审批", form, schema), "system");
            definitions.publish("expense", "system", "初始化演示流程");
        }
    }

    private void dept(String code, String name, String leaderId, int sort, LocalDateTime now) {
        if (deptMapper.selectOne(new LambdaQueryWrapper<OrgDept>().eq(OrgDept::getDeptCode, code)) != null) {
            return;
        }
        OrgDept dept = new OrgDept();
        dept.setId(Ids.id());
        dept.setDeptCode(code);
        dept.setName(name);
        dept.setLeaderId(leaderId);
        dept.setSortNo(sort);
        dept.setCreatedAt(now);
        dept.setUpdatedAt(now);
        deptMapper.insert(dept);
    }

    private void user(String code, String name, String deptCode, String deptName, String leaderCode,
                      String title, LocalDateTime now) {
        if (orgUserMapper.selectOne(new LambdaQueryWrapper<OrgUser>().eq(OrgUser::getUserCode, code)) != null) {
            return;
        }
        OrgUser user = new OrgUser();
        user.setId(Ids.id());
        user.setUserCode(code);
        user.setName(name);
        user.setDeptCode(deptCode);
        user.setDeptName(deptName);
        user.setLeaderCode(leaderCode);
        user.setTitle(title);
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        orgUserMapper.insert(user);
    }

    private void role(String userCode, String roleCode, LocalDateTime now) {
        if (roleMapper.selectOne(new LambdaQueryWrapper<OrgUserRole>()
                .eq(OrgUserRole::getUserCode, userCode)
                .eq(OrgUserRole::getRoleCode, roleCode)) != null) {
            return;
        }
        OrgUserRole role = new OrgUserRole();
        role.setId(Ids.id());
        role.setUserCode(userCode);
        role.setRoleCode(roleCode);
        role.setCreatedAt(now);
        roleMapper.insert(role);
    }

    private void consoleUser(String username, String password, String displayName, String externalUserId,
                             String role, LocalDateTime now) {
        if (consoleUserMapper.selectOne(new LambdaQueryWrapper<ConsoleUser>()
                .eq(ConsoleUser::getUsername, username)) != null) {
            return;
        }
        ConsoleUser user = new ConsoleUser();
        user.setId(Ids.id());
        user.setUsername(username);
        user.setPasswordHash(passwordService.hash(password));
        user.setDisplayName(displayName);
        user.setExternalUserId(externalUserId);
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        consoleUserMapper.insert(user);
    }
}
