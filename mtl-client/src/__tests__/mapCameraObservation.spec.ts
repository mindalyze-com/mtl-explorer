import { describe, it, expect } from 'vitest';
import type { Map } from 'maplibre-gl';
import { observeMapCamera } from '@/components/map/mapCameraObservation';

describe('map camera observation', () => {
  it('publishes actual camera values initially and after camera movement', () => {
    const canvas = document.createElement('canvas');
    const listeners = new globalThis.Map<string, () => void>();
    let longitude = 8;
    const map = {
      getCenter: () => ({ lng: longitude, lat: 47 }),
      getZoom: () => 12,
      getPitch: () => 0,
      getBearing: () => 0,
      getPadding: () => ({ left: 3, right: 4, top: 5, bottom: 6 }),
      getCanvas: () => canvas,
      on: (event: string, callback: () => void) => listeners.set(event, callback),
    } as unknown as Map;
    observeMapCamera(map);
    expect(JSON.parse(canvas.dataset.mtlCamera!)).toMatchObject({ longitude: 8, latitude: 47, zoom: 12 });
    longitude = 9;
    listeners.get('moveend')!();
    expect(JSON.parse(canvas.dataset.mtlCamera!)).toMatchObject({
      longitude: 9,
      padding: { left: 3, right: 4, top: 5, bottom: 6 },
    });
  });
});
