package com.easybpm.org;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织数据读取。审批人解析全部走这里，接入方只需同步组织，不需要把人员关系传给审批引擎。
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
}
