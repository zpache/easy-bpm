package com.easybpm.flow;

import java.util.List;

/**
 * 单个审批节点。condition 为空表示必经；否则用 SpEL 表达式在发起时求值，false 则跳过该节点。
 *
 * <p>type = CONDITION 时为排他网关，{@code branches} 按顺序 FIRST-MATCH 选路，
 * 条件为空的分支即默认分支；分支内不允许再嵌套网关。
 */
public record FlowNode(String id, NodeType type, String name, String condition, NodeProps props,
                       List<Branch> branches) {

    public boolean hasCondition() {
        return condition != null && !condition.isBlank();
    }

    public boolean isGateway() {
        return type == NodeType.CONDITION;
    }
}
