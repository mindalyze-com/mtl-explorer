import type { GpsTrack, SegmentGeometryPoint, SegmentGhostPoint } from 'x8ing-mtl-api-typescript-fetch';
export type SegmentAxis = 'distance' | 'time';
export type SegmentHover = { x: number; axis: SegmentAxis };
export const SEGMENT_POSITION_CURSOR_STEPS = 500;
export const SEGMENT_REPLAY_UNAVAILABLE_HINT = 'Replay needs timing and complete recorded geometry for every selected attempt.';
export function segmentStatusLabel(value?: string) {
  return value === 'AMBIGUOUS_DWELL'
    ? 'uncertain crossing time during a stop'
    : (value?.toLowerCase().replaceAll('_', ' ') ?? 'unavailable');
}
export type SegmentChartSeries = {
  id: string;
  name: string;
  color: string;
  dashStyle?: 'Solid' | 'Dash' | 'ShortDash';
  data: Array<[number, number | null]>;
  pointLabels?: Array<string | undefined>;
};

/** Read a rendered sample without extending coverage or crossing a server-defined gap. */
export function segmentSampleAt(series: SegmentChartSeries | undefined, x: number) {
  const points = series?.data ?? [];
  for (let i = 0; i < points.length; i++) {
    const right = points[i];
    if (right[0] === x) {
      // Break sentinels can precede a valid sample at the same position; stops use arrival first.
      if (right[1] == null && points[i + 1]?.[0] === x) continue;
      return { value: right[1], label: series?.pointLabels?.[i] };
    }
    if (right[0] > x) {
      const left = points[i - 1];
      if (!left || left[1] == null || right[1] == null) return null;
      const nearest = x - left[0] <= right[0] - x ? i - 1 : i;
      return { value: points[nearest][1], label: series?.pointLabels?.[nearest] };
    }
  }
  return null;
}
const METERS_PER_KILOMETER = 1_000;
const MILLISECONDS_PER_SECOND = 1_000;

export function segmentTrackName(track: GpsTrack | undefined, id: number) {
  return track?.trackName?.trim() || track?.metaName?.trim() || track?.indexedFile?.name || `Track ${id}`;
}

export function segmentChartX(
  point: { distanceMeters?: number | null; elapsedSeconds?: number | null },
  axis: SegmentAxis
): number | null {
  const value = axis === 'distance' ? point.distanceMeters : point.elapsedSeconds;
  return value == null || !Number.isFinite(value)
    ? null
    : axis === 'distance'
      ? value / METERS_PER_KILOMETER
      : value * MILLISECONDS_PER_SECOND;
}

/** Display interpolation only. Exact stopped distances use the first arrival, and breaks remain unsupported. */
export function segmentHoverSample(geometry: SegmentGeometryPoint[], hover: SegmentHover) {
  if (!Number.isFinite(hover.x)) return null;
  for (let i = 0; i < geometry.length; i++) {
    const right = geometry[i];
    const x = segmentChartX(right, hover.axis);
    if (x == null) continue;
    if (x === hover.x) return { position: coordinates(right), elapsedSeconds: elapsedSeconds(right) };
    if (x > hover.x && i > 0) {
      const left = geometry[i - 1];
      const leftX = segmentChartX(left, hover.axis);
      const a = coordinates(left),
        b = coordinates(right);
      if (right.breakBefore || leftX == null || leftX > hover.x || x <= leftX) return null;
      const fraction = (hover.x - leftX) / (x - leftX);
      let position: [number, number] | null = null;
      if (a && b) {
        const wrapped = ((b[0] - a[0] + 540) % 360) - 180;
        const longitude = ((a[0] + wrapped * fraction + 540) % 360) - 180;
        position = [longitude, a[1] + (b[1] - a[1]) * fraction];
      }
      const leftTime = elapsedSeconds(left),
        rightTime = elapsedSeconds(right);
      return {
        position,
        elapsedSeconds:
          leftTime != null && rightTime != null && rightTime >= leftTime
            ? leftTime + (rightTime - leftTime) * fraction
            : null,
      };
    }
  }
  return null;
}
export function segmentHoverPosition(geometry: SegmentGeometryPoint[], hover: SegmentHover): [number, number] | null {
  return segmentHoverSample(geometry, hover)?.position ?? null;
}

/** The server's nearest ghost sample at a reference distance; arrival wins at a stop and breaks are never crossed. */
export function segmentGhostAt(points: SegmentGhostPoint[] | undefined, referenceKm: number): SegmentGhostPoint | null {
  const ghosts = points ?? [];
  const x = (point: SegmentGhostPoint) => (point.referenceDistanceMeters ?? NaN) / METERS_PER_KILOMETER;
  for (let i = 0; i < ghosts.length; i++) {
    const right = ghosts[i];
    if (x(right) === referenceKm) return right;
    if (x(right) > referenceKm) {
      const left = ghosts[i - 1];
      if (!left || right.breakBefore) return null;
      return referenceKm - x(left) <= x(right) - referenceKm ? left : right;
    }
  }
  return null;
}

/** Route lines between two traveled distances of one attempt, split at recording breaks. */
export function segmentGeometrySlice(geometry: SegmentGeometryPoint[], fromKm: number, toKm: number): number[][][] {
  const [start, end] = fromKm <= toKm ? [fromKm, toKm] : [toKm, fromKm];
  const lines: number[][][] = [];
  let line: number[][] = [];
  const finish = () => {
    if (line.length > 1) lines.push(line);
    line = [];
  };
  const first = segmentHoverPosition(geometry, { x: start, axis: 'distance' });
  if (first) line.push(first);
  for (const point of geometry) {
    const km = segmentChartX(point, 'distance');
    if (km == null || km <= start) continue;
    if (km >= end) break;
    if (point.breakBefore) finish();
    const position = coordinates(point);
    if (position) line.push(position);
    else finish();
  }
  const last = segmentHoverPosition(geometry, { x: end, axis: 'distance' });
  if (last) line.push(last);
  finish();
  return lines;
}
function elapsedSeconds(point: SegmentGeometryPoint): number | null {
  const value = point.elapsedSeconds;
  return value != null && Number.isFinite(value) && value >= 0 ? value : null;
}
function coordinates(point: SegmentGeometryPoint): [number, number] | null {
  const { longitude, latitude } = point;
  return longitude != null &&
    latitude != null &&
    Number.isFinite(longitude) &&
    Number.isFinite(latitude) &&
    Math.abs(longitude) <= 180 &&
    Math.abs(latitude) <= 90
    ? [longitude, latitude]
    : null;
}
