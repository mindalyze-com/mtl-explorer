export type SegmentTrackPoint = {
  id?: number;
  distanceInMeterSinceStart?: number | null;
  durationSinceStart?: number | null;
  pointLongLat?: { coordinates?: number[] } | null;
};

export type SegmentSelectionCode = {
  point1?: string;
  point2?: string;
  consolidated?: boolean;
  p1Visit?: number;
  p2Visit?: number;
};

export function segmentSelectionKey(selection: { code: SegmentSelectionCode }): string {
  const code = selection.code;
  if (code.consolidated === false) {
    return `${String(code.point1)}${String(code.p1Visit)}-${String(code.point2)}${String(code.p2Visit)}`;
  }
  return `${String(code.point1)}||${String(code.point2)}`;
}

export type VisitSegmentCrossing = {
  triggerPoint: { name: string };
};

export type VisitSegmentOption = {
  name: string;
  count: number;
  code: SegmentSelectionCode;
};

export function buildVisitSegmentOptions<T extends VisitSegmentCrossing>(
  crossingGroups: Iterable<readonly T[]>
): VisitSegmentOption[] {
  const segmentMap = new Map<string, VisitSegmentOption>();
  for (const crossings of crossingGroups) {
    const visitsByTrigger = new Map<string, number>();
    let previous: T | null = null;
    let previousVisit: number | null = null;

    for (const crossing of crossings) {
      const point2 = crossing.triggerPoint.name;
      const point2Visit = (visitsByTrigger.get(point2) ?? 0) + 1;
      visitsByTrigger.set(point2, point2Visit);

      if (previous && previousVisit != null) {
        const point1 = previous.triggerPoint.name;
        const key = `${point1}${previousVisit}-${point2}${point2Visit}`;
        const existing = segmentMap.get(key);
        if (existing) {
          existing.count += 1;
        } else {
          segmentMap.set(key, {
            name: `${point1}${previousVisit} - ${point2}${point2Visit}`,
            count: 1,
            code: {
              point1,
              p1Visit: previousVisit,
              point2,
              p2Visit: point2Visit,
              consolidated: false,
            },
          });
        }
      }

      previous = crossing;
      previousVisit = point2Visit;
    }
  }
  return Array.from(segmentMap.values());
}

export function validTrackPointCoordinates(
  point: { pointLongLat?: { coordinates?: number[] } | null } | null | undefined
): [number, number] | null {
  const coords = point?.pointLongLat?.coordinates;
  if (!Array.isArray(coords) || coords.length < 2) return null;
  const lng = Number(coords[0]);
  const lat = Number(coords[1]);
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) return null;
  if (Math.abs(lng) > 180 || Math.abs(lat) > 90) return null;
  return [lng, lat];
}

export type SegmentSliceCrossing<T extends SegmentTrackPoint = SegmentTrackPoint> = {
  gpsTrackDataPoint?: (T & { id?: number }) | null;
  distanceInMeterSinceLastTriggerPoint?: number | null;
  timeInSecSinceLastTriggerPoint?: number | null;
};

export type SegmentSliceInvalidReason = 'missing_crossings' | 'invalid_coordinates' | 'no_extent' | 'not_enough_points';

export type SegmentSliceResult<T extends SegmentTrackPoint = SegmentTrackPoint> =
  | {
      valid: true;
      distanceM: number;
      durationSec: number;
      points: T[];
      reason: null;
    }
  | {
      valid: false;
      distanceM: number;
      durationSec: number;
      points: T[];
      reason: SegmentSliceInvalidReason;
    };

export type SegmentSliceOptions = {
  requirePositiveDistanceOrGeometry?: boolean;
  requirePositiveDuration?: boolean;
};

const EPSILON_SECONDS = 0.001;
const EPSILON_METERS = 0.01;
const EPSILON_COORDINATES = 1e-10;

export function normalizeSegmentSlice<T extends SegmentTrackPoint>(
  fetchedPoints: T[],
  crossingPair: [SegmentSliceCrossing<T>, SegmentSliceCrossing<T>] | undefined,
  options: SegmentSliceOptions = {}
): SegmentSliceResult<T> {
  const requirePositiveDuration = options.requirePositiveDuration ?? false;
  const requirePositiveDistanceOrGeometry = options.requirePositiveDistanceOrGeometry ?? true;

  if (!Array.isArray(crossingPair) || crossingPair.length < 2) {
    return invalid([], 0, 0, 'missing_crossings');
  }

  const start = crossingPair[0]?.gpsTrackDataPoint;
  const end = crossingPair[1]?.gpsTrackDataPoint;
  if (!start || !end) {
    return invalid([], 0, 0, 'missing_crossings');
  }

  const fallbackDurationSec = positiveNumber(crossingPair[1]?.timeInSecSinceLastTriggerPoint);
  const fallbackDistanceM = positiveNumber(crossingPair[1]?.distanceInMeterSinceLastTriggerPoint);
  const startPoint = cloneTrackPoint(start);
  const endPoint = cloneTrackPoint(end);

  patchFlatEndpointMetric(startPoint, endPoint, 'durationSinceStart', fallbackDurationSec);
  patchFlatEndpointMetric(startPoint, endPoint, 'distanceInMeterSinceStart', fallbackDistanceM);

  const durationSec = segmentDurationSec(startPoint, endPoint, fallbackDurationSec);
  const distanceM = segmentDistanceM(startPoint, endPoint, fallbackDistanceM);
  const points = buildNormalizedPoints(fetchedPoints, startPoint, endPoint);
  const hasDuration = durationSec > EPSILON_SECONDS;
  const hasDistanceOrGeometry = distanceM > EPSILON_METERS || coordinatesDiffer(startPoint, endPoint);

  if (!hasFinitePointCoordinates(startPoint) || !hasFinitePointCoordinates(endPoint)) {
    return invalid(points, durationSec, distanceM, 'invalid_coordinates');
  }
  if (points.length < 2 || sameTrackPointSample(points[0], points[points.length - 1])) {
    return invalid(points, durationSec, distanceM, 'not_enough_points');
  }
  if (requirePositiveDuration && !hasDuration) {
    return invalid(points, durationSec, distanceM, 'no_extent');
  }
  if (requirePositiveDistanceOrGeometry && !hasDistanceOrGeometry) {
    return invalid(points, durationSec, distanceM, 'no_extent');
  }

  return {
    valid: true,
    distanceM,
    durationSec,
    points,
    reason: null,
  };
}

function buildNormalizedPoints<T extends SegmentTrackPoint>(fetchedPoints: T[], start: T, end: T) {
  const inner = filterInnerPoints(fetchedPoints, start, end);
  return dedupeAdjacentTrackPoints([start, ...inner, end]);
}

function filterInnerPoints<T extends SegmentTrackPoint>(points: T[], start: T, end: T) {
  if (!Array.isArray(points) || points.length === 0) return [];
  const validPoints = points.filter(hasFinitePointCoordinates);

  const startDuration = numberOrNull(start.durationSinceStart);
  const endDuration = numberOrNull(end.durationSinceStart);
  if (startDuration != null && endDuration != null && endDuration > startDuration) {
    return validPoints.filter((point) => {
      const duration = numberOrNull(point.durationSinceStart);
      return duration != null && duration > startDuration + EPSILON_SECONDS && duration < endDuration - EPSILON_SECONDS;
    });
  }

  const startDistance = numberOrNull(start.distanceInMeterSinceStart);
  const endDistance = numberOrNull(end.distanceInMeterSinceStart);
  if (startDistance != null && endDistance != null && endDistance > startDistance) {
    return validPoints.filter((point) => {
      const distance = numberOrNull(point.distanceInMeterSinceStart);
      return distance != null && distance > startDistance + EPSILON_METERS && distance < endDistance - EPSILON_METERS;
    });
  }

  return validPoints.filter((point) => !sameTrackPointSample(point, start) && !sameTrackPointSample(point, end));
}

function dedupeAdjacentTrackPoints<T extends SegmentTrackPoint>(points: T[]) {
  const deduped: T[] = [];
  for (const point of points) {
    const previous = deduped[deduped.length - 1];
    if (!previous || !sameTrackPointSample(previous, point)) {
      deduped.push(point);
    }
  }
  return deduped;
}

function sameTrackPointSample(a: SegmentTrackPoint | undefined, b: SegmentTrackPoint | undefined) {
  if (!a || !b) return false;
  const sameId = a.id != null && b.id != null && a.id === b.id;
  const sameDuration = !positiveDelta(a.durationSinceStart, b.durationSinceStart, EPSILON_SECONDS);
  const sameDistance = !positiveDelta(a.distanceInMeterSinceStart, b.distanceInMeterSinceStart, EPSILON_METERS);
  return sameId && sameDuration && sameDistance && !coordinatesDiffer(a, b);
}

function coordinatesDiffer(a: SegmentTrackPoint, b: SegmentTrackPoint) {
  const aCoords = validTrackPointCoordinates(a);
  const bCoords = validTrackPointCoordinates(b);
  if (!aCoords || !bCoords) {
    return false;
  }
  return (
    Math.abs(aCoords[0] - bCoords[0]) > EPSILON_COORDINATES || Math.abs(aCoords[1] - bCoords[1]) > EPSILON_COORDINATES
  );
}

function hasFinitePointCoordinates(point: SegmentTrackPoint) {
  return validTrackPointCoordinates(point) != null;
}

function patchFlatEndpointMetric<T extends SegmentTrackPoint>(
  start: T,
  end: T,
  key: 'distanceInMeterSinceStart' | 'durationSinceStart',
  fallbackDelta: number | null
) {
  if (fallbackDelta == null || fallbackDelta <= 0) return;
  const startValue = numberOrNull(start[key]);
  const endValue = numberOrNull(end[key]);
  if (startValue == null) return;
  if (endValue == null || endValue <= startValue) {
    end[key] = startValue + fallbackDelta;
  }
}

function segmentDurationSec(start: SegmentTrackPoint, end: SegmentTrackPoint, fallbackDurationSec: number | null) {
  const delta = positiveDelta(start.durationSinceStart, end.durationSinceStart, EPSILON_SECONDS);
  return delta ?? fallbackDurationSec ?? 0;
}

function segmentDistanceM(start: SegmentTrackPoint, end: SegmentTrackPoint, fallbackDistanceM: number | null) {
  const delta = positiveDelta(start.distanceInMeterSinceStart, end.distanceInMeterSinceStart, EPSILON_METERS);
  return delta ?? fallbackDistanceM ?? 0;
}

function positiveDelta(from: number | null | undefined, to: number | null | undefined, epsilon: number) {
  const start = numberOrNull(from);
  const end = numberOrNull(to);
  if (start == null || end == null) return null;
  const delta = end - start;
  return delta > epsilon ? delta : null;
}

function positiveNumber(value: number | null | undefined) {
  const number = numberOrNull(value);
  return number != null && number > 0 ? number : null;
}

function numberOrNull(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function cloneTrackPoint<T extends SegmentTrackPoint>(point: T) {
  return { ...point } as T;
}

function invalid<T extends SegmentTrackPoint>(
  points: T[],
  durationSec: number,
  distanceM: number,
  reason: SegmentSliceInvalidReason
): SegmentSliceResult<T> {
  return {
    valid: false,
    distanceM,
    durationSec,
    points,
    reason,
  };
}
