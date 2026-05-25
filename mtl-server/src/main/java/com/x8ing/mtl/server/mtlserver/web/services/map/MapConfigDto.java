package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * DTO returned by GET /api/map/config — tells the frontend how to load map tiles.
 * <p>
 * tileMode "local"  → vector tiles served by the companion docker-maps container (PMTiles / PBF).
 * tileMode "remote" → raster tiles fetched from the public internet (e.g. OpenStreetMap).
 */
@Data
@JsonPropertyOrder({
        "tileMode",
        "tileBaseUrl",
        "tileArchiveUrl",
        "tilesetName",
        "lowzoomTilesetName",
        "lowzoomArchiveUrl",
        "tileSource",
        "archiveId",
        "remoteTileUrl",
        "initialBounds",
        "demoAreaBbox",
        "demoAreaMaxZoom",
        "plannerEnabled",
        "plannerProfiles"
})
public class MapConfigDto {

    /**
     * "local" or "remote"
     */
    @Schema(allowableValues = {MapProxyConstants.TILE_MODE_LOCAL, MapProxyConstants.TILE_MODE_REMOTE})
    private String tileMode;

    /**
     * Base URL for the local vector tile server, e.g. "http://localhost:18081"
     */
    private String tileBaseUrl;

    /**
     * Complete PMTiles URL for the main archive, including stable cache-identity
     * query parameters. Prefer this over composing {@link #tileBaseUrl} and
     * {@link #tilesetName} on the client.
     */
    private String tileArchiveUrl;

    /**
     * Name of the main PMTiles tileset (without .pmtiles), e.g. "planet"
     */
    private String tilesetName;

    /**
     * Name of the low-zoom PMTiles tileset for client-side caching, e.g. "world-lowzoom"
     */
    private String lowzoomTilesetName;

    /**
     * Complete PMTiles URL for the low-zoom archive, including stable
     * cache-identity query parameters.
     */
    private String lowzoomArchiveUrl;

    /**
     * The upstream chosen for the PMTiles archive URLs: "local" or "public".
     */
    @Schema(allowableValues = {MapProxyConstants.SOURCE_LOCAL, MapProxyConstants.SOURCE_PUBLIC})
    private String tileSource;

    /**
     * Stable cache identity for the PMTiles byte layout.
     */
    private String archiveId;

    /**
     * Raster tile URL template used when tileMode is "remote"
     */
    private String remoteTileUrl;

    /**
     * Initial map bounds. Configured explicitly, derived from stored track
     * bounding boxes, or set to the default startup area when no tracks exist.
     */
    private MapBoundsDto initialBounds;

    /**
     * Legacy bounded-map metadata. Normally null because demo deployments use
     * the hosted PMTiles service.
     */
    private List<Double> demoAreaBbox;

    /**
     * Legacy bounded-map metadata. Normally null.
     */
    private Integer demoAreaMaxZoom;

    /**
     * True when the route-planner feature is enabled server-side
     * ({@code mtl.planner.enabled}). The client uses this to show/hide planner UI.
     */
    private boolean plannerEnabled;

    /**
     * Available routing profiles when {@link #plannerEnabled} is true. Empty otherwise.
     */
    private List<String> plannerProfiles;
}
