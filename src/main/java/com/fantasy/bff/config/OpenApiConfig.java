package com.fantasy.bff.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Fantasy Hockey BFF API")
                        .version("1.0")
                        .description("Backend for Frontend aggregation layer for the Fantasy Hockey application"))
                // Pin the server URL to "/" (instead of springdoc's request-derived
                // default, which leaks the runtime port) so the generated spec is
                // deterministic — see OpenApiSpecSnapshotTest.
                .servers(List.of(new Server().url("/")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    // springdoc 3.0.3 represents a @jakarta.annotation.Nullable field that references a
    // schema by pushing "null" onto that schema's own type — corrupting object schemas
    // (DraftState / RosterSlots / YahooSync) into type: "null" and every nullable
    // primitive into [type, "null"]. The former breaks the generated web client. Strip the
    // spurious "null" back out so the contract stays as it was: absence is conveyed by the
    // field being optional, not by nulling the schema.
    @Bean
    public OpenApiCustomizer stripNullTypeFromSchemas() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components != null && components.getSchemas() != null) {
                components.getSchemas().values().forEach(this::stripNullType);
            }
        };
    }

    private void stripNullType(Schema<?> schema) {
        if (schema == null) {
            return;
        }
        Set<String> types = schema.getTypes();
        if (types != null && types.remove("null")) {
            if (types.isEmpty()) {
                types.add("object");
            }
            if (types.size() == 1) {
                schema.setType(types.iterator().next());
            }
        }
        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(this::stripNullType);
        }
        stripNullType(schema.getItems());
        if (schema.getAdditionalProperties() instanceof Schema<?> additional) {
            stripNullType(additional);
        }
        stripComposed(schema.getAllOf());
        stripComposed(schema.getAnyOf());
        stripComposed(schema.getOneOf());
    }

    private void stripComposed(List<Schema> schemas) {
        if (schemas != null) {
            schemas.forEach(this::stripNullType);
        }
    }
}
