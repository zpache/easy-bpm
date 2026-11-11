package com.easybpm.auth.sso;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部身份与本地控制台账号的绑定关系。
 *
 * <p>{@code subject}（IdP 的 sub / NameID）只在 issuer 内唯一，因此唯一键是
 * {@code (provider_id, subject)} 而不是单独 subject。
 *
 * <p>{@code consoleUserId} 为空有两种含义，靠 {@code status} 区分：
 * <ul>
 *   <li>{@code PENDING}：IdP 已认证成功，但本地没有对应账号或尚未绑定，等待管理员开通；</li>
 *   <li>{@code DISABLED}：管理员解绑，保留记录用于审计。</li>
 * </ul>
 * 非空且 status=ACTIVE 才是可用登录态。
 *
 * <p>该表同样不参与租户插件自动隔离，查询点手动按 tenantId 过滤。
 */
@Data
@TableName("bpm_sso_identity")
public class SsoIdentity {

    public static final String PENDING = "PENDING";
    public static final String ACTIVE = "ACTIVE";
    public static final String DISABLED = "DISABLED";

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String providerId;
    private String subject;
    private String issuer;
    /** 空表示尚未绑定本地账号 */
    private String consoleUserId;
    /** 绑定后同步本地账号的 externalUserId，便于审计与排查 */
    private String externalUserId;
    /** 来自 IdP claim，仅用于管理员识别待绑定人员，不参与登录判定 */
    private String email;
    private String displayName;
    private String status;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
