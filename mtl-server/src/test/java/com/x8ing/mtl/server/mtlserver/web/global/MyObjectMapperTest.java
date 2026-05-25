package com.x8ing.mtl.server.mtlserver.web.global;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MyObjectMapperTest {

    @Test
    void serializesInstantAsUtcIsoString() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new DateTimeConfig().jsonCustomizer().customize(builder);
        ObjectMapper mapper = new MyObjectMapper(builder).objectMapper();

        String json = mapper.writeValueAsString(Map.of(
                "timestamp", Instant.parse("2026-05-18T06:10:11Z")));

        assertThat(json).isEqualTo("{\"timestamp\":\"2026-05-18T06:10:11Z\"}");
    }
}
