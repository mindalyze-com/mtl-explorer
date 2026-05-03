package com.x8ing.mtl.server.mtlserver.web.services.map;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mtl.map-server")
public class MapServerProperties {

    /**
     * Full URL to the map-server status endpoint, e.g. http://map-server:8081/status
     */
    private String statusUrl = "http://map-server:8081/status";

    /**
     * "local" = vector tiles from the companion docker-maps container (default).
     * "remote" = raster tiles from the public internet (e.g. OpenStreetMap).
     */
    private String tileMode = "local";

    /**
     * Docker-internal upstream URL of the map-server container.
     * Used only server-side by the tile proxy — never returned to the client.
     */
    private String tileUpstreamUrl = "http://map-server:8081";

    /**
     * Hosted PMTiles upstream used when the local docker-maps sidecar is absent.
     * It is intended only for MTL Explorer traffic.
     */
    private String publicUpstreamUrl = "https://mtl-maps-public-prod.mindalyze.com";

    /**
     * Optional health URL for detecting the local docker-maps sidecar.
     * If blank, the server probes {@link #tileUpstreamUrl} + "/health".
     */
    private String localProbeUrl;

    /**
     * How often the background probe may re-check the local sidecar while maps
     * were requested recently.
     */
    private int localProbeIntervalSeconds = 20;

    /**
     * The scheduler probes only when a tile request happened within this window.
     */
    private int localProbeActiveWindowSeconds = 120;

    /**
     * Connection and read timeout for the local sidecar probe.
     */
    private int localProbeTimeoutMs = 800;

    /**
     * TTL for on-demand upstream decisions. The scheduled probe may refresh this
     * earlier while the map is actively used.
     */
    private int upstreamDecisionCacheTtlSeconds = 60;

    /**
     * Cache identity for the public PMTiles archive. Change this whenever the
     * public archive byte layout changes.
     */
    private String publicArchiveId = "public-default";

    /**
     * Fallback cache identity for local PMTiles when the sidecar status does not
     * expose the active archive id yet.
     */
    private String localArchiveId = "local-default";

    /**
     * Public-facing base URL of the tile proxy, returned to the client via /api/map/config.
     * Should be the path (relative to origin) that the browser uses to fetch PMTiles files.
     * Only relevant when tileMode = "local".
     */
    private String tileBaseUrl = "/mtl/api/map-proxy";

    /**
     * Name of the main PMTiles tileset file (without .pmtiles extension).
     */
    private String tilesetName = "planet";

    /**
     * Name of the low-zoom PMTiles tileset for client-side offline caching.
     */
    private String lowzoomTilesetName = "world-lowzoom";

    /**
     * Raster tile URL template for remote mode. Must contain {z}, {x}, {y} placeholders.
     */
    private String remoteTileUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

    /**
     * Initial map center longitude (MapLibre [lng, lat] order).
     * Overridable per environment — e.g. set to Porto in demo mode.
     */
    private double initialCenterLng = 8.505778;  // default: Glattfelden

    /**
     * Initial map center latitude.
     */
    private double initialCenterLat = 47.5605;   // default: Glattfelden

    /**
     * Initial map zoom level.
     */
    private int initialZoom = 10;

    /**
     * Connect timeout (ms) for the tile proxy RestClient.
     */
    private int proxyConnectTimeoutMs = 8000;

    /**
     * Read timeout (ms) for the tile proxy RestClient.
     * PMTiles range responses for large tiles can be a few hundred KB.
     */
    private int proxyReadTimeoutMs = 15000;

    /**
     * Absolute wall-clock timeout (ms) for one tile proxy request.
     * Protects request threads from upstreams that keep trickling bytes.
     */
    private int proxyCallTimeoutMs = 30000;

    /**
     * Maximum number of idle connections kept in the OkHttp connection pool.
     * PMTiles fires many parallel Range requests per render — size accordingly.
     */
    private int proxyMaxIdleConnections = 20;

    /**
     * How long idle connections are kept alive in the pool (seconds).
     */
    private int proxyKeepAliveDurationSeconds = 60;

    /**
     * Legacy comma-separated bounding box for bounded local maps:
     * "west,south,east,north". Normally unset.
     */
    private String demoAreaBbox;

    /**
     * Legacy maximum zoom level for bounded local maps. Normally unset.
     */
    private Integer demoAreaMaxZoom;
}
