package com.easybpm.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 审批链定义：首节点 START、尾节点 END，中间为 APPROVAL / CC / CONDITION（排他网关），
 * 按顺序执行；网关分支内的子节点链在运行时按 FIRST-MATCH 选路后汇聚回主链。
 */
public record FlowSchema(List<FlowNode> nodes) {

    public FlowNode first() {
        return nodes.getFirst();
    }

    public FlowNode last() {
        return nodes.getLast();
    }

    /** 主链 + 各网关分支的子节点（网关自身也包含），深度优先扁平化。 */
    public List<FlowNode> flatNodes() {
        List<FlowNode> result = new ArrayList<>();
        collect(nodes, result);
        return result;
    }

    public Optional<FlowNode> findNode(String nodeId) {
        return flatNodes().stream().filter(n -> nodeId.equals(n.id())).findFirst();
    }

    private void collect(List<FlowNode> list, List<FlowNode> result) {
        if (list == null) {
            return;
        }
        for (FlowNode node : list) {
            result.add(node);
            if (node.branches() != null) {
                for (Branch branch : node.branches()) {
                    collect(branch.nodes(), result);
                }
            }
        }
    }
}
