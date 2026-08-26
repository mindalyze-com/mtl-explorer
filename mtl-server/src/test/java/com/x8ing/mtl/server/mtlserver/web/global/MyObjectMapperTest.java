package com.x8ing.mtl.server.mtlserver.web.global;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MyObjectMapperTest {

    @Test
    void serializesInstantAsUtcIsoString() throws Exception {
        JsonMapper.Builder builder = JsonMapper.builder();
        new DateTimeConfig().jsonCustomizer().customize(builder);
        new MyObjectMapper().mtlJsonMapperCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();

        String json = mapper.writeValueAsString(Map.of(
                "timestamp", Instant.parse("2026-05-18T06:10:11Z")));

        assertThat(json).isEqualTo("{\"timestamp\":\"2026-05-18T06:10:11Z\"}");
    }

    @Test
    void serializesJtsPointAsGeoJson() throws Exception {
        JsonMapper.Builder builder = JsonMapper.builder();
        new MyObjectMapper().mtlJsonMapperCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();

        String json = mapper.writeValueAsString(
                new GeometryFactory().createPoint(new Coordinate(8.5, 47.5, 430.0)));

        assertThat(json).isEqualTo("{\"type\":\"Point\",\"coordinates\":[8.5,47.5,430.0]}");
    }
}
