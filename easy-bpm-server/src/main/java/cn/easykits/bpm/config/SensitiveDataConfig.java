package cn.easykits.bpm.config;

import cn.easykits.bpm.common.crypto.DataCipher;
import cn.easykits.bpm.common.crypto.EncryptedStringTypeHandler;
import org.springframework.context.annotation.Configuration;

/**
 * 把 Spring 管理的 {@link DataCipher} 注入 MyBatis TypeHandler（TypeHandler 由 MyBatis 实例化，无法直接注入）。
 */
@Configuration
public class SensitiveDataConfig {

    public SensitiveDataConfig(DataCipher dataCipher) {
        EncryptedStringTypeHandler.install(dataCipher);
    }
}
