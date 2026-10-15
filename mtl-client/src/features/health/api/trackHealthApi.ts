import { TrackHealthControllerApi } from 'x8ing-mtl-api-typescript-fetch';
import type { TrackHealthResponse } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { getApiConfiguration } from '@/utils/openApiClient';

export async function fetchTrackHealth(trackId: number, maxBuckets: number): Promise<TrackHealthResponse> {
  return new TrackHealthControllerApi(getApiConfiguration()).getTrackHealth({ trackId, maxBuckets });
}
