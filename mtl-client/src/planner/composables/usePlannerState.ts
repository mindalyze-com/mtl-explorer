/**
 * Planner reactive state + route recomputation logic. Deliberately framework-lite
 * (no Pinia) so the planner stays self-contained and easy to delete.
 */
import { computed, ref } from 'vue';
import type { LegResult, LiveStats, Waypoint } from '@/planner/types';
import type { PlannedTrackDetailDto } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { computeRoute, emptyStats, prewarmForBbox } from '@/planner/repositories/plannerRepository';
import {
  MAX_PLANNING_SPAN_KM,
  MAX_PREWARM_KM,
  MIN_ELEVATION_DELTA_M,
  MAX_UNDO_STACK,
  MAX_WAYPOINTS_FALLBACK,
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
/** Tiny tolerance for comparing saved waypoint orientation against saved geometry. */
const WAYPOINT_ORIENTATION_EPSILON = 1e-12;

let idCounter = 0;
const nextId = () => `wp-${Date.now().toString(36)}-${(idCounter++).toString(36)}`;

export function usePlannerState() {
  const waypoints = ref<Waypoint[]>([]);
  const legs = ref<LegResult[]>([]);
  const stats = ref<LiveStats>(emptyStats());
  const profile = ref<string>(PROFILE_DEFAULT);
  const maxWaypoints = ref(MAX_WAYPOINTS_FALLBACK);
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
  let routeRequestSeq = 0;
  let segmentRetryCount = 0;
  let segmentRetryTimer: number | null = null;
  let prewarmDebounceTimer: number | null = null;

  /** Approximate bbox of the last prewarm we sent — avoids duplicate requests. */
  let lastPrewarmBbox: { minLat: number; minLng: number; maxLat: number; maxLng: number } | null = null;

  // ── Viewport state ───────────────────────────────────────────────

  interface MapBounds {
    minLat: number;
    minLng: number;
    maxLat: number;
    maxLng: number;
  }
  const mapBounds = ref<MapBounds | null>(null);

  /** True when the visible map extent is too large for planning. */
  const viewportTooLarge = computed(() => {
    if (!mapBounds.value) return true; // no bounds yet → block
    return visibleSpanKm(mapBounds.value) > MAX_PLANNING_SPAN_KM;
  });

  function visibleSpanKm(b: MapBounds): number {
    const centerLat = (b.minLat + b.maxLat) / 2;
    const spanLatKm = (b.maxLat - b.minLat) * 111.32;
    const spanLngKm = (b.maxLng - b.minLng) * 111.32 * Math.cos((centerLat * Math.PI) / 180);
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
    const halfDeltaLat = target / 111.32 / 2;
    const halfDeltaLng = target / (111.32 * Math.cos((centerLat * Math.PI) / 180)) / 2;
    const expanded: MapBounds = {
      minLat: centerLat - halfDeltaLat,
      maxLat: centerLat + halfDeltaLat,
      minLng: centerLng - halfDeltaLng,
      maxLng: centerLng + halfDeltaLng,
    };
    // Skip if the last prewarm already covers this area
    if (
      lastPrewarmBbox &&
      expanded.minLat >= lastPrewarmBbox.minLat &&
      expanded.maxLat <= lastPrewarmBbox.maxLat &&
      expanded.minLng >= lastPrewarmBbox.minLng &&
      expanded.maxLng <= lastPrewarmBbox.maxLng
    ) {
      return;
    }
    lastPrewarmBbox = expanded;
    console.info(
      `[planner] Viewport prewarm: ${target.toFixed(0)}km side → bbox=[${expanded.minLng.toFixed(2)},${expanded.minLat.toFixed(2)},${expanded.maxLng.toFixed(2)},${expanded.maxLat.toFixed(2)}]`
    );
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

  function canAddWaypoint(): boolean {
    if (waypoints.value.length < maxWaypoints.value) return true;
    lastError.value = `Waypoint limit reached (max ${maxWaypoints.value}).`;
    return false;
  }

  function addWaypoint(lat: number, lng: number) {
    if (viewportTooLarge.value) return;
    if (!canAddWaypoint()) return;
    clearPristine();
    snapshotForUndo();
    waypoints.value.push({ id: nextId(), lat, lng });
    scheduleRecompute();
  }

  /** Insert a new draggable waypoint at a given position between existing ones. */
  function insertWaypoint(afterIndex: number, lat: number, lng: number) {
    if (viewportTooLarge.value) return;
    if (!canAddWaypoint()) return;
    clearPristine();
    snapshotForUndo();
    const idx = Math.max(0, Math.min(afterIndex + 1, waypoints.value.length));
    waypoints.value.splice(idx, 0, { id: nextId(), lat, lng });
    scheduleRecompute();
  }

  function moveWaypoint(id: string, lat: number, lng: number) {
    const wp = waypoints.value.find((w) => w.id === id);
    if (!wp) return;
    if (wp.lat === lat && wp.lng === lng) return;
    clearPristine();
    snapshotForUndo();
    wp.lat = lat;
    wp.lng = lng;
    scheduleRecompute();
  }

  function removeWaypoint(id: string) {
    if (!waypoints.value.some((w) => w.id === id)) return;
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
  function loadPlan(detail: PlannedTrackDetailDto) {
    // Reset undo/redo so the loaded route becomes the new baseline.
    undoStack.value = [];
    redoStack.value = [];
    if (detail.profile) profile.value = detail.profile;
    const coordinates = toCoordinates(detail.coordinates);
    const restoredWaypoints = orientWaypointsToGeometry(toWaypoints(detail.waypoints), coordinates);
    waypoints.value = restoredWaypoints.map((w) => ({
      id: nextId(),
      lat: w.lat,
      lng: w.lng,
    }));
    const savedLegs = toLegResults(detail.legs);
    if (savedLegs.length > 0) {
      legs.value = savedLegs;
      stats.value = detail.stats ? toLiveStats(detail.stats) : aggregateLegStats(savedLegs);
    } else if (coordinates.length >= 2) {
      const { ascentM, descentM } = computeAscentDescent(coordinates);
      legs.value = [
        {
          coordinates,
          distanceM: detail.distanceM ?? 0,
          ascentM,
          descentM,
          durationSec: 0,
          cached: true,
        },
      ];
      stats.value = {
        distanceM: detail.distanceM ?? 0,
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

  function toWaypoints(raw: PlannedTrackDetailDto['waypoints']): { lat: number; lng: number }[] {
    return (raw ?? [])
      .filter((waypoint) => Number.isFinite(waypoint.lat) && Number.isFinite(waypoint.lng))
      .map((waypoint) => ({ lat: waypoint.lat ?? 0, lng: waypoint.lng ?? 0 }));
  }

  function toCoordinates(raw: PlannedTrackDetailDto['coordinates']): [number, number, number][] {
    return (raw ?? [])
      .filter((coord) => coord.length >= 2 && Number.isFinite(coord[0]) && Number.isFinite(coord[1]))
      .map((coord) => [coord[0] ?? 0, coord[1] ?? 0, coord[2] ?? 0]);
  }

  function toLegResults(raw: PlannedTrackDetailDto['legs']): LegResult[] {
    return (raw ?? [])
      .map((leg) => ({
        coordinates: toCoordinates(leg.coordinates),
        distanceM: leg.distanceM ?? 0,
        ascentM: leg.ascentM ?? 0,
        descentM: leg.descentM ?? 0,
        durationSec: leg.durationSec ?? 0,
        cached: leg.cached ?? false,
      }))
      .filter((leg) => leg.coordinates.length >= 2);
  }

  function toLiveStats(raw: PlannedTrackDetailDto['stats']): LiveStats {
    return {
      distanceM: raw?.distanceM ?? 0,
      ascentM: raw?.ascentM ?? 0,
      descentM: raw?.descentM ?? 0,
      durationSec: raw?.durationSec ?? 0,
      legCount: raw?.legCount ?? 0,
      anyLegCached: raw?.anyLegCached ?? false,
    };
  }

  function aggregateLegStats(routeLegs: LegResult[]): LiveStats {
    return routeLegs.reduce<LiveStats>((acc, leg) => {
      acc.distanceM += leg.distanceM;
      acc.ascentM += leg.ascentM;
      acc.descentM += leg.descentM;
      acc.durationSec += leg.durationSec;
      acc.legCount += 1;
      if (leg.cached) acc.anyLegCached = true;
      return acc;
    }, emptyStats());
  }

  /** Sum of positive / negative elevation deltas along a polyline, ignoring SRTM-sized noise. */
  function computeAscentDescent(coords: [number, number, number][]): { ascentM: number; descentM: number } {
    let ascent = 0;
    let descent = 0;
    for (let i = 1; i < coords.length; i++) {
      const dz = coords[i][2] - coords[i - 1][2];
      if (dz > MIN_ELEVATION_DELTA_M) ascent += dz;
      else if (dz < -MIN_ELEVATION_DELTA_M) descent += -dz;
    }
    return { ascentM: ascent, descentM: descent };
  }

  function orientWaypointsToGeometry(
    sourceWaypoints: { lat: number; lng: number }[],
    coordinates: [number, number, number][]
  ): { lat: number; lng: number }[] {
    if (sourceWaypoints.length < 2 || coordinates.length < 2) return sourceWaypoints;

    const firstCoord = coordinates[0];
    const lastCoord = coordinates[coordinates.length - 1];
    const firstWaypoint = sourceWaypoints[0];
    const lastWaypoint = sourceWaypoints[sourceWaypoints.length - 1];
    const forwardScore =
      coordinateDistanceSquared(firstWaypoint, firstCoord) + coordinateDistanceSquared(lastWaypoint, lastCoord);
    const reversedScore =
      coordinateDistanceSquared(firstWaypoint, lastCoord) + coordinateDistanceSquared(lastWaypoint, firstCoord);

    return reversedScore + WAYPOINT_ORIENTATION_EPSILON < forwardScore
      ? [...sourceWaypoints].reverse()
      : sourceWaypoints;
  }

  function coordinateDistanceSquared(
    waypoint: { lat: number; lng: number },
    coordinate: [number, number, number]
  ): number {
    const dx = waypoint.lng - coordinate[0];
    const dy = waypoint.lat - coordinate[1];
    return dx * dx + dy * dy;
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
    if (waypoints.value.length > maxWaypoints.value) {
      lastError.value = `Too many waypoints (max ${maxWaypoints.value}).`;
      return;
    }
    if (abortCtrl) abortCtrl.abort();
    abortCtrl = new AbortController();
    const requestSeq = ++routeRequestSeq;
    const requestProfile = profile.value;
    const requestWaypoints = waypoints.value.map((w) => ({ ...w }));
    computing.value = true;
    lastError.value = null;
    if (segmentRetryTimer !== null) {
      window.clearTimeout(segmentRetryTimer);
      segmentRetryTimer = null;
    }
    try {
      const resp = await computeRoute(requestWaypoints, requestProfile, abortCtrl.signal);
      if (
        requestSeq !== routeRequestSeq ||
        requestProfile !== profile.value ||
        !sameWaypoints(requestWaypoints, waypoints.value)
      ) {
        return;
      }
      legs.value = orientLegsToWaypoints(resp.legs, requestWaypoints);
      stats.value = resp.stats;
      segmentRetryCount = 0; // success — reset
    } catch (e: unknown) {
      if (requestSeq !== routeRequestSeq) return;
      const err = e as {
        code?: string;
        response?: { status?: number; data?: { error?: string; detail?: string } };
        message?: string;
      };
      if (err?.code === 'ERR_CANCELED') return;

      const errorCode = err?.response?.data?.error;

      if (errorCode === 'segment-downloading' && segmentRetryCount < SEGMENT_RETRY_MAX) {
        segmentRetryCount++;
        const detail = err?.response?.data?.detail ?? 'Downloading routing data for this area…';
        lastError.value = `${detail} (auto-retry ${segmentRetryCount}/${SEGMENT_RETRY_MAX})`;
        console.info(
          `[planner] Segment downloading — auto-retry ${segmentRetryCount}/${SEGMENT_RETRY_MAX} in ${SEGMENT_RETRY_DELAY_MS}ms`
        );
        segmentRetryTimer = window.setTimeout(() => {
          void recomputeNow();
        }, SEGMENT_RETRY_DELAY_MS);
      } else {
        segmentRetryCount = 0;
        lastError.value = errorCode ?? err?.message ?? 'routing-failed';
      }
    } finally {
      if (requestSeq === routeRequestSeq) computing.value = false;
    }
  }

  function orientLegsToWaypoints(routeLegs: LegResult[], routeWaypoints: Waypoint[]): LegResult[] {
    if (routeLegs.length !== routeWaypoints.length - 1) return routeLegs;
    return routeLegs.map((leg, index) => orientLegToWaypoints(leg, routeWaypoints[index], routeWaypoints[index + 1]));
  }

  function orientLegToWaypoints(leg: LegResult, from: Waypoint, to: Waypoint): LegResult {
    const coords = leg.coordinates;
    if (coords.length < 2) return leg;

    const firstCoord = coords[0];
    const lastCoord = coords[coords.length - 1];
    const forwardScore = coordinateDistanceSquared(from, firstCoord) + coordinateDistanceSquared(to, lastCoord);
    const reversedScore = coordinateDistanceSquared(from, lastCoord) + coordinateDistanceSquared(to, firstCoord);

    return reversedScore + WAYPOINT_ORIENTATION_EPSILON < forwardScore
      ? { ...leg, coordinates: [...coords].reverse() }
      : leg;
  }

  function sameWaypoints(a: Waypoint[], b: Waypoint[]): boolean {
    return (
      a.length === b.length &&
      a.every((wp, index) => {
        const other = b[index];
        return other?.id === wp.id && other.lat === wp.lat && other.lng === wp.lng;
      })
    );
  }

  function setProfile(p: string) {
    clearPristine();
    profile.value = p;
    scheduleRecompute();
  }

  function setMaxWaypoints(value: number) {
    maxWaypoints.value = Number.isFinite(value) && value >= 2 ? Math.floor(value) : MAX_WAYPOINTS_FALLBACK;
    if (waypoints.value.length > maxWaypoints.value) {
      lastError.value = `Too many waypoints (max ${maxWaypoints.value}).`;
    }
  }

  return {
    waypoints,
    legs,
    stats,
    profile,
    maxWaypoints,
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
    setMaxWaypoints,
    setViewport,
    recomputeNow,
  };
}

export type PlannerState = ReturnType<typeof usePlannerState>;
