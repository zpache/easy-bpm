package com.easybpm.engine;

import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.flow.FlowDefinitionService;
import com.easybpm.org.OrgService;
import com.easybpm.org.OrgUser;
import com.easybpm.tenant.Tenant;
import com.easybpm.tenant.TenantContext;
import com.easybpm.tenant.TenantMapper;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审批核心链路集成测试（H2 内存库 + 真实 Flowable 引擎）。
 *
 * <p>覆盖：发起→同意、驳回 TO_END、驳回 TO_START→重提、撤回、幂等键、租户隔离、
 * 抄送闭环、审批人为空兜底。每个用例使用独立租户，避免数据互相污染。
 */
@SpringBootTest
@ActiveProfiles("test")
class FlowEngineIntegrationTest {

    @Autowired
    private FlowEngineService engine;
    @Autowired
    private FlowDefinitionService definitions;
    @Autowired
    private WorkbenchQueryService query;
    @Autowired
    private OrgService orgService;
    @Autowired
    private TenantMapper tenantMapper;
    @Autowired
    private TaskService taskService;

    private String tenantId;

    @BeforeEach
    void setUp() {
        // 每个用例独立租户，天然隔离引擎实例与业务数据
        tenantId = "t" + System.nanoTime();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setCode(tenantId);
        tenant.setName("测试租户");
        tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.insert(tenant);
        TenantContext.set(tenantId);

        user("u1", "张三");
        user("u2", "李四");
        user("u3", "王五");
        user("u4", "抄送人");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("发起→待办→同意：实例结束为 APPROVED，待办与已办数量正确")
    void startAndApprove() {
        publishFlow("leave_apply", schema(approval("n1", "部门审批", List.of("u2"), "ANY", "TO_END")));

        StartResult started = engine.start(command("leave_apply", "biz-001", Map.of("days", 2), null));
        assertEquals("RUNNING", started.status());
        assertEquals(1, started.pendingTasks().size());
        TaskView task = started.pendingTasks().getFirst();
        assertEquals("u2", task.assigneeId());
        assertEquals("李四", task.assigneeName());
        assertEquals(1, query.todo("u2", 1, 10, null, null).total());

        ActionResult result = engine.approve(task.taskId(), "u2", "同意", null);
        assertEquals("APPROVED", result.status());
        assertNotNull(result.endedAt());
        assertEquals(0, query.todo("u2", 1, 10, null, null).total());
        assertEquals(1, query.done("u2", 1, 10, null, null).total());
        assertEquals(1, query.submitted("u1", 1, 10, null, null, null).total());
    }

    @Test
    @DisplayName("驳回 TO_END：实例直接结束为 REJECTED，不再流转")
    void rejectToEnd() {
        publishFlow("expense_apply", schema(
                approval("n1", "主管审批", List.of("u2"), "ANY", "TO_END"),
                approval("n2", "财务审批", List.of("u3"), "ANY", "TO_END")));

        StartResult started = engine.start(command("expense_apply", "biz-002", Map.of(), null));
        ActionResult result = engine.reject(started.pendingTasks().getFirst().taskId(), "u2", "金额不符");

        assertEquals("REJECTED", result.status());
        assertEquals(0, query.todo("u3", 1, 10, null, null).total());
    }

    @Test
    @DisplayName("驳回 TO_START→重提：对外实例 ID 恒定，引擎实例 ID 更换，重新回到首个节点")
    void rejectToStartThenResubmit() {
        publishFlow("purchase_apply", schema(
                approval("n1", "主管审批", List.of("u2"), "ANY", "TO_START")));

        StartResult started = engine.start(command("purchase_apply", "biz-003", Map.of("amount", 100), null));
        String externalId = started.instanceId();

        ActionResult rejected = engine.reject(started.pendingTasks().getFirst().taskId(), "u2", "资料不全");
        assertEquals(FlowEngineService.PENDING_RESUBMIT_NODE, rejected.currentNodeName());

        StartResult resubmitted = engine.resubmit(externalId, "u1", Map.of("amount", 120), "已补充资料");
        assertEquals(externalId, resubmitted.instanceId(), "对外实例 ID 必须恒定");
        assertNotEquals(started.pendingTasks().getFirst().taskId(),
                resubmitted.pendingTasks().getFirst().taskId());
        assertEquals(1, query.todo("u2", 1, 10, null, null).total());
    }

    @Test
    @DisplayName("撤回：发起人撤回后实例为 CANCELED，待办清空")
    void withdraw() {
        publishFlow("overtime_apply", schema(approval("n1", "主管审批", List.of("u2"), "ANY", "TO_END")));

        StartResult started = engine.start(command("overtime_apply", "biz-004", Map.of(), null));
        ActionResult result = engine.withdraw(started.instanceId(), "u1", "填错了");

        assertEquals("CANCELED", result.status());
        assertEquals(0, query.todo("u2", 1, 10, null, null).total());
    }

    @Test
    @DisplayName("幂等键：同一键重复发起返回同一实例，不产生第二条实例")
    void idempotentStart() {
        publishFlow("leave_idem", schema(approval("n1", "主管审批", List.of("u2"), "ANY", "TO_END")));

        StartResult first = engine.start(command("leave_idem", "biz-005", Map.of(), "idem-key-001"));
        StartResult second = engine.start(command("leave_idem", "biz-005", Map.of(), "idem-key-001"));

        assertEquals(first.instanceId(), second.instanceId());
        assertEquals(1, query.submitted("u1", 1, 10, null, null, null).total());
    }

    @Test
    @DisplayName("租户隔离：跨租户查询实例直接 404，且看不到对方待办")
    void tenantIsolation() {
        publishFlow("leave_iso", schema(approval("n1", "主管审批", List.of("u2"), "ANY", "TO_END")));
        StartResult started = engine.start(command("leave_iso", "biz-006", Map.of(), null));

        // 切换到另一个租户
        String otherTenant = "t" + System.nanoTime();
        Tenant tenant = new Tenant();
        tenant.setId(otherTenant);
        tenant.setCode(otherTenant);
        tenant.setName("另一个租户");
        tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.insert(tenant);
        TenantContext.set(otherTenant);

        BizException ex = assertThrows(BizException.class, () -> query.detail(started.instanceId()));
        assertEquals(ErrorCode.INSTANCE_NOT_FOUND, ex.errorCode());
        assertEquals(0, query.todo("u2", 1, 10, null, null).total());
    }

    @Test
    @DisplayName("抄送闭环：抄送节点落库→抄送我的可查→标记已读后未读数归零")
    void ccClosedLoop() {
        Map<String, Object> ccNode = node("c1", "CC", "知会财务", Map.of("ccUsers", List.of("u4")));
        publishFlow("cc_apply", schema(approval("n1", "主管审批", List.of("u2"), "ANY", "TO_END"), ccNode));

        StartResult started = engine.start(command("cc_apply", "biz-007", Map.of(), null));
        engine.approve(started.pendingTasks().getFirst().taskId(), "u2", "同意", null);

        var ccPage = query.cc("u4", 1, 10, false, null);
        assertEquals(1, ccPage.total());
        CcView cc = ccPage.list().getFirst();
        assertTrue(!cc.read());
        assertEquals("知会财务", cc.nodeName());
        assertEquals("张三", cc.initiatorName());
        assertEquals(1, query.counts("u4").ccUnread());

        query.markCcRead(cc.id(), "u4");
        assertEquals(0, query.counts("u4").ccUnread());
        assertEquals(0, query.cc("u4", 1, 10, true, null).total());
        assertEquals(1, query.cc("u4", 1, 10, false, null).total());
    }

    @Test
    @DisplayName("补抄送记录越权：不能标记他人的抄送")
    void markCcReadForbidden() {
        Map<String, Object> ccNode = node("c1", "CC", "知会财务", Map.of("ccUsers", List.of("u4")));
        publishFlow("cc_forbidden", schema(approval("n1", "主管审批", List.of("u2"), "ANY", "TO_END"), ccNode));

        StartResult started = engine.start(command("cc_forbidden", "biz-008", Map.of(), null));
        engine.approve(started.pendingTasks().getFirst().taskId(), "u2", "同意", null);

        CcView cc = query.cc("u4", 1, 10, false, null).list().getFirst();
        BizException ex = assertThrows(BizException.class, () -> query.markCcRead(cc.id(), "u3"));
        assertEquals(ErrorCode.FORBIDDEN, ex.errorCode());
    }

    @Test
    @DisplayName("审批人为空兜底 AUTO_PASS：被停用/未同步人员不指派，节点自动通过并流转到下一节点")
    void emptyApproverAutoPass() {
        Map<String, Object> autoPass = new LinkedHashMap<>();
        autoPass.put("approverType", "SPECIFIED");
        autoPass.put("approvers", List.of("ghost_user"));
        autoPass.put("mode", "ANY");
        autoPass.put("rejectStrategy", "TO_END");
        autoPass.put("emptyStrategy", "AUTO_PASS");
        publishFlow("auto_pass_apply", schema(
                node("n1", "APPROVAL", "无人节点", autoPass),
                approval("n2", "人工审批", List.of("u2"), "ANY", "TO_END")));

        StartResult started = engine.start(command("auto_pass_apply", "biz-009", Map.of(), null));

        assertEquals("RUNNING", started.status());
        assertEquals(1, started.pendingTasks().size());
        assertEquals("u2", started.pendingTasks().getFirst().assigneeId());
        assertEquals(0, taskService.createTaskQuery().taskTenantId(tenantId).taskAssignee("ghost_user").count());
        boolean autoPassed = query.detail(started.instanceId()).timeline().stream()
                .anyMatch(t -> t.comment() != null && t.comment().contains("兜底策略自动通过"));
        assertTrue(autoPassed, "时间线应记录系统自动通过");
    }

    // ---------- 测试数据构造 ----------

    private void user(String code, String name) {
        OrgUser user = new OrgUser();
        user.setUserCode(code);
        user.setName(name);
        user.setStatus("ACTIVE");
        orgService.upsertUser(user);
    }

    private StartCommand command(String flowCode, String businessKey, Map<String, Object> formData,
                                 String idempotencyKey) {
        return new StartCommand(flowCode, businessKey, "测试单据 " + businessKey,
                "u1", "张三", null, formData, List.of(), null, idempotencyKey);
    }

    private Map<String, Object> schema(Map<String, Object>... middle) {
        List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        nodes.add(node("start", "START", "发起", null));
        nodes.addAll(List.of(middle));
        nodes.add(node("end", "END", "结束", null));
        return Map.of("nodes", nodes);
    }

    private Map<String, Object> approval(String id, String name, List<String> approvers,
                                         String mode, String rejectStrategy) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("approverType", "SPECIFIED");
        props.put("approvers", approvers);
        props.put("mode", mode);
        props.put("rejectStrategy", rejectStrategy);
        return node(id, "APPROVAL", name, props);
    }

    private Map<String, Object> node(String id, String type, String name, Map<String, Object> props) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("name", name);
        if (props != null) {
            node.put("props", props);
        }
        return node;
    }

    private void publishFlow(String code, Map<String, Object> flowSchema) {
        definitions.save(new FlowDefinitionService.SaveCommand(code, "测试流程-" + code,
                null, null, null, null, flowSchema), "tester");
        definitions.publish(code, "tester", null);
    }
}
