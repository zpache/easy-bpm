package com.easybpm.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    /**
     * 对外实例 ID 的流程变量名。重新提交（TO_START 驳回后重提）会在启动前写入，
     * 因为此时投影行的 engineInstanceId 还指向旧引擎实例，无法按引擎 ID 反查。
     */
    public static final String INSTANCE_ID_VAR = "__instance_id";

    private final BpmCcMapper ccMapper;
    private final BpmTaskRecordMapper recordMapper;
    private final BpmInstanceMapper instanceMapper;
    private final OrgService orgService;

    public CcDelegate(BpmCcMapper ccMapper, BpmTaskRecordMapper recordMapper,
                      BpmInstanceMapper instanceMapper, OrgService orgService) {
        this.ccMapper = ccMapper;
        this.recordMapper = recordMapper;
        this.instanceMapper = instanceMapper;
        this.orgService = orgService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String tenantId = execution.getTenantId();
        String nodeId = execution.getCurrentActivityId();
        String nodeName = execution.getCurrentFlowElement() == null ? nodeId : execution.getCurrentFlowElement().getName();
        Object raw = execution.getVariable("cc_" + nodeId);
        if (!(raw instanceof Collection<?> collection) || collection.isEmpty()) {
            return;
        }
        List<String> userIds = collection.stream().map(String::valueOf).distinct().toList();
        Map<String, String> names = orgService.namesOf(userIds);

        TenantContext.callAs(tenantId, () -> {
            String externalId = resolveInstanceId(execution);
            LocalDateTime now = LocalDateTime.now();
            for (String userId : userIds) {
                BpmCc cc = new BpmCc();
                cc.setId(Ids.id());
                cc.setInstanceId(externalId);
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
            record.setInstanceId(externalId);
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

    /**
     * 解析抄送记录应归属的对外实例 ID。
     *
     * <p>三级取值：① 流程变量（重提场景显式写入）→ ② 按引擎实例 ID 反查投影 →
     * ③ 引擎实例 ID 本身。③ 覆盖「发起时抄送节点先于实例投影落库执行」的场景，
     * 首次发起对外 ID 与引擎 ID 相同，取值正确。
     */
    private String resolveInstanceId(DelegateExecution execution) {
        Object fromVariable = execution.getVariable(INSTANCE_ID_VAR);
        if (fromVariable != null && !String.valueOf(fromVariable).isBlank()) {
            return String.valueOf(fromVariable);
        }
        BpmInstance projection = instanceMapper.selectOne(new LambdaQueryWrapper<BpmInstance>()
                .eq(BpmInstance::getEngineInstanceId, execution.getProcessInstanceId()));
        return projection != null ? projection.getInstanceId() : execution.getProcessInstanceId();
    }
}
