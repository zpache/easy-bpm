package com.easybpm.sdk.model;

import java.util.List;

/** 组织同步结果：本次 upsert 的人员/部门/角色条数。 */
public record OrgSyncResult(int users, int depts, int roles) {
}
