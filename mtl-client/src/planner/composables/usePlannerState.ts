/**
 * Planner reactive state + route recomputation logic. Deliberately framework-lite
 * (no Pinia) so the planner stays self-contained and easy to delete.
 */
import { warnRequestFailure } from '@/utils/safeLogging';
import { computed, ref } from 'vue';
import type { LegResult, LiveStats, PointOfInterest, Waypoint } from '@/planner/types';
import type { PlannedTrackDetailDto } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { computeRoute, computeRouteStats, emptyStats, prewarmForBbox } from '@/planner/repositories/plannerRepository';
import { isAbortLikeError } from '@/utils/errors';
import { createUuid } from '@/utils/random';
import { DEFAULT_PLANNER_POI_ICON_TYPE } from '@/planner/utils/plannerPoiIconTypes';
import {
  MAX_PLANNING_SPAN_KM,
  MAX_PREWARM_KM,
  MAX_UNDO_STACK,
  MAX_WAYPOINTS_FALLBACK,
  MAX_POINTS_OF_INTEREST_FALLBACK,
  POINT_OF_INTEREST_DEFAULT_NAME,
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
const ROUTING_UNAVAILABLE_MESSAGE = 'Routing service is unavailable. Check the BRouter status, then retry.';
const ROUTING_FAILED_MESSAGE = 'Route calculation failed. Please adjust the route or try again later.';
const MACHINE_ERROR_KEY = /^[a-z0-9]+(?:-[a-z0-9]+)+$/;

let idCounter = 0;
const nextId = () => `wp-${Date.now().toString(36)}-${(idCounter++).toString(36)}`;

function routeErrorMessage(errorCode?: string, detail?: string, fallback?: string): string {
  if (detail?.trim() && detail !== 'BRouter call failed') return detail.trim();
  if (errorCode === 'routing-unavailable') return ROUTING_UNAVAILABLE_MESSAGE;
  if (errorCode === 'routing-failed') return ROUTING_FAILED_MESSAGE;
  if (errorCode && !MACHINE_ERROR_KEY.test(errorCode)) return errorCode;
  return fallback ?? ROUTING_FAILED_MESSAGE;
}

export function usePlannerState(options: { onPrewarmComplete?: () => void } = {}) {
  const waypoints = ref<Waypoint[]>([]);
  const pointsOfInterest = ref<PointOfInterest[]>([]);
  const maxPointsOfInterest = ref(MAX_POINTS_OF_INTEREST_FALLBACK);
  const legs = ref<LegResult[]>([]);
  const stats = ref<LiveStats>(emptyStats());
  const profile = ref<string>(PROFILE_DEFAULT);
  const maxWaypoints = ref(MAX_WAYPOINTS_FALLBACK);
  const computing = ref(false);
  const lastError = ref<string | null>(null);
  type Snapshot = {
    waypoints: Waypoint[];
    pointsOfInterest: PointOfInterest[];
    profile: string;
    legs: LegResult[];
    stats: LiveStats;
    pristineLoaded: boolean;
    routeReady: boolean;
    geometryStatsPending: boolean;
  };
  const undoStack = ref<Snapshot[]>([]);
  const redoStack = ref<Snapshot[]>([]);
  const routeReady = ref(false);
  const geometryStatsPending = ref(false);
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
    prewarmForBbox(expanded.minLng, expanded.minLat, expanded.maxLng, expanded.maxLat)
      .catch(() => {})
      .finally(() => options.onPrewarmComplete?.());
  }

  const routeCoordinates = computed<[number, number, number][]>(() => {
    const out: [number, number, number][] = [];
    for (const l of legs.value) {
      for (const c of l.coordinates) out.push(c);
    }
    return out;
  });

  function snapshot(): Snapshot {
    return JSON.parse(
      JSON.stringify({
        waypoints: waypoints.value,
        pointsOfInterest: pointsOfInterest.value,
        profile: profile.value,
        legs: legs.value,
        stats: stats.value,
        pristineLoaded: pristineLoaded.value,
        routeReady: routeReady.value,
        geometryStatsPending: geometryStatsPending.value,
      })
    );
  }

  function snapshotForUndo() {
    undoStack.value.push(snapshot());
    if (undoStack.value.length > MAX_UNDO_STACK) undoStack.value.shift();
    redoStack.value = [];
  }

  function cancelRecompute() {
    if (debounceTimer !== null) window.clearTimeout(debounceTimer);
    if (segmentRetryTimer !== null) window.clearTimeout(segmentRetryTimer);
    debounceTimer = null;
    segmentRetryTimer = null;
    abortCtrl?.abort();
    abortCtrl = null;
    routeRequestSeq++;
    segmentRetryCount = 0;
    computing.value = false;
  }

  function restore(previous: Snapshot) {
    cancelRecompute();
    waypoints.value = previous.waypoints;
    pointsOfInterest.value = previous.pointsOfInterest;
    profile.value = previous.profile;
    legs.value = previous.legs;
    stats.value = previous.stats;
    pristineLoaded.value = previous.pristineLoaded;
    routeReady.value = previous.routeReady;
    geometryStatsPending.value = previous.geometryStatsPending;
    lastError.value = null;
    if (geometryStatsPending.value) void recomputeGeometryStats();
    else if (!routeReady.value) scheduleRecompute();
  }

  function undo() {
    const previous = undoStack.value.pop();
    if (!previous) return;
    redoStack.value.push(snapshot());
    restore(previous);
  }

  function redo() {
    const next = redoStack.value.pop();
    if (!next) return;
    undoStack.value.push(snapshot());
    restore(next);
  }

  function reverseRoute() {
    if (waypoints.value.length < 2) return;
    snapshotForUndo();
    clearPristine();
    waypoints.value.reverse();
    // Direction can affect routing restrictions and duration; ask the server.
    scheduleRecompute();
  }

  /** Keep the selected point as the new start or finish. */
  function trimRoute(id: string, side: 'before' | 'after') {
    const index = waypoints.value.findIndex((waypoint) => waypoint.id === id);
    const lastIndex = waypoints.value.length - 1;
    if (index <= 0 || index >= lastIndex) return;
    snapshotForUndo();
    clearPristine();
    const canKeepLegs = routeReady.value && legs.value.length === lastIndex;
    cancelRecompute();
    waypoints.value = side === 'before' ? waypoints.value.slice(index) : waypoints.value.slice(0, index + 1);
    if (canKeepLegs) {
      legs.value = side === 'before' ? legs.value.slice(index) : legs.value.slice(0, index);
      void recomputeGeometryStats();
    } else {
      scheduleRecompute();
    }
  }

  function canAddWaypoint(): boolean {
    if (waypoints.value.length < maxWaypoints.value) return true;
    lastError.value = `Waypoint limit reached (max ${maxWaypoints.value}).`;
    return false;
  }

  function addWaypoint(lat: number, lng: number) {
    if (viewportTooLarge.value) return;
    if (!canAddWaypoint()) return;
    snapshotForUndo();
    clearPristine();
    waypoints.value.push({ id: nextId(), lat, lng });
    scheduleRecompute();
  }

  /** Insert a new draggable waypoint at a given position between existing ones. */
  function insertWaypoint(afterIndex: number, lat: number, lng: number): Waypoint | null {
    if (viewportTooLarge.value) return null;
    if (!canAddWaypoint()) return null;
    if (!Number.isInteger(afterIndex) || afterIndex < 0 || afterIndex >= waypoints.value.length) return null;
    snapshotForUndo();
    clearPristine();
    const idx = afterIndex + 1;
    const waypoint = { id: nextId(), lat, lng };
    waypoints.value.splice(idx, 0, waypoint);
    scheduleRecompute();
    return waypoint;
  }

  function moveWaypoint(id: string, lat: number, lng: number) {
    const wp = waypoints.value.find((w) => w.id === id);
    if (!wp) return;
    if (wp.lat === lat && wp.lng === lng) return;
    snapshotForUndo();
    clearPristine();
    wp.lat = lat;
    wp.lng = lng;
    scheduleRecompute();
  }

  function removeWaypoint(id: string) {
    if (!waypoints.value.some((w) => w.id === id)) return;
    snapshotForUndo();
    clearPristine();
    waypoints.value = waypoints.value.filter((w) => w.id !== id);
    scheduleRecompute();
  }

  /** POI edits share document history without recalculating the route. */
  function addPointOfInterest(
    lat: number,
    lng: number,
    details: Pick<PointOfInterest, 'name' | 'iconType' | 'notes'> = { name: POINT_OF_INTEREST_DEFAULT_NAME }
  ): PointOfInterest | null {
    if (pointsOfInterest.value.length >= maxPointsOfInterest.value) return null;
    const point = {
      id: createUuid(),
      lat,
      lng,
      ...details,
      iconType: details.iconType ?? DEFAULT_PLANNER_POI_ICON_TYPE,
    };
    snapshotForUndo();
    pointsOfInterest.value.push(point);
    return point;
  }

  function updatePointOfInterest(point: PointOfInterest) {
    const index = pointsOfInterest.value.findIndex((existing) => existing.id === point.id);
    if (index < 0 || JSON.stringify(pointsOfInterest.value[index]) === JSON.stringify(point)) return;
    snapshotForUndo();
    pointsOfInterest.value[index] = { ...point };
  }

  function movePointOfInterest(id: string, lat: number, lng: number) {
    const point = pointsOfInterest.value.find((existing) => existing.id === id);
    if (point) updatePointOfInterest({ ...point, lat, lng });
  }

  function removePointOfInterest(id: string) {
    if (!pointsOfInterest.value.some((point) => point.id === id)) return;
    snapshotForUndo();
    pointsOfInterest.value = pointsOfInterest.value.filter((point) => point.id !== id);
  }

  function clearAll() {
    if (!waypoints.value.length) return;
    snapshotForUndo();
    clearPristine();
    cancelRecompute();
    waypoints.value = [];
    legs.value = [];
    stats.value = emptyStats();
    routeReady.value = false;
    geometryStatsPending.value = false;
    lastError.value = null;
  }

  /**
   * Replace current state with a previously-saved plan: waypoints, profile and
   * the already-routed geometry. The route shows immediately (no recompute
   * round-trip) and stays editable — moving / inserting a waypoint will
   * trigger a recompute as usual.
   */
  function loadPlan(detail: PlannedTrackDetailDto) {
    cancelRecompute();
    routeReady.value = false;
    geometryStatsPending.value = false;
    // Reset undo/redo so the loaded route becomes the new baseline.
    undoStack.value = [];
    redoStack.value = [];
    pointsOfInterest.value = (detail.pointsOfInterest ?? []).map((point) => ({ ...point }));
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
      stats.value = toLiveStats(detail.stats);
    } else if (coordinates.length >= 2) {
      stats.value = toLiveStats(detail.stats);
      legs.value = [
        {
          coordinates,
          distanceM: detail.distanceM ?? 0,
          ascentM: stats.value.ascentM,
          descentM: stats.value.descentM,
          durationSec: 0,
          cached: true,
        },
      ];
    } else {
      legs.value = [];
      stats.value = emptyStats();
    }
    routeReady.value = legs.value.length > 0 && waypoints.value.length >= 2;
    lastError.value = null;
    // If we restored waypoints but no geometry (legacy plan), trigger a recompute.
    if (waypoints.value.length >= 2 && legs.value.length === 0) {
      pristineLoaded.value = false;
      scheduleRecompute();
    } else {
      // Saved geometry is shown as-is; first edit will clear the flag and re-route.
      pristineLoaded.value = true;
      if (legs.value.length > 0 && !detail.stats) void recomputeGeometryStats();
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
      .map((coord) => [coord[0] ?? 0, coord[1] ?? 0, coord[2] ?? Number.NaN]);
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

  /** Recalculate retained geometry on the server without changing its route. */
  async function recomputeGeometryStats() {
    cancelRecompute();
    geometryStatsPending.value = true;
    routeReady.value = false;
    computing.value = true;
    lastError.value = null;
    const requestAbortCtrl = new AbortController();
    abortCtrl = requestAbortCtrl;
    const requestSeq = ++routeRequestSeq;
    try {
      const result = await computeRouteStats(legs.value, requestAbortCtrl.signal);
      if (requestSeq !== routeRequestSeq) return;
      stats.value = result;
      geometryStatsPending.value = false;
      routeReady.value = legs.value.length > 0 && waypoints.value.length >= 2;
    } catch (error: unknown) {
      if (requestSeq !== routeRequestSeq || isAbortLikeError(error, requestAbortCtrl.signal)) return;
      lastError.value = 'Route statistics could not be calculated. Please try again.';
      warnRequestFailure('Route statistics failed', error, {
        topic1: 'planner',
        topic2: 'usePlannerState',
        topic3: 'statistics',
      });
    } finally {
      if (requestSeq === routeRequestSeq) computing.value = false;
    }
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
    cancelRecompute();
    geometryStatsPending.value = false;
    routeReady.value = false;
    lastError.value = null;
    if (waypoints.value.length < 2) {
      legs.value = [];
      stats.value = emptyStats();
      return;
    }
    computing.value = true;
    debounceTimer = window.setTimeout(() => {
      void recomputeNow();
    }, ROUTE_DEBOUNCE_MS);
  }

  async function recomputeNow() {
    if (geometryStatsPending.value) return recomputeGeometryStats();
    if (debounceTimer !== null) window.clearTimeout(debounceTimer);
    debounceTimer = null;
    routeReady.value = false;
    if (waypoints.value.length < 2) {
      legs.value = [];
      stats.value = emptyStats();
      computing.value = false;
      return;
    }
    if (waypoints.value.length > maxWaypoints.value) {
      lastError.value = `Too many waypoints (max ${maxWaypoints.value}).`;
      computing.value = false;
      return;
    }
    if (abortCtrl) abortCtrl.abort();
    const requestAbortCtrl = new AbortController();
    abortCtrl = requestAbortCtrl;
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
      const resp = await computeRoute(requestWaypoints, requestProfile, requestAbortCtrl.signal);
      if (
        requestSeq !== routeRequestSeq ||
        requestProfile !== profile.value ||
        !sameWaypoints(requestWaypoints, waypoints.value)
      ) {
        return;
      }
      legs.value = orientLegsToWaypoints(resp.legs, requestWaypoints);
      stats.value = resp.stats;
      routeReady.value =
        legs.value.length === requestWaypoints.length - 1 && legs.value.every((leg) => leg.coordinates.length >= 2);
      if (!routeReady.value) lastError.value = ROUTING_FAILED_MESSAGE;
      if (!routeReady.value)
        warnRequestFailure('Routing response did not contain a usable route', undefined, {
          topic1: 'planner',
          topic2: 'usePlannerState',
          topic3: 'calculate',
        });
      segmentRetryCount = 0; // success — reset
    } catch (e: unknown) {
      if (requestSeq !== routeRequestSeq) return;
      const err = e as {
        code?: string;
        response?: { status?: number; data?: { error?: string; detail?: string } };
        message?: string;
      };
      if (isAbortLikeError(e, requestAbortCtrl.signal)) return;

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
        lastError.value = routeErrorMessage(errorCode, err?.response?.data?.detail, err?.message);
        warnRequestFailure('Route calculation failed', e, {
          topic1: 'planner',
          topic2: 'usePlannerState',
          topic3: 'calculate',
          context: { code: typeof errorCode === 'string' && MACHINE_ERROR_KEY.test(errorCode) ? errorCode : undefined },
        });
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
    if (p === profile.value) return;
    snapshotForUndo();
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
    pointsOfInterest,
    maxPointsOfInterest,
    setMaxPointsOfInterest: (limit: number) => {
      maxPointsOfInterest.value = limit;
    },
    addPointOfInterest,
    updatePointOfInterest,
    movePointOfInterest,
    removePointOfInterest,
    legs,
    stats,
    profile,
    maxWaypoints,
    computing,
    routeReady,
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
    reverseRoute,
    trimRoute,
    dispose: () => {
      cancelRecompute();
      if (prewarmDebounceTimer !== null) window.clearTimeout(prewarmDebounceTimer);
    },
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
