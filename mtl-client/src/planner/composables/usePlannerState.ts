/**
 * Planner reactive state + route recomputation logic. Deliberately framework-lite
 * (no Pinia) so the planner stays self-contained and easy to delete.
 */
import { computed, ref } from 'vue';
import type { LegResult, LiveStats, Waypoint } from '@/planner/types';
import { computeRoute, emptyStats, prewarmForBbox } from '@/planner/repositories/plannerRepository';
import {
  MAX_PLANNING_SPAN_KM,
  MAX_PREWARM_KM,
  MAX_UNDO_STACK,
  MIN_PREWARM_KM,
  PREWARM_MULTIPLIER,
  PROFILE_DEFAULT,
  ROUTE_DEBOUNCE_MS,
  VIEWPORT_PREWARM_DEBOUNCE_MS,
} from '@/planner/constants/PlannerConstants';

/** How long to wait before auto-retrying after a segment-downloading response. */
const SEGMENT_RETRY_DELAY_MS = 8_000;
/** Max consecutive segment-downloading retries to prevent infinite loops. */
const SEGMENT_RETRY_MAX = 6;

let idCounter = 0;
const nextId = () => `wp-${Date.now().toString(36)}-${(idCounter++).toString(36)}`;

export function usePlannerState() {
  const waypoints = ref<Waypoint[]>([]);
  const legs = ref<LegResult[]>([]);
  const stats = ref<LiveStats>(emptyStats());
  const profile = ref<string>(PROFILE_DEFAULT);
  const computing = ref(false);
  const lastError = ref<string | null>(null);
  const undoStack = ref<Waypoint[][]>([]);
  const redoStack = ref<Waypoint[][]>([]);
  /**
   * True from the moment a saved plan is loaded until the user's first edit.
   * While pristine, the map shows the exact saved polyline with no recompute;
   * the first mutation clears the flag and triggers a full re-route.
   */
  const pristineLoaded = ref(false);

  /** Any mutation path calls this before it touches state. */
  function clearPristine() {
    if (pristineLoaded.value) pristineLoaded.value = false;
  }

  let debounceTimer: number | null = null;
  let abortCtrl: AbortController | null = null;
  let segmentRetryCount = 0;
  let segmentRetryTimer: number | null = null;
  let prewarmDebounceTimer: number | null = null;

  /** Approximate bbox of the last prewarm we sent — avoids duplicate requests. */
  let lastPrewarmBbox: { minLat: number; minLng: number; maxLat: number; maxLng: number } | null = null;

  // ── Viewport state ───────────────────────────────────────────────

  interface MapBounds { minLat: number; minLng: number; maxLat: number; maxLng: number }
  const mapBounds = ref<MapBounds | null>(null);

  /** True when the visible map extent is too large for planning. */
  const viewportTooLarge = computed(() => {
    if (!mapBounds.value) return true; // no bounds yet → block
    return visibleSpanKm(mapBounds.value) > MAX_PLANNING_SPAN_KM;
  });

  function visibleSpanKm(b: MapBounds): number {
    const centerLat = (b.minLat + b.maxLat) / 2;
    const spanLatKm = (b.maxLat - b.minLat) * 111.32;
    const spanLngKm = (b.maxLng - b.minLng) * 111.32 * Math.cos(centerLat * Math.PI / 180);
    return Math.max(spanLatKm, spanLngKm);
  }

  /**
   * Called by PlannerMap whenever the viewport changes (load / moveend).
   * Updates bounds, recalculates viewportTooLarge, and schedules a prewarm
   * if the visible span is within limits.
   */
  function setViewport(minLat: number, minLng: number, maxLat: number, maxLng: number) {
    mapBounds.value = { minLat, minLng, maxLat, maxLng };
    if (prewarmDebounceTimer !== null) window.clearTimeout(prewarmDebounceTimer);
    if (viewportTooLarge.value) return;
    prewarmDebounceTimer = window.setTimeout(() => {
      triggerViewportPrewarm();
    }, VIEWPORT_PREWARM_DEBOUNCE_MS);
  }

  function triggerViewportPrewarm() {
    const b = mapBounds.value;
    if (!b) return;
    const centerLat = (b.minLat + b.maxLat) / 2;
    const centerLng = (b.minLng + b.maxLng) / 2;
    const span = visibleSpanKm(b);
    const target = Math.min(MAX_PREWARM_KM, Math.max(MIN_PREWARM_KM, span * PREWARM_MULTIPLIER));
    const halfDeltaLat = (target / 111.32) / 2;
    const halfDeltaLng = (target / (111.32 * Math.cos(centerLat * Math.PI / 180))) / 2;
    const expanded: MapBounds = {
      minLat: centerLat - halfDeltaLat,
      maxLat: centerLat + halfDeltaLat,
      minLng: centerLng - halfDeltaLng,
      maxLng: centerLng + halfDeltaLng,
    };
    // Skip if the last prewarm already covers this area
    if (lastPrewarmBbox &&
        expanded.minLat >= lastPrewarmBbox.minLat && expanded.maxLat <= lastPrewarmBbox.maxLat &&
        expanded.minLng >= lastPrewarmBbox.minLng && expanded.maxLng <= lastPrewarmBbox.maxLng) {
      return;
    }
    lastPrewarmBbox = expanded;
    console.info(`[planner] Viewport prewarm: ${target.toFixed(0)}km side → bbox=[${expanded.minLng.toFixed(2)},${expanded.minLat.toFixed(2)},${expanded.maxLng.toFixed(2)},${expanded.maxLat.toFixed(2)}]`);
    prewarmForBbox(expanded.minLng, expanded.minLat, expanded.maxLng, expanded.maxLat).catch(() => {});
  }

  const routeCoordinates = computed<[number, number, number][]>(() => {
    const out: [number, number, number][] = [];
    for (const l of legs.value) {
      for (const c of l.coordinates) out.push(c);
    }
    return out;
  });

  function snapshotForUndo() {
    undoStack.value.push(JSON.parse(JSON.stringify(waypoints.value)));
    if (undoStack.value.length > MAX_UNDO_STACK) undoStack.value.shift();
    redoStack.value = [];
  }

  function undo() {
    const prev = undoStack.value.pop();
    if (!prev) return;
    clearPristine();
    redoStack.value.push(JSON.parse(JSON.stringify(waypoints.value)));
    waypoints.value = prev;
    scheduleRecompute();
  }

  function redo() {
    const next = redoStack.value.pop();
    if (!next) return;
    clearPristine();
    undoStack.value.push(JSON.parse(JSON.stringify(waypoints.value)));
    waypoints.value = next;
    scheduleRecompute();
  }

  function addWaypoint(lat: number, lng: number) {
    if (viewportTooLarge.value) return;
    clearPristine();
    snapshotForUndo();
    waypoints.value.push({ id: nextId(), lat, lng });
    scheduleRecompute();
  }

  /** Insert a new draggable waypoint at a given position between existing ones. */
  function insertWaypoint(afterIndex: number, lat: number, lng: number) {
    if (viewportTooLarge.value) return;
    clearPristine();
    snapshotForUndo();
    const idx = Math.max(0, Math.min(afterIndex + 1, waypoints.value.length));
    waypoints.value.splice(idx, 0, { id: nextId(), lat, lng });
    scheduleRecompute();
  }

  function moveWaypoint(id: string, lat: number, lng: number) {
    const wp = waypoints.value.find((w) => w.id === id);
    if (!wp) return;
    clearPristine();
    wp.lat = lat;
    wp.lng = lng;
    scheduleRecompute();
  }

  function removeWaypoint(id: string) {
    clearPristine();
    snapshotForUndo();
    waypoints.value = waypoints.value.filter((w) => w.id !== id);
    scheduleRecompute();
  }

  function clearAll() {
    clearPristine();
    snapshotForUndo();
    waypoints.value = [];
    legs.value = [];
    stats.value = emptyStats();
  }

  /**
   * Replace current state with a previously-saved plan: waypoints, profile and
   * the already-routed geometry. The route shows immediately (no recompute
   * round-trip) and stays editable — moving / inserting a waypoint will
   * trigger a recompute as usual.
   */
  function loadPlan(detail: {
    profile: string | null;
    waypoints: { lat: number; lng: number }[];
    coordinates: [number, number, number][];
    distanceM: number;
  }) {
    // Reset undo/redo so the loaded route becomes the new baseline.
    undoStack.value = [];
    redoStack.value = [];
    if (detail.profile) profile.value = detail.profile;
    waypoints.value = (detail.waypoints || []).map((w) => ({
      id: nextId(),
      lat: w.lat,
      lng: w.lng,
    }));
    if (detail.coordinates && detail.coordinates.length >= 2) {
      const { ascentM, descentM } = computeAscentDescent(detail.coordinates);
      legs.value = [{
        coordinates: detail.coordinates,
        distanceM: detail.distanceM,
        ascentM,
        descentM,
        durationSec: 0,
        cached: true,
      }];
      stats.value = {
        distanceM: detail.distanceM,
        ascentM,
        descentM,
        durationSec: 0,
        legCount: 1,
        anyLegCached: true,
      };
    } else {
      legs.value = [];
      stats.value = emptyStats();
    }
    lastError.value = null;
    // If we restored waypoints but no geometry (legacy plan), trigger a recompute.
    if (waypoints.value.length >= 2 && legs.value.length === 0) {
      pristineLoaded.value = false;
      scheduleRecompute();
    } else {
      // Saved geometry is shown as-is; first edit will clear the flag and re-route.
      pristineLoaded.value = true;
    }
  }

  /** Sum of positive / negative elevation deltas (>= MIN threshold) along a polyline. */
  function computeAscentDescent(coords: [number, number, number][]): { ascentM: number; descentM: number } {
    let ascent = 0;
    let descent = 0;
    for (let i = 1; i < coords.length; i++) {
      const dz = coords[i][2] - coords[i - 1][2];
      if (dz > 0) ascent += dz;
      else descent += -dz;
    }
    return { ascentM: ascent, descentM: descent };
  }

  function scheduleRecompute() {
    if (debounceTimer !== null) window.clearTimeout(debounceTimer);
    debounceTimer = window.setTimeout(() => {
      void recomputeNow();
    }, ROUTE_DEBOUNCE_MS);
  }

  async function recomputeNow() {
    if (waypoints.value.length < 2) {
      legs.value = [];
      stats.value = emptyStats();
      return;
    }
    if (abortCtrl) abortCtrl.abort();
    abortCtrl = new AbortController();
    computing.value = true;
    lastError.value = null;
    if (segmentRetryTimer !== null) {
      window.clearTimeout(segmentRetryTimer);
      segmentRetryTimer = null;
    }
    try {
      const resp = await computeRoute(waypoints.value, profile.value, abortCtrl.signal);
      legs.value = resp.legs;
      stats.value = resp.stats;
      segmentRetryCount = 0; // success — reset
    } catch (e: unknown) {
      const err = e as { code?: string; response?: { status?: number; data?: { error?: string; detail?: string } }; message?: string };
      if (err?.code === 'ERR_CANCELED') return;

      const errorCode = err?.response?.data?.error;

      if (errorCode === 'segment-downloading' && segmentRetryCount < SEGMENT_RETRY_MAX) {
        segmentRetryCount++;
        const detail = err?.response?.data?.detail ?? 'Downloading routing data for this area…';
        lastError.value = `${detail} (auto-retry ${segmentRetryCount}/${SEGMENT_RETRY_MAX})`;
        console.info(`[planner] Segment downloading — auto-retry ${segmentRetryCount}/${SEGMENT_RETRY_MAX} in ${SEGMENT_RETRY_DELAY_MS}ms`);
        segmentRetryTimer = window.setTimeout(() => {
          void recomputeNow();
        }, SEGMENT_RETRY_DELAY_MS);
      } else {
        segmentRetryCount = 0;
        lastError.value = errorCode ?? err?.message ?? 'routing-failed';
      }
    } finally {
      computing.value = false;
    }
  }

  function setProfile(p: string) {
    clearPristine();
    profile.value = p;
    scheduleRecompute();
  }

  return {
    waypoints,
    legs,
    stats,
    profile,
    computing,
    lastError,
    pristineLoaded,
    routeCoordinates,
    viewportTooLarge,
    canUndo: computed(() => undoStack.value.length > 0),
    canRedo: computed(() => redoStack.value.length > 0),
    addWaypoint,
    insertWaypoint,
    moveWaypoint,
    removeWaypoint,
    clearAll,
    loadPlan,
    undo,
    redo,
    setProfile,
    setViewport,
    recomputeNow,
  };
}

export type PlannerState = ReturnType<typeof usePlannerState>;
