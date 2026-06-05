import { describe, expect, it } from 'vitest';
import { nearestByNumericValue, projectClickToTrackLine, valueAtFraction } from '@/components/map/trackLineHitTest';

describe('trackLineHitTest', () => {
  it('projects a click onto a sparse straight line in screen space', () => {
    const projection = projectClickToTrackLine({
      map: {
        project: ([lng, lat]: [number, number]) => ({ x: lng * 10_000, y: lat * 10_000 }),
      },
      clickPoint: { x: 150, y: 4 },
      lngLat: { lng: 0.015, lat: 0 },
      coordinates: [
        [0, 0],
        [0.03, 0],
      ],
      pixelTolerance: 12,
    });

    expect(projection?.fraction).toBeCloseTo(0.5);
    expect(projection?.anchor[0]).toBeCloseTo(0.015);
    expect(projection?.anchor[1]).toBe(0);
  });

  it('rejects clicks beyond the configured line tolerance', () => {
    const projection = projectClickToTrackLine({
      map: {
        project: ([lng, lat]: [number, number]) => ({ x: lng * 10_000, y: lat * 10_000 }),
      },
      clickPoint: { x: 150, y: 20 },
      lngLat: { lng: 0.015, lat: 0 },
      coordinates: [
        [0, 0],
        [0.03, 0],
      ],
      pixelTolerance: 12,
      meterTolerance: 1,
    });

    expect(projection).toBeNull();
  });

  it('finds nearest numeric items and interpolates ranges', () => {
    const items = [{ index: 0 }, { index: 50 }, { index: 100 }];

    expect(valueAtFraction({ min: 0, max: 100 }, 0.48)).toBe(48);
    expect(nearestByNumericValue(items, 48, (item) => item.index)).toEqual({ index: 50 });
  });
});
