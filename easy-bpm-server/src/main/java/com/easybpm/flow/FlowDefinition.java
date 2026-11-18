package com.easybpm.flow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bpm_flow_definition")
public class FlowDefinition {

    @TableId(type = IdType.INPUT)
    private String id;
    private String code;
    private String name;
    private String category;
    private String icon;
    private String description;
    private String formSchema;
    private String flowSchema;
    private String bpmnXml;
    private String processDefinitionId;
    private Integer version;
    private String status;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
}
