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
     * Comma-separated bounding box for the demo tile area: "west,south,east,north".
     * Only set in demo mode (application-demo.yml). Null in production.
     */
    private String demoAreaBbox;

    /**
     * Maximum zoom level available in the demo tiles.
     * Only set in demo mode. Null in production.
     */
    private Integer demoAreaMaxZoom;
}
