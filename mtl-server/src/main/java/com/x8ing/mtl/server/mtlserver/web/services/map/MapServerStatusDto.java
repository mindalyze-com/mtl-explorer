package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonProperty;
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
     * Factory for the fallback status when the map-server is unreachable.
     */
    public static MapServerStatusDto unreachable() {
        MapServerStatusDto dto = new MapServerStatusDto();
        dto.setPhase("unreachable");
        dto.setReady(false);
        dto.setMessage("Map server is not reachable");
        return dto;
    }
}
