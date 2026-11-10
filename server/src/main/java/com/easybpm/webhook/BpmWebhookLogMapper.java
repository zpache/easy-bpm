package com.easybpm.webhook;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;

/**
 * 回调投递日志 Mapper。
 *
 * <p>后台轮询器没有租户上下文，且需要跨租户扫描 PENDING 行，
 * 因此整体忽略租户插件，租户 ID 一律由服务层显式写入与过滤。
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BpmWebhookLogMapper extends BaseMapper<BpmWebhookLog> {
}
