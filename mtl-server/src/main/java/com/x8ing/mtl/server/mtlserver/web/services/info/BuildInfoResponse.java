package com.x8ing.mtl.server.mtlserver.web.services.info;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.measurement.MeasurementSystem;


@JsonPropertyOrder({
        "version",
        "buildTime",
        "defaultLocale",
        "defaultMeasurementSystem",
        "defaultGpsTrackFilterName",
        "serverId",
        "image"
})
public record BuildInfoResponse(String version,
                                String buildTime,
                                String defaultLocale,
                                MeasurementSystem defaultMeasurementSystem,
                                String defaultGpsTrackFilterName,
                                String serverId,
                                ImageVersionInfoDto image) {
}
