package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.StoredPoint;

public final class StoredRepresentationMapper {

    private StoredRepresentationMapper() {
    }

    public static StoredPoint.Representation from(DataTypePolicy.Mode mode) {
        return switch (mode) {
            case RAW -> StoredPoint.Representation.RAW;
            case RECONCILED_INTERVAL -> StoredPoint.Representation.RECONCILED_INTERVAL;
            case DAILY_ROLLUP -> StoredPoint.Representation.DAILY_ROLLUP;
            case HEART_RATE_SAMPLE -> throw new IllegalArgumentException("Heart rate samples use compact storage");
        };
    }
}
