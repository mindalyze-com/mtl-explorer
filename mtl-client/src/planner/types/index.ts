/** Shared types for the planner feature. */
import type { BRouterStatusDto } from 'x8ing-mtl-api-typescript-fetch';

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
  profile: string | null;
}

export type SidecarStatus = BRouterStatusDto;
