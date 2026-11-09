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
    private String username;
    private String passwordHash;
    private String displayName;
    private String externalUserId;
    private String role;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
