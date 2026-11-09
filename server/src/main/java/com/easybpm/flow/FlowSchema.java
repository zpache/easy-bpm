package com.easybpm.flow;

import java.util.List;

/**
 * 审批链定义：首节点 START、尾节点 END，中间为 APPROVAL / CC，按顺序执行。
 */
public record FlowSchema(List<FlowNode> nodes) {

    public FlowNode first() {
        return nodes.getFirst();
    }

    public FlowNode last() {
        return nodes.getLast();
    }
}
