package com.easybpm.bootstrap;

import com.easybpm.common.crypto.DataCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 敏感字段存量数据迁移（幂等）。
 *
 * <p>把历史明文（以及用旧密钥加密）的手机号、邮箱、AppSecret 重写为当前密钥的密文，
 * 同时补齐手机号盲索引。直接走 JdbcTemplate 读写原始值，绕过多租户插件与 TypeHandler，
 * 避免「读出来已解密、判断不出是否需要升级」的问题。
 *
 * <p>每次启动只做一次全表扫描比对，无需变更的行不产生 UPDATE（不会刷新 updated_at）。
 */
@Component
public class SensitiveDataMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataMigration.class);

    private final JdbcTemplate jdbc;
    private final DataCipher cipher;

    public SensitiveDataMigration(JdbcTemplate jdbc, DataCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateOrgUsers();
        migrateCredentials();
    }

    private void migrateOrgUsers() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("select id, mobile, email, mobile_hash from bpm_org_user");
        } catch (Exception e) {
            log.warn("跳过组织人员敏感字段迁移（表不可用）：{}", e.getMessage());
            return;
        }
        int updated = 0;
        for (Map<String, Object> row : rows) {
            String id = string(row, "id");
            String mobile = string(row, "mobile");
            String email = string(row, "email");
            String storedHash = string(row, "mobile_hash");
            try {
                String newMobile = cipher.reencryptIfNeeded(mobile);
                String newEmail = cipher.reencryptIfNeeded(email);
                String expectedHash = cipher.blindIndex(cipher.decrypt(mobile));
                boolean hashChanged = !Objects.equals(expectedHash, storedHash);
                if (newMobile == null && newEmail == null && !hashChanged) {
                    continue;
                }
                jdbc.update("update bpm_org_user set mobile = ?, email = ?, mobile_hash = ? where id = ?",
                        newMobile == null ? mobile : newMobile,
                        newEmail == null ? email : newEmail,
                        expectedHash,
                        id);
                updated++;
            } catch (RuntimeException e) {
                // 单行密钥不匹配不阻塞启动：失败的行会在访问时暴露同样的问题，由日志定位
                log.error("组织人员敏感字段迁移失败 id={}：{}", id, e.getMessage());
            }
        }
        if (updated > 0) {
            log.info("敏感字段迁移完成：组织人员 {} 行已加密/重建盲索引", updated);
        }
    }

    private void migrateCredentials() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("select id, app_secret from bpm_tenant_credential");
        } catch (Exception e) {
            log.warn("跳过接入凭证敏感字段迁移（表不可用）：{}", e.getMessage());
            return;
        }
        int updated = 0;
        for (Map<String, Object> row : rows) {
            String stored = string(row, "app_secret");
            try {
                String rewritten = cipher.reencryptIfNeeded(stored);
                if (rewritten == null) {
                    continue;
                }
                jdbc.update("update bpm_tenant_credential set app_secret = ? where id = ?",
                        rewritten, string(row, "id"));
                updated++;
            } catch (RuntimeException e) {
                log.error("接入凭证敏感字段迁移失败 id={}：{}", string(row, "id"), e.getMessage());
            }
        }
        if (updated > 0) {
            log.info("敏感字段迁移完成：接入凭证 {} 条的 AppSecret 已加密/轮换密钥", updated);
        }
    }

    /** 列名大小写兼容：MySQL 返回小写、H2 返回大写。 */
    private String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase(java.util.Locale.ROOT));
        }
        return value == null ? null : String.valueOf(value);
    }
}
