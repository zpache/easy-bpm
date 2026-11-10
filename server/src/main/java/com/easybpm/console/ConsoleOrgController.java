package com.easybpm.console;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easybpm.common.ApiResponse;
import com.easybpm.common.PageResult;
import com.easybpm.org.OrgDept;
import com.easybpm.org.OrgDeptMapper;
import com.easybpm.org.OrgUser;
import com.easybpm.org.OrgUserMapper;
import com.easybpm.org.OrgUserRole;
import com.easybpm.org.OrgUserRoleMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台组织数据查询（只读）。组织数据的写入入口是 OpenAPI /org/sync，控制台用于核对同步结果。
 * 表受租户插件隔离，无需手动加租户条件。
 */
@RestController
@RequestMapping("/api/org")
public class ConsoleOrgController {

    private final OrgUserMapper userMapper;
    private final OrgDeptMapper deptMapper;
    private final OrgUserRoleMapper roleMapper;

    public ConsoleOrgController(OrgUserMapper userMapper, OrgDeptMapper deptMapper, OrgUserRoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.roleMapper = roleMapper;
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
