package com.x8ing.mtl.server.mtlserver.web.global;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Rounds geographic coordinate doubles to {@link LineStringSerializer#DECIMAL_PLACES} decimal places
 * before serialization, avoiding binary floating-point noise.
 */
public class GeoDoubleSerializer extends ValueSerializer<Double> {

    @Override
    public void serialize(Double value, JsonGenerator jsonGenerator, SerializationContext context) throws JacksonException {
        jsonGenerator.writeNumber(LineStringSerializer.roundToDecimalPlaces(value, LineStringSerializer.DECIMAL_PLACES));
    }
}
