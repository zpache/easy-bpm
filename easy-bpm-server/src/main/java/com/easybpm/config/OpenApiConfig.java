package com.easybpm.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档。OpenAPI 对接方按 AppKey + Secret 签名调用，控制台按 Bearer Token 调用。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI easyBpmOpenApi() {
        SecurityScheme appKey = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-App-Key");
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("easy-bpm 审批流中台 API")
                        .version("1.0.0")
                        .description("独立部署的审批能力：创建审批、待办/已办/已提交、同意/驳回。所有 OpenAPI 请求需携带 X-App-Key 与 X-App-Secret（或 X-Signature）。"))
                .components(new Components()
                        .addSecuritySchemes("AppKey", appKey)
                        .addSecuritySchemes("Bearer", bearer))
                .addSecurityItem(new SecurityRequirement().addList("AppKey"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"));
    }
}
