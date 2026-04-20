package com.x8ing.mtl.server.mtlserver.web.services.map;

import lombok.Data;

import java.util.List;

/**
 * DTO returned by GET /api/map/config — tells the frontend how to load map tiles.
 * <p>
 * tileMode "local"  → vector tiles served by the companion docker-maps container (PMTiles / PBF).
 * tileMode "remote" → raster tiles fetched from the public internet (e.g. OpenStreetMap).
 */
@Data
public class MapConfigDto {

    /**
     * "local" or "remote"
     */
    private String tileMode;

    /**
     * Base URL for the local vector tile server, e.g. "http://localhost:18081"
     */
    private String tileBaseUrl;

    /**
     * Name of the main PMTiles tileset (without .pmtiles), e.g. "planet"
     */
    private String tilesetName;

    /**
     * Name of the low-zoom PMTiles tileset for client-side caching, e.g. "world-lowzoom"
     */
    private String lowzoomTilesetName;

    /**
     * Raster tile URL template used when tileMode is "remote"
     */
    private String remoteTileUrl;

    /**
     * Initial map center longitude [lng, lat] — MapLibre order.
     */
    private double initialCenterLng;

    /**
     * Initial map center latitude.
     */
    private double initialCenterLat;

    /**
     * Initial map zoom level.
     */
    private int initialZoom;

    /**
     * Bounding box of the demo tile area: [west, south, east, north].
     * Null in production mode (full planet tiles available).
     */
    private List<Double> demoAreaBbox;

    /**
     * Maximum zoom level available in the demo tiles.
     * Null in production mode.
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
