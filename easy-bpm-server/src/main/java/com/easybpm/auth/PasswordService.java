package com.easybpm.auth;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 控制台密码散列：PBKDF2-HMAC-SHA256，格式 pbkdf2$iterations$salt$hash。
 */
@Service
public class PasswordService {

    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String hash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword, salt, ITERATIONS);
        Base64.Encoder encoder = Base64.getEncoder();
        return "pbkdf2$" + ITERATIONS + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash);
    }

    public boolean matches(String rawPassword, String stored) {
        if (stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        int iterations = Integer.parseInt(parts[1]);
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] salt = decoder.decode(parts[2]);
        byte[] expected = decoder.decode(parts[3]);
        byte[] actual = pbkdf2(rawPassword, salt, iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("密码散列失败", e);
        }
    }
}
