package com.x8ing.mtl.server.mtlserver.web.global;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
@Slf4j
public class MyObjectMapper {


    private final Jackson2ObjectMapperBuilder builder;

    public MyObjectMapper(Jackson2ObjectMapperBuilder builder) {
        this.builder = builder;
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        log.info("Prepare custom object mapper.");

        // important: use builder to keep springs useful defaults and hooks

        // do not serialize null values
        builder.serializationInclusion(JsonInclude.Include.NON_NULL)
                .modules(new JtsModule());

        return builder.createXmlMapper(false).build();

    }
}
