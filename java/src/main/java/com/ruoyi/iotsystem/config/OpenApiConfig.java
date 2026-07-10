package com.ruoyi.iotsystem.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 配置 — Swagger UI 访问地址: /swagger-ui/index.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI iotOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智慧农业 IoT 系统 API")
                        .description("Spring Boot 后端 REST API — 传感器数据采集、设备管理、AI 助手、支付等")
                        .version("1.0.0")
                        .contact(new Contact().name("IoT Team"))
                        .license(new License().name("MIT")));
    }
}
