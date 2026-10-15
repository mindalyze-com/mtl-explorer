import { SegmentCardiacCostControllerApi } from 'x8ing-mtl-api-typescript-fetch';
import type { SegmentCardiacCostSummary } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { getApiConfiguration } from '@/utils/openApiClient';

export async function fetchSegmentCardiacCost(
  trackId: number,
  fromPointId: number,
  toPointId: number
): Promise<SegmentCardiacCostSummary> {
  return new SegmentCardiacCostControllerApi(getApiConfiguration()).getSegmentCardiacCost({
    trackId,
    fromPointId,
    toPointId,
  });
}
