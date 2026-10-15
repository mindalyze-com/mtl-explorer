import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useTrackPointLayer, type TrackPointMapCandidate } from '@/components/map/composables/useTrackPointLayer';
import type { MapControllerRuntime } from '@/components/map/composables/mapControllerRuntime';
import { fetchTrackCanonicalPoints, fetchTrackPointsForRenderedShape } from '@/utils/ServiceHelper';
import { createTrackPointPopup } from '@/components/map/trackPointPopup';
import type { GpsTrackDataPoint } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

vi.mock('@/utils/ServiceHelper', () => ({
  fetchTrackPointsForRenderedShape: vi.fn(),
  fetchTrackCanonicalPoints: vi.fn(),
}));
vi.mock('@/components/map/trackPointPopup', () => ({
  createTrackPointPopup: vi.fn(() => ({ remove: vi.fn() })),
  updateTrackPointPopupContent: vi.fn(),
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((finish) => {
    resolve = finish;
  });
  return { promise, resolve };
}

function point(pointIndex = 0): GpsTrackDataPoint {
  return {
    pointIndex,
    pointIndexMax: 5,
    canonicalPointIndex: pointIndex,
    pointLongLat: { type: 'Point', coordinates: [8, 47] },
  } as GpsTrackDataPoint;
}

function controllerFixture() {
  const map = {
    getZoom: vi.fn(() => 17),
    getLayer: vi.fn(() => ({})),
    getLayoutProperty: vi.fn(() => 'visible'),
    queryRenderedFeatures: vi.fn(() => [] as unknown[]),
    project: vi.fn((coordinates: number[]) => ({ x: coordinates[0], y: coordinates[1] })),
    getCanvas: vi.fn(() => ({ style: { cursor: '' } })),
    on: vi.fn(),
    off: vi.fn(),
  };
  const controller = Object.assign(
    {
      overlayMap: map,
      trackPointsVisible: true,
      layerOpacities: { trackpoints: 100 },
      plannerToolActive: false,
      measureToolActive: false,
      geoDrawingParamDef: null,
      gpsTrackIdToFeature: new Map(),
      gpsTracksById: new Map([[7, { trackName: 'Morning walk' }]]),
      trackPrecisions: new Map([[7, 1]]),
      trackPointsDetailsCache: new Map(),
      trackPointsCanonicalCache: new Map(),
      trackPointsPopup: null,
      trackPointsPopupContext: null,
      trackPointLayerHandlers: null,
    },
    useTrackPointLayer()
  ) as unknown as MapControllerRuntime;
  return { controller, map };
}

function candidate(pointIndex = 0): TrackPointMapCandidate {
  return {
    id: `track-point:7:${pointIndex}`,
    kind: 'track-point',
    sourceId: 'track-points-layer',
    label: 'Morning walk',
    detail: `Point ${pointIndex + 1}`,
    coordinates: [8, 47],
    distancePx: 0,
    trackId: 7,
    pointIndex,
    precision: 1,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(fetchTrackPointsForRenderedShape).mockResolvedValue([point(0), point(1)]);
  vi.mocked(fetchTrackCanonicalPoints).mockResolvedValue([point(0), point(1)]);
});

describe('track point exploration provider', () => {
  it('collects all semantic points and retains distinct tracks while deduplicating rendered copies', () => {
    const { controller, map } = controllerFixture();
    const feature = {
      geometry: { type: 'Point', coordinates: [8, 47] },
      properties: { trackId: 7, pointIndex: 0 },
    };
    map.queryRenderedFeatures.mockReturnValue([
      feature,
      { ...feature, properties: { trackId: '7', pointIndex: '0' } },
      { ...feature, properties: { trackId: 7, pointIndex: 1 } },
      { ...feature, properties: { trackId: 8, pointIndex: 0 } },
      { ...feature, properties: { trackId: 7 } },
    ]);

    const candidates = controller.collectTrackPointCandidates({ x: 8, y: 47 }, 12);
    expect(candidates.map((entry) => entry.id)).toEqual(['track-point:7:0', 'track-point:7:1', 'track-point:8:0']);
    expect(candidates[0]).toMatchObject({ label: 'Morning walk', detail: 'Point 1', trackId: 7, pointIndex: 0 });
    expect(map.queryRenderedFeatures).toHaveBeenCalledWith(
      [
        [-4, 35],
        [20, 59],
      ],
      { layers: ['track-points-layer'] }
    );
  });

  it('retains hover handlers and removes independent exploration clicks', () => {
    const { controller, map } = controllerFixture();
    controller.attachTrackPointLayerHandlers();
    expect(map.on.mock.calls.map(([event]) => event)).toEqual(['mouseenter', 'mouseleave']);
    controller.detachTrackPointLayerHandlers();
    expect(map.off.mock.calls.map(([event]) => event)).toEqual(['mouseenter', 'mouseleave']);
  });

  it.each(['hidden', 'transparent', 'below zoom', 'measurement', 'planner', 'drawing'])(
    'excludes %s points',
    (mode) => {
      const { controller, map } = controllerFixture();
      if (mode === 'hidden') controller.trackPointsVisible = false;
      if (mode === 'transparent') controller.layerOpacities.trackpoints = 0;
      if (mode === 'below zoom') map.getZoom.mockReturnValue(15);
      if (mode === 'measurement') controller.measureToolActive = true;
      if (mode === 'planner') controller.plannerToolActive = true;
      if (mode === 'drawing') controller.geoDrawingParamDef = {} as never;
      expect(controller.collectTrackPointCandidates({ x: 8, y: 47 }, 12)).toEqual([]);
      expect(map.queryRenderedFeatures).not.toHaveBeenCalled();
    }
  );

  it('loads selected content into the shared surface context without opening a popup', async () => {
    const { controller } = controllerFixture();
    await controller.openTrackPointCandidate(candidate(), () => true, { presentation: 'context' });
    expect(controller.trackPointsPopupContext).toMatchObject({ trackId: 7, point: { pointIndex: 0 } });
    expect(createTrackPointPopup).not.toHaveBeenCalled();
  });

  it('preserves selected content when an already stale request attempts to start', async () => {
    const { controller } = controllerFixture();
    await controller.openTrackPointCandidate(candidate(), () => true, { presentation: 'context' });
    const selectedContext = controller.trackPointsPopupContext;
    await controller.showTrackPointPopup([8, 47], 7, 1, { isCurrent: () => false, presentation: 'context' });
    expect(controller.trackPointsPopupContext).toBe(selectedContext);
    expect(fetchTrackPointsForRenderedShape).toHaveBeenCalledTimes(1);
  });

  it('uses the clicked shape precision after the viewport loads another detail level', async () => {
    const { controller } = controllerFixture();
    const clicked = candidate(1);
    controller.trackPrecisions.set(7, 10);
    await controller.openTrackPointCandidate(clicked, () => true, { presentation: 'context' });
    expect(fetchTrackPointsForRenderedShape).toHaveBeenCalledWith(7, 1);
    expect(controller.trackPointsPopupContext?.point.pointIndex).toBe(1);
  });

  it('ignores a previous selected point when its simplified details arrive after the new selection', async () => {
    const { controller } = controllerFixture();
    const oldDetails = deferred<GpsTrackDataPoint[]>();
    const newDetails = deferred<GpsTrackDataPoint[]>();
    vi.mocked(fetchTrackPointsForRenderedShape)
      .mockReturnValueOnce(oldDetails.promise)
      .mockReturnValueOnce(newDetails.promise);
    const oldRequest = controller.openTrackPointCandidate(candidate(0), () => true, { presentation: 'context' });
    const newRequest = controller.openTrackPointCandidate(candidate(1), () => true, { presentation: 'context' });
    newDetails.resolve([point(1)]);
    await newRequest;
    oldDetails.resolve([point(0)]);
    await oldRequest;

    expect(controller.trackPointsPopupContext?.point.pointIndex).toBe(1);
    expect(controller.trackPointsDetailsCache.get('7|1')).toEqual([point(1)]);
    expect(fetchTrackCanonicalPoints).toHaveBeenCalledTimes(1);
  });

  it('does not apply canonical details after dismissal', async () => {
    const { controller } = controllerFixture();
    const canonical = deferred<GpsTrackDataPoint[]>();
    vi.mocked(fetchTrackCanonicalPoints).mockReturnValueOnce(canonical.promise);
    const request = controller.openTrackPointCandidate(candidate(), () => true, { presentation: 'context' });
    await vi.waitFor(() => expect(fetchTrackCanonicalPoints).toHaveBeenCalledTimes(1));
    controller.closeTrackPointPopup();
    canonical.resolve([point()]);
    await request;

    expect(controller.trackPointsPopupContext).toBeNull();
    expect(controller.trackPointsCanonicalCache.size).toBe(0);
    expect(createTrackPointPopup).not.toHaveBeenCalled();
  });

  it('guards non-abortable details with the originating interaction and map', async () => {
    const { controller } = controllerFixture();
    const details = deferred<GpsTrackDataPoint[]>();
    let current = true;
    vi.mocked(fetchTrackPointsForRenderedShape).mockReturnValueOnce(details.promise);
    const request = controller.openTrackPointCandidate(candidate(), () => current, { presentation: 'context' });
    current = false;
    controller.overlayMap = null;
    details.resolve([point()]);
    await request;

    expect(controller.trackPointsDetailsCache.size).toBe(0);
    expect(controller.trackPointsPopupContext).toBeNull();
    expect(fetchTrackCanonicalPoints).not.toHaveBeenCalled();
  });
});
