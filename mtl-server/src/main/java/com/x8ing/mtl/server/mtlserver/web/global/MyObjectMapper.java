package com.x8ing.mtl.server.mtlserver.web.global;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;

@Configuration
@Slf4j
@JsonPropertyOrder({
        "builder"
})
public class MyObjectMapper {

    @Bean
    public JsonMapperBuilderCustomizer mtlJsonMapperCustomizer() {
        return builder -> {
            log.info("Prepare custom JSON mapper.");
            SimpleModule geometryModule = new SimpleModule("MTL JTS geometry");
            geometryModule.addSerializer(Geometry.class, new JtsGeometrySerializer());
            builder.changeDefaultPropertyInclusion(ignored -> JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL,
                    JsonInclude.Include.NON_NULL));
            builder.addModule(geometryModule);
        };
    }
}
