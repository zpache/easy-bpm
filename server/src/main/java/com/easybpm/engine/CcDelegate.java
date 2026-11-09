package com.easybpm.engine;

import com.easybpm.common.Ids;
import com.easybpm.org.OrgService;
import com.easybpm.tenant.TenantContext;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 抄送节点处理器。BPMN 里的 serviceTask 通过 delegateExpression="${ccDelegate}" 调用。
 */
@Component("ccDelegate")
public class CcDelegate implements JavaDelegate {

    private final BpmCcMapper ccMapper;
    private final BpmTaskRecordMapper recordMapper;
    private final OrgService orgService;

    public CcDelegate(BpmCcMapper ccMapper, BpmTaskRecordMapper recordMapper, OrgService orgService) {
        this.ccMapper = ccMapper;
        this.recordMapper = recordMapper;
        this.orgService = orgService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String tenantId = execution.getTenantId();
        String instanceId = execution.getProcessInstanceId();
        String nodeId = execution.getCurrentActivityId();
        String nodeName = execution.getCurrentFlowElement() == null ? nodeId : execution.getCurrentFlowElement().getName();
        Object raw = execution.getVariable("cc_" + nodeId);
        if (!(raw instanceof Collection<?> collection) || collection.isEmpty()) {
            return;
        }
        List<String> userIds = collection.stream().map(String::valueOf).distinct().toList();
        Map<String, String> names = orgService.namesOf(userIds);

        TenantContext.callAs(tenantId, () -> {
            LocalDateTime now = LocalDateTime.now();
            for (String userId : userIds) {
                BpmCc cc = new BpmCc();
                cc.setId(Ids.id());
                cc.setInstanceId(instanceId);
                cc.setNodeId(nodeId);
                cc.setNodeName(nodeName);
                cc.setUserId(userId);
                cc.setUserName(names.getOrDefault(userId, userId));
                cc.setReadFlag(0);
                cc.setCreatedAt(now);
                ccMapper.insert(cc);
            }
            BpmTaskRecord record = new BpmTaskRecord();
            record.setId(Ids.id());
            record.setInstanceId(instanceId);
            record.setNodeId(nodeId);
            record.setNodeName(nodeName);
            record.setOperatorId("system");
            record.setOperatorName("系统");
            record.setAction(TaskAction.CC.name());
            record.setComment("抄送 " + userIds.size() + " 人");
            record.setCreatedAt(now);
            recordMapper.insert(record);
            return null;
        });
    }
}
