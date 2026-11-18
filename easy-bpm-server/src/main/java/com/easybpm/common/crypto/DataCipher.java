package com.easybpm.common.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 敏感字段加解密（AES-256-GCM）。
 *
 * <p>落库格式：{@code enc:v1:} + Base64(12 字节随机 IV + 密文 + GCM tag)。
 * 未带前缀的值视为历史明文，直接返回，保证存量数据可读、迁移脚本可逐行升级。
 *
 * <p>密钥来自 {@code easybpm.security.data-key}（环境变量 {@code EASY_BPM_DATA_KEY}），
 * 任意长度字符串经 SHA-256 派生为 32 字节密钥；轮换时把旧密钥配到
 * {@code data-key-previous}，启动迁移会把仍用旧密钥加密的字段重写为新密钥。
 *
 * <p>另提供 {@link #blindIndex(String)}：带密钥的 HMAC-SHA256，
 * 用于手机号这类需要「精确查找但不想存明文/明文索引」的字段。
 */
@Component
public class DataCipher {

    private static final Logger log = LoggerFactory.getLogger(DataCipher.class);

    /** 密文前缀，同时是「是否已加密」的判定依据。 */
    public static final String PREFIX = "enc:v1:";

    /** 内置开发密钥：未配置 data-key 时使用，仅用于本地开发。 */
    private static final String DEV_KEY = "easy-bpm-dev-data-key-change-me-2026-11-11";

    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec currentKey;
    private final SecretKeySpec previousKey;
    private final String currentKeyMaterial;
    private final SecureRandom random = new SecureRandom();

    public DataCipher(@Value("${easybpm.security.data-key:}") String dataKey,
                      @Value("${easybpm.security.data-key-previous:}") String previousDataKey) {
        String material = dataKey == null || dataKey.isBlank() ? DEV_KEY : dataKey.trim();
        if (dataKey == null || dataKey.isBlank() || DEV_KEY.equals(material)) {
            log.warn("未配置 easybpm.security.data-key（EASY_BPM_DATA_KEY），敏感字段使用内置开发密钥；"
                    + "生产环境必须配置独立随机密钥，否则换机/重建环境后历史密文无法解密");
        }
        this.currentKeyMaterial = material;
        this.currentKey = new SecretKeySpec(sha256(material), "AES");
        this.previousKey = previousDataKey == null || previousDataKey.isBlank()
                ? null : new SecretKeySpec(sha256(previousDataKey.trim()), "AES");
        if (this.previousKey != null) {
            log.info("已启用敏感字段密钥轮换：新写入使用当前 data-key，旧密钥仅用于解密，迁移完成后可移除");
        }
    }

    /** 加密。null / 空串 / 已加密的值原样返回，因此可重复调用。 */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty() || isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, currentKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("敏感字段加密失败", e);
        }
    }

    /**
     * 解密。未加密的历史明文原样返回。
     *
     * @throws IllegalStateException 密文无法用当前/旧密钥解开（密钥配错或数据被篡改）
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty() || !isEncrypted(stored)) {
            return stored;
        }
        byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        try {
            return doDecrypt(currentKey, payload);
        } catch (GeneralSecurityException currentFailed) {
            if (previousKey != null) {
                try {
                    return doDecrypt(previousKey, payload);
                } catch (GeneralSecurityException ignored) {
                    // 继续抛出当前密钥的失败信息
                }
            }
            throw new IllegalStateException(
                    "敏感字段解密失败：data-key 与数据不匹配，请检查 EASY_BPM_DATA_KEY / EASY_BPM_DATA_KEY_PREVIOUS", currentFailed);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * 启动迁移用：返回该字段应写入的新值，无需变更时返回 null。
     * <ul>
     *   <li>历史明文 → 当前密钥密文；</li>
     *   <li>当前密钥密文 → null（跳过）；</li>
     *   <li>旧密钥密文 → 当前密钥密文（轮换）。</li>
     * </ul>
     */
    public String reencryptIfNeeded(String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        if (!isEncrypted(stored)) {
            return encrypt(stored);
        }
        byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        try {
            doDecrypt(currentKey, payload);
            return null;
        } catch (GeneralSecurityException notCurrent) {
            if (previousKey == null) {
                return null;
            }
            try {
                return encrypt(doDecrypt(previousKey, payload));
            } catch (GeneralSecurityException ignored) {
                return null;
            }
        }
    }

    /**
     * 盲索引：规范化后做 HMAC-SHA256，用于手机号等字段的精确查找。
     * 同一密钥下结果稳定，缺少密钥无法离线爆破；null / 空值返回 null。
     */
    public String blindIndex(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sha256(currentKeyMaterial + "|blind-index"), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("盲索引计算失败", e);
        }
    }

    private String doDecrypt(SecretKeySpec key, byte[] payload) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private byte[] sha256(String material) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
