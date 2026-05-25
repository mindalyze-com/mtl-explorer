package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.web.services.info.VersionInfoDto;
import lombok.Data;

@Data
@JsonPropertyOrder({
        "phase",
        "ready",
        "message",
        "dbPath",
        "sourceBuildDate",
        "sourceAttribution",
        "sourceUrl",
        "sourceLicense",
        "sourceLicenseUrl",
        "sqliteVersion",
        "rowCount",
        "populatedPlaceCount",
        "terrainCount",
        "versionInfo"
})
public class LocationSearchStatusDto {

    private String phase;

    private boolean ready;

    private String message;

    @JsonProperty("db_path")
    private String dbPath;

    @JsonProperty("source_build_date")
    private String sourceBuildDate;

    @JsonProperty("source_attribution")
    private String sourceAttribution;

    @JsonProperty("source_url")
    private String sourceUrl;

    @JsonProperty("source_license")
    private String sourceLicense;

    @JsonProperty("source_license_url")
    private String sourceLicenseUrl;

    @JsonProperty("sqlite_version")
    private String sqliteVersion;

    @JsonProperty("row_count")
    private Long rowCount;

    @JsonProperty("populated_place_count")
    private Long populatedPlaceCount;

    @JsonProperty("terrain_count")
    private Long terrainCount;

    private VersionInfoDto versionInfo;

    public static LocationSearchStatusDto unavailable(String phase, String message) {
        LocationSearchStatusDto dto = new LocationSearchStatusDto();
        dto.setPhase(phase);
        dto.setReady(false);
        dto.setMessage(message);
        return dto;
    }
}
