package com.x8ing.mtl.server.mtlserver.web.global;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiSchemaConfigTest {

    @Test
    void sortsUnorderedJtsPropertiesWithoutReorderingApplicationSchemas() {
        var geometryProperties = new LinkedHashMap<String, io.swagger.v3.oas.models.media.Schema>();
        geometryProperties.put("valid", new StringSchema());
        geometryProperties.put("envelope", new StringSchema());
        var applicationProperties = new LinkedHashMap<String, io.swagger.v3.oas.models.media.Schema>();
        applicationProperties.put("second", new StringSchema());
        applicationProperties.put("first", new StringSchema());

        var openApi = new OpenAPI().components(new Components()
                .addSchemas("Geometry", new ObjectSchema().properties(geometryProperties))
                .addSchemas("BuildInfoResponse", new ObjectSchema().properties(applicationProperties)));

        new OpenApiSchemaConfig().stableJtsSchemaPropertyOrder().customise(openApi);

        assertThat(openApi.getComponents().getSchemas().get("Geometry").getProperties().keySet())
                .containsExactly("envelope", "valid");
        assertThat(openApi.getComponents().getSchemas().get("BuildInfoResponse").getProperties().keySet())
                .containsExactly("second", "first");
    }
}
