package com.neha.StreamHub.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "StreamHub API",
                description = "API documentation for StreamHub. This application allows users to stream videos, manage their profiles, and provide feedback.",
                contact = @Contact(
                        name = "Neha Kaneriya",
                        email = "nehakaneriya29@gmail.com"
                ),
                version = "1.0"
        ),
        security = {
                @SecurityRequirement(name = "bearerAuth")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT token-based authentication. Include the token in the Authorization header as: Bearer {token}"
)
public class APIDocConfig {

}
