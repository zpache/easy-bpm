package com.easybpm.sdk.model;

import java.util.Map;

/** TO_START 驳回后，发起人修改表单重新提交。 */
public record ResubmitRequest(String userId, Map<String, Object> formData, String comment) {
}
