package de.tudarmstadt.campus.admin.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI campusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TU Darmstadt 3D Campus Explorer — Administration API")
                        .version("0.0.1-SNAPSHOT")
                        .description("""
                                Role based administration infrastructure for the 3D Campus Explorer.

                                Authorization is enforced through permission authorities (for example
                                POI_PUBLISH), never through role names. Roles are fixed bundles of
                                permissions seeded at startup.

                                Not implemented on purpose: creating, changing or deleting roles
                                (POST/PUT/DELETE on /api/roles). The ROLE_MANAGE permission and the
                                role_permission relation already exist, so this remains a planned
                                extension rather than a redesign."""))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
