/**
 * Central constants for the planner feature. No magic numbers may appear anywhere
 * else in `src/planner/` — if you need a new threshold, add it here with a
 * meaningful name and comment.
 */

/** Minimum interval between successive /route calls during interactive dragging. */
export const ROUTE_DEBOUNCE_MS = 120;

/** Max stack depth for undo/redo. Keeps memory bounded under heavy editing. */
export const MAX_UNDO_STACK = 50;

/** Fallback waypoint cap until the server planner config has loaded. */
export const MAX_WAYPOINTS_FALLBACK = 150;

/** Fallback profile when the server has not disclosed one. */
export const PROFILE_DEFAULT = 'trekking';

/** Base URL path for planner HTTP endpoints (relative to VITE_BACKEND_URL). */
export const PLANNER_API_BASE = 'api/planner';

/** Map source/layer identifiers for the planner route line. */
export const PLANNER_SOURCE_ID = 'mtl-planner-route-src';
export const PLANNER_LAYER_ID = 'mtl-planner-route-layer';
export const PLANNER_ROUTE_HIT_LAYER_ID = 'mtl-planner-route-hit-layer';
export const PLANNER_WAYPOINT_SOURCE_ID = 'mtl-planner-waypoints-src';
export const PLANNER_WAYPOINT_DRAG_RING_LAYER_ID = 'mtl-planner-waypoints-drag-ring-layer';
export const PLANNER_WAYPOINT_LAYER_ID = 'mtl-planner-waypoints-layer';
export const PLANNER_WAYPOINT_HIT_LAYER_ID = 'mtl-planner-waypoints-hit-layer';

/** Colours for the route line + waypoints (keep in sync with CSS theme). */
export const ROUTE_LINE_COLOR = '#ff5722';
export const ROUTE_LINE_WIDTH_PX = 5;
export const ROUTE_HIT_LINE_WIDTH_PX = 20;
export const ROUTE_MOUSE_INSERT_RADIUS_PX = 8;
export const ROUTE_TOUCH_INSERT_RADIUS_PX = 12;
export const WAYPOINT_RADIUS_PX = 8;
export const WAYPOINT_FILL_COLOR = '#ffffff';
export const WAYPOINT_STROKE_COLOR = '#ff5722';
export const WAYPOINT_STROKE_WIDTH_PX = 3;
export const WAYPOINT_DRAG_RING_COLOR = 'rgba(255, 87, 34, 0.12)';
export const WAYPOINT_ACTIVE_RING_RADIUS_PX = 15;
export const WAYPOINT_DRAGGING_RING_RADIUS_PX = 18;
export const WAYPOINT_MOUSE_HIT_RADIUS_PX = 12;
export const WAYPOINT_TOUCH_HIT_RADIUS_PX = 24;
export const WAYPOINT_MOUSE_DRAG_THRESHOLD_PX = 3;
export const WAYPOINT_TOUCH_DRAG_THRESHOLD_PX = 6;
export const MAP_CLICK_SUPPRESSION_MS = 350;
export const TOUCH_SYNTHETIC_CLICK_SUPPRESSION_MS = 1_200;
export const SYNTHETIC_CLICK_SUPPRESSION_RADIUS_PX = 24;

/** Status-poll interval for the BRouter sidecar status endpoint. */
export const SIDECAR_STATUS_POLL_MS = 5_000;

/** Minimum elevation delta to count as ascent/descent when deriving stats from saved planner geometry. */
export const MIN_ELEVATION_DELTA_M = 2.0;

// ── Viewport-aware segment pre-download ─────────────────────────

/** If the visible map span exceeds this, planning is blocked (user must zoom in). */
export const MAX_PLANNING_SPAN_KM = 300;

/** Expand visible span by this factor to get the download area side length. */
export const PREWARM_MULTIPLIER = 3;

/** Floor: even when zoomed in very far, download at least this many km per side. */
export const MIN_PREWARM_KM = 20;

/** Ceiling: never request a download area larger than this per side. */
export const MAX_PREWARM_KM = 300;

/** Debounce after map moveend before triggering a viewport prewarm POST. */
export const VIEWPORT_PREWARM_DEBOUNCE_MS = 1_500;
