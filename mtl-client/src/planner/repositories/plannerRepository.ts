/**
 * Planner HTTP client. Wraps {@link PlannerControllerApi} from the generated
 * OpenAPI fetch client for every endpoint whose schema is already expressive
 * enough. A handful of endpoints stay on the hand-rolled axios path because
 * their generated signatures are too loose (server returns {@code object} or
 * a raw {@code Map<String,Object>}) or because we need behaviour the generator
 * cannot express (blob download for GPX, AbortSignal cancellation, typed
 * response shape for {@code /route} that the current schema does not yet describe).
 *
 * Whenever {@code PlannerController} grows stricter return types, revisit this
 * file and move the remaining endpoints over to the generated client.
 */
import { apiClient } from '@/utils/apiClient';
import { apiUrl } from '@/utils/apiBase';
import { getApiConfiguration } from '@/utils/openApiClient';
import { createStatusRequestService, type StatusRequestOptions } from '@/utils/statusPolling';
import { PlannerControllerApi } from 'x8ing-mtl-api-typescript-fetch';
import type {
  PlannedTrackDetailDto,
  PlannedTrackSummaryDto,
  SavePlannedTrackDto,
  WaypointDto,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { MAX_WAYPOINTS_FALLBACK, PLANNER_API_BASE } from '@/planner/constants/PlannerConstants';
import type {
  LegResult,
  LiveStats,
  PlannedTrackSummary,
  RouteResponse,
  SidecarStatus,
  Waypoint,
} from '@/planner/types';

const GPX_FILE_EXTENSION = '.gpx';
const GPX_FILENAME_FALLBACK = 'planned-route';
const INVALID_FILENAME_CHARS = /[\\/:*?"<>|]+/g;
const WHITESPACE_CHARS = /\s+/g;

function getPlannerApi(): PlannerControllerApi {
  return new PlannerControllerApi(getApiConfiguration());
}

const plannerStatusRequests = createStatusRequestService<SidecarStatus>({
  request: (signal) => getPlannerApi().status({ signal }),
});

export async function fetchPlannerConfig(): Promise<{
  profiles: string[];
  defaultProfile: string;
  maxWaypoints: number;
}> {
  const raw = await getPlannerApi().config();
  return {
    profiles: Array.isArray(raw.profiles) ? (raw.profiles as string[]) : [],
    defaultProfile: typeof raw.defaultProfile === 'string' ? raw.defaultProfile : 'trekking',
    maxWaypoints: typeof raw.maxWaypoints === 'number' ? raw.maxWaypoints : MAX_WAYPOINTS_FALLBACK,
  };
}

/**
 * The /route endpoint declares {@code ResponseEntity<?>} on the server side so
 * the generator emits {@code Promise<object>}. We still call it via axios to
 * preserve AbortSignal support and a properly typed response.
 */
export async function computeRoute(
  waypoints: Waypoint[],
  profile: string,
  signal?: AbortSignal
): Promise<RouteResponse> {
  const body = {
    waypoints: waypoints.map((w): WaypointDto => ({ lat: w.lat, lng: w.lng })),
    profile,
  };
  const r = await apiClient.post<RouteResponse>(`${PLANNER_API_BASE}/route`, body, { signal });
  return r.data;
}

export async function fetchSidecarStatus(options: StatusRequestOptions = {}): Promise<SidecarStatus> {
  return plannerStatusRequests.fetch(options);
}

export interface SaveArgs {
  name: string;
  description?: string;
  profile: string;
  coordinates: [number, number, number][];
  /** Original BRouter route legs, preserving per-leg stats and boundaries. */
  legs: LegResult[];
  /** Original BRouter aggregate stats for the saved route. */
  stats: LiveStats;
  /** Original user waypoints (lat/lng) so the plan can be re-loaded into the editor. */
  waypoints: { lat: number; lng: number }[];
}

export async function savePlannedRoute(args: SaveArgs): Promise<{ id: number; name: string; distanceM: number }> {
  const dto: SavePlannedTrackDto = {
    name: args.name,
    description: args.description,
    profile: args.profile,
    coordinates: args.coordinates,
    legs: args.legs,
    stats: args.stats,
    waypoints: args.waypoints,
  };
  const raw = await getPlannerApi().save({ savePlannedTrackDto: dto });
  return {
    id: raw.id ?? 0,
    name: raw.name ?? '',
    distanceM: raw.distanceM ?? 0,
  };
}

export async function listPlannedTracks(): Promise<PlannedTrackSummary[]> {
  const raw: PlannedTrackSummaryDto[] = await getPlannerApi().plans();
  return raw.map((p) => ({
    id: p.id ?? 0,
    name: p.name ?? '',
    description: p.description ?? '',
    distanceM: p.distanceM ?? 0,
    centerLat: p.centerLat ?? 0,
    centerLng: p.centerLng ?? 0,
    createDate: p.createDate ? new Date(p.createDate).toISOString() : '',
    profile: ((p as unknown as Record<string, unknown>).profile as string | null) ?? null,
  }));
}

export async function loadPlannedTrack(id: number): Promise<PlannedTrackDetailDto> {
  return getPlannerApi().loadPlan({ id });
}

export async function deletePlannedTrack(id: number): Promise<void> {
  // The endpoint returns 204 No Content; avoid the generated convenience method,
  // which tries to parse an object response from the empty body.
  await getPlannerApi().deletePlanRaw({ id });
}

/**
 * Start a native same-origin download while the original pointer event is
 * active. The generated client models this byte response as text, and waiting
 * for a fetched Blob before clicking an anchor can cause browsers to block the
 * delayed download.
 */
export async function downloadPlannedTrackGpx(id: number, name: string): Promise<void> {
  const a = document.createElement('a');
  a.href = apiUrl(`${PLANNER_API_BASE}/plans/${id}/gpx`);
  a.download = makeGpxFileName(name);
  document.body.appendChild(a);
  a.click();
  a.remove();
}

function makeGpxFileName(name: string): string {
  const baseName =
    name.trim().replace(INVALID_FILENAME_CHARS, '-').replace(WHITESPACE_CHARS, ' ') || GPX_FILENAME_FALLBACK;
  return baseName.toLowerCase().endsWith(GPX_FILE_EXTENSION) ? baseName : `${baseName}${GPX_FILE_EXTENSION}`;
}

/** Fire-and-forget: tell the sidecar to download segments covering the given bbox. */
export async function prewarmForBbox(minLng: number, minLat: number, maxLng: number, maxLat: number): Promise<void> {
  await getPlannerApi().prewarm({ requestBody: { minLng, minLat, maxLng, maxLat } });
}

/** Convenience — zero stats object, used when no waypoints / no route yet. */
export function emptyStats(): LiveStats {
  return {
    distanceM: 0,
    ascentM: 0,
    descentM: 0,
    durationSec: 0,
    legCount: 0,
    anyLegCached: false,
  };
}
