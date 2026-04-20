import { apiClient } from "@/utils/apiClient";

const backendUrl = import.meta.env.VITE_BACKEND_URL;

export type RawMediaPoint = {
  id: number;
  exifGpsLocationLat: number;  // may be wrong order from server
  exifGpsLocationLong: number; // may be wrong order from server
  title?: string;
  fileName?: string;
};

/** Lightweight point returned by the bounds endpoint */
export type MediaBoundsPoint = {
  id: number;
  lat: number;
  lng: number;
};

/** Full media info returned by /get/{id} */
export type MediaInfo = {
  id: number;
  indexedFile: { name: string; path: string; fullPath: string } | null;
  exifDateImageTaken: string | null;
  cameraMake: string | null;
  cameraModel: string | null;
};

export async function getMediaPoints(): Promise<RawMediaPoint[]> {
  const resp = await apiClient.get('api/media/get-media-with-location-info');
  return resp.data as RawMediaPoint[];
}

/** Fetch media points within a map bounding box. Supports AbortController for cancellation. */
export async function getMediaInBounds(
  minLat: number, minLng: number, maxLat: number, maxLng: number,
  signal?: AbortSignal
): Promise<MediaBoundsPoint[]> {
  const resp = await apiClient.get('api/media/get-media-in-bounds', {
    params: { minLat, minLng, maxLat, maxLng },
    signal,
  });
  return resp.data as MediaBoundsPoint[];
}

export function mediaContentUrl(id: number, maxSize?: number): string {
  // No auth token in the URL — the browser sends the mtl_jwt HttpOnly cookie automatically.
  // A stable URL (no session-specific query params) is required for HTTP cache hits.
  const base = `${backendUrl}api/media/get/${id}/content`;
  return maxSize ? `${base}?maxSize=${maxSize}` : base;
}

export async function getMediaInfo(id: number): Promise<MediaInfo> {
  const resp = await apiClient.get(`api/media/get/${id}`);
  return resp.data as MediaInfo;
}
