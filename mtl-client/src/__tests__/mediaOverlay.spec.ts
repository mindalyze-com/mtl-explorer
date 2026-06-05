import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MediaOverlay } from '@/layers/MediaOverlay';
import { getMediaInBounds } from '@/repositories/mediaRepository';

const mediaRepositoryMock = vi.hoisted(() => ({
  getMediaInBounds: vi.fn(),
}));

const maplibreMock = vi.hoisted(() => {
  type LngLatLike = [number, number];

  class MockLngLatBounds {
    private readonly sw: { lng: number; lat: number };
    private readonly ne: { lng: number; lat: number };

    constructor(sw: LngLatLike, ne: LngLatLike) {
      assertValidLatitude(sw[1]);
      assertValidLatitude(ne[1]);
      this.sw = { lng: sw[0], lat: sw[1] };
      this.ne = { lng: ne[0], lat: ne[1] };
    }

    getSouthWest() {
      return this.sw;
    }

    getNorthEast() {
      return this.ne;
    }

    contains(point: { lng: number; lat: number }) {
      return (
        point.lng >= this.sw.lng && point.lng <= this.ne.lng && point.lat >= this.sw.lat && point.lat <= this.ne.lat
      );
    }
  }

  function assertValidLatitude(latitude: number): void {
    if (latitude < -90 || latitude > 90) {
      throw new Error('Invalid LngLat latitude value: must be between -90 and 90');
    }
  }

  return { LngLatBounds: MockLngLatBounds };
});

vi.mock('maplibre-gl', () => ({
  default: maplibreMock,
}));

vi.mock('@/repositories/mediaRepository', () => mediaRepositoryMock);

describe('MediaOverlay', () => {
  beforeEach(() => {
    vi.mocked(getMediaInBounds).mockReset();
    vi.mocked(getMediaInBounds).mockResolvedValue([{ id: 1, lat: 47.48, lng: 7.52 }]);
  });

  it('clamps padded fetch bounds at overview zoom before constructing MapLibre bounds', async () => {
    const map = createMapMock({
      getSouthWest: () => ({ lng: -180, lat: -85 }),
      getNorthEast: () => ({ lng: 180, lat: 85 }),
    });

    const overlay = new MediaOverlay(map, vi.fn());

    await expect(overlay.show()).resolves.toBeUndefined();

    const call = vi.mocked(getMediaInBounds).mock.calls[0];
    expect(call.slice(0, 4)).toEqual([-90, -900, 90, 900]);
    expect(call[4]).toBeInstanceOf(AbortSignal);
  });
});

function createMapMock(bounds: {
  getSouthWest: () => { lng: number; lat: number };
  getNorthEast: () => { lng: number; lat: number };
}) {
  const layers = new Set<string>();
  const sources = new Map<string, { setData: ReturnType<typeof vi.fn> }>();

  return {
    addSource: vi.fn((id: string) => {
      sources.set(id, { setData: vi.fn() });
    }),
    addLayer: vi.fn((layer: { id: string }) => {
      layers.add(layer.id);
    }),
    getBounds: vi.fn(() => bounds),
    getCanvas: vi.fn(() => ({ style: { cursor: '' } })),
    getLayer: vi.fn((id: string) => (layers.has(id) ? { id } : undefined)),
    getSource: vi.fn((id: string) => sources.get(id)),
    off: vi.fn(),
    on: vi.fn(),
    removeLayer: vi.fn((id: string) => {
      layers.delete(id);
    }),
    removeSource: vi.fn((id: string) => {
      sources.delete(id);
    }),
    queryRenderedFeatures: vi.fn(() => []),
  } as never;
}
