import type {
  CrossingPointsResponseDto,
  SegmentCoreAnalysis,
  SegmentHealthAnalysis,
  SegmentComparison,
} from 'x8ing-mtl-api-typescript-fetch';
import type { Attempt } from '@/components/measure/useSegmentAttempts';
export const selectedSegment = { point1: 'A', point2: 'B', consolidated: true };
export function attempt(trackId = 1, visit = 1, duration = 60): Attempt {
  const start = new Date(Date.UTC(2026, 0, 1, 12, visit * 5));
  return {
    identity: {
      reference: `${trackId}-${visit}`,
      trackId,
      fromZone: 'A',
      fromVisit: visit,
      toZone: 'B',
      toVisit: visit,
      start,
      end: new Date(start.getTime() + duration * 1000),
      startPosition: 0,
      endPosition: 2,
      startCoordinate: [8, 47],
      endCoordinate: [8.01, 47],
      revision: `source-${trackId}`,
      zoneRevision: 'zones',
      timingStatus: 'AVAILABLE',
    },
    elapsedSeconds: duration,
    distanceMeters: 1000,
    averageSpeedKmh: 60,
  };
}
export function core(selected: Attempt): SegmentCoreAnalysis {
  const identity = selected.identity;
  const points = [0, 0.5, 1].map((f) => ({
    distanceMeters: 1000 * f,
    elapsedSeconds: selected.elapsedSeconds! * f,
    timestamp: new Date(identity.start!.getTime() + selected.elapsedSeconds! * f * 1000),
    longitude: 8 + 0.01 * f,
    latitude: 47,
    speedKmh: 25,
    altitudeMeters: 500 + 100 * f,
    powerWatts: f === 0 ? 900 : 100,
    energyWh: f * 20,
    slopePercent: 1,
    breakBefore: false,
  }));
  return {
    identity,
    summary: { ...selected, ascentMeters: 100, descentMeters: 0, maximumPowerWatts: 900, energyWh: 20 },
    points,
    geometry: points,
    sourcePointCount: points.length,
  };
}
export function health(selected: Attempt): SegmentHealthAnalysis {
  return {
    identity: selected.identity,
    healthRevision: 'health-1',
    heartRate: { averageBpm: 146, coveredSeconds: 30, elapsedSeconds: selected.elapsedSeconds!, coveragePercent: 50 },
    points: [{ distanceMeters: 200, elapsedSeconds: 12, averageBpm: 180, sampleCount: 2, breakBefore: false }],
    cardiacCost: { available: true, beatsPerKj: 12, wattsPerBpm: 1.4, coveredMovingSeconds: 20, coveragePercent: 80 },
    availability: { state: 'PARTIAL', coveredSeconds: 30 },
  };
}
export function comparison(selected: Attempt, reference: Attempt): SegmentComparison {
  return {
    identity: selected.identity,
    referenceIdentity: reference.identity,
    method: 'EQUAL_TRAVELED_DISTANCE',
    finishDifferenceSeconds: -5,
    supportedDistanceMeters: 1000,
    points: [
      { distanceMeters: 0, differenceSeconds: 0 },
      { distanceMeters: 1000, differenceSeconds: 4.09 },
    ],
    ghostPoints: ghostPoints(selected, reference),
  };
}
/** Both fixture attempts keep a constant pace over 1000 m, so the ghost follows the elapsed-time ratio. */
export function ghostPoints(selected: Attempt, reference: Attempt) {
  return [0, 500, 1000].map((referenceDistanceMeters) => {
    const referenceElapsedSeconds = (referenceDistanceMeters * reference.elapsedSeconds!) / 1000;
    const finished = referenceElapsedSeconds > selected.elapsedSeconds!;
    const distanceMeters = finished ? 1000 : (referenceElapsedSeconds / selected.elapsedSeconds!) * 1000;
    return {
      referenceDistanceMeters,
      referenceElapsedSeconds,
      distanceMeters,
      leadMeters: distanceMeters - referenceDistanceMeters,
      state: finished ? 'FINISHED' : 'IN_PROGRESS',
      breakBefore: false,
      positionMeaning: 'ARRIVAL',
    };
  });
}
export function result(...attempts: Attempt[]): CrossingPointsResponseDto {
  const ids = [...new Set(attempts.map((a) => a.identity.trackId))];
  return {
    crossings: Object.fromEntries(
      ids.map((id) => {
        const visits = attempts.filter((a) => a.identity.trackId === id);
        return [
          id,
          {
            gpsTrack: { id, indexedFile: { name: `Synthetic ${id}` }, startDate: visits[0].identity.start },
            attempts: visits,
            segmentSummaries: [
              {
                fromZone: 'A',
                toZone: 'B',
                visitCount: visits.length,
                elapsedCount: visits.length,
                speedCount: visits.length,
                distanceCount: visits.length,
                meanElapsedSeconds: 120,
                meanDistanceMeters: 1000,
                meanVisitSpeedKmh: 40,
              },
            ],
            crossings: visits.flatMap((a) => [
              { triggerPoint: { name: 'A' }, visitNumber: a.identity.fromVisit },
              {
                triggerPoint: { name: 'B' },
                visitNumber: a.identity.toVisit,
                timeInSecSinceLastTriggerPoint: a.elapsedSeconds,
                distanceInMeterSinceLastTriggerPoint: a.distanceMeters,
                avgSpeedSinceLastTriggerPoint: a.averageSpeedKmh,
              },
            ]),
          },
        ];
      })
    ),
    segmentsStats: [{ point1: 'A', point2: 'B', label: 'A - B', count: ids.length }],
  };
}
