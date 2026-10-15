package com.x8ing.mtl.server.mtlserver.web.global;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Suppresses unsupported response measurements without changing the entity or its stored evidence.
 */
public final class RecordedMetricSerializer extends ValueSerializer<Object> {
    @Override
    public void serialize(Object value, JsonGenerator generator, SerializationContext context) {
        Object owner = generator.currentValue();
        if ((owner instanceof GpsTrack track && track.isTimingUnreliable())
            || (owner instanceof GpsTrackDataPoint point && point.isTimingUnsupportedForResponse())) generator.writeNull();
        else context.writeValue(generator, value);
    }
}
