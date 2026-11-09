package com.easybpm.flow;

import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 审批链 JSON -> BPMN 2.0 编译器。
 *
 * <p>生成规则：
 * <ul>
 *   <li>每个审批节点编译为多实例 userTask，collection = ap_&lt;nodeId&gt;，元素变量 = a_&lt;nodeId&gt;，
 *       从而由引擎原生支持或签/会签；</li>
 *   <li>节点后接排他网关，decision_&lt;nodeId&gt;=REJECT 时走 endRejected，否则进入下一节点；</li>
 *   <li>带条件的节点前接排他网关，cond_&lt;nodeId&gt; 由发起时求值，false 直接跳过；</li>
 *   <li>抄送节点编译为 serviceTask，由 ccDelegate 落抄送记录。</li>
 * </ul>
 */
@Component
public class BpmnCompiler {

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final SpelExpressionParser SPEL = new SpelExpressionParser();

    public String compile(String processKey, String processName, FlowSchema schema) {
        validate(schema);
        List<FlowNode> nodes = schema.nodes();
        List<FlowNode> middle = nodes.subList(1, nodes.size() - 1);

        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"")
                .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
                .append(" xmlns:flowable=\"http://flowable.org/bpmn\"")
                .append(" targetNamespace=\"http://easybpm.com/bpmn\">\n");
        xml.append("  <process id=\"").append(processKey).append("\" name=\"").append(escape(processName))
                .append("\" isExecutable=\"true\">\n");
        xml.append("    <startEvent id=\"start\" name=\"发起\"/>\n");

        xml.append("    <sequenceFlow id=\"sf_start\" sourceRef=\"start\" targetRef=\"")
                .append(entryOf(middle.getFirst())).append("\"/>\n");

        for (int i = 0; i < middle.size(); i++) {
            FlowNode node = middle.get(i);
            String nextEntry = i + 1 < middle.size() ? entryOf(middle.get(i + 1)) : "end";

            if (hasCondition(node)) {
                xml.append("    <exclusiveGateway id=\"gw_in_").append(node.id())
                        .append("\" default=\"sf_skip_").append(node.id()).append("\"/>\n");
                xml.append("    <sequenceFlow id=\"sf_take_").append(node.id())
                        .append("\" sourceRef=\"gw_in_").append(node.id()).append("\" targetRef=\"")
                        .append(node.id()).append("\">\n")
                        .append("      <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${cond_")
                        .append(node.id()).append("}]]></conditionExpression>\n")
                        .append("    </sequenceFlow>\n");
                xml.append("    <sequenceFlow id=\"sf_skip_").append(node.id())
                        .append("\" sourceRef=\"gw_in_").append(node.id()).append("\" targetRef=\"")
                        .append(nextEntry).append("\"/>\n");
            }

            if (node.type() == NodeType.APPROVAL) {
                appendUserTask(xml, node);
                xml.append("    <exclusiveGateway id=\"gw_out_").append(node.id())
                        .append("\" default=\"sf_reject_").append(node.id()).append("\"/>\n");
                xml.append("    <sequenceFlow id=\"sf_done_").append(node.id())
                        .append("\" sourceRef=\"").append(node.id()).append("\" targetRef=\"gw_out_")
                        .append(node.id()).append("\"/>\n");
                xml.append("    <sequenceFlow id=\"sf_pass_").append(node.id())
                        .append("\" sourceRef=\"gw_out_").append(node.id()).append("\" targetRef=\"")
                        .append(nextEntry).append("\">\n")
                        .append("      <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${decision_")
                        .append(node.id()).append(" != 'REJECT'}]]></conditionExpression>\n")
                        .append("    </sequenceFlow>\n");
                xml.append("    <sequenceFlow id=\"sf_reject_").append(node.id())
                        .append("\" sourceRef=\"gw_out_").append(node.id())
                        .append("\" targetRef=\"endRejected\"/>\n");
            } else {
                appendCcTask(xml, node);
                xml.append("    <sequenceFlow id=\"sf_cc_").append(node.id())
                        .append("\" sourceRef=\"").append(node.id()).append("\" targetRef=\"")
                        .append(nextEntry).append("\"/>\n");
            }
        }

        xml.append("    <endEvent id=\"end\" name=\"审批通过\"/>\n");
        xml.append("    <endEvent id=\"endRejected\" name=\"审批驳回\"/>\n");
        xml.append("  </process>\n");
        xml.append("</definitions>\n");
        return xml.toString();
    }

    private void appendUserTask(StringBuilder xml, FlowNode node) {
        NodeProps props = node.props();
        String elementVar = "a_" + node.id();
        String completion;
        if (props.modeOrDefault() == ApprovalMode.ALL) {
            completion = "${nrOfCompletedInstances >= nrOfInstances || decision_" + node.id() + " == 'REJECT'}";
        } else {
            completion = "${nrOfCompletedInstances >= 1}";
        }
        xml.append("    <userTask id=\"").append(node.id()).append("\" name=\"").append(escape(node.name()))
                .append("\" flowable:assignee=\"${").append(elementVar).append("}\">\n");
        xml.append("      <multiInstanceLoopCharacteristics isSequential=\"false\" flowable:collection=\"${ap_")
                .append(node.id()).append("}\" flowable:elementVariable=\"").append(elementVar).append("\">\n");
        xml.append("        <completionCondition xsi:type=\"tFormalExpression\"><![CDATA[")
                .append(completion).append("]]></completionCondition>\n");
        xml.append("      </multiInstanceLoopCharacteristics>\n");
        xml.append("    </userTask>\n");
    }

    private void appendCcTask(StringBuilder xml, FlowNode node) {
        xml.append("    <serviceTask id=\"").append(node.id()).append("\" name=\"").append(escape(node.name()))
                .append("\" flowable:delegateExpression=\"${ccDelegate}\">\n");
        xml.append("      <extensionElements>\n");
        xml.append("        <flowable:field name=\"nodeId\"><flowable:string>").append(node.id())
                .append("</flowable:string></flowable:field>\n");
        xml.append("      </extensionElements>\n");
        xml.append("    </serviceTask>\n");
    }

    public void validate(FlowSchema schema) {
        if (schema == null || schema.nodes() == null || schema.nodes().size() < 3) {
            throw invalid("审批链至少需要 发起节点 + 1 个审批节点 + 结束节点");
        }
        List<FlowNode> nodes = schema.nodes();
        if (nodes.getFirst().type() != NodeType.START || nodes.getLast().type() != NodeType.END) {
            throw invalid("审批链首节点必须是 START，尾节点必须是 END");
        }
        Set<String> ids = new HashSet<>();
        int approvals = 0;
        for (int i = 1; i < nodes.size() - 1; i++) {
            FlowNode node = nodes.get(i);
            if (node.id() == null || !ID_PATTERN.matcher(node.id()).matches()) {
                throw invalid("节点 id 只能包含字母、数字、下划线，且不能以数字开头：" + node.id());
            }
            if (!ids.add(node.id())) {
                throw invalid("节点 id 重复：" + node.id());
            }
            if (node.name() == null || node.name().isBlank()) {
                throw invalid("节点 " + node.id() + " 缺少名称");
            }
            if (node.type() == NodeType.APPROVAL) {
                approvals++;
                validateApproval(node);
            } else if (node.type() == NodeType.CC) {
                if (node.props() == null || node.props().ccUsers() == null || node.props().ccUsers().isEmpty()) {
                    throw invalid("抄送节点 " + node.name() + " 未配置抄送人");
                }
            } else {
                throw invalid("中间节点只支持 APPROVAL 或 CC：" + node.id());
            }
            if (hasCondition(node)) {
                validateExpression(node);
            }
        }
        if (approvals == 0) {
            throw invalid("审批链至少需要 1 个审批节点");
        }
    }

    private void validateApproval(FlowNode node) {
        NodeProps props = node.props();
        if (props == null || props.approverType() == null) {
            throw invalid("审批节点 " + node.name() + " 未配置审批人类型");
        }
        switch (props.approverType()) {
            case SPECIFIED -> {
                if (props.approvers() == null || props.approvers().isEmpty()) {
                    throw invalid("审批节点 " + node.name() + " 未指定审批人");
                }
            }
            case ROLE -> {
                if (props.roleCodes() == null || props.roleCodes().isEmpty()) {
                    throw invalid("审批节点 " + node.name() + " 未指定角色");
                }
            }
            case FORM_FIELD -> {
                if (props.approvers() == null || props.approvers().isEmpty()) {
                    throw invalid("审批节点 " + node.name() + " 未指定人员表单字段");
                }
            }
            default -> {
                // INITIATOR_LEADER / DEPT_LEADER / SELF 无需额外配置
            }
        }
    }

    private void validateExpression(FlowNode node) {
        String expression = node.condition().trim();
        String lowered = expression.toLowerCase();
        List<String> forbidden = new ArrayList<>(List.of("t(", "new ", "#", "@", "getclass", "class.forname"));
        for (String token : forbidden) {
            if (lowered.contains(token)) {
                throw invalid("节点 " + node.name() + " 的条件表达式包含不允许的语法：" + token);
            }
        }
        try {
            SPEL.parseExpression(expression);
        } catch (Exception e) {
            throw invalid("节点 " + node.name() + " 的条件表达式不合法：" + e.getMessage());
        }
    }

    public String processKeyOf(String flowCode) {
        return "f_" + flowCode.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String entryOf(FlowNode node) {
        return hasCondition(node) ? "gw_in_" + node.id() : node.id();
    }

    private boolean hasCondition(FlowNode node) {
        return node.condition() != null && !node.condition().isBlank();
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private BizException invalid(String message) {
        return new BizException(ErrorCode.FLOW_SCHEMA_INVALID, message);
    }
}
