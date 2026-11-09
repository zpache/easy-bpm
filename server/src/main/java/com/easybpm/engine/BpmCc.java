package com.easybpm.engine;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bpm_cc")
public class BpmCc {

    @TableId(type = IdType.INPUT)
    private String id;
    private String instanceId;
    private String nodeId;
    private String nodeName;
    private String userId;
    private String userName;
    private Integer readFlag;
    private LocalDateTime createdAt;
}
