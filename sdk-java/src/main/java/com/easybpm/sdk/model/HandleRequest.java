package com.easybpm.sdk.model;

import java.util.Map;

/** 同意 / 驳回请求。formData 仅同意时生效，会合并进实例表单。 */
public record HandleRequest(String userId, String comment, Map<String, Object> formData) {

    public static HandleRequest of(String userId, String comment) {
        return new HandleRequest(userId, comment, null);
    }
}
