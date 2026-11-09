package com.easybpm.console;

import com.easybpm.auth.CurrentUser;
import com.easybpm.common.ApiResponse;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.JsonCodec;
import com.easybpm.flow.FlowDefinition;
import com.easybpm.flow.FlowDefinitionService;
import com.easybpm.flow.FlowVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 控制台的流程定义管理。写入操作要求租户管理员。
 */
@RestController
@RequestMapping("/api/flows")
public class ConsoleFlowController {

    private final FlowDefinitionService definitions;
    private final JsonCodec json;

    public ConsoleFlowController(FlowDefinitionService definitions, JsonCodec json) {
        this.definitions = definitions;
        this.json = json;
    }

    @GetMapping
    public ApiResponse<List<FlowView>> list() {
        return ApiResponse.ok(definitions.list().stream().map(this::toView).toList());
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
        return ApiResponse.ok(toView(definition));
    }

    @PostMapping("/{code}/publish")
    public ApiResponse<FlowView> publish(@PathVariable String code,
                                         @RequestBody(required = false) PublishRequest request) {
        requireAdmin();
        String changelog = request == null ? null : request.changelog();
        return ApiResponse.ok(toView(definitions.publish(code, CurrentUser.requireApproverId(), changelog)));
    }

    @PostMapping("/{code}/disable")
    public ApiResponse<Void> disable(@PathVariable String code) {
        requireAdmin();
        definitions.disable(code, CurrentUser.requireApproverId());
        return ApiResponse.ok();
    }

    @GetMapping("/{code}/versions")
    public ApiResponse<List<VersionView>> versions(@PathVariable String code) {
        return ApiResponse.ok(definitions.versions(code).stream().map(this::toVersionView).toList());
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
