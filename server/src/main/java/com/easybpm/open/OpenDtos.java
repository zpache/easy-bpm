package com.easybpm.open;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * OpenAPI 请求体。字段语义见 docs/integration.md。
 *
 * <p>StartRequest 不做 Bean Validation：幂等键重发必须先于参数校验命中（即使补全字段前的旧请求
 * 也应返回原实例），完整性校验由引擎在幂等检查之后执行。
 */
public final class OpenDtos {

    private OpenDtos() {
    }

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
    }

    public record HandleRequest(@NotBlank String userId, String comment, Map<String, Object> formData) {
    }

    public record ResubmitRequest(@NotBlank String userId, Map<String, Object> formData, String comment) {
    }

    public record WithdrawRequest(@NotBlank String userId, String comment) {
    }

    public record TransferRequest(@NotBlank String userId, @NotBlank String targetUserId, String comment) {
    }

    /** 组织同步：全量 upsert，按 userCode / deptCode 幂等。 */
    public record OrgSyncRequest(List<OrgUserSync> users, List<OrgDeptSync> depts, List<OrgRoleSync> roles) {

        public record OrgUserSync(
                @NotBlank String userCode,
                @NotBlank String name,
                String deptCode,
                String deptName,
                String leaderCode,
                String title,
                String mobile,
                String email,
                String status   // ACTIVE / DISABLED，缺省 ACTIVE
        ) {
        }

        public record OrgDeptSync(
                @NotBlank String deptCode,
                @NotBlank String name,
                String parentCode,
                String leaderId,
                Integer sortNo
        ) {
        }

        public record OrgRoleSync(@NotBlank String userCode, List<String> roleCodes) {
        }
    }
}
