package com.easybpm.common;

import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * JSON 编解码。统一把解析失败转成可读的业务异常，避免把 Jackson 的异常细节透给接入方。
 */
@Component
public class JsonCodec {

    private final ObjectMapper mapper;

    public JsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new BizException(ErrorCode.FLOW_SCHEMA_INVALID, "JSON 解析失败：" + e.getMessage());
        }
    }

    public Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new BizException(ErrorCode.FLOW_SCHEMA_INVALID, "JSON 解析失败：" + e.getMessage());
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
