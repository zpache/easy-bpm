package com.easybpm.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bpm_console_user")
public class ConsoleUser {

    @TableId(type = IdType.INPUT)
    private String id;
    /** 所属租户。表已移出租户插件自动隔离，由各查询点手动按此列过滤 */
    private String tenantId;
    private String username;
    private String passwordHash;
    private String displayName;
    private String externalUserId;
    private String role;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
