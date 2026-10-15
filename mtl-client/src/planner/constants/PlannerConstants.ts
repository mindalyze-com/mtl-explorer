import { STATUS_POLL_INTERVAL_MS } from '@/utils/statusPolling';

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

export const MAX_POINTS_OF_INTEREST_FALLBACK = 1000;
export const POINT_OF_INTEREST_NAME_MAX_LENGTH = 255;
export const POINT_OF_INTEREST_NOTES_MAX_LENGTH = 4000;
export const POINT_OF_INTEREST_DEFAULT_NAME = 'Point of interest';
export const PLANNER_POI_SOURCE_ID = 'mtl-planner-pois-src';
export const PLANNER_POI_LAYER_ID = 'mtl-planner-pois-layer';
export const PLANNER_POI_ICON_LAYER_ID = 'mtl-planner-pois-icons';
export const PLANNER_POI_ICON_IMAGE_PREFIX = 'mtl-planner-poi-icon-';
export const PLANNER_POI_LABEL_LAYER_ID = 'mtl-planner-pois-labels';
export const PLANNER_POI_COLOR = '#6366f1';
export const PLANNER_POI_LABEL_OFFSET_EM = 1.5;
export const PLANNER_POI_LABEL_HALO_WIDTH_PX = 2;

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
export const PLANNER_WAYPOINT_LABEL_LAYER_ID = 'mtl-planner-waypoint-labels';
export const WAYPOINT_LABEL_SIZE_PX = 11;
export const WAYPOINT_START_COLOR = '#15803d';
export const WAYPOINT_FINISH_COLOR = '#b91c1c';
export const PLANNER_WAYPOINT_LAYER_ID = 'mtl-planner-waypoints-layer';
export const PLANNER_WAYPOINT_HIT_LAYER_ID = 'mtl-planner-waypoints-hit-layer';

/** Colours for the route line + waypoints (keep in sync with CSS theme). */
export const ROUTE_LINE_COLOR = '#ff5722';
export const ROUTE_LINE_WIDTH_PX = 5;
export const ROUTE_HIT_LINE_WIDTH_PX = 20;
export const ROUTE_MOUSE_INSERT_RADIUS_PX = 8;
export const ROUTE_TOUCH_INSERT_RADIUS_PX = 12;
export const WAYPOINT_RADIUS_PX = 11;
export const WAYPOINT_FILL_COLOR = '#ffffff';
export const WAYPOINT_STROKE_COLOR = '#ff5722';
export const WAYPOINT_STROKE_WIDTH_PX = 3;
export const WAYPOINT_DRAG_RING_COLOR = 'rgba(255, 87, 34, 0.12)';
export const WAYPOINT_ACTIVE_RING_RADIUS_PX = 15;
export const WAYPOINT_DRAGGING_RING_RADIUS_PX = 18;
export const WAYPOINT_MOUSE_HIT_RADIUS_PX = 18;
export const WAYPOINT_TOUCH_HIT_RADIUS_PX = 24;
export const WAYPOINT_MOUSE_DRAG_THRESHOLD_PX = 3;
export const WAYPOINT_TOUCH_DRAG_THRESHOLD_PX = 6;
export const MAP_CLICK_SUPPRESSION_MS = 350;
export const TOUCH_SYNTHETIC_CLICK_SUPPRESSION_MS = 1_200;
export const SYNTHETIC_CLICK_SUPPRESSION_RADIUS_PX = 24;

/** Status-poll interval for the BRouter sidecar status endpoint. */
export const SIDECAR_STATUS_POLL_MS = STATUS_POLL_INTERVAL_MS;

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

// ── Drawing panel sizes ────────────────────────────────────────────

/** Baseline for dimensions that follow the user's root text size. */
export const PLANNER_BASE_FONT_PX = 16;
/** Narrowest totals area that keeps three stat cards from overlapping. */
export const PLANNER_SUMMARY_TOTALS_MIN_PX = 232;
/** Width of the full-layout actions: Undo, Redo and the profile toggle. */
export const PLANNER_SUMMARY_ACTIONS_PX = 3 * 44 + 2 * 6;
/** Narrowest profile that stays readable beside the totals. */
export const PLANNER_SIDE_PROFILE_MIN_PX = 240;
const PLANNER_SUMMARY_INLINE_PADDING_PX = 2 * 16;
const PLANNER_SUMMARY_COLUMN_GAP_PX = 12;
const PLANNER_SUMMARY_ROW_GAP_PX = 8;
/** Totals sit beside the profile only when both columns fit at their minimum widths. */
export const PLANNER_WIDE_SUMMARY_MIN_PX =
  PLANNER_SUMMARY_INLINE_PADDING_PX +
  PLANNER_SUMMARY_TOTALS_MIN_PX +
  PLANNER_SUMMARY_ROW_GAP_PX +
  PLANNER_SUMMARY_ACTIONS_PX +
  PLANNER_SUMMARY_COLUMN_GAP_PX +
  PLANNER_SIDE_PROFILE_MIN_PX;
/** Vertical padding above and below the pinned summary. */
export const PLANNER_SUMMARY_PADDING_PX = 6;
export const PLANNER_ESSENTIAL_SUMMARY_PADDING_PX = 2;
/** Gap between the totals row and the stacked profile. */
export const PLANNER_SUMMARY_GAP_PX = 4;
/** Profile heights per density; compact keeps the existing 80 px chart minimum readable. */
export const PLANNER_PROFILE_FULL_HEIGHT_PX = 120;
export const PLANNER_PROFILE_PLACEHOLDER_HEIGHT_PX = 20;
/** Match the shared sheet's desktop layout breakpoint. */
export const PLANNER_DESKTOP_MIN_WIDTH_PX = 769;
/** Stable opening size for the hub, tools, guide and saved plans. */
export const PLANNER_WORKSPACE_HEIGHT = 'min(60vh, 480px)';
export const PLANNER_PROFILE_COMPACT_HEIGHT_PX = 88;
/** Visible tool space the full drawing size keeps below the summary. */
export const PLANNER_FULL_TOOLS_MIN_PX = 150;
/** One full touch target plus the scrolling body's and tools' bottom padding. */
export const PLANNER_SHORT_TOOLS_MIN_PX = 72;
/** Match the shared sheet's hard cap when a short screen needs the extra space. */
export const PLANNER_SHORT_VIEWPORT_FRACTION = 0.98;
/** Preferred share of the viewport for the full drawing size. */
export const PLANNER_FULL_VIEWPORT_FRACTION = 0.5;
/** Upper bound for full and enlarged drawing sizes. */
export const PLANNER_ENLARGED_VIEWPORT_FRACTION = 0.92;
/** Sizes closer than this are merged so each named size remains distinct. */
export const PLANNER_MIN_SIZE_GAP_PX = 32;
/** Extra travel past a density boundary before slow dragging switches layout. */
export const PLANNER_DENSITY_BUFFER_PX = 12;
/** Fallbacks before the panel has been measured. */
export const PLANNER_DEFAULT_CHROME_PX = 64;
export const PLANNER_DEFAULT_INSET_PX = 8;
export const PLANNER_DEFAULT_ROW_PX = 44;
/** Floating size tips leave after this long; each is shown only once. */
export const PLANNER_TIP_MS = 5_000;
export const PLANNER_SAVE_CONFIRMATION_MS = 4_000;
/** Gap between a floating tip and the top edge of the panel. */
export const PLANNER_TIP_OFFSET_PX = 8;
