package com.easybpm.flow;

/**
 * 驳回后的走向。P1 只支持 TO_END（直接结束并标记驳回），TO_START 在 P2 提供。
 */
public enum RejectStrategy {
    TO_END, TO_START
}
