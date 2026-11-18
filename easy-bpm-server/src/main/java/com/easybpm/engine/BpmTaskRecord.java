package com.easybpm.engine;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bpm_task_record")
public class BpmTaskRecord {

    @TableId(type = IdType.INPUT)
    private String id;
    private String instanceId;
    private String taskId;
    private String nodeId;
    private String nodeName;
    private String operatorId;
    private String operatorName;
    private String action;
    private String comment;
    private String attachments;
    private LocalDateTime createdAt;
}
