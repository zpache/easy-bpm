package com.easybpm.engine;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批实例投影。Flowable 是状态机，这张表负责列表检索、标题/业务主键映射与统计。
 */
@Data
@TableName("bpm_instance")
public class BpmInstance {

    @TableId(type = IdType.INPUT)
    private String id;
    private String instanceId;
    private String flowCode;
    private String flowName;
    private Integer flowVersion;
    private String businessKey;
    private String title;
    private String initiatorId;
    private String initiatorName;
    private String initiatorDeptCode;
    private String formData;
    private String status;
    private String currentNodeName;
    private String idempotencyKey;
    private String callbackUrl;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime updatedAt;
}
