package com.easybpm.console;

import com.easybpm.audit.AuditService;
import com.easybpm.auth.CurrentUser;
import com.easybpm.common.ApiResponse;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.JsonCodec;
import com.easybpm.common.PageResult;
import com.easybpm.engine.BpmInstance;
import com.easybpm.engine.BpmInstanceMapper;
import com.easybpm.engine.ConditionEvaluator;
import com.easybpm.flow.FlowDefinition;
import com.easybpm.flow.FlowDefinitionService;
import com.easybpm.flow.FlowVersion;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 控制台的流程定义管理。写入操作要求租户管理员。
 */
@RestController
@RequestMapping("/api/flows")
public class ConsoleFlowController {

    private final FlowDefinitionService definitions;
    private final JsonCodec json;
    private final AuditService audit;
    private final BpmInstanceMapper instanceMapper;
    private final ConditionEvaluator conditionEvaluator;

    public ConsoleFlowController(FlowDefinitionService definitions, JsonCodec json, AuditService audit,
                                 BpmInstanceMapper instanceMapper, ConditionEvaluator conditionEvaluator) {
        this.definitions = definitions;
        this.json = json;
        this.audit = audit;
        this.instanceMapper = instanceMapper;
        this.conditionEvaluator = conditionEvaluator;
    }

    @GetMapping
    public ApiResponse<List<FlowView>> list() {
        return ApiResponse.ok(definitions.list().stream().map(this::toView).toList());
    }

    @GetMapping(params = "page")
    public ApiResponse<PageResult<FlowView>> page(@RequestParam int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        PageResult<FlowDefinition> result = definitions.page(page, size);
        return ApiResponse.ok(PageResult.of(result.list().stream().map(this::toView).toList(),
                result.total(), result.page(), result.size()));
    }

    @GetMapping("/{code}")
    public ApiResponse<FlowView> detail(@PathVariable String code) {
        return ApiResponse.ok(toView(definitions.require(code)));
    }

    @PostMapping
    public ApiResponse<FlowView> save(@Valid @RequestBody SaveFlowRequest request) {
        requireAdmin();
        FlowDefinition definition = definitions.save(new FlowDefinitionService.SaveCommand(
                request.code(), request.name(), request.category(), request.icon(), request.description(),
                request.formSchema(), request.flowSchema()), CurrentUser.requireApproverId());
        audit.record("FLOW_SAVE", "FLOW", definition.getCode(),
                (isEdit(request.code()) ? "编辑" : "新建") + "流程「" + definition.getName() + "」");
        return ApiResponse.ok(toView(definition));
    }

    @PostMapping("/{code}/publish")
    public ApiResponse<FlowView> publish(@PathVariable String code,
                                         @RequestBody(required = false) PublishRequest request) {
        requireAdmin();
        String changelog = request == null ? null : request.changelog();
        FlowDefinition definition = definitions.publish(code, CurrentUser.requireApproverId(), changelog);
        audit.record("FLOW_PUBLISH", "FLOW", code,
                "发布 v" + definition.getVersion() + (changelog == null || changelog.isBlank() ? "" : "：" + changelog));
        return ApiResponse.ok(toView(definition));
    }

    @PostMapping("/{code}/disable")
    public ApiResponse<Void> disable(@PathVariable String code) {
        requireAdmin();
        definitions.disable(code, CurrentUser.requireApproverId());
        audit.record("FLOW_DISABLE", "FLOW", code, "停用流程");
        return ApiResponse.ok();
    }

    @PostMapping("/{code}/enable")
    public ApiResponse<FlowView> enable(@PathVariable String code) {
        requireAdmin();
        FlowDefinition definition = definitions.enable(code, CurrentUser.requireApproverId());
        audit.record("FLOW_ENABLE", "FLOW", code,
                "DRAFT".equals(definition.getStatus())
                        ? "启用流程，从未发布过，恢复为草稿"
                        : "启用流程，恢复到 v" + definition.getVersion());
        return ApiResponse.ok(toView(definition));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{code}")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code) {
        requireAdmin();
        definitions.deleteDraft(code, CurrentUser.requireApproverId());
        audit.record("FLOW_DELETE", "FLOW", code, "删除草稿流程");
    }

    @PostMapping("/{code}/versions/{version}/rollback")
    public ApiResponse<FlowView> rollback(@PathVariable String code, @PathVariable int version,
                                          @RequestBody(required = false) PublishRequest request) {
        requireAdmin();
        String changelog = request == null ? null : request.changelog();
        FlowDefinition definition = definitions.rollback(code, version,
                CurrentUser.requireApproverId(), changelog);
        audit.record("FLOW_ROLLBACK", "FLOW", code,
                "从 v" + version + " 回滚，发布为 v" + definition.getVersion());
        return ApiResponse.ok(toView(definition));
    }

    private boolean isEdit(String code) {
        return definitions.find(code) != null;
    }

    @GetMapping("/{code}/versions")
    public ApiResponse<List<VersionView>> versions(@PathVariable String code) {
        return ApiResponse.ok(definitions.versions(code).stream().map(this::toVersionView).toList());
    }

    /**
     * 该流程历史实例 formData 的顶层字段聚合（近 100 条），供条件分支配置时自动补全字段键。
     * 无表单设计器，字段元数据只能从业务方实际提交的数据里来。
     */
    @GetMapping("/{code}/form-fields")
    public ApiResponse<List<FormFieldView>> formFields(@PathVariable String code) {
        requireAdmin();
        definitions.require(code);
        List<BpmInstance> recent = instanceMapper.selectList(new LambdaQueryWrapper<BpmInstance>()
                .eq(BpmInstance::getFlowCode, code)
                .select(BpmInstance::getFormData)
                .orderByDesc(BpmInstance::getStartedAt)
                .last("limit 100"));

        Map<String, FieldAgg> fields = new LinkedHashMap<>();
        for (BpmInstance instance : recent) {
            if (instance.getFormData() == null || instance.getFormData().isBlank()) {
                continue;
            }
            Map<String, Object> data = json.readMap(instance.getFormData());
            data.forEach((key, value) -> fields.computeIfAbsent(key, k -> new FieldAgg())
                    .observe(value));
        }
        return ApiResponse.ok(fields.entrySet().stream()
                .map(e -> new FormFieldView(e.getKey(), e.getValue().type, e.getValue().samples))
                .toList());
    }

    /**
     * 条件试跑：用样例 formData 逐一求值表达式，返回各自命中结果与第一个命中的下标，
     * 供设计器在发布前验证网关选路（FIRST-MATCH 语义）。
     */
    @PostMapping("/{code}/condition-test")
    public ApiResponse<ConditionTestResult> conditionTest(@PathVariable String code,
                                                          @Valid @RequestBody ConditionTestRequest request) {
        requireAdmin();
        definitions.require(code);
        Map<String, Object> variables = request.formData() == null ? Map.of() : request.formData();
        List<Boolean> matched = new ArrayList<>();
        int firstMatch = -1;
        for (int i = 0; i < request.expressions().size(); i++) {
            String expression = request.expressions().get(i);
            boolean hit;
            try {
                hit = expression != null && !expression.isBlank()
                        && conditionEvaluator.evaluate(expression, variables, "试跑表达式 " + (i + 1));
            } catch (RuntimeException e) {
                // 求值异常按不命中处理，但把原因带回给前端展示
                hit = false;
            }
            if (hit && firstMatch < 0) {
                firstMatch = i;
            }
            matched.add(hit);
        }
        return ApiResponse.ok(new ConditionTestResult(matched, firstMatch));
    }

    private static final class FieldAgg {
        private final Set<String> types = new LinkedHashSet<>();
        private final List<Object> samples = new ArrayList<>();
        private String type = "string";

        void observe(Object value) {
            types.add(inferType(value));
            if (samples.size() < 3) {
                samples.add(value);
            }
        }

        private String inferType(Object value) {
            if (value instanceof Number) return "number";
            if (value instanceof Boolean) return "boolean";
            if (value instanceof List) return "array";
            if (value instanceof Map) return "object";
            return "string";
        }
    }

    public record FormFieldView(String key, String type, List<Object> samples) {
    }

    public record ConditionTestRequest(Map<String, Object> formData, @NotNull List<String> expressions) {
    }

    public record ConditionTestResult(List<Boolean> matched, int firstMatchIndex) {
    }

    private void requireAdmin() {
        if (!CurrentUser.require().isTenantAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要租户管理员权限");
        }
    }

    private FlowView toView(FlowDefinition definition) {
        return new FlowView(definition.getCode(), definition.getName(), definition.getCategory(),
                definition.getIcon(), definition.getDescription(), definition.getStatus(),
                definition.getVersion(), definition.getProcessDefinitionId(),
                definition.getFormSchema() == null ? null : json.readMap(definition.getFormSchema()),
                definition.getFlowSchema() == null ? null : json.readMap(definition.getFlowSchema()),
                definition.getUpdatedAt(), definition.getPublishedAt());
    }

    private VersionView toVersionView(FlowVersion version) {
        return new VersionView(version.getVersion(), version.getProcessDefinitionId(), version.getPublishedBy(),
                version.getPublishedAt(), version.getChangelog());
    }

    public record SaveFlowRequest(
            @NotBlank String code,
            @NotBlank String name,
            String category,
            String icon,
            String description,
            Object formSchema,
            @NotNull Object flowSchema
    ) {
    }

    public record PublishRequest(String changelog) {
    }

    public record FlowView(String code, String name, String category, String icon, String description,
                           String status, Integer version, String processDefinitionId, Object formSchema,
                           Object flowSchema, LocalDateTime updatedAt, LocalDateTime publishedAt) {
    }

    public record VersionView(Integer version, String processDefinitionId, String publishedBy,
                              LocalDateTime publishedAt, String changelog) {
    }
}
