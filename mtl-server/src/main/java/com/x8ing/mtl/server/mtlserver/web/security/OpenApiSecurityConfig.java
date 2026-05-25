package com.x8ing.mtl.server.mtlserver.web.security;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(security = @SecurityRequirement(name = OpenApiSecurityConfig.BEARER_AUTH_SCHEME))
@SecurityScheme(
        name = OpenApiSecurityConfig.BEARER_AUTH_SCHEME,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiSecurityConfig {

    public static final String BEARER_AUTH_SCHEME = "bearerAuth";
}
