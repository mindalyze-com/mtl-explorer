package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.simplified;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import org.locationtech.jts.geom.LineString;

import java.util.Objects;


/**
 * Somehow, real projection does not work
 */
@Data
@Entity
public class SimplifiedTrack {

    /**
     * HACK: Fake primary key, to avoid strange caching effects.
     * Overwrite equals() seems not good enough
     */
    @Id
    String fakeId;

    Long gpsTrackId;

    Long gpsTrackDataId;

    String trackType;

    LineString lineString;

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fakeId);
    }
}
