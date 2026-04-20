/**
 * Central constants for the planner feature. No magic numbers may appear anywhere
 * else in `src/planner/` — if you need a new threshold, add it here with a
 * meaningful name and comment.
 */

/** Minimum interval between successive /route calls during interactive dragging. */
export const ROUTE_DEBOUNCE_MS = 120;

/** Long-press duration (mobile) that adds a waypoint at the touch location. */
export const LONG_PRESS_MS = 450;

/** Below this pixel movement a pointer-down + pointer-up counts as a click. */
export const DRAG_THRESHOLD_PX = 8;

/** Max stack depth for undo/redo. Keeps memory bounded under heavy editing. */
export const MAX_UNDO_STACK = 50;

/** Fallback profile when the server has not disclosed one. */
export const PROFILE_DEFAULT = 'trekking';

/** Base URL path for planner HTTP endpoints (relative to VITE_BACKEND_URL). */
export const PLANNER_API_BASE = 'api/planner';

/** Map source/layer identifiers for the planner route line. */
export const PLANNER_SOURCE_ID = 'mtl-planner-route-src';
export const PLANNER_LAYER_ID = 'mtl-planner-route-layer';
export const PLANNER_WAYPOINT_SOURCE_ID = 'mtl-planner-waypoints-src';
export const PLANNER_WAYPOINT_LAYER_ID = 'mtl-planner-waypoints-layer';

/** Colours for the route line + waypoints (keep in sync with CSS theme). */
export const ROUTE_LINE_COLOR = '#ff5722';
export const ROUTE_LINE_WIDTH_PX = 5;
export const WAYPOINT_RADIUS_PX = 8;
export const WAYPOINT_FILL_COLOR = '#ffffff';
export const WAYPOINT_STROKE_COLOR = '#ff5722';
export const WAYPOINT_STROKE_WIDTH_PX = 3;

/** Status-poll interval for the BRouter sidecar status endpoint. */
export const SIDECAR_STATUS_POLL_MS = 5_000;

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
