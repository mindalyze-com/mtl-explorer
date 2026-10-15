import { SourceReviewControllerApi, ResponseError } from 'x8ing-mtl-api-typescript-fetch';
import { getApiConfiguration } from '@/utils/openApiClient';
import { warnRequestFailure } from '@/utils/safeLogging';

export const reviewApi = () => new SourceReviewControllerApi(getApiConfiguration());

export async function reviewError(error: unknown, operation = 'request'): Promise<string> {
  if (!(error instanceof ResponseError) || error.response.status >= 500) {
    warnRequestFailure('Source review request failed', error, {
      topic1: 'review',
      topic2: 'reviewApi',
      topic3: operation,
    });
  }
  if (error instanceof ResponseError) {
    const body = await error.response
      .clone()
      .json()
      .catch(() => null);
    return body?.detail ?? body?.message ?? `The review request failed (${error.response.status}).`;
  }
  return error instanceof Error ? error.message : 'The review request failed. Try again.';
}

export function saveDownload(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export const stateLabel = (state: string | undefined) =>
  ({
    NEEDS_REVIEW: 'Needs review',
    EXCLUDED: 'Excluded from map and statistics',
    INCLUDED: 'Included',
    NOT_ASSESSED: 'Not assessed',
    EXCLUDED_MAP: 'Excluded from map',
    EXCLUDED_STATISTICS: 'Excluded from statistics',
    MIXED: 'Different section settings',
  })[state ?? ''] ?? 'Not assessed';

export const decisionLabel = (decision: string | undefined) =>
  ({
    INCLUDE: 'Include on map and in statistics',
    EXCLUDE_MAP: 'Exclude only from map',
    EXCLUDE_STATISTICS: 'Exclude only from statistics',
    EXCLUDE: 'Exclude from map and statistics',
    LATER: 'Review later',
  })[decision ?? ''] ??
  decision ??
  'No decision';

export const reasonLabel = (reason: string) =>
  ({
    SOURCE_UNREADABLE: 'Source unreadable',
    CONVERSION_FAILED: 'Conversion failed',
    PARSE_FAILED: 'Parsing failed',
    ASSESSMENT_LIMIT: 'Assessment limit',
    INVALID_VALUE: 'Invalid values',
    NO_OBSERVATIONS: 'No observations',
    TIME_MISSING: 'Missing timestamps',
    TIME_DUPLICATE: 'Repeated timestamps',
    TIME_BACKWARD: 'Backward timestamps',
    TIME_EXCURSION: 'Conflicting dates',
    TIME_ESTIMATED: 'Estimated timing',
    TIMING_UNRELIABLE: 'Unsupported timing',
    POSITION_INVALID: 'Invalid GPS positions',
    POSITION_JUMP: 'GPS jumps',
    POSITION_CORRECTION: 'Position corrections',
    WEAK_SOURCE_FIX: 'Weak GPS fixes',
    RECORDING_GAP: 'Possible recording gaps',
    RECORDING_BREAK: 'Recording break',
    ELEVATION_MISSING: 'Missing elevation',
    ELEVATION_SPIKE: 'Cleared elevation spikes',
    ELEVATION_CORRECTION: 'Elevation corrections',
    EXCESSIVE_FRAGMENTATION: 'Many derived fragments',
    INCONSISTENT_TIME_DOMAIN: 'Processed timing mismatch',
    INCONSISTENT_METRIC_TOTALS: 'Processed totals mismatch',
    PROCESSING_FAILED: 'Import processing failed',
    PROCESSING_EVIDENCE_UNAVAILABLE: 'Cleaning not assessed',
    PROCESSED_SOURCE_CHANGED: 'Derived data from an older source',
  })[reason] ?? reason;
