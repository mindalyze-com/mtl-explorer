/**
 * Planner HTTP client. Wraps {@link PlannerControllerApi} from the generated
 * OpenAPI fetch client for every endpoint whose schema is already expressive
 * enough. A handful of endpoints stay on the hand-rolled axios path because
 * their generated signatures are too loose (server returns {@code object} or
 * a raw {@code Map<String,Object>}) or because we need behaviour the generator
 * cannot express (blob download for GPX, AbortSignal cancellation, typed
 * response shape for {@code /route} and {@code /plans/{id}} that the current
 * schema does not yet describe).
 *
 * Whenever {@code PlannerController} grows stricter return types, revisit this
 * file and move the remaining endpoints over to the generated client.
 */
import { getAuthHeaderValue } from '@/utils/auth';
import { apiClient } from '@/utils/apiClient';
import { getApiConfiguration } from '@/utils/openApiClient';
import { PlannerControllerApi } from 'x8ing-mtl-api-typescript-fetch';
import type {
  PlannedTrackSummaryDto,
  SavePlannedTrackDto,
  WaypointDto,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { PLANNER_API_BASE } from '@/planner/constants/PlannerConstants';
import type {
  LiveStats,
  PlannedTrackDetail,
  PlannedTrackSummary,
  RouteResponse,
  SidecarStatus,
  Waypoint,
} from '@/planner/types';

const backendUrl = import.meta.env.VITE_BACKEND_URL;

function getPlannerApi(): PlannerControllerApi {
  return new PlannerControllerApi(getApiConfiguration());
}

export async function fetchPlannerConfig(): Promise<{ profiles: string[]; defaultProfile: string; maxWaypoints: number }> {
  const raw = await getPlannerApi().config();
  return {
    profiles: Array.isArray(raw.profiles) ? raw.profiles as string[] : [],
    defaultProfile: typeof raw.defaultProfile === 'string' ? raw.defaultProfile : 'trekking',
    maxWaypoints: typeof raw.maxWaypoints === 'number' ? raw.maxWaypoints : 50,
  };
}

/**
 * The /route endpoint declares {@code ResponseEntity<?>} on the server side so
 * the generator emits {@code Promise<object>}. We still call it via axios to
 * preserve AbortSignal support and a properly typed response.
 */
export async function computeRoute(waypoints: Waypoint[], profile: string, signal?: AbortSignal): Promise<RouteResponse> {
  const body = {
    waypoints: waypoints.map((w): WaypointDto => ({ lat: w.lat, lng: w.lng })),
    profile,
  };
  const r = await apiClient.post<RouteResponse>(`${PLANNER_API_BASE}/route`, body, { signal });
  return r.data;
}

export async function fetchSidecarStatus(): Promise<SidecarStatus> {
  const raw = await getPlannerApi().status();
  return raw as unknown as SidecarStatus;
}

export interface SaveArgs {
  name: string;
  description?: string;
  profile: string;
  coordinates: [number, number, number][];
  /** Original user waypoints (lat/lng) so the plan can be re-loaded into the editor. */
  waypoints: { lat: number; lng: number }[];
}

export async function savePlannedRoute(args: SaveArgs): Promise<{ id: number; name: string; distanceM: number }> {
  const dto: SavePlannedTrackDto = {
    name: args.name,
    description: args.description,
    profile: args.profile,
    coordinates: args.coordinates,
    waypoints: args.waypoints,
  };
  const raw = await getPlannerApi().save({ savePlannedTrackDto: dto });
  return raw as unknown as { id: number; name: string; distanceM: number };
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
  }));
}

/**
 * Not yet exposed by the generated client (GET /api/planner/plans/{id} is a
 * new endpoint — the running server needs a restart + schema regen before it
 * appears). Kept on axios so the feature works against stale schemas too.
 */
export async function loadPlannedTrack(id: number): Promise<PlannedTrackDetail> {
  const r = await apiClient.get<PlannedTrackDetail>(`${PLANNER_API_BASE}/plans/${id}`);
  return r.data;
}

export async function deletePlannedTrack(id: number): Promise<void> {
  await getPlannerApi().deletePlan({ id });
}

/**
 * GPX download. We cannot use the generated client because its return type is
 * {@code Promise<string>} (it eagerly parses the body as text) and we need a
 * Blob + anchor-click to trigger the browser download.
 */
export function downloadPlannedTrackGpx(id: number, name: string): void {
  const auth = getAuthHeaderValue();
  fetch(`${backendUrl}${PLANNER_API_BASE}/plans/${id}/gpx`, {
    headers: { Authorization: auth },
    credentials: 'include',
  })
    .then((r) => r.blob())
    .then((blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${name || 'planned-route'}.gpx`;
      a.click();
      URL.revokeObjectURL(url);
    });
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
