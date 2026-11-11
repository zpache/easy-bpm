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
    /** SSO 专属账号为 null（不允许空密码登录，PasswordService.matches 会直接失败） */
    private String passwordHash;
    /** 身份来源：LOCAL 密码登录 / SSO 企业单点登录。仅用于提示与审计，不参与鉴权判定 */
    private String authSource;
    private String displayName;
    private String externalUserId;
    private String role;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
