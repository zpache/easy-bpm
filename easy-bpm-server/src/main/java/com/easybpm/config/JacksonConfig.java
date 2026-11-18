package com.easybpm.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局时间序列化：LocalDateTime 统一输出 yyyy-MM-dd HH:mm:ss（控制台与 OpenAPI 一致）。
 *
 * <p>反序列化兼容两种分隔符（yyyy-MM-ddTHH:mm:ss 与 yyyy-MM-dd HH:mm:ss），
 * 避免历史接入方传 ISO 格式报错；秒与小数部分均可省略。
 * 项目使用 Jackson 3（tools.jackson），Boot 4.1 的定制入口为 JsonMapperBuilderCustomizer，
 * 序列化器经 SimpleModule 注册（Jackson 3 的 MapperBuilder 无 serializers() 便捷方法）。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter SERIALIZER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter DESERIALIZER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd['T'][' ']HH:mm[:ss[.SSS]]");

    @Bean
    public JsonMapperBuilderCustomizer localDateTimeFormatCustomizer() {
        SimpleModule module = new SimpleModule("easybpm-local-datetime")
                .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(SERIALIZER))
                .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DESERIALIZER));
        return builder -> builder.addModule(module);
    }
}
