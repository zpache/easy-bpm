package com.easybpm.flow;

import com.easybpm.common.BizException;
import com.easybpm.common.JsonCodec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 表单数据校验。formSchema 约定：
 * <pre>{ "fields": [ { "key": "days", "label": "天数", "type": "number", "required": true } ] }</pre>
 * type 支持 string / number / boolean / date（date 按字符串透传）。无 formSchema 或无 fields 时跳过校验。
 */
@Component
public class FormValidator {

    private final JsonCodec json;

    public FormValidator(JsonCodec json) {
        this.json = json;
    }

    @SuppressWarnings("unchecked")
    public void validate(String formSchemaJson, Map<String, Object> formData) {
        if (formSchemaJson == null || formSchemaJson.isBlank() || formData == null) {
            return;
        }
        Map<String, Object> schema = json.readMap(formSchemaJson);
        Object rawFields = schema.get("fields");
        if (!(rawFields instanceof List<?> fields) || fields.isEmpty()) {
            return;
        }
        for (Object item : fields) {
            if (!(item instanceof Map<?, ?> field)) {
                continue;
            }
            String key = String.valueOf(field.get("key"));
            if (key == null || key.isBlank() || "null".equals(key)) {
                continue;
            }
            Object value = formData.get(key);
            boolean required = Boolean.TRUE.equals(field.get("required"));
            if (value == null || (value instanceof String s && s.isBlank())) {
                if (required) {
                    throw BizException.badRequest("表单字段必填：" + key);
                }
                continue;
            }
            String type = field.get("type") == null ? "string" : String.valueOf(field.get("type"));
            if (!matchesType(type, value)) {
                throw BizException.badRequest("表单字段类型错误：" + key + " 应为 " + type);
            }
        }
    }

    private boolean matchesType(String type, Object value) {
        return switch (type) {
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "string", "date", "text", "textarea", "select" -> value instanceof String;
            default -> true;
        };
    }
}
