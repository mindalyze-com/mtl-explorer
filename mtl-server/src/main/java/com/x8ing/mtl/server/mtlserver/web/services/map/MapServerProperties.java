package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "mtl.map-server")
@JsonPropertyOrder({
        "statusUrl",
        "tileMode",
        "tileUpstreamUrl",
        "publicUpstreamUrl",
        "localProbeUrl",
        "localProbeIntervalSeconds",
        "localProbeActiveWindowSeconds",
        "localProbeTimeoutMs",
        "upstreamDecisionCacheTtlSeconds",
        "publicArchiveId",
        "localArchiveId",
        "tileBaseUrl",
        "tilesetName",
        "lowzoomTilesetName",
        "remoteRasterStyles",
        "initialBounds",
        "proxyConnectTimeoutMs",
        "proxyReadTimeoutMs",
        "proxyCallTimeoutMs",
        "proxyMaxIdleConnections",
        "proxyKeepAliveDurationSeconds",
        "demoAreaBbox",
        "demoAreaMaxZoom"
})
public class MapServerProperties {

    public static final String REMOTE_RASTER_STYLE_LIGHT = "light";
    public static final String REMOTE_RASTER_STYLE_LIGHT_TOPO = "light-topo";
    public static final String REMOTE_RASTER_STYLE_DARK = "dark";

    private static final String OSM_ATTRIBUTION =
            "© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors";
    private static final String OPENTOPOMAP_ATTRIBUTION =
            "Map data: © <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors, SRTM | " +
                    "Map style: © <a href=\"https://opentopomap.org\">OpenTopoMap</a> " +
                    "(<a href=\"https://creativecommons.org/licenses/by-sa/3.0/\">CC-BY-SA</a>)";
    private static final String CARTO_ATTRIBUTION =
            "© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors " +
                    "© <a href=\"https://carto.com/attributions\">CARTO</a>";

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
     * Remote raster map styles exposed to the browser when {@link #tileMode} is
     * {@code remote}, or when local vector tiles are temporarily unavailable.
     *
     * <p>Each entry key is a frontend map theme id. The app currently expects
     * three provider-backed entries:
     * <ul>
     *   <li>{@code light}: regular remote raster map, defaulting to OSM Standard.</li>
     *   <li>{@code light-topo}: topographic remote raster map, defaulting to OpenTopoMap.</li>
     *   <li>{@code dark}: dark remote raster map, defaulting to CARTO Dark Matter.</li>
     * </ul>
     *
     * <p>The {@code grayscale} UI theme intentionally derives from {@code light}
     * and applies a client-side grayscale raster paint, so it does not need a
     * fourth provider URL.
     *
     * <p>To use another provider, override both {@code url} and
     * {@code attribution} for the relevant style. For example, Simon's remote
     * raster setup can keep OSM Standard for {@code light}, use OpenTopoMap for
     * {@code light-topo}, and use CARTO Dark Matter for {@code dark}; each entry
     * must carry the attribution required by that provider. If a deployment
     * points a style at a custom provider but leaves the attribution wrong or
     * blank, that deployment owns the incorrect attribution.
     *
     * <p>URL templates must contain {@code {z}}, {@code {x}}, and {@code {y}}.
     */
    private Map<String, RemoteRasterStyleProperties> remoteRasterStyles = defaultRemoteRasterStyles();

    /**
     * Optional initial map bounds. When unset, the server derives the initial
     * bounds from stored track bounding boxes.
     */
    private MapBoundsDto initialBounds;

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

    @Data
    @JsonPropertyOrder({
            "url",
            "attribution"
    })
    public static class RemoteRasterStyleProperties {

        /**
         * Raster tile URL template. Must contain {@code {z}}, {@code {x}}, and {@code {y}}.
         */
        private String url;

        /**
         * Provider attribution shown by MapLibre for this raster source. Keep it
         * matched to {@link #url}; do not reuse OSM attribution for non-OSM tile providers.
         */
        private String attribution;
    }

    private static Map<String, RemoteRasterStyleProperties> defaultRemoteRasterStyles() {
        Map<String, RemoteRasterStyleProperties> styles = new LinkedHashMap<>();
        styles.put(
                REMOTE_RASTER_STYLE_LIGHT,
                remoteRasterStyle("https://tile.openstreetmap.org/{z}/{x}/{y}.png", OSM_ATTRIBUTION));
        styles.put(
                REMOTE_RASTER_STYLE_LIGHT_TOPO,
                remoteRasterStyle("https://tile.opentopomap.org/{z}/{x}/{y}.png", OPENTOPOMAP_ATTRIBUTION));
        styles.put(
                REMOTE_RASTER_STYLE_DARK,
                remoteRasterStyle("https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png", CARTO_ATTRIBUTION));
        return styles;
    }

    private static RemoteRasterStyleProperties remoteRasterStyle(String url, String attribution) {
        RemoteRasterStyleProperties style = new RemoteRasterStyleProperties();
        style.setUrl(url);
        style.setAttribution(attribution);
        return style;
    }
}
