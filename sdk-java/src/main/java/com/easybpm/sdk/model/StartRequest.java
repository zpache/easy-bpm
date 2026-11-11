package com.easybpm.sdk.model;

import java.util.List;
import java.util.Map;

/**
 * 发起审批请求。除 flowCode / initiatorId / initiatorName 外均可选。
 *
 * <pre>{@code
 * StartRequest req = StartRequest.builder()
 *         .flowCode("leave")
 *         .businessKey("LEAVE-2026-0001")
 *         .title("张三请假 3 天")
 *         .initiatorId("u1001").initiatorName("张三")
 *         .formData(Map.of("days", 3, "reason", "家中有事"))
 *         .ccUsers(List.of("u1004"))
 *         .idempotencyKey("LEAVE-2026-0001")
 *         .build();
 * }</pre>
 *
 * <p>强烈建议带 idempotencyKey（用业务单号）：网络超时后原样重试不会产生重复实例。
 */
public record StartRequest(
        String flowCode,
        String businessKey,
        String title,
        String initiatorId,
        String initiatorName,
        String initiatorDeptCode,
        Map<String, Object> formData,
        List<String> ccUsers,
        String callbackUrl,
        String idempotencyKey
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String flowCode;
        private String businessKey;
        private String title;
        private String initiatorId;
        private String initiatorName;
        private String initiatorDeptCode;
        private Map<String, Object> formData;
        private List<String> ccUsers;
        private String callbackUrl;
        private String idempotencyKey;

        public Builder flowCode(String flowCode) {
            this.flowCode = flowCode;
            return this;
        }

        public Builder businessKey(String businessKey) {
            this.businessKey = businessKey;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder initiator(String userId, String userName) {
            this.initiatorId = userId;
            this.initiatorName = userName;
            return this;
        }

        public Builder initiatorId(String initiatorId) {
            this.initiatorId = initiatorId;
            return this;
        }

        public Builder initiatorName(String initiatorName) {
            this.initiatorName = initiatorName;
            return this;
        }

        public Builder initiatorDeptCode(String initiatorDeptCode) {
            this.initiatorDeptCode = initiatorDeptCode;
            return this;
        }

        public Builder formData(Map<String, Object> formData) {
            this.formData = formData;
            return this;
        }

        public Builder ccUsers(List<String> ccUsers) {
            this.ccUsers = ccUsers;
            return this;
        }

        public Builder callbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public StartRequest build() {
            return new StartRequest(flowCode, businessKey, title, initiatorId, initiatorName,
                    initiatorDeptCode, formData, ccUsers, callbackUrl, idempotencyKey);
        }
    }
}
