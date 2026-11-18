package com.easybpm.flow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bpm_flow_version")
public class FlowVersion {

    @TableId(type = IdType.INPUT)
    private String id;
    private String definitionId;
    private Integer version;
    private String flowSchema;
    private String formSchema;
    private String bpmnXml;
    private String processDefinitionId;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private String changelog;
}
