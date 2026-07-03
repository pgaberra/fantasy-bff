package com.fantasy.bff.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiCustomizer customizer = new OpenApiConfig().stripNullTypeFromSchemas();

    @Test
    void restoresObjectTypeWhenAnObjectSchemaIsNulled() {
        Schema<?> draftState = new Schema<>();
        draftState.setTypes(new LinkedHashSet<>(List.of("null")));
        draftState.addProperty("teams", new Schema<>().type("array"));
        OpenAPI api = new OpenAPI().components(new Components().addSchemas("DraftState", draftState));

        customizer.customise(api);

        assertThat(api.getComponents().getSchemas().get("DraftState").getType()).isEqualTo("object");
    }

    @Test
    void collapsesANullablePrimitiveBackToItsBaseType() {
        Schema<?> leagueSize = new Schema<>();
        leagueSize.setTypes(new LinkedHashSet<>(List.of("integer", "null")));
        Schema<?> settings = new Schema<>().type("object");
        settings.addProperty("leagueSize", leagueSize);
        OpenAPI api = new OpenAPI().components(new Components().addSchemas("ProjectionSettings", settings));

        customizer.customise(api);

        Schema<?> fixedSettings = api.getComponents().getSchemas().get("ProjectionSettings");
        Schema<?> fixed = fixedSettings.getProperties().get("leagueSize");
        assertThat(fixed.getType()).isEqualTo("integer");
        assertThat(fixed.getTypes()).doesNotContain("null");
    }
}
