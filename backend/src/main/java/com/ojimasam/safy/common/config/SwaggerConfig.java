package com.ojimasam.safy.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
	public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI safyOpenAPI() {
        return new OpenAPI()
				.components(new Components().addSecuritySchemes(BEARER_AUTH,
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("로그인 후 발급받은 Access Token을 입력하세요.")))
                .info(new Info()
                        .title("SAFY API")
                        .description("SAFY backend API specification")
                        .version("v1"));
    }
}
