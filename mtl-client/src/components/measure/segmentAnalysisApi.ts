import { SegmentAnalysisControllerApi } from 'x8ing-mtl-api-typescript-fetch';
import { getApiConfiguration } from '@/utils/openApiClient';
import {
  clampTrackDetailsChartPointCount,
  TRACK_DETAILS_CHART_POINTS_DEFAULT,
} from '@/utils/trackDetailsChartPointSettings';

export type SegmentGraphSettings = { pointCount: number; axis: 'distance' | 'time' };
const DEFAULT_GRAPH_SETTINGS: SegmentGraphSettings = {
  pointCount: TRACK_DETAILS_CHART_POINTS_DEFAULT,
  axis: 'distance',
};
const REQUEST_TIMEOUT_MS = 30_000;
const requestSignal = (signal?: AbortSignal) =>
  signal ? AbortSignal.any([signal, AbortSignal.timeout(REQUEST_TIMEOUT_MS)]) : AbortSignal.timeout(REQUEST_TIMEOUT_MS);
const api = () => new SegmentAnalysisControllerApi(getApiConfiguration());
export const fetchSegmentAnalysis = (reference: string, signal?: AbortSignal, settings = DEFAULT_GRAPH_SETTINGS) =>
  api().getSegmentAttemptAnalysis(
    {
      reference,
      maxBuckets: clampTrackDetailsChartPointCount(settings.pointCount),
      x: settings.axis === 'time' ? 'TIME' : 'DISTANCE',
    },
    { signal: requestSignal(signal) }
  );
export const fetchSegmentHealth = (reference: string, signal?: AbortSignal, settings = DEFAULT_GRAPH_SETTINGS) =>
  api().getSegmentAttemptHealth(
    { reference, maxBuckets: clampTrackDetailsChartPointCount(settings.pointCount) },
    { signal: requestSignal(signal) }
  );
export const fetchSegmentComparison = (reference: string, referenceAttempt: string, signal?: AbortSignal) =>
  api().getSegmentAttemptComparison({ reference, referenceAttempt }, { signal: requestSignal(signal) });
export const fetchSegmentTableHeartRate = (requestBody: string[], signal?: AbortSignal) =>
  api().getSegmentAttemptHeartRateSummaries({ requestBody }, { signal: requestSignal(signal) });
