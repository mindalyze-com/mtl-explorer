import { describe, expect, it } from 'vitest';
import {
  applyReplayCameraFrameWithScreenGuard,
  ReplayCameraScreenGuard,
} from '@/components/replay/replayCameraScreenGuard';
import { buildReplayPath } from '@/components/replay/trackReplayPath';
import type { ReplayCameraFrame } from '@/components/replay/trackReplayCamera';

type View = ReplayCameraFrame & {
  padding: { top: number; right: number; bottom: number; left: number };
};

class LinearScreenMap {
  center = { lng: 0, lat: 0 };
  zoom = 14;
  readonly canvas = elementWithSize(1280, 720);
  readonly appliedViews: View[] = [];

  getCanvas() {
    return this.canvas;
  }

  getCenter() {
    return this.center;
  }

  getZoom() {
    return this.zoom;
  }

  project(lngLat: [number, number] | { lng: number; lat: number }) {
    const lng = Array.isArray(lngLat) ? lngLat[0] : lngLat.lng;
    const lat = Array.isArray(lngLat) ? lngLat[1] : lngLat.lat;
    const scale = this.scale();
    return {
      x: 640 + (lng - this.center.lng) * scale,
      y: 360 - (lat - this.center.lat) * scale,
    };
  }

  unproject(point: [number, number] | { x: number; y: number }) {
    const x = Array.isArray(point) ? point[0] : point.x;
    const y = Array.isArray(point) ? point[1] : point.y;
    const scale = this.scale();
    return {
      lng: this.center.lng + (x - 640) / scale,
      lat: this.center.lat - (y - 360) / scale,
    };
  }

  jumpTo(view: View) {
    this.center = { lng: view.center[0], lat: view.center[1] };
    this.zoom = view.zoom;
    this.appliedViews.push(view);
  }

  private scale() {
    return 100 * 2 ** (this.zoom - 14);
  }
}

class NoisyScreenMap extends LinearScreenMap {
  projectionNoise = 0;

  project(lngLat: [number, number] | { lng: number; lat: number }) {
    const point = super.project(lngLat);
    return { x: point.x, y: point.y + this.projectionNoise };
  }
}

function elementWithSize(width: number, height: number): HTMLElement {
  const el = document.createElement('div');
  Object.defineProperty(el, 'clientWidth', { value: width });
  Object.defineProperty(el, 'clientHeight', { value: height });
  Object.defineProperty(el, 'getBoundingClientRect', {
    value: () => new DOMRect(0, 0, width, height),
  });
  return el;
}

describe('replay camera screen guard', () => {
  it('moves the camera when the current replay samples would land below the visible map area', () => {
    const map = new LinearScreenMap();
    const path = buildReplayPath({
      coordinates: [
        [0, -3],
        [0, -2.8],
      ],
    });

    applyReplayCameraFrameWithScreenGuard({
      map,
      path,
      progress: 0,
      frame: {
        center: [0, 0],
        bearing: 0,
        pitch: 55,
        zoom: 14,
      },
      padding: { top: 32, right: 32, bottom: 350, left: 32 },
      applyFrame: (view) => map.jumpTo(view),
    });

    const currentPoint = map.project([0, -3]);

    expect(map.appliedViews.length).toBeGreaterThan(1);
    expect(currentPoint.y).toBeLessThanOrEqual(370);
  });

  it('zooms out before shifting when the guarded route samples are larger than the visible area', () => {
    const map = new LinearScreenMap();
    const path = buildReplayPath({
      coordinates: [
        [0, -30],
        [0, 30],
      ],
    });

    applyReplayCameraFrameWithScreenGuard({
      map,
      path,
      progress: 0.5,
      frame: {
        center: [0, 0],
        bearing: 0,
        pitch: 55,
        zoom: 14,
      },
      padding: { top: 32, right: 32, bottom: 350, left: 32 },
      applyFrame: (view) => map.jumpTo(view),
    });

    expect(map.zoom).toBeLessThan(14);
  });

  it('eases the residual correction so projection noise does not produce frame-to-frame jitter', () => {
    const path = buildReplayPath({
      coordinates: [
        [0, -3],
        [0, -2.8],
      ],
    });
    const padding = { top: 32, right: 32, bottom: 350, left: 32 };
    const frame: ReplayCameraFrame = { center: [0, 0], bearing: 0, pitch: 55, zoom: 14 };

    const committedLat = (map: NoisyScreenMap, guard?: ReplayCameraScreenGuard): number[] => {
      const lats: number[] = [];
      for (let i = 0; i < 30; i += 1) {
        // Alternating projection noise emulates terrain DEM tiles bobbing under samples.
        map.projectionNoise = i % 2 === 0 ? 40 : -40;
        const view = (guard ?? new ReplayCameraScreenGuard()).apply({
          map,
          path,
          progress: 0,
          frame,
          padding,
          applyFrame: (v) => map.jumpTo(v),
        });
        lats.push(view.center[1]);
      }
      return lats;
    };

    const maxStep = (lats: number[]): number =>
      lats.slice(1).reduce((max, lat, idx) => Math.max(max, Math.abs(lat - lats[idx])), 0);

    const easedSteps = maxStep(committedLat(new NoisyScreenMap(), new ReplayCameraScreenGuard()));
    const snapSteps = maxStep(committedLat(new NoisyScreenMap()));

    // The persistent eased guard absorbs the oscillation; a fresh-snap guard does not.
    expect(easedSteps).toBeLessThan(snapSteps * 0.5);
  });
});
