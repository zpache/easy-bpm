package com.easybpm.common;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * ID 生成。业务主键统一 32 位无横线 UUID；接入凭证带可识别前缀，便于日志排查。
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String appKey() {
        return "ak_" + randomHex(16);
    }

    public static String appSecret() {
        return "sk_" + randomHex(24);
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}
