package com.easybpm.auth.sso;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 安全约定：本表已移出租户插件自动隔离，调用方必须显式追加 tenantId 条件。
 * 合法调用点仅限 {@link SsoProviderService}（登录前置查询、平台端配置管理）。
 */
@Mapper
public interface SsoProviderMapper extends BaseMapper<SsoProvider> {
}
