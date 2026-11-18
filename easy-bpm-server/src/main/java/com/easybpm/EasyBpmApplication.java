package com.easybpm;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * easy-bpm 审批流中台。
 *
 * <p>独立部署的审批能力提供方：ERP / SaaS 通过 OpenAPI 接入，无需了解引擎内部实现。
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan(basePackages = "com.easybpm", annotationClass = Mapper.class)
public class EasyBpmApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyBpmApplication.class, args);
    }
}
