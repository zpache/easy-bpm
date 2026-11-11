package com.easybpm.engine;

public enum TaskAction {
    SUBMIT, APPROVE, REJECT, TRANSFER, REVOKE, RESUBMIT, CC, AUTO_PASS,
    /** 管理员强制转办：不校验原处理人，用于审批人离职/停用等卡单救援 */
    ADMIN_TRANSFER
}
