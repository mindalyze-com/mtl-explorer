import { apiClient } from '@/utils/apiClient';
import { apiUrl } from '@/utils/apiBase';
import { getApiConfiguration } from '@/utils/openApiClient';
import {
  MediaControllerApi,
  VideoTranscodeControllerApi,
  VideoTranscodeSessionRequestQualityEnum,
  type ManualMediaLocationRequest,
  type MediaDetailsDto,
  type MediaTrendItemPageDto,
  type MediaTrendItemsRequest,
  type MediaTrendRequest,
  type MediaTrendResponseDto,
  type MediaTimeCorrectionRequest,
  type TrackMediaPageDto,
  type VideoTranscodeSessionDto,
  type VideoTranscodeSessionRequest,
} from 'x8ing-mtl-api-typescript-fetch';

export type RawMediaPoint = {
  id: number;
  exifGpsLocationLat: number; // may be wrong order from server
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

/** User-facing file and capture metadata returned by /get/{id}. */
export type MediaInfo = MediaDetailsDto;

export const VIDEO_TRANSCODE_QUALITY_OPTIONS = [
  { value: VideoTranscodeSessionRequestQualityEnum.Auto, label: 'Auto' },
  { value: VideoTranscodeSessionRequestQualityEnum.P480, label: '480p' },
  { value: VideoTranscodeSessionRequestQualityEnum.P720, label: '720p' },
  { value: VideoTranscodeSessionRequestQualityEnum.P1080, label: '1080p' },
] as const;

export type VideoTranscodeQuality = NonNullable<VideoTranscodeSessionRequest['quality']>;
export type VideoTranscodeSession = VideoTranscodeSessionDto;

export type {
  MediaTrendItemDto,
  MediaTrendItemPageDto,
  MediaTrendItemsRequest,
  MediaTrendRequest,
  MediaTrendResponseDto,
  TrackMediaDto,
  TrackMediaPageDto,
} from 'x8ing-mtl-api-typescript-fetch';

export async function getMediaPoints(): Promise<RawMediaPoint[]> {
  const resp = await apiClient.get('api/media/get-media-with-location-info');
  return resp.data as RawMediaPoint[];
}

/** Fetch media points within a map bounding box. Supports AbortController for cancellation. */
export async function getMediaInBounds(
  minLat: number,
  minLng: number,
  maxLat: number,
  maxLng: number,
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
  const base = apiUrl(`api/media/get/${id}/content`);
  return maxSize ? `${base}?maxSize=${maxSize}` : base;
}

export async function getMediaInfo(id: number, signal?: AbortSignal): Promise<MediaInfo> {
  return new MediaControllerApi(getApiConfiguration()).getMediaDetails({ id }, { signal });
}

export async function getMediaByTrack(
  trackId: number,
  cameraOffsetSeconds = 0,
  page = 0,
  pageSize = 200,
  signal?: AbortSignal
): Promise<TrackMediaPageDto> {
  return new MediaControllerApi(getApiConfiguration()).getMediaByTrack(
    { trackId, cameraOffsetSeconds, page, pageSize },
    { signal }
  );
}

export async function getMediaTrends(request: MediaTrendRequest, signal?: AbortSignal): Promise<MediaTrendResponseDto> {
  return new MediaControllerApi(getApiConfiguration()).getMediaTrends({ mediaTrendRequest: request }, { signal });
}

export async function getMediaTrendItems(
  request: MediaTrendItemsRequest,
  signal?: AbortSignal
): Promise<MediaTrendItemPageDto> {
  return new MediaControllerApi(getApiConfiguration()).getMediaTrendItems(
    { mediaTrendItemsRequest: request },
    { signal }
  );
}

export async function saveMediaTimeCorrections(request: MediaTimeCorrectionRequest): Promise<void> {
  return new MediaControllerApi(getApiConfiguration()).saveMediaTimeCorrections({
    mediaTimeCorrectionRequest: request,
  });
}

export async function setManualMediaLocation(mediaId: number, request: ManualMediaLocationRequest): Promise<void> {
  return new MediaControllerApi(getApiConfiguration()).setManualMediaLocation({
    mediaId,
    manualMediaLocationRequest: request,
  });
}

export async function clearManualMediaLocation(mediaId: number): Promise<void> {
  return new MediaControllerApi(getApiConfiguration()).clearManualMediaLocation({ mediaId });
}

export async function createVideoTranscodeSession(
  mediaId: number,
  quality: VideoTranscodeQuality,
  signal?: AbortSignal
): Promise<VideoTranscodeSession> {
  return new VideoTranscodeControllerApi(getApiConfiguration()).createVideoTranscodeSession(
    { mediaId, videoTranscodeSessionRequest: { quality } },
    { signal }
  );
}

export async function getVideoTranscodeSession(
  sessionId: string,
  signal?: AbortSignal
): Promise<VideoTranscodeSession> {
  return new VideoTranscodeControllerApi(getApiConfiguration()).getVideoTranscodeSession({ sessionId }, { signal });
}

export async function cancelVideoTranscodeSession(sessionId: string): Promise<void> {
  return new VideoTranscodeControllerApi(getApiConfiguration()).cancelVideoTranscodeSession({ sessionId });
}

export function videoTranscodePlaylistUrl(playlistUrl: string): string {
  return apiUrl(playlistUrl);
}
