package de.omarfourati.belegfluss.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adds the "Authorize" button to Swagger UI for the JWT from /api/auth/login. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI belegflussOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Belegfluss API")
                        .version("0.2.0")
                        .description("""
                                AI-powered invoice inbox. Log in via `POST /api/auth/login`, then click **Authorize** \
                                and paste the `accessToken`.

                                Read-only demo account: `demo@belegfluss.app` / `demo-belegfluss`"""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
