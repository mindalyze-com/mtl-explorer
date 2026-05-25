package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder({
        "sortOrder",
        "rowKey",
        "trackId",
        "value"
})
public interface GpsTrackOverviewTrackRow {

    Integer getSortOrder();

    String getRowKey();

    Long getTrackId();

    Double getValue();
}
