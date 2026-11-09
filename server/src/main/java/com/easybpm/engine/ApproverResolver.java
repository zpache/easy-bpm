package com.easybpm.engine;

import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.flow.FlowNode;
import com.easybpm.flow.NodeProps;
import com.easybpm.org.OrgDept;
import com.easybpm.org.OrgService;
import com.easybpm.org.OrgUser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审批人解析。发起时一次性算清所有节点的人选，写入流程变量，
 * 这样 BPMN 只负责流转，不需要在运行时回查组织数据。
 */
@Component
public class ApproverResolver {

    private final OrgService orgService;

    public ApproverResolver(OrgService orgService) {
        this.orgService = orgService;
    }

    public List<String> resolve(FlowNode node, StartCommand command, Map<String, Object> variables) {
        NodeProps props = node.props();
        List<String> raw = switch (props.approverType()) {
            case SPECIFIED -> props.approvers();
            case ROLE -> props.roleCodes().stream()
                    .flatMap(role -> orgService.findUsersByRole(role).stream())
                    .toList();
            case INITIATOR_LEADER -> leaderOf(command.initiatorId(), props.leaderLevelOrDefault());
            case DEPT_LEADER -> deptLeader(command);
            case FORM_FIELD -> formFieldUsers(props, variables);
            case SELF -> List.of(command.initiatorId());
        };
        return normalize(raw);
    }

    private List<String> leaderOf(String userCode, int level) {
        String leader = orgService.findLeader(userCode, level);
        return leader == null ? List.of() : List.of(leader);
    }

    private List<String> deptLeader(StartCommand command) {
        String deptCode = command.initiatorDeptCode();
        if (deptCode == null || deptCode.isBlank()) {
            OrgUser user = orgService.findUser(command.initiatorId());
            deptCode = user == null ? null : user.getDeptCode();
        }
        OrgDept dept = orgService.findDept(deptCode);
        return dept == null || dept.getLeaderId() == null ? List.of() : List.of(dept.getLeaderId());
    }

    @SuppressWarnings("unchecked")
    private List<String> formFieldUsers(NodeProps props, Map<String, Object> variables) {
        String field = props.approvers().getFirst();
        Object value = variables.get(field);
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        if (value instanceof String text) {
            if (text.isBlank()) {
                return List.of();
            }
            return text.contains(",") ? List.of(text.split(",")) : List.of(text);
        }
        return List.of(String.valueOf(value));
    }

    private List<String> normalize(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return new ArrayList<>(result);
    }

    public void requireNotEmpty(List<String> approvers, FlowNode node) {
        if (approvers.isEmpty()) {
            throw new BizException(ErrorCode.NO_APPROVER,
                    "节点「" + node.name() + "」未匹配到审批人，请检查组织架构或审批人配置");
        }
    }
}
