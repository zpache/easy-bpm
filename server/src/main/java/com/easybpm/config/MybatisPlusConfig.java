package com.easybpm.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.easybpm.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;
import java.util.Set;

/**
 * MyBatis-Plus 多租户插件：所有业务表 SQL 自动追加 tenant_id 条件。
 *
 * <p>忽略表三类：租户主表、接入凭证表（认证发生在租户上下文建立之前）、
 * 控制台账号表（平台管理员切换租户后需在目标租户上下文中解析自身账号）；
 * 后者的租户条件由各查询点手动追加，保证登录与账号校验仍按租户隔离。
 */
@Configuration
public class MybatisPlusConfig {

    private static final Set<String> IGNORED_TABLES =
            Set.of("bpm_tenant", "bpm_tenant_credential", "bpm_console_user");

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new StringValue(TenantContext.require());
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return IGNORED_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
            }
        }));
        // 不指定 DbType，由运行时连接自动识别（开发 MySQL / 测试 H2）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
