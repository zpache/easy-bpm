package com.easybpm.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 控制台账号 Mapper。
 *
 * <p><strong>⚠️ 安全约定：bpm_console_user 已移出租户插件自动隔离</strong>
 * （见 MybatisPlusConfig.IGNORED_TABLES，平台管理员切换租户时需跨上下文解析账号）。
 * 因此本表的所有查询/统计<strong>必须手动追加 tenant_id 条件</strong>，
 * 否则即为跨租户越权查询。当前合法调用点：
 * <ul>
 *   <li>ConsoleAuthService.login —— tenantId + username 双条件</li>
 *   <li>ConsoleAuthFilter —— 按 JWT 签名的 consoleUserId 查本人</li>
 *   <li>ConsolePlatformController —— PLATFORM_ADMIN 接口内、显式 tenantId 条件</li>
 * </ul>
 */
@Mapper
public interface ConsoleUserMapper extends BaseMapper<ConsoleUser> {
}
