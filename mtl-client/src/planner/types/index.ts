/** Shared types for the planner feature. */

export interface Waypoint {
  /** Stable client-side id so Vue `v-for` keys survive drags/inserts. */
  id: string;
  lat: number;
  lng: number;
}

export interface LegResult {
  /** [lng, lat, elevationM] */
  coordinates: [number, number, number][];
  distanceM: number;
  ascentM: number;
  descentM: number;
  durationSec: number;
  cached: boolean;
}

export interface LiveStats {
  distanceM: number;
  ascentM: number;
  descentM: number;
  durationSec: number;
  legCount: number;
  anyLegCached: boolean;
}

export interface RouteResponse {
  legs: LegResult[];
  stats: LiveStats;
  profile: string;
}

export interface PlannedTrackSummary {
  id: number;
  name: string;
  description: string;
  distanceM: number;
  centerLat: number;
  centerLng: number;
  createDate: string;
}

export interface PlannedTrackDetail {
  id: number;
  name: string;
  description: string;
  profile: string | null;
  distanceM: number;
  /** Original user waypoints in placement order. Empty for legacy (pre-026) plans. */
  waypoints: { lat: number; lng: number }[];
  /** Routed polyline as [lng, lat, elevationM] triples. */
  coordinates: [number, number, number][];
}

export interface SidecarStatus {
  available: boolean;
  brouterRunning?: boolean;
  segmentsOnDisk?: number;
  segmentsQueued?: number;
  segmentsInProgress?: string[];
  segmentsCompletedThisRun?: string[];
  segmentsFailed?: Record<string, string>;
  reason?: string;
}
