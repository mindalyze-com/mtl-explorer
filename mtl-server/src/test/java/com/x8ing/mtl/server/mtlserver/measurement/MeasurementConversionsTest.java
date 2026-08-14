package com.x8ing.mtl.server.mtlserver.measurement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeasurementConversionsTest {

    @Test
    void exposesTheCanonicalExactConversionConstants() {
        assertThat(MeasurementConversions.METERS_PER_KILOMETER).isEqualTo(1000.0);
        assertThat(MeasurementConversions.METERS_PER_MILE).isEqualTo(1609.344);
        assertThat(MeasurementConversions.METERS_PER_FOOT).isEqualTo(0.3048);
        assertThat(MeasurementConversions.KILOMETERS_PER_MILE).isEqualTo(1.609344);
        assertThat(MeasurementConversions.KILOGRAMS_PER_POUND).isEqualTo(0.45359237);
    }
}
