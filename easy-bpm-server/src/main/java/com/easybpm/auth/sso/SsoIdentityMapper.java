package com.easybpm.auth.sso;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 安全约定：本表已移出租户插件自动隔离，调用方必须显式追加 tenantId 条件。
 * 合法调用点仅限 {@link SsoLoginService} 与 {@link SsoProviderService}。
 */
@Mapper
public interface SsoIdentityMapper extends BaseMapper<SsoIdentity> {
}
