package com.x8ing.mtl.server.mtlserver.planner.constants;

/**
 * Central constants for the planner feature (server side). No magic numbers may
 * appear anywhere in {@code com.x8ing.mtl.server.mtlserver.planner.*} — if you
 * need a new threshold, add it here with a meaningful name and comment.
 */
public final class PlannerConstants {

    private PlannerConstants() {
    }

    /**
     * Base path under which all planner endpoints are mounted.
     */
    public static final String PLANNER_API_BASE = "/api/planner";

    /**
     * Max waypoints accepted in a single /route request — protects against accidental huge payloads.
     */
    public static final int MAX_WAYPOINTS = 150;

    public static final int MAX_POINTS_OF_INTEREST = 1000;
    public static final int MAX_POINT_OF_INTEREST_NAME_LENGTH = 255;
    public static final int MAX_POINT_OF_INTEREST_NOTES_LENGTH = 4000;

    /**
     * Below this distance (metres) two adjacent waypoints are considered the same point.
     */
    public static final double MIN_WAYPOINT_DIST_M = 5.0;

    /**
     * Decimal places used when snapping lat/lng for cache-key hashing (≈11 cm at the equator).
     */
    public static final int COORDINATE_SNAP_DECIMALS = 6;

    /**
     * Max number of cached leg-results kept in memory (LRU eviction).
     */
    public static final int SEGMENT_CACHE_MAX_SIZE = 1000;

    /**
     * TTL for cached leg-results, in minutes.
     */
    public static final int SEGMENT_CACHE_TTL_MIN = 60;

    /**
     * BRouter's fallback buffer when elevation-source resolution is unknown.
     * GeoJSON does not expose the per-point resolution needed to select its 5 m variant.
     */
    public static final double ELEVATION_FILTER_BUFFER_M = 10.0;

    /**
     * Default routing profile if the client does not specify one.
     */
    public static final String DEFAULT_PROFILE = "trekking";

    /**
     * Name used on planner-saved tracks when the user does not provide one.
     */
    public static final String DEFAULT_PLANNED_TRACK_NAME = "Planned route";

    /**
     * BRouter endpoint path (relative to base URL).
     */
    public static final String BROUTER_ROUTE_PATH = "/brouter";

    /**
     * BRouter response format. "geojson" is richer than "gpx" — exposes cost, total-time, energy.
     */
    public static final String BROUTER_FORMAT = "geojson";

    /**
     * Size (degrees) of BRouter's rd5 segment tiles.
     */
    public static final int BROUTER_SEGMENT_DEGREES = 5;
}
