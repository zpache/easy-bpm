package com.easybpm.flow;

import java.util.List;

/**
 * 审批链节点属性。字段按节点类型使用，未使用的留空。
 */
public record NodeProps(
        ApproverType approverType,
        List<String> approvers,
        List<String> roleCodes,
        Integer leaderLevel,
        ApprovalMode mode,
        RejectStrategy rejectStrategy,
        List<String> ccUsers
) {

    public ApprovalMode modeOrDefault() {
        return mode == null ? ApprovalMode.ANY : mode;
    }

    public RejectStrategy rejectOrDefault() {
        return rejectStrategy == null ? RejectStrategy.TO_END : rejectStrategy;
    }

    public int leaderLevelOrDefault() {
        return leaderLevel == null || leaderLevel < 1 ? 1 : leaderLevel;
    }
}
