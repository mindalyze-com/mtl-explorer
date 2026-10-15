package com.x8ing.mtl.server.mtlserver.gpx;

import java.util.Map;

public record GpxTrackReplacementEvent(Map<Long, Long> replacementTrackIds) {
    public GpxTrackReplacementEvent {
        replacementTrackIds = Map.copyOf(replacementTrackIds);
    }
}
