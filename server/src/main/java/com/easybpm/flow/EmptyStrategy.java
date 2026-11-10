package com.easybpm.flow;

/**
 * 审批人兜底策略：审批节点在发起时解析不到任何有效审批人（含被停用过滤后为空）时的处理方式。
 */
public enum EmptyStrategy {

    /** 拦截发起（默认）：直接报错 NO_APPROVER，不创建实例。 */
    FAIL,

    /** 自动通过：节点视为已同意，时间线记录「系统自动通过」。 */
    AUTO_PASS,

    /** 自动驳回：节点视为已驳回，实例结束为 REJECTED，时间线记录「系统自动驳回」。 */
    AUTO_REJECT
}
