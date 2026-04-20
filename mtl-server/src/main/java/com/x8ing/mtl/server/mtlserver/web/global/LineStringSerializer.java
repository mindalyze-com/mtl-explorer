package com.x8ing.mtl.server.mtlserver.web.global;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;
import org.locationtech.jts.geom.LineString;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * On the DB ST_SnapToGrid and ST_Simplify together do a nice job, yet it might produce rounding errors due to binary system.
 * Clean that out on serialization.
 */
public class LineStringSerializer extends JsonSerializer<LineString> {

    // https://en.wikipedia.org/wiki/Decimal_degrees
    // decimal
    //places   degrees          distance
    //-------  -------          --------
    // 0        1                111  km
    // 1        0.1              11.1 km
    // 2        0.01             1.11 km
    // 3        0.001            111  m
    // 4        0.0001           11.1 m
    // 5        0.00001          1.11 m
    // 6        0.000001         11.1 cm
    // 7        0.0000001        1.11 cm
    // 8        0.00000001       1.11 mm
    public static final int DECIMAL_PLACES = 6;      // 6 digits ~11.1 cm, used for lat/lng
    public static final int DECIMAL_PLACES_Z = 1;    // 1 digit  ~0.1 m,  elevation is never that precise

    @Override
    public void serialize(LineString lineString, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartArray();
        for (Coordinate coordinate : lineString.getCoordinates()) {
            if (Double.isNaN(coordinate.getX()) || Double.isNaN(coordinate.getY()) ||
                Double.isInfinite(coordinate.getX()) || Double.isInfinite(coordinate.getY())) {
                continue; // Skip completely corrupted coordinates rather than sending 0.0 to the frontend
            }

            jsonGenerator.writeStartArray();
            jsonGenerator.writeNumber(roundToDecimalPlaces(coordinate.getX(), DECIMAL_PLACES));
            jsonGenerator.writeNumber(roundToDecimalPlaces(coordinate.getY(), DECIMAL_PLACES));
            jsonGenerator.writeNumber(roundToDecimalPlaces(coordinate.getZ(), DECIMAL_PLACES_Z));

            if (coordinate instanceof CoordinateXYZM) {
                CoordinateXYZM coordinateXYZM = (CoordinateXYZM) coordinate;
                jsonGenerator.writeNumber(roundToDecimalPlaces(coordinateXYZM.getM(), DECIMAL_PLACES));
            }

            jsonGenerator.writeEndArray();
        }
        jsonGenerator.writeEndArray();
    }

    public static BigDecimal roundToDecimalPlaces(double value, int decimalPlaces) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO.setScale(decimalPlaces, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(value).setScale(decimalPlaces, RoundingMode.HALF_UP);
    }
}
