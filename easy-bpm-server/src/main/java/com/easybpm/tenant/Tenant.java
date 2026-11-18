package com.easybpm.tenant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bpm_tenant")
public class Tenant {

    @TableId(type = IdType.INPUT)
    private String id;
    private String code;
    private String name;
    private String status;
    private String webhookUrl;
    private String webhookSecret;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
