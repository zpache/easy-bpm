package com.easybpm.flow;

import java.util.List;

/**
 * 单个审批节点。condition 为空表示必经；否则用 SpEL 表达式在发起时求值，false 则跳过该节点。
 */
public record FlowNode(String id, NodeType type, String name, String condition, NodeProps props) {
}
