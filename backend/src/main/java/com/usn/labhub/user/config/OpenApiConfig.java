package com.usn.labhub.user.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("USN实验室管理系统 API")
                        .version("1.0")
                        .description("考勤与用户管理系统接口文档"))
                // 1. 定义安全方案（告诉 Swagger 有个东西叫 JWT）
                .components(new Components()
                        .addSecuritySchemes("tokenHeader", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY) // 类型是 API 密钥
                                .in(SecurityScheme.In.HEADER)    // 放在请求头里
                                .name("token")))                 // 请求头的名字必须叫 token
                // 2. 全局应用这个方案（让所有接口都带上这个锁）
                .addSecurityItem(new SecurityRequirement().addList("tokenHeader"));
    }
}