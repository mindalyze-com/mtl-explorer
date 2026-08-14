package com.x8ing.mtl.server.mtlserver.web.global;

import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps schemas reflected from external JTS classes stable between JVM runs.
 */
@Configuration
public class OpenApiSchemaConfig {

    private static final Map<String, List<String>> JTS_SCHEMA_PROPERTY_ORDER = Map.of(
            "CoordinateSequence", List.of("dimension", "measures"),
            "Envelope", List.of("height", "width", "minY", "maxY", "minX", "maxX", "area", "diameter", "null"),
            "Geometry", List.of("envelope", "factory", "userData", "dimension", "boundary", "boundaryDimension",
                    "geometryType", "simple", "numPoints", "srid", "coordinates", "envelopeInternal", "centroid",
                    "coordinate", "numGeometries", "precisionModel", "rectangle", "area", "interiorPoint", "length",
                    "empty", "valid"),
            "LineString", List.of("envelope", "factory", "userData", "dimension", "boundary", "boundaryDimension",
                    "geometryType", "coordinateSequence", "closed", "numPoints", "coordinates", "coordinate", "ring",
                    "startPoint", "endPoint", "length", "empty", "simple", "srid", "envelopeInternal", "centroid",
                    "numGeometries", "precisionModel", "rectangle", "area", "interiorPoint", "valid"),
            "Point", List.of("envelope", "factory", "userData", "coordinates", "dimension", "boundary",
                    "boundaryDimension", "geometryType", "coordinateSequence", "x", "y", "simple", "numPoints",
                    "coordinate", "empty", "srid", "envelopeInternal", "centroid", "numGeometries", "precisionModel",
                    "rectangle", "area", "interiorPoint", "length", "valid")
    );

    @Bean
    OpenApiCustomizer stableJtsSchemaPropertyOrder() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }

            JTS_SCHEMA_PROPERTY_ORDER.forEach((schemaName, propertyOrder) -> {
                Schema<?> schema = openApi.getComponents().getSchemas().get(schemaName);
                if (schema != null && schema.getProperties() != null) {
                    schema.setProperties(stableProperties(schema.getProperties(), propertyOrder));
                }
            });
        };
    }

    private static Map<String, Schema> stableProperties(Map<String, Schema> properties, List<String> propertyOrder) {
        Map<String, Schema> ordered = new LinkedHashMap<>();
        propertyOrder.forEach(name -> {
            if (properties.containsKey(name)) {
                ordered.put(name, properties.get(name));
            }
        });
        properties.entrySet().stream()
                .filter(entry -> !ordered.containsKey(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return ordered;
    }
}
