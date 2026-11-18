package com.easybpm.flow;

public enum NodeType {
    START, APPROVAL, CC, END,
    /** 排他网关：branches 按顺序 FIRST-MATCH 选路，条件为空的分支即默认兜底分支 */
    CONDITION
}
