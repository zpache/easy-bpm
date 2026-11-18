package com.easybpm.config;

import com.easybpm.auth.ConsoleAuthFilter;
import com.easybpm.auth.OpenApiAuthFilter;
import com.easybpm.open.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web 配置：控制台与管理端分开鉴权，OpenAPI 只认租户凭证。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

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
        bean.setUrlPatterns(List.of("/api/*"));
        bean.setOrder(20);
        return bean;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Trace-Id")
                .maxAge(3600);
    }
}
