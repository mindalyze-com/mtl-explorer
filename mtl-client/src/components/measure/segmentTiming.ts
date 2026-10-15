import type { CrossingDto } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

type Timing = Pick<CrossingDto, 'timingStatusSinceLastTriggerPoint' | 'timeInSecSinceLastTriggerPoint'>;

export function segmentTimingUnavailable(crossing: Timing): boolean {
  const status = crossing.timingStatusSinceLastTriggerPoint;
  return status != null && status !== 'AVAILABLE';
}

export function segmentTimingMessage(crossing: Timing): string {
  return crossing.timingStatusSinceLastTriggerPoint === 'RECORDING_GAP' ? 'Recording gap' : 'Time unavailable';
}

export function segmentPairKey(from: string, to: string): string {
  const encode = (name: string) => encodeURIComponent(name).replaceAll('-', '%2D');
  return `${encode(from)}-${encode(to)}`;
}
