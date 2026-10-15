import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';

/** Use the same imported-name fallbacks in track lists and map interactions. */
export function trackDisplayName(track: GpsTrack | null | undefined, fallbackId = track?.id): string {
  return (
    [track?.trackName, track?.metaName, track?.trackDescription, track?.metaDescription]
      .map((value) => value?.trim())
      .find(Boolean) || `Track ${fallbackId ?? ''}`.trim()
  );
}
