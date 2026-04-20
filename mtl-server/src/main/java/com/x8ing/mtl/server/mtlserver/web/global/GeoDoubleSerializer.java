package com.x8ing.mtl.server.mtlserver.web.global;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Rounds geographic coordinate doubles to {@link LineStringSerializer#DECIMAL_PLACES} decimal places
 * before serialization, avoiding binary floating-point noise.
 */
public class GeoDoubleSerializer extends JsonSerializer<Double> {

    @Override
    public void serialize(Double value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeNumber(LineStringSerializer.roundToDecimalPlaces(value, LineStringSerializer.DECIMAL_PLACES));
    }
}
