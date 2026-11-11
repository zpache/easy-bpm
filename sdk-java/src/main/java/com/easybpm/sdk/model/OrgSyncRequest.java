package com.easybpm.sdk.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 组织同步请求：按编码幂等 upsert，只需传变化的段落。
 *
 * <pre>{@code
 * OrgSyncRequest sync = OrgSyncRequest.builder()
 *         .addDept(new OrgSyncRequest.Dept("D-SALES", "销售部", "D-HQ", "u1002", 10))
 *         .addUser(OrgSyncRequest.User.of("u1001", "张三", "D-SALES"))
 *         .addRole("u1003", List.of("FINANCE"))
 *         .build();
 * }</pre>
 */
public record OrgSyncRequest(List<User> users, List<Dept> depts, List<Role> roles) {

    public static Builder builder() {
        return new Builder();
    }

    /** 人员。status 缺省 ACTIVE；离职传 DISABLED，将不再被解析为审批人。 */
    public record User(String userCode, String name, String deptCode, String deptName,
                       String leaderCode, String title, String mobile, String email, String status) {

        public static User of(String userCode, String name, String deptCode) {
            return new User(userCode, name, deptCode, null, null, null, null, null, "ACTIVE");
        }
    }

    /** 部门。leaderId 用于「直属主管」继承，部门内人员无需逐人维护 leaderCode。 */
    public record Dept(String deptCode, String name, String parentCode, String leaderId, Integer sortNo) {
    }

    /** 角色：全量替换该人员的角色列表。 */
    public record Role(String userCode, List<String> roleCodes) {
    }

    public static final class Builder {
        private final List<User> users = new ArrayList<>();
        private final List<Dept> depts = new ArrayList<>();
        private final List<Role> roles = new ArrayList<>();

        public Builder addUser(User user) {
            this.users.add(user);
            return this;
        }

        public Builder addUsers(List<User> users) {
            this.users.addAll(users);
            return this;
        }

        public Builder addDept(Dept dept) {
            this.depts.add(dept);
            return this;
        }

        public Builder addDepts(List<Dept> depts) {
            this.depts.addAll(depts);
            return this;
        }

        public Builder addRole(String userCode, List<String> roleCodes) {
            this.roles.add(new Role(userCode, roleCodes));
            return this;
        }

        public OrgSyncRequest build() {
            return new OrgSyncRequest(users, depts, roles);
        }
    }
}
