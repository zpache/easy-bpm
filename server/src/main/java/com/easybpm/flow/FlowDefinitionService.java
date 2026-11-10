package com.easybpm.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.common.Ids;
import com.easybpm.common.JsonCodec;
import com.easybpm.tenant.TenantContext;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 流程定义管理：保存 JSON 审批链，发布时编译为 BPMN 并按租户部署到 Flowable。
 */
@Service
public class FlowDefinitionService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");

    private final FlowDefinitionMapper definitionMapper;
    private final FlowVersionMapper versionMapper;
    private final BpmnCompiler compiler;
    private final JsonCodec json;
    private final RepositoryService repositoryService;

    public FlowDefinitionService(FlowDefinitionMapper definitionMapper, FlowVersionMapper versionMapper,
                                 BpmnCompiler compiler, JsonCodec json, RepositoryService repositoryService) {
        this.definitionMapper = definitionMapper;
        this.versionMapper = versionMapper;
        this.compiler = compiler;
        this.json = json;
        this.repositoryService = repositoryService;
    }

    public List<FlowDefinition> list() {
        return definitionMapper.selectList(new LambdaQueryWrapper<FlowDefinition>()
                .orderByDesc(FlowDefinition::getUpdatedAt));
    }

    public List<FlowDefinition> listPublished() {
        return definitionMapper.selectList(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getStatus, "PUBLISHED")
                .orderByDesc(FlowDefinition::getUpdatedAt));
    }

    public FlowDefinition require(String code) {
        FlowDefinition definition = find(code);
        if (definition == null) {
            throw new BizException(ErrorCode.FLOW_NOT_FOUND, "流程定义不存在：" + code);
        }
        return definition;
    }

    public FlowDefinition requirePublished(String code) {
        FlowDefinition definition = require(code);
        if (!"PUBLISHED".equals(definition.getStatus()) || definition.getProcessDefinitionId() == null) {
            throw new BizException(ErrorCode.FLOW_NOT_FOUND, "流程未发布：" + code);
        }
        return definition;
    }

    public FlowDefinition find(String code) {
        return definitionMapper.selectOne(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getCode, code));
    }

    @Transactional
    public FlowDefinition save(SaveCommand command, String operator) {
        if (command.code() == null || !CODE_PATTERN.matcher(command.code()).matches()) {
            throw BizException.badRequest("流程编码只能以字母开头，包含字母、数字、下划线或中划线");
        }
        if (command.flowSchema() == null) {
            throw new BizException(ErrorCode.FLOW_SCHEMA_INVALID, "flowSchema 不能为空");
        }
        String flowSchemaJson = json.write(command.flowSchema());
        String formSchemaJson = command.formSchema() == null ? null : json.write(command.formSchema());
        FlowSchema schema = json.read(flowSchemaJson, FlowSchema.class);
        compiler.validate(schema);

        LocalDateTime now = LocalDateTime.now();
        FlowDefinition existing = find(command.code());
        if (existing == null) {
            FlowDefinition definition = new FlowDefinition();
            definition.setId(Ids.id());
            definition.setCode(command.code());
            definition.setName(command.name());
            definition.setCategory(command.category());
            definition.setIcon(command.icon());
            definition.setDescription(command.description());
            definition.setFormSchema(formSchemaJson);
            definition.setFlowSchema(flowSchemaJson);
            definition.setVersion(0);
            definition.setStatus("DRAFT");
            definition.setCreatedBy(operator);
            definition.setUpdatedBy(operator);
            definition.setCreatedAt(now);
            definition.setUpdatedAt(now);
            definitionMapper.insert(definition);
            return definition;
        }

        existing.setName(command.name());
        existing.setCategory(command.category());
        existing.setIcon(command.icon());
        existing.setDescription(command.description());
        existing.setFormSchema(formSchemaJson);
        existing.setFlowSchema(flowSchemaJson);
        existing.setStatus("DRAFT");
        existing.setUpdatedBy(operator);
        existing.setUpdatedAt(now);
        definitionMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public FlowDefinition publish(String code, String operator, String changelog) {
        FlowDefinition definition = require(code);
        FlowSchema schema = json.read(definition.getFlowSchema(), FlowSchema.class);
        String processKey = compiler.processKeyOf(definition.getCode());
        String bpmn = compiler.compile(processKey, definition.getName(), schema);

        int nextVersion = (definition.getVersion() == null ? 0 : definition.getVersion()) + 1;
        String tenantId = TenantContext.require();
        Deployment deployment = repositoryService.createDeployment()
                .name("easy-bpm " + definition.getCode() + " v" + nextVersion)
                .tenantId(tenantId)
                .addString(processKey + ".bpmn20.xml", bpmn)
                .deploy();
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId()).singleResult();
        if (processDefinition == null) {
            throw new BizException(ErrorCode.FLOW_SCHEMA_INVALID, "BPMN 部署失败，未生成流程定义");
        }

        LocalDateTime now = LocalDateTime.now();
        definition.setBpmnXml(bpmn);
        definition.setProcessDefinitionId(processDefinition.getId());
        definition.setVersion(nextVersion);
        definition.setStatus("PUBLISHED");
        definition.setPublishedAt(now);
        definition.setUpdatedAt(now);
        definition.setUpdatedBy(operator);
        definitionMapper.updateById(definition);

        FlowVersion version = new FlowVersion();
        version.setId(Ids.id());
        version.setDefinitionId(definition.getId());
        version.setVersion(nextVersion);
        version.setFlowSchema(definition.getFlowSchema());
        version.setFormSchema(definition.getFormSchema());
        version.setBpmnXml(bpmn);
        version.setProcessDefinitionId(processDefinition.getId());
        version.setPublishedBy(operator);
        version.setPublishedAt(now);
        version.setChangelog(changelog);
        versionMapper.insert(version);
        return definition;
    }

    @Transactional
    public void disable(String code, String operator) {
        FlowDefinition definition = require(code);
        if (!"PUBLISHED".equals(definition.getStatus())) {
            throw BizException.badRequest("仅已发布的流程可停用（code=" + code + "）");
        }
        definition.setStatus("DISABLED");
        definition.setUpdatedBy(operator);
        definition.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(definition);
    }

    /**
     * 启用已停用的流程：恢复到其最后一次发布的版本，无需重新走发布流程。
     */
    @Transactional
    public FlowDefinition enable(String code, String operator) {
        FlowDefinition definition = require(code);
        if (!"DISABLED".equals(definition.getStatus())) {
            throw BizException.badRequest("仅已停用的流程可启用（code=" + code + "）");
        }
        if (definition.getProcessDefinitionId() == null) {
            throw new BizException(ErrorCode.FLOW_SCHEMA_INVALID, "流程缺少引擎部署记录，无法启用，请重新发布");
        }
        definition.setStatus("PUBLISHED");
        definition.setUpdatedBy(operator);
        definition.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(definition);
        return definition;
    }

    /**
     * 删除从未发布过的草稿定义。已发布（含停用）的定义不可删除，保证历史实例可追溯。
     */
    @Transactional
    public void deleteDraft(String code, String operator) {
        FlowDefinition definition = require(code);
        if (definition.getVersion() != null && definition.getVersion() > 0) {
            throw BizException.badRequest("已发布过的流程不可删除（code=" + code + "），请使用停用");
        }
        definitionMapper.deleteById(definition.getId());
        versionMapper.delete(new LambdaQueryWrapper<FlowVersion>()
                .eq(FlowVersion::getDefinitionId, definition.getId()));
    }

    /**
     * 版本回滚：取历史版本的审批链/表单覆盖当前定义，并立即发布为新版本（N+1），
     * 引擎部署与版本快照同步推进，历史版本永不丢失。
     */
    @Transactional
    public FlowDefinition rollback(String code, int targetVersion, String operator, String changelog) {
        FlowDefinition definition = require(code);
        FlowVersion target = versionMapper.selectOne(new LambdaQueryWrapper<FlowVersion>()
                .eq(FlowVersion::getDefinitionId, definition.getId())
                .eq(FlowVersion::getVersion, targetVersion));
        if (target == null) {
            throw BizException.badRequest("目标版本不存在：v" + targetVersion);
        }
        if (targetVersion == definition.getVersion()) {
            throw BizException.badRequest("目标版本已是当前版本：v" + targetVersion);
        }

        definition.setFlowSchema(target.getFlowSchema());
        definition.setFormSchema(target.getFormSchema());
        definition.setStatus("DRAFT");
        definition.setUpdatedBy(operator);
        definition.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(definition);
        return publish(code, operator,
                (changelog == null || changelog.isBlank() ? "回滚到 v" + targetVersion : changelog));
    }

    public List<FlowVersion> versions(String code) {
        FlowDefinition definition = require(code);
        return versionMapper.selectList(new LambdaQueryWrapper<FlowVersion>()
                .eq(FlowVersion::getDefinitionId, definition.getId())
                .orderByDesc(FlowVersion::getVersion));
    }

    public record SaveCommand(String code, String name, String category, String icon, String description,
                              Object formSchema, Object flowSchema) {
    }
}
