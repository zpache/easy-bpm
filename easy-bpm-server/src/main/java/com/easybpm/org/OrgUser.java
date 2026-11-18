package com.easybpm.org;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.easybpm.common.crypto.EncryptedStringTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织人员。mobile / email 为个人敏感信息，落库经 AES-256-GCM 加密（{@link EncryptedStringTypeHandler}），
 * 读取时自动解密，业务代码仍按明文使用。
 *
 * <p>{@code mobileHash} 是手机号的带密钥盲索引（HMAC-SHA256），仅用于「按手机号精确查找」，
 * 因为密文无法做 LIKE / 等值匹配。
 */
@Data
@TableName(value = "bpm_org_user", autoResultMap = true)
public class OrgUser {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userCode;
    private String name;
    private String deptCode;
    private String deptName;
    private String leaderCode;
    private String title;

    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String mobile;

    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String email;

    /** 手机号盲索引，由 OrgService 维护，不接受外部写入。 */
    private String mobileHash;

    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
