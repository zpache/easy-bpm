package com.easybpm.flow;

import java.util.List;

/**
 * 条件网关的一个分支。
 *
 * <p>{@code condition} 为 SpEL 表达式（以 formData 顶层字段为根），在运行时到达网关时
 * 用实例当前变量求值；为空表示默认分支（所有条件都不满足时兜底），一个网关必须且只能有一个。
 * {@code nodes} 为该分支的子节点链（APPROVAL / CC），按顺序执行后汇聚回主链。
 */
public record Branch(String name, String condition, List<FlowNode> nodes) {
}
