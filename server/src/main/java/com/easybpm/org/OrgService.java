package com.easybpm.org;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easybpm.common.BizException;
import com.easybpm.common.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织数据读取与同步。审批人解析全部走这里，接入方只需同步组织，不需要把人员关系传给审批引擎。
 */
@Service
public class OrgService {

    private final OrgUserMapper userMapper;
    private final OrgDeptMapper deptMapper;
    private final OrgUserRoleMapper roleMapper;

    public OrgService(OrgUserMapper userMapper, OrgDeptMapper deptMapper, OrgUserRoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.roleMapper = roleMapper;
    }

    public OrgUser findUser(String userCode) {
        if (userCode == null || userCode.isBlank()) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<OrgUser>().eq(OrgUser::getUserCode, userCode));
    }

    public OrgDept findDept(String deptCode) {
        if (deptCode == null || deptCode.isBlank()) {
            return null;
        }
        return deptMapper.selectOne(new LambdaQueryWrapper<OrgDept>().eq(OrgDept::getDeptCode, deptCode));
    }

    /**
     * 沿直属主管链向上取第 level 级主管。
     */
    public String findLeader(String userCode, int level) {
        String current = userCode;
        for (int i = 0; i < Math.max(level, 1); i++) {
            OrgUser user = findUser(current);
            if (user == null || user.getLeaderCode() == null || user.getLeaderCode().isBlank()) {
                return null;
            }
            current = user.getLeaderCode();
        }
        return current;
    }

    public List<String> findUsersByRole(String roleCode) {
        return roleMapper.selectList(new LambdaQueryWrapper<OrgUserRole>()
                        .eq(OrgUserRole::getRoleCode, roleCode))
                .stream().map(OrgUserRole::getUserCode).distinct().toList();
    }

    public Map<String, String> namesOf(Collection<String> userCodes) {
        if (userCodes == null || userCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        userMapper.selectList(new LambdaQueryWrapper<OrgUser>().in(OrgUser::getUserCode, userCodes))
                .forEach(u -> result.put(u.getUserCode(), u.getName()));
        return result;
    }

    public Set<String> deptUserCodes(String deptCode) {
        if (deptCode == null || deptCode.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(userMapper.selectList(new LambdaQueryWrapper<OrgUser>()
                        .eq(OrgUser::getDeptCode, deptCode).eq(OrgUser::getStatus, "ACTIVE"))
                .stream().map(OrgUser::getUserCode).toList());
    }

    /** 组织同步结果统计。 */
    public record SyncResult(int usersUpserted, int deptsUpserted, int rolesUpserted) {
    }

    /** 按 userCode 幂等 upsert 人员。 */
    @Transactional
    public int upsertUser(OrgUser input) {
        if (input.getUserCode() == null || input.getUserCode().isBlank()) {
            throw BizException.badRequest("org sync: userCode 不能为空");
        }
        if (input.getName() == null || input.getName().isBlank()) {
            throw BizException.badRequest("org sync: name 不能为空（userCode=" + input.getUserCode() + "）");
        }
        OrgUser existing = findUser(input.getUserCode());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            input.setId(Ids.id());
            input.setStatus(input.getStatus() == null || input.getStatus().isBlank()
                    ? "ACTIVE" : input.getStatus());
            input.setCreatedAt(now);
            input.setUpdatedAt(now);
            userMapper.insert(input);
            return 1;
        }
        existing.setName(input.getName());
        existing.setDeptCode(input.getDeptCode());
        existing.setDeptName(input.getDeptName());
        existing.setLeaderCode(input.getLeaderCode());
        existing.setTitle(input.getTitle());
        existing.setMobile(input.getMobile());
        existing.setEmail(input.getEmail());
        existing.setStatus(input.getStatus() == null || input.getStatus().isBlank()
                ? existing.getStatus() : input.getStatus());
        existing.setUpdatedAt(now);
        userMapper.updateById(existing);
        return 1;
    }

    /** 按 deptCode 幂等 upsert 部门。 */
    @Transactional
    public int upsertDept(OrgDept input) {
        if (input.getDeptCode() == null || input.getDeptCode().isBlank()) {
            throw BizException.badRequest("org sync: deptCode 不能为空");
        }
        if (input.getName() == null || input.getName().isBlank()) {
            throw BizException.badRequest("org sync: 部门 name 不能为空（deptCode=" + input.getDeptCode() + "）");
        }
        OrgDept existing = findDept(input.getDeptCode());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            input.setId(Ids.id());
            input.setSortNo(input.getSortNo() == null ? 0 : input.getSortNo());
            input.setCreatedAt(now);
            input.setUpdatedAt(now);
            deptMapper.insert(input);
            return 1;
        }
        existing.setName(input.getName());
        existing.setParentCode(input.getParentCode());
        existing.setLeaderId(input.getLeaderId());
        if (input.getSortNo() != null) {
            existing.setSortNo(input.getSortNo());
        }
        existing.setUpdatedAt(now);
        deptMapper.updateById(existing);
        return 1;
    }

    /** 全量替换某人员的角色列表（roleCodes 为空表示清空）。 */
    @Transactional
    public int replaceRoles(String userCode, List<String> roleCodes) {
        if (userCode == null || userCode.isBlank()) {
            throw BizException.badRequest("org sync: role.userCode 不能为空");
        }
        roleMapper.delete(new LambdaQueryWrapper<OrgUserRole>().eq(OrgUserRole::getUserCode, userCode));
        if (roleCodes == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (String roleCode : new LinkedHashSet<>(roleCodes)) {
            if (roleCode == null || roleCode.isBlank()) {
                continue;
            }
            OrgUserRole role = new OrgUserRole();
            role.setId(Ids.id());
            role.setUserCode(userCode);
            role.setRoleCode(roleCode);
            role.setCreatedAt(now);
            roleMapper.insert(role);
        }
        return roleCodes.size();
    }
}
