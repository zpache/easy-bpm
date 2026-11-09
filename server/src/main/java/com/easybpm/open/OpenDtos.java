package com.easybpm.open;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * OpenAPI 请求体。字段语义见 docs/integration.md。
 */
public final class OpenDtos {

    private OpenDtos() {
    }

    public record StartRequest(
            @NotBlank String flowCode,
            String businessKey,
            String title,
            @NotBlank String initiatorId,
            @NotBlank String initiatorName,
            String initiatorDeptCode,
            Map<String, Object> formData,
            List<String> ccUsers,
            String callbackUrl,
            String idempotencyKey
    ) {
    }

    public record HandleRequest(@NotBlank String userId, String comment, Map<String, Object> formData) {
    }
}
