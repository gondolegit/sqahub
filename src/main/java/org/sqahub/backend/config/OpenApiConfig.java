package org.sqahub.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Dokumentasi API interaktif (Swagger UI) di /swagger-ui.html, spesifikasi mentah di /v3/api-docs.
 * Tombol "Authorize" di Swagger UI memakai skema "bearerAuth" - tempel JWT (tanpa prefix "Bearer ",
 * sudah ditambahkan otomatis) hasil dari POST /api/v1/auth/authenticate.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "SQAHUB API",
                version = "v1",
                description = "API untuk platform manajemen pengujian (test management) SQAHUB: "
                        + "Project, Feature, Test Case, Test Suite Run, Test Evidence, API Key, dan Activity Log.",
                contact = @Contact(name = "SQAHUB")
        ),
        security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
