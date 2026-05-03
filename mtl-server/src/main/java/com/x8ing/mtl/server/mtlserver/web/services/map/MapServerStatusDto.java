package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Mirrors the JSON written by the map-server Python orchestrator
 * at /tmp/map-status.json (served via nginx GET /status).
 */
@Data
public class MapServerStatusDto {

    private String phase;

    private boolean ready;

    @JsonProperty("download_pct")
    private int downloadPct;

    @JsonProperty("download_bytes")
    private long downloadBytes;

    @JsonProperty("download_total")
    private long downloadTotal;

    private String message;

    /**
     * The upstream currently backing PMTiles requests: "local" or "public".
     */
    @Schema(allowableValues = {MapProxyConstants.SOURCE_LOCAL, MapProxyConstants.SOURCE_PUBLIC})
    private String tileSource;

    /**
     * Stable cache identity of the PMTiles archive currently being served.
     */
    @JsonProperty("archive_id")
    private String archiveId;

    /**
     * Factory for the fallback status when the map-server is unreachable.
     */
    public static MapServerStatusDto unreachable() {
        MapServerStatusDto dto = new MapServerStatusDto();
        dto.setPhase("unreachable");
        dto.setReady(false);
        dto.setMessage("Map server is not reachable");
        dto.setTileSource(MapProxyConstants.SOURCE_LOCAL);
        return dto;
    }

    public static MapServerStatusDto publicFallback(String archiveId) {
        MapServerStatusDto dto = new MapServerStatusDto();
        dto.setPhase("public-fallback");
        dto.setReady(true);
        dto.setDownloadPct(100);
        dto.setMessage("Using hosted map service");
        dto.setTileSource(MapProxyConstants.SOURCE_PUBLIC);
        dto.setArchiveId(archiveId);
        return dto;
    }
}
