package com.x8ing.mtl.server.mtlserver.web.services.info;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder({
        "version",
        "buildTime",
        "defaultLocale",
        "defaultGpsTrackFilterName",
        "serverId",
        "image"
})
public record BuildInfoResponse(String version,
                                String buildTime,
                                String defaultLocale,
                                String defaultGpsTrackFilterName,
                                String serverId,
                                ImageVersionInfoDto image) {
}
