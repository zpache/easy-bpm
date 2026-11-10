package com.easybpm.console;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easybpm.audit.AuditService;
import com.easybpm.auth.CurrentUser;
import com.easybpm.common.ApiResponse;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.PageResult;
import com.easybpm.org.OrgDept;
import com.easybpm.org.OrgDeptMapper;
import com.easybpm.org.OrgService;
import com.easybpm.org.OrgUser;
import com.easybpm.org.OrgUserMapper;
import com.easybpm.org.OrgUserRole;
import com.easybpm.org.OrgUserRoleMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 控制台组织管理：查询 + 管理员维护入口（人员/部门/角色分配）。
 * 写接口复用 OrgService 的 upsert 逻辑，与 OpenAPI /org/sync 幂等语义一致；敏感操作写审计。
 * 表受租户插件隔离，无需手动加租户条件。
 */
@RestController
@RequestMapping("/api/org")
public class ConsoleOrgController {

    private final OrgUserMapper userMapper;
    private final OrgDeptMapper deptMapper;
    private final OrgUserRoleMapper roleMapper;
    private final OrgService orgService;
    private final AuditService audit;

    public ConsoleOrgController(OrgUserMapper userMapper, OrgDeptMapper deptMapper,
                                OrgUserRoleMapper roleMapper, OrgService orgService, AuditService audit) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.roleMapper = roleMapper;
        this.orgService = orgService;
        this.audit = audit;
    }

    @GetMapping("/depts")
    public ApiResponse<List<DeptView>> depts() {
        List<OrgDept> list = deptMapper.selectList(new LambdaQueryWrapper<OrgDept>()
                .orderByAsc(OrgDept::getSortNo).orderByAsc(OrgDept::getDeptCode));
        return ApiResponse.ok(list.stream()
                .map(d -> new DeptView(d.getDeptCode(), d.getName(), d.getParentCode(),
                        d.getLeaderId(), d.getSortNo(), d.getUpdatedAt()))
                .toList());
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<UserView>> users(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String deptCode,
                                                   @RequestParam(required = false) String status) {
        LambdaQueryWrapper<OrgUser> wrapper = new LambdaQueryWrapper<OrgUser>()
                .eq(deptCode != null && !deptCode.isBlank(), OrgUser::getDeptCode, deptCode)
                .eq(status != null && !status.isBlank(), OrgUser::getStatus, status)
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(OrgUser::getName, keyword)
                                .or().like(OrgUser::getUserCode, keyword)
                                .or().like(OrgUser::getMobile, keyword))
                .orderByDesc(OrgUser::getUpdatedAt);
        Page<OrgUser> result = userMapper.selectPage(new Page<>(Math.max(page, 1),
                Math.min(Math.max(size, 1), 100)), wrapper);
        List<UserView> views = result.getRecords().stream()
                .map(u -> new UserView(u.getUserCode(), u.getName(), u.getDeptCode(), u.getDeptName(),
                        u.getLeaderCode(), u.getTitle(), u.getMobile(), u.getEmail(), u.getStatus(),
                        u.getUpdatedAt()))
                .toList();
        return ApiResponse.ok(PageResult.of(views, result.getTotal(), page, size));
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleView>> roles() {
        List<OrgUserRole> all = roleMapper.selectList(null);
        Map<String, Long> counts = new LinkedHashMap<>();
        all.forEach(r -> counts.merge(r.getRoleCode(), 1L, Long::sum));
        return ApiResponse.ok(counts.entrySet().stream()
                .map(e -> new RoleView(e.getKey(), e.getValue().intValue()))
                .toList());
    }

    @GetMapping("/roles/{roleCode}/users")
    public ApiResponse<List<String>> roleUsers(@PathVariable String roleCode) {
        return ApiResponse.ok(orgService.findUsersByRole(roleCode));
    }

    // ---------------- 管理员维护接口 ----------------

    /** 新增/编辑部门（按 deptCode 幂等 upsert）。 */
    @PostMapping("/depts")
    public ApiResponse<Void> saveDept(@Valid @RequestBody SaveDeptRequest request) {
        requireAdmin();
        OrgDept dept = new OrgDept();
        dept.setDeptCode(request.deptCode().trim());
        dept.setName(request.name().trim());
        dept.setParentCode(blankToNull(request.parentCode()));
        dept.setLeaderId(blankToNull(request.leaderId()));
        dept.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        orgService.upsertDept(dept);
        audit.record("DEPT_SAVE", "DEPT", dept.getDeptCode(), "保存部门「" + dept.getName() + "」(" + dept.getDeptCode() + ")");
        return ApiResponse.ok();
    }

    @DeleteMapping("/depts/{deptCode}")
    public ApiResponse<Void> deleteDept(@PathVariable String deptCode) {
        requireAdmin();
        OrgDept dept = orgService.findDept(deptCode);
        if (dept == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "部门不存在：" + deptCode);
        }
        if (deptMapper.selectCount(new LambdaQueryWrapper<OrgDept>()
                .eq(OrgDept::getParentCode, deptCode)) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "存在下级部门，请先处理下级部门");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<OrgUser>()
                .eq(OrgUser::getDeptCode, deptCode)) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "部门下仍有人员，请先迁移人员");
        }
        deptMapper.deleteById(dept.getId());
        audit.record("DEPT_DELETE", "DEPT", deptCode, "删除部门「" + dept.getName() + "」(" + deptCode + ")");
        return ApiResponse.ok();
    }

    /** 新增/编辑人员（按 userCode 幂等 upsert；编辑时角色列表全量替换，空数组表示清空）。 */
    @PostMapping("/users")
    public ApiResponse<Void> saveUser(@Valid @RequestBody SaveUserRequest request) {
        requireAdmin();
        OrgUser user = new OrgUser();
        user.setUserCode(request.userCode().trim());
        user.setName(request.name().trim());
        user.setDeptCode(blankToNull(request.deptCode()));
        user.setDeptName(resolveDeptName(user.getDeptCode()));
        user.setLeaderCode(blankToNull(request.leaderCode()));
        if (user.getLeaderCode() != null && user.getLeaderCode().equals(user.getUserCode())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "直属主管不能是自己");
        }
        user.setTitle(blankToNull(request.title()));
        user.setMobile(blankToNull(request.mobile()));
        user.setEmail(blankToNull(request.email()));
        user.setStatus(request.status() == null || request.status().isBlank() ? "ACTIVE" : request.status());
        orgService.upsertUser(user);
        if (request.roleCodes() != null) {
            orgService.replaceRoles(user.getUserCode(), request.roleCodes());
        }
        audit.record("USER_SAVE", "USER", user.getUserCode(),
                "保存人员「" + user.getName() + "」(" + user.getUserCode() + ")，状态=" + user.getStatus()
                        + (request.roleCodes() == null ? "" : "，角色=" + request.roleCodes()));
        return ApiResponse.ok();
    }

    @DeleteMapping("/users/{userCode}")
    public ApiResponse<Void> deleteUser(@PathVariable String userCode) {
        requireAdmin();
        OrgUser user = orgService.findUser(userCode);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "人员不存在：" + userCode);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<OrgUser>()
                .eq(OrgUser::getLeaderCode, userCode)) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该人员是其他人员的直属主管，请先调整其下属的主管设置");
        }
        List<OrgDept> leadingDepts = deptMapper.selectList(new LambdaQueryWrapper<OrgDept>()
                .eq(OrgDept::getLeaderId, userCode));
        if (!leadingDepts.isEmpty()) {
            String names = leadingDepts.stream().map(OrgDept::getName).toList().toString();
            throw new BizException(ErrorCode.CONFLICT, "该人员是部门主管：" + names + "，请先调整相关部门的主管设置");
        }
        roleMapper.delete(new LambdaQueryWrapper<OrgUserRole>().eq(OrgUserRole::getUserCode, userCode));
        userMapper.deleteById(user.getId());
        audit.record("USER_DELETE", "USER", userCode, "删除人员「" + user.getName() + "」(" + userCode + ")，角色已同步清除");
        return ApiResponse.ok();
    }

    /** 角色人员全量分配（与当前成员 diff，增删映射）。 */
    @PostMapping("/roles/{roleCode}/assign")
    public ApiResponse<RoleAssignResult> assignRole(@PathVariable String roleCode,
                                                    @Valid @RequestBody AssignRoleRequest request) {
        requireAdmin();
        List<String> current = orgService.findUsersByRole(roleCode);
        LinkedHashSet<String> target = new LinkedHashSet<>(request.userCodes().stream()
                .filter(c -> c != null && !c.isBlank()).map(String::trim).toList());
        List<String> added = new ArrayList<>(target.stream().filter(c -> !current.contains(c)).toList());
        List<String> removed = new ArrayList<>(current.stream().filter(c -> !target.contains(c)).toList());
        for (String userCode : removed) {
            roleMapper.delete(new LambdaQueryWrapper<OrgUserRole>()
                    .eq(OrgUserRole::getUserCode, userCode)
                    .eq(OrgUserRole::getRoleCode, roleCode));
        }
        LocalDateTime now = LocalDateTime.now();
        for (String userCode : added) {
            if (orgService.findUser(userCode) == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "人员不存在：" + userCode);
            }
            OrgUserRole role = new OrgUserRole();
            role.setId(com.easybpm.common.Ids.id());
            role.setUserCode(userCode);
            role.setRoleCode(roleCode);
            role.setCreatedAt(now);
            roleMapper.insert(role);
        }
        if (!added.isEmpty() || !removed.isEmpty()) {
            audit.record("ROLE_ASSIGN", "ROLE", roleCode,
                    "分配角色 " + roleCode + "：+ " + added + " / - " + removed);
        }
        return ApiResponse.ok(new RoleAssignResult(added.size(), removed.size()));
    }

    private void requireAdmin() {
        if (!CurrentUser.require().isTenantAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要管理员权限");
        }
    }

    private String resolveDeptName(String deptCode) {
        OrgDept dept = orgService.findDept(deptCode);
        return dept == null ? null : dept.getName();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record SaveDeptRequest(@NotBlank String deptCode, @NotBlank String name,
                                  String parentCode, String leaderId, Integer sortNo) {
    }

    public record SaveUserRequest(@NotBlank String userCode, @NotBlank String name, String deptCode,
                                  String leaderCode, String title, String mobile, String email,
                                  String status, List<String> roleCodes) {
    }

    public record AssignRoleRequest(List<String> userCodes) {
    }

    public record RoleAssignResult(int added, int removed) {
    }

    public record DeptView(String deptCode, String name, String parentCode, String leaderId,
                           Integer sortNo, LocalDateTime updatedAt) {
    }

    public record UserView(String userCode, String name, String deptCode, String deptName,
                           String leaderCode, String title, String mobile, String email,
                           String status, LocalDateTime updatedAt) {
    }

    public record RoleView(String roleCode, int userCount) {
    }
}
