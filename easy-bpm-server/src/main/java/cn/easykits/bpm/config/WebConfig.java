package cn.easykits.bpm.config;

import cn.easykits.bpm.auth.ConsoleAuthFilter;
import cn.easykits.bpm.auth.OpenApiAuthFilter;
import cn.easykits.bpm.open.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Web 配置：控制台与管理端分开鉴权，OpenAPI 只认租户凭证。
 */
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setUrlPatterns(List.of("/open/*"));
        bean.setOrder(5);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<OpenApiAuthFilter> openApiFilterRegistration(OpenApiAuthFilter filter) {
        FilterRegistrationBean<OpenApiAuthFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setUrlPatterns(List.of("/open/*"));
        bean.setOrder(10);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<ConsoleAuthFilter> consoleFilterRegistration(ConsoleAuthFilter filter) {
        FilterRegistrationBean<ConsoleAuthFilter> bean = new FilterRegistrationBean<>(filter);
        // 控制台接口无统一 /api 前缀，逐前缀注册鉴权过滤器
        bean.setUrlPatterns(List.of("/auth/*", "/flows/*", "/workbench/*", "/org/*", "/tenant/*",
                "/instances/*", "/audit/*", "/dashboard/*", "/platform/*", "/public/*"));
        bean.setOrder(20);
        return bean;
    }

    /**
     * CORS 以 Servlet Filter 实现（order 15，先于控制台鉴权过滤器 20）：
     * 浏览器预检 OPTIONS 请求不带 Authorization 头，若由 MVC 层处理会被鉴权过滤器
     * 先行 401 且无 CORS 响应头，浏览器表现为跨域错误。
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.addExposedHeader("X-Trace-Id");
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(15);
        return bean;
    }
}
