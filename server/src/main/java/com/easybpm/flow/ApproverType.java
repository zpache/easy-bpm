package com.easybpm.flow;

/**
 * 审批人来源。接入方只需维护组织数据，具体人选由引擎在发起时解析。
 */
public enum ApproverType {
    /** 指定人员：props.approvers */
    SPECIFIED,
    /** 指定角色：props.roleCodes */
    ROLE,
    /** 发起人的第 N 级主管：props.leaderLevel，默认 1 */
    INITIATOR_LEADER,
    /** 发起人所在部门主管 */
    DEPT_LEADER,
    /** 表单字段中的人员编码：props.approvers[0] 为字段 key */
    FORM_FIELD,
    /** 发起人本人 */
    SELF
}
