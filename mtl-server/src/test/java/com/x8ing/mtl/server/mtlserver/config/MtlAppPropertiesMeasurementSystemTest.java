package com.x8ing.mtl.server.mtlserver.config;

import com.x8ing.mtl.server.mtlserver.measurement.MeasurementSystem;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MtlAppPropertiesMeasurementSystemTest {

    @Test
    void bindsTheTypedDefaultMeasurementSystem() {
        MtlAppProperties properties = bind("US_CUSTOMARY");

        assertThat(properties.getDefaultMeasurementSystem()).isEqualTo(MeasurementSystem.US_CUSTOMARY);
    }

    @Test
    void rejectsAnInvalidDefaultMeasurementSystem() {
        assertThatThrownBy(() -> bind("IMPERIAL"))
                .isInstanceOf(BindException.class);
    }

    private MtlAppProperties bind(String value) {
        var source = new MapConfigurationPropertySource(Map.of("mtl.default-measurement-system", value));
        return new Binder(source).bind("mtl", Bindable.of(MtlAppProperties.class))
                .orElseThrow(IllegalStateException::new);
    }
}
