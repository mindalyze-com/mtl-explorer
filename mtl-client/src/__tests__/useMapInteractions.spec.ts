import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useMapInteractions, watchMapInteractionContext } from '@/components/map/composables/useMapInteractions';
import { effectScope, reactive } from 'vue';
import {
  MAP_POINTER_TOLERANCE_PX,
  MAP_TOUCH_TOLERANCE_PX,
  mapTargetKey,
  type MapTargetCandidate,
} from '@/components/map/mapInteractionDecision';
import type { MapInteractionState } from '@/components/map/mapInteractionCoordinator';
import type { MapControllerRuntime } from '@/components/map/composables/mapControllerRuntime';
import { rankMapCategoryCandidates } from '@/components/map/mapTargetCategories';
import type { PoiMapCandidate } from '@/components/map/composables/useMapPois';
import type { MediaOverlayCandidate, MediaOverlaySelection } from '@/layers/MediaOverlay';
import { fetchTrackIdsWithinDistanceOfPoint, fetchTrackMediaOptionsWithinDistanceOfPoint } from '@/utils/ServiceHelper';
import {
  MAP_CLICK_CONFIRMATION_DELAY_MS,
  MAP_TRACK_MEDIA_OPTIONS_TIMEOUT_MS,
} from '@/components/map/composables/useMapInteractions';
import { warnSanitized } from '@/utils/safeLogging';
import { MAP_IDENTIFICATION_TIMEOUT_MS } from '@/components/map/mapInteractionCoordinator';
import { identifyWaymarkedTrails } from '@/components/map/waymarkedTrailProvider';
import { fetchSwissRouteDetails } from '@/components/map/swissRouteDetails';
import { useMediaAndHeatmap } from '@/components/map/composables/useMediaAndHeatmap';
import { TrackProcessingStatusDtoStateEnum } from 'x8ing-mtl-api-typescript-fetch';

const markerMock = vi.hoisted(() => ({ added: vi.fn(), removed: vi.fn() }));
const serviceMock = vi.hoisted(() => ({
  fetchTrackIdsWithinDistanceOfPoint: vi.fn(),
  fetchTrackMediaOptionsWithinDistanceOfPoint: vi.fn(),
}));
vi.mock('@/utils/ServiceHelper', () => serviceMock);
vi.mock('@/components/map/waymarkedTrailProvider', () => ({ identifyWaymarkedTrails: vi.fn() }));
vi.mock('@/components/map/swissRouteDetails', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/components/map/swissRouteDetails')>()),
  fetchSwissRouteDetails: vi.fn(),
}));
vi.mock('@/utils/safeLogging', () => ({ warnSanitized: vi.fn(), logSanitizedError: vi.fn() }));
vi.mock('maplibre-gl', () => ({
  Marker: class {
    setLngLat() {
      return this;
    }
    addTo() {
      markerMock.added();
      return this;
    }
    remove() {
      markerMock.removed();
      return this;
    }
  },
}));

const disposers: (() => void)[] = [];

beforeEach(() => {
  vi.useFakeTimers();
  vi.clearAllMocks();
  vi.mocked(identifyWaymarkedTrails).mockResolvedValue([]);
  vi.mocked(fetchSwissRouteDetails).mockResolvedValue({});
  vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mockResolvedValue([]);
  vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValue({
    trackIds: [],
    processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
  });
});

afterEach(() => {
  for (const dispose of disposers.splice(0)) dispose();
  vi.useRealTimers();
});

describe('main map exploration integration', () => {
  it.each(['context', 'style'])('discards hidden GPS details when %s invalidates retained results', async (cause) => {
    const test = setup();
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({
      trackIds: [42],
      processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
    });
    test.runtime.showTrackLinePointPopup.mockResolvedValueOnce(false);
    test.runtime.selectTrackById.mockImplementation((id: number) => {
      test.runtime.selectedTrackId = id;
    });
    test.runtime.deselectTrack.mockImplementation(() => {
      test.runtime.selectedTrackId = null;
    });
    test.runtime.openTrackDetails.mockImplementation((id: number) => {
      test.runtime.trackDetailsVisible = true;
      test.runtime.trackDetailsId = id;
    });
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    test.runtime.selectMapTarget(mapTargetKey(test.runtime.mapInteraction!.candidates[0]));
    await flushMicrotasks();
    test.runtime.showMapTargetChooser();
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.trackDetailsId).toBe(42);
    if (cause === 'style') test.fire('style.load', {});
    else {
      const context = reactive({ trackSetRevision: 1 });
      const scope = effectScope();
      scope.run(() =>
        watchMapInteractionContext(
          () => [context.trackSetRevision],
          () => test.runtime.endMapInteraction()
        )
      );
      disposers.push(() => scope.stop());
      context.trackSetRevision++;
    }
    expect(test.runtime.mapInteraction).toBeNull();
    expect(test.runtime.trackDetailsVisible).toBe(false);
    expect(test.runtime.trackDetailsId).toBeNull();
    expect(test.runtime.selectedTrackId).toBeNull();
  });

  it('preserves visible GPS details as standalone content when their results are invalidated', () => {
    const test = setup();
    test.runtime.mapInteraction = {
      id: 1,
      presentation: 'target',
    } as MapInteractionState;
    test.runtime.mapInteractionDetailKind = 'track';
    test.runtime.trackDetailsVisible = true;
    test.runtime.trackDetailsId = 42;
    test.runtime.endMapInteraction();
    expect(test.runtime.mapInteraction).toBeNull();
    expect(test.runtime.trackDetailsVisible).toBe(true);
    expect(test.runtime.trackDetailsId).toBe(42);
    expect(test.runtime.deselectTrack).not.toHaveBeenCalled();
  });

  it('preserves standalone GPS details opened before initial installation and reinstallation', () => {
    const test = setup();
    test.runtime.trackDetailsVisible = true;
    test.runtime.trackDetailsId = 42;
    test.runtime.installMapInteractions();
    expect(test.runtime.mapInteraction).toBeNull();
    expect(test.runtime.trackDetailsVisible).toBe(true);
    expect(test.runtime.trackDetailsId).toBe(42);
    test.runtime.installMapInteractions();
    expect(test.runtime.trackDetailsVisible).toBe(true);
    expect(test.runtime.trackDetailsId).toBe(42);
    test.runtime.disposeMapInteractions();
    expect(test.runtime.trackDetailsVisible).toBe(true);
    expect(test.runtime.trackDetailsId).toBe(42);
    expect(test.runtime.deselectTrack).not.toHaveBeenCalled();
    expect(test.runtime.closeTrackPointPopup).not.toHaveBeenCalled();
    expect(test.listeners.size).toBe(0);
  });

  it('still closes standalone GPS content when a new exploration click explicitly replaces it', () => {
    const test = setup();
    test.runtime.trackDetailsVisible = true;
    test.runtime.trackDetailsId = 42;
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    expect(test.runtime.trackDetailsVisible).toBe(false);
    expect(test.runtime.trackDetailsId).toBeNull();
    expect(test.runtime.deselectTrack).toHaveBeenCalledOnce();
  });

  it('closes dispatcher-owned GPS details and releases its marker, highlight, and candidate context on disposal', async () => {
    const test = setup();
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({
      trackIds: [42],
      processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
    });
    test.runtime.showTrackLinePointPopup.mockResolvedValueOnce(false);
    test.runtime.selectTrackById.mockImplementation((id: number) => {
      test.runtime.selectedTrackId = id;
    });
    test.runtime.deselectTrack.mockImplementation(() => {
      test.runtime.selectedTrackId = null;
    });
    test.runtime.openTrackDetails.mockImplementation((id: number) => {
      test.runtime.trackDetailsVisible = true;
      test.runtime.trackDetailsId = id;
    });
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    test.runtime.selectMapTarget(mapTargetKey(test.runtime.mapInteraction!.candidates[0]));
    await flushMicrotasks();
    expect(test.runtime.trackDetailsVisible).toBe(true);
    expect(test.runtime.trackDetailsId).toBe(42);
    expect(test.runtime.selectedTrackId).toBe(42);

    test.runtime.disposeMapInteractions();
    expect(test.runtime.trackDetailsVisible).toBe(false);
    expect(test.runtime.trackDetailsId).toBeNull();
    expect(test.runtime.selectedTrackId).toBeNull();
    expect(test.runtime.mapInteraction).toBeNull();
    expect(test.runtime.previewTrackById).toHaveBeenLastCalledWith(null);
    expect(test.media.releaseCandidateContext).toHaveBeenCalledOnce();
    expect(markerMock.removed).toHaveBeenCalledOnce();
    expect(test.listeners.size).toBe(0);
  });

  it('carries canonical track recording times without parsing displayed dates or accepting invalid timestamps', async () => {
    const test = setup();
    const startDate = new Date('2026-10-11T08:30:00Z');
    test.runtime.gpsTracksById.set(5, { startDate });
    test.runtime.gpsTracksById.set(7, { startDate: new Date(Number.NaN) });
    test.runtime.getTrackPopupMeta.mockReturnValue({
      id: 5,
      name: 'Morning walk',
      displayName: 'Morning walk',
      description: '',
      activityType: '',
      date: '02/03/2026',
    });
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({
      trackIds: [5, 6, 7],
      processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
    });
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    const candidates = test.runtime.mapInteraction!.candidates;
    expect(candidates.find((candidate) => candidate.id === '5')).toMatchObject({
      recordedAt: startDate.getTime(),
      detail: '02/03/2026',
    });
    expect(candidates.find((candidate) => candidate.id === '6')?.recordedAt).toBeUndefined();
    expect(candidates.find((candidate) => candidate.id === '7')?.recordedAt).toBeUndefined();
  });

  it('keeps total track metrics separate from click proximity and carries them before optional media facts arrive', async () => {
    const test = setup();
    const recordedAt = Date.UTC(2026, 9, 11, 8, 30);
    test.runtime.getTrackPopupMeta.mockReturnValue({
      id: 5,
      name: 'Morning ride',
      displayName: 'Morning ride',
      description: '',
      activityType: 'BICYCLE',
      date: '11/10/2026',
      recordedAt,
      lengthMeters: 48_700,
      ascentMeters: 420,
    });
    let resolveMediaFacts!: (value: { trackId: number; distanceMeters: number; matchedMediaCount: number }[]) => void;
    vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mockReturnValueOnce(
      new Promise((resolve) => {
        resolveMediaFacts = resolve;
      })
    );
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({
      trackIds: [5],
      processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
    });
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    expect(test.runtime.mapInteraction!.candidates[0]).toMatchObject({
      recordedAt,
      lengthMeters: 48_700,
      ascentMeters: 420,
    });
    expect(test.runtime.mapInteraction!.candidates[0].distanceMeters).toBeUndefined();

    resolveMediaFacts([{ trackId: 5, distanceMeters: 74, matchedMediaCount: 3 }]);
    await flushMicrotasks();
    expect(test.runtime.mapInteraction!.candidates[0]).toMatchObject({
      recordedAt,
      lengthMeters: 48_700,
      ascentMeters: 420,
      distanceMeters: 74,
      matchedMediaCount: 3,
    });
    expect(fetchTrackIdsWithinDistanceOfPoint).toHaveBeenCalledOnce();
    expect(fetchTrackMediaOptionsWithinDistanceOfPoint).toHaveBeenCalledOnce();
  });

  it.each([undefined, -1, Number.NaN, Number.POSITIVE_INFINITY])(
    'omits invalid track metrics (%s) while keeping known zero metrics',
    async (metric) => {
      const test = setup();
      test.runtime.getTrackPopupMeta.mockImplementation((id) => ({
        id,
        name: `Track ${id}`,
        displayName: `Track ${id}`,
        description: '',
        activityType: '',
        date: 'No date',
        lengthMeters: id === 5 ? metric : 0,
        ascentMeters: id === 5 ? metric : 0,
      }));
      vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({
        trackIds: [5, 6],
        processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
      });
      test.runtime.installMapInteractions();
      test.fire('click', clickEvent());
      await flushMicrotasks();
      const invalid = test.runtime.mapInteraction!.candidates.find((candidate) => candidate.id === '5');
      expect(invalid?.kind).toBe('track');
      if (invalid?.kind !== 'track') throw new Error('Expected GPS track candidate');
      expect(invalid.lengthMeters).toBeUndefined();
      expect(invalid.ascentMeters).toBeUndefined();
      expect(test.runtime.mapInteraction!.candidates.find((candidate) => candidate.id === '6')).toMatchObject({
        lengthMeters: 0,
        ascentMeters: 0,
      });
    }
  );

  it('supports component consumer registration before initialization, across map recreation, and cleanup', async () => {
    const test = setup();
    const collect = vi.fn(() => [poiCandidate()]);
    const cleanup = test.runtime.registerMapInteractionConsumer({
      id: 'custom-place-layer',
      label: 'Custom places',
      kind: 'local',
      collect,
    });
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    expect(test.runtime.mapInteraction?.candidates.map((candidate) => candidate.kind)).toEqual(['poi']);
    expect(collect).toHaveBeenCalledOnce();
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    expect(collect).toHaveBeenCalledTimes(2);
    cleanup();
    expect(test.runtime.mapInteraction).toBeNull();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    expect(collect).toHaveBeenCalledTimes(2);
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    expect(test.runtime.mapInteraction?.candidates).toEqual([]);
  });

  it('ends retained photo context safely when a component consumer changes', async () => {
    const test = setup();
    test.media.collectCandidates.mockReturnValue([photoCandidate()]);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    test.runtime.selectMapTarget(mapTargetKey(test.runtime.mapInteraction!.candidates[0]));
    await flushMicrotasks();
    expect(test.runtime.chooseMediaCollection).toHaveBeenCalledExactlyOnceWith('primary');
    test.runtime.mediaSheetVisible = true;
    const cleanup = test.runtime.registerMapInteractionConsumer({ id: 'new-layer', kind: 'local', collect: () => [] });
    expect(test.runtime.mapInteraction).toBeNull();
    expect(test.runtime.mediaSheetVisible).toBe(false);
    cleanup();
  });

  it('offers photo collection scopes in Photos and opens either directly without another chooser or lookup', async () => {
    const test = setup();
    test.runtime.tracksEnabled = false;
    const photo = photoCandidate();
    const viewport = [
      { id: 9, lat: 47.48, lng: 7.52 },
      { id: 10, lat: 47.49, lng: 7.53 },
      { id: 11, lat: 47.5, lng: 7.54 },
    ];
    photo.viewportMediaPoints = viewport;
    test.media.collectCandidates.mockReturnValue([photo]);
    test.media.selectCandidate.mockImplementation(async (_candidate, isCurrent, scope) =>
      isCurrent()
        ? {
            ...photoSelection(9),
            mediaIds: scope === 'viewport' ? [9, 10, 11] : [9],
            mediaPoints: scope === 'viewport' ? viewport : [viewport[0]],
            totalMediaCount: scope === 'viewport' ? 3 : 1,
            viewportMediaPoints: viewport,
          }
        : null
    );
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    const originalId = test.runtime.mapInteraction!.id;
    const collections = test.runtime.mapInteraction!.candidates.filter((candidate) => candidate.kind === 'photo');
    expect(collections).toMatchObject([
      { label: 'At this location', count: 1, collectionScope: 'primary' },
      { id: 'media-viewport', label: 'Map view at tap', count: 3, collectionScope: 'viewport' },
    ]);
    expect(test.media.selectCandidate).not.toHaveBeenCalled();

    test.runtime.selectMapTarget(mapTargetKey(collections[1]));
    await flushMicrotasks();
    expect(test.media.selectCandidate).toHaveBeenCalledExactlyOnceWith(photo, expect.any(Function), 'viewport');
    expect(test.runtime.chooseMediaCollection).toHaveBeenCalledExactlyOnceWith('viewport');
    expect(test.runtime.mediaNavList).toEqual(viewport);
    expect(test.runtime.mediaNavScope).toBe('viewport');
    expect(test.runtime.mediaNavTotal).toBe(3);
    expect(test.runtime.mediaNavClusterId).toBeNull();
    expect(test.runtime.mediaSheetVisible).toBe(true);
    expect(test.runtime.mediaPendingSelection).toBeNull();
    expect(test.runtime.mediaSelectionSheetVisible).toBe(false);
    expect(test.runtime.openMediaSelection).not.toHaveBeenCalled();
    expect(fetchTrackMediaOptionsWithinDistanceOfPoint).not.toHaveBeenCalled();

    test.runtime.showMapTargetChooser();
    test.runtime.backFromMapTargetChooser();
    expect(test.runtime.mapInteraction?.id).toBe(originalId);
    expect(test.runtime.mapInteraction?.presentation).toBe('target');
    expect(test.media.selectCandidate).toHaveBeenCalledOnce();
    expect(test.runtime.mediaNavList).toEqual(viewport);
    test.runtime.showMapTargetChooser();
    test.runtime.selectMapTarget(mapTargetKey(collections[0]));
    await flushMicrotasks();
    expect(test.runtime.chooseMediaCollection).toHaveBeenLastCalledWith('primary');
    expect(test.runtime.mediaNavList).toEqual([viewport[0]]);
    expect(test.runtime.mediaNavScope).toBe('photo');
    expect(test.runtime.mediaNavTotal).toBe(1);
    expect(test.runtime.mapInteraction?.id).toBe(originalId);
  });

  it.each(['location', 'cluster'] as const)(
    'omits an identical captured viewport when complete %s membership is known, regardless of ID order',
    (kind) => {
      const test = setup();
      const photo = photoCandidate(9, kind);
      photo.mediaPoints = [
        { id: 9, lat: 47.48, lng: 7.52 },
        { id: 10, lat: 47.49, lng: 7.53 },
      ];
      photo.totalMediaCount = 2;
      photo.viewportMediaPoints = [...photo.mediaPoints].reverse();
      test.media.collectCandidates.mockReturnValue([photo]);
      test.runtime.installMapInteractions();
      test.fire('click', clickEvent());
      expect(test.runtime.mapInteraction?.candidates.filter((candidate) => candidate.kind === 'photo')).toMatchObject([
        { label: kind === 'cluster' ? 'This cluster' : 'At this location', count: 2, collectionScope: 'primary' },
      ]);
    }
  );

  it.each(['partial', 'unknown', 'different IDs'])(
    'keeps both collections when cluster membership is %s',
    (membership) => {
      const test = setup();
      const photo = photoCandidate(9, 'cluster');
      const points = [
        { id: 9, lat: 47.48, lng: 7.52 },
        { id: 10, lat: 47.49, lng: 7.53 },
      ];
      photo.totalMediaCount = membership === 'partial' ? 250 : 2;
      photo.mediaPoints = membership === 'unknown' ? [] : points;
      photo.viewportMediaPoints = membership === 'different IDs' ? [points[0], { ...points[1], id: 11 }] : points;
      test.media.collectCandidates.mockReturnValue([photo]);
      test.runtime.installMapInteractions();
      test.fire('click', clickEvent());
      expect(test.runtime.mapInteraction?.candidates.filter((candidate) => candidate.kind === 'photo')).toMatchObject([
        { label: 'This cluster', count: photo.totalMediaCount, collectionScope: 'primary' },
        { label: 'Map view at tap', count: 2, collectionScope: 'viewport' },
      ]);
    }
  );

  it('offers one viewport collection across overlapping local photo hits and none for unrelated clicks', () => {
    const test = setup();
    const viewport = [9, 10, 11].map((id) => ({ id, lat: 47.48, lng: 7.52 }));
    const first = { ...photoCandidate(9), viewportMediaPoints: viewport };
    const second = { ...photoCandidate(10), viewportMediaPoints: viewport };
    test.media.collectCandidates.mockReturnValueOnce([first, second]).mockReturnValueOnce([]);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    expect(test.runtime.mapInteraction?.candidates.filter((candidate) => candidate.kind === 'photo')).toMatchObject([
      { id: 'media:10', collectionScope: 'primary' },
      { id: 'media:9', collectionScope: 'primary' },
      { id: 'media-viewport', collectionScope: 'viewport', count: 3 },
    ]);
    test.fire('click', clickEvent());
    expect(test.runtime.mapInteraction?.candidates.some((candidate) => candidate.kind === 'photo')).toBe(false);
  });

  it('opens a bounded primary cluster page directly and retains its paging context', async () => {
    const test = setup();
    test.media.collectCandidates.mockReturnValue([photoCandidate(9, 'cluster')]);
    const firstPage = [9, 10].map((id) => ({ id, lat: 47.48, lng: 7.52 }));
    test.media.selectCandidate.mockResolvedValueOnce({
      ...photoSelection(9),
      kind: 'cluster',
      clusterId: 17,
      totalMediaCount: 250,
      mediaIds: [9, 10],
      mediaPoints: firstPage,
    });
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    const primary = test.runtime.mapInteraction!.candidates.find((candidate) => candidate.kind === 'photo')!;
    test.runtime.selectMapTarget(mapTargetKey(primary));
    await flushMicrotasks();
    expect(test.runtime.mediaNavList).toEqual(firstPage);
    expect(test.runtime.mediaNavScope).toBe('cluster');
    expect(test.runtime.mediaNavTotal).toBe(250);
    expect(test.runtime.mediaNavClusterId).toBe(17);
    expect(test.runtime.mediaNavPageSize).toBe(2);
    expect(test.runtime.openMediaSelection).not.toHaveBeenCalled();
    expect(test.runtime.mediaSelectionSheetVisible).toBe(false);
  });

  it('collects a photo and POI before opening content and appends delayed overlay hits to the selected surface', async () => {
    const test = setup();
    const overlay = deferred<MapTargetCandidate[]>();
    test.runtime.identifySwissMobilityRoutes.mockReturnValueOnce(overlay.promise);
    test.collectPois.mockReturnValue([poiCandidate()]);
    const photo = photoCandidate();
    test.media.collectCandidates.mockReturnValue([photo]);
    test.runtime.installMapInteractions();

    test.fire('click', clickEvent());
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.candidates.map((target) => target.kind)).toEqual(['photo', 'poi']);
    expect(test.runtime.openMediaSelection).not.toHaveBeenCalled();
    expect(test.media.selectCandidate).not.toHaveBeenCalled();

    const target = test.runtime.mapInteraction!.candidates.find((candidate) => candidate.kind === 'photo')!;
    test.runtime.selectMapTarget(mapTargetKey(target));
    await flushMicrotasks();
    expect(test.media.selectCandidate).toHaveBeenCalledWith(photo, expect.any(Function), 'primary');
    expect(test.runtime.chooseMediaCollection).toHaveBeenCalledExactlyOnceWith('primary');
    overlay.resolve([routeCandidate()]);
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.presentation).toBe('target');
    expect(test.runtime.mapInteraction?.selected?.kind).toBe('photo');
    expect(test.runtime.mapInteraction?.candidates).toHaveLength(3);
    expect(test.runtime.chooseMediaCollection).toHaveBeenCalledExactlyOnceWith('primary');
    test.runtime.showMapTargetChooser();
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    test.runtime.backFromMapTargetChooser();
    expect(test.runtime.mapInteraction?.presentation).toBe('target');
    expect(test.media.selectCandidate).toHaveBeenCalledOnce();
  });

  it.each(['location', 'cluster'] as const)(
    'keeps a %s photo hit in All while track and route discovery adds alternatives',
    async (kind) => {
      const test = setup();
      const tracks = deferred<Awaited<ReturnType<typeof fetchTrackIdsWithinDistanceOfPoint>>>();
      const routes = deferred<MapTargetCandidate[]>();
      const photo = photoCandidate(9, kind);
      test.runtime.activeOverlays = ['wmt-hiking'];
      test.media.collectCandidates.mockReturnValue([photo]);
      vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockReturnValueOnce(tracks.promise);
      vi.mocked(identifyWaymarkedTrails).mockReturnValueOnce(routes.promise);
      test.runtime.installMapInteractions();

      test.fire('click', clickEvent());
      expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
      expect(test.runtime.mapInteraction?.selected).toBeNull();
      expect(test.media.selectCandidate).not.toHaveBeenCalled();
      expect(test.runtime.openMediaSelection).not.toHaveBeenCalled();

      tracks.resolve({
        trackIds: [5],
        processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
      });
      await flushMicrotasks();
      expect(test.runtime.mapInteraction?.candidates.map((candidate) => candidate.kind)).toEqual(['photo', 'track']);
      expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
      expect(test.runtime.mapInteraction?.selected).toBeNull();
      expect(test.media.selectCandidate).not.toHaveBeenCalled();

      routes.resolve([{ ...routeCandidate(), source: 'waymarked:hiking' }]);
      await flushMicrotasks();
      expect(test.runtime.mapInteraction?.candidates.map((candidate) => candidate.kind)).toEqual([
        'photo',
        'track',
        'overlay',
      ]);
      expect(test.runtime.mapInteraction?.sources.every((source) => source.status === 'complete')).toBe(true);
      expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
      expect(test.runtime.mapInteraction?.selected).toBeNull();
      expect(test.media.selectCandidate).not.toHaveBeenCalled();
      expect(test.runtime.openMediaSelection).not.toHaveBeenCalled();
      expect(test.runtime.selectTrackById).not.toHaveBeenCalled();

      const candidate = test.runtime.mapInteraction!.candidates.find((candidate) => candidate.kind === 'photo')!;
      test.runtime.selectMapTarget(mapTargetKey(candidate));
      await flushMicrotasks();
      expect(test.runtime.mapInteraction?.presentation).toBe('target');
      expect(test.runtime.mapInteraction?.selected?.kind).toBe('photo');
      expect(test.media.selectCandidate).toHaveBeenCalledExactlyOnceWith(photo, expect.any(Function), 'primary');
      expect(test.runtime.chooseMediaCollection).toHaveBeenCalledExactlyOnceWith('primary');
    }
  );

  it('clears row preview highlights before opening a target and when the interaction ends', async () => {
    const test = setup();
    test.media.collectCandidates.mockReturnValue([photoCandidate()]);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    test.runtime.previewTrackById(1);
    test.runtime.selectMapTarget(
      mapTargetKey(test.runtime.mapInteraction!.candidates.find((candidate) => candidate.kind === 'photo')!)
    );
    await flushMicrotasks();
    expect(test.runtime.previewTrackById).toHaveBeenLastCalledWith(null);
    test.runtime.previewTrackById(2);
    test.runtime.endMapInteraction(true);
    expect(test.runtime.previewTrackById).toHaveBeenLastCalledWith(null);
  });

  it('keeps one photo in All after discovery completes until the user selects it', async () => {
    const test = setup();
    const tracks = deferred<Awaited<ReturnType<typeof fetchTrackIdsWithinDistanceOfPoint>>>();
    const routes = deferred<MapTargetCandidate[]>();
    const photo = photoCandidate();
    test.media.collectCandidates.mockReturnValue([photo]);
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockReturnValueOnce(tracks.promise);
    test.runtime.identifySwissMobilityRoutes.mockReturnValueOnce(routes.promise);
    test.runtime.installMapInteractions();

    test.fire('click', clickEvent());
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.media.selectCandidate).not.toHaveBeenCalled();
    tracks.resolve({
      trackIds: [],
      processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
    });
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    expect(test.media.selectCandidate).not.toHaveBeenCalled();
    routes.resolve([]);
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    expect(test.media.selectCandidate).not.toHaveBeenCalled();
    expect(test.runtime.openMediaSelection).not.toHaveBeenCalled();
    test.runtime.selectMapTarget(mapTargetKey(test.runtime.mapInteraction!.candidates[0]));
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.presentation).toBe('target');
    expect(test.runtime.mapInteraction?.selected?.kind).toBe('photo');
    expect(test.media.selectCandidate).toHaveBeenCalledExactlyOnceWith(photo, expect.any(Function), 'primary');
    expect(test.runtime.chooseMediaCollection).toHaveBeenCalledExactlyOnceWith('primary');
  });

  it('closes photo content when its retained collection context is invalidated', async () => {
    const test = setup();
    test.media.collectCandidates.mockReturnValue([photoCandidate()]);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    test.runtime.selectMapTarget(mapTargetKey(test.runtime.mapInteraction!.candidates[0]));
    await flushMicrotasks();
    expect(test.runtime.chooseMediaCollection).toHaveBeenCalledExactlyOnceWith('primary');
    test.runtime.mediaSheetVisible = true;
    test.runtime.closeMediaSheet.mockClear();
    test.runtime.endMapInteraction();
    expect(test.runtime.mapInteraction).toBeNull();
    expect(test.runtime.mediaSheetVisible).toBe(false);
    expect(test.runtime.closeMediaSheet).toHaveBeenCalledOnce();
  });

  it('registers enabled Waymarked activities independently and respects their visibility', async () => {
    const test = setup();
    test.runtime.activeOverlays = ['wmt-hiking', 'wmt-cycling', 'wmt-mtb'];
    test.runtime.layerOpacities['wmt-mtb'] = 0;
    const hiking = deferred<MapTargetCandidate[]>();
    const cycling = deferred<MapTargetCandidate[]>();
    vi.mocked(identifyWaymarkedTrails).mockImplementation((activity) =>
      activity === 'hiking' ? hiking.promise : cycling.promise
    );
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    expect(identifyWaymarkedTrails).toHaveBeenCalledTimes(2);
    expect(identifyWaymarkedTrails).toHaveBeenCalledWith(
      'hiking',
      expect.objectContaining({ tolerancePx: MAP_POINTER_TOLERANCE_PX }),
      expect.any(AbortSignal)
    );
    const sourceIds = test.runtime.mapInteraction?.sources.map((source) => source.id);
    expect(sourceIds).toContain('waymarked:cycling');
    expect(sourceIds).not.toContain('waymarked:mtb');
    expect(sourceIds).not.toContain('routes');
    cycling.resolve([{ ...routeCandidate(), source: 'waymarked:cycling', label: 'Cycling route' }]);
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    expect(test.runtime.mapInteraction?.candidates[0]?.label).toBe('Cycling route');
    hiking.resolve([{ ...routeCandidate('2'), source: 'waymarked:hiking', label: 'Hiking route' }]);
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.candidates.map((candidate) => candidate.label)).toEqual([
      'Cycling route',
      'Hiking route',
    ]);
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
  });

  it('loads published Swiss links only for the selected result and keeps that detail request during panning', async () => {
    const test = setup();
    const details = deferred<import('@/components/map/swissRouteDetails').SwissRouteDetails>();
    vi.mocked(fetchSwissRouteDetails).mockReturnValue(details.promise);
    const candidate: MapTargetCandidate = {
      ...routeCandidate('4.03'),
      source: 'ch.astra.wanderland',
      kind: 'overlay',
      route: { name: 'ViaJacobi', type: 'Hiking', icon: 'bi-map', featureId: '4.03' },
    };
    test.runtime.identifySwissMobilityRoutes.mockResolvedValue([candidate, routeCandidate('5')]);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    expect(fetchSwissRouteDetails).not.toHaveBeenCalled();
    test.runtime.selectMapTarget(mapTargetKey(candidate));
    expect(fetchSwissRouteDetails).toHaveBeenCalledOnce();
    const signal = vi.mocked(fetchSwissRouteDetails).mock.calls[0][1];
    test.fire('movestart', { originalEvent: new Event('wheel') });
    expect(signal.aborted).toBe(false);
    details.resolve({
      stageLabel: 'Stage 3',
      stageUrl: 'https://schweizmobil.ch/en/hiking-in-switzerland/route-4/stage-3',
      fullRouteUrl: 'https://schweizmobil.ch/en/hiking-in-switzerland/route-4',
    });
    await flushMicrotasks();
    expect(test.runtime.mapInteractionOverlayDetails?.stageLabel).toBe('Stage 3');
    expect(test.runtime.mapInteraction?.selected?.id).toBe('4.03');
  });

  it('aborts and rejects stale Swiss detail enrichment after another target is selected', async () => {
    const test = setup();
    const details = deferred<import('@/components/map/swissRouteDetails').SwissRouteDetails>();
    vi.mocked(fetchSwissRouteDetails).mockReturnValue(details.promise);
    const candidate: MapTargetCandidate = {
      ...routeCandidate('4.03'),
      source: 'ch.astra.wanderland',
      kind: 'overlay',
      route: { name: 'ViaJacobi', type: 'Hiking', icon: 'bi-map', featureId: '4.03' },
    };
    test.runtime.identifySwissMobilityRoutes.mockResolvedValue([candidate]);
    test.collectPois.mockReturnValue([poiCandidate()]);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    test.runtime.selectMapTarget(mapTargetKey(candidate));
    const signal = vi.mocked(fetchSwissRouteDetails).mock.calls[0][1];
    const poi = test.runtime.mapInteraction!.candidates.find((candidate) => candidate.kind === 'poi')!;
    test.runtime.selectMapTarget(mapTargetKey(poi));
    expect(signal.aborted).toBe(true);
    details.resolve({ stageLabel: 'Stage 3' });
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.selected?.kind).toBe('poi');
    expect(test.runtime.mapInteractionOverlayDetails).toBeNull();
  });

  it('restores the same route without restarting its pending detail lookup', async () => {
    const test = setup();
    const first = deferred<import('@/components/map/swissRouteDetails').SwissRouteDetails>();
    vi.mocked(fetchSwissRouteDetails).mockReturnValueOnce(first.promise);
    const candidate: MapTargetCandidate = {
      ...routeCandidate('4.03'),
      source: 'ch.astra.wanderland',
      kind: 'overlay',
      route: { name: 'ViaJacobi', type: 'Hiking', icon: 'bi-map', featureId: '4.03' },
    };
    test.runtime.identifySwissMobilityRoutes.mockResolvedValue([candidate]);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    expect(fetchSwissRouteDetails).not.toHaveBeenCalled();
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    test.runtime.selectMapTarget(mapTargetKey(candidate));
    expect(fetchSwissRouteDetails).toHaveBeenCalledOnce();
    expect(test.runtime.mapInteraction?.selected?.id).toBe('4.03');
    test.runtime.showMapTargetChooser();
    test.runtime.selectMapTarget(mapTargetKey(candidate));
    expect(test.runtime.mapInteraction?.presentation).toBe('target');
    expect(fetchSwissRouteDetails).toHaveBeenCalledOnce();
    expect(vi.mocked(fetchSwissRouteDetails).mock.calls[0][1].aborted).toBe(false);
    expect(test.runtime.mapInteractionDetailLoading).toBe(true);
    expect(test.runtime.mapInteractionOverlayDetails?.stageLabel).toBeUndefined();
    first.resolve({ stageLabel: 'Stage 3' });
    await flushMicrotasks();
    expect(test.runtime.mapInteractionOverlayDetails?.stageLabel).toBe('Stage 3');
    expect(test.runtime.mapInteractionDetailLoading).toBe(false);
  });

  it('bounds Swiss detail lookup without losing the selected result and Swiss map fallback', async () => {
    vi.useFakeTimers();
    const test = setup();
    const candidate: MapTargetCandidate = {
      ...routeCandidate('4.03'),
      source: 'ch.astra.wanderland',
      kind: 'overlay',
      route: { name: 'ViaJacobi', type: 'Hiking', icon: 'bi-map', featureId: '4.03' },
    };
    test.runtime.identifySwissMobilityRoutes.mockResolvedValue([candidate]);
    vi.mocked(fetchSwissRouteDetails).mockImplementation(
      (_query, signal) =>
        new Promise((_resolve, reject) =>
          signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
        )
    );
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    test.runtime.selectMapTarget(mapTargetKey(candidate));
    await vi.advanceTimersByTimeAsync(MAP_IDENTIFICATION_TIMEOUT_MS);
    expect(test.runtime.mapInteraction?.selected?.id).toBe('4.03');
    expect(test.runtime.mapInteractionOverlayDetails?.swissMapUrl).toContain('map.geo.admin.ch');
    expect(test.runtime.mapInteractionDetailError).toContain('Additional route information');
  });

  it('keeps a lone local symbol in the chooser until the user selects it', async () => {
    const test = setup();
    const overlay = deferred<MapTargetCandidate[]>();
    test.runtime.identifySwissMobilityRoutes.mockReturnValueOnce(overlay.promise);
    test.collectPois.mockReturnValue([poiCandidate()]);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    expect(test.runtime.mapInteractionDetailKind).toBeNull();
    expect(
      test.runtime.mapInteraction?.sources
        .filter((source) => source.kind !== 'local')
        .every((source) => source.status === 'pending')
    ).toBe(true);
    expect(test.runtime.identifySwissMobilityRoutes).toHaveBeenCalledOnce();
    expect(fetchTrackIdsWithinDistanceOfPoint).toHaveBeenCalledOnce();
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    overlay.resolve([]);
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    expect(test.runtime.mapInteractionDetailKind).toBeNull();
    test.runtime.selectMapTarget(mapTargetKey(test.runtime.mapInteraction!.candidates[0]));
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.selected?.kind).toBe('poi');
    expect(test.runtime.mapInteraction?.presentation).toBe('target');
    expect(test.runtime.mapInteractionDetailKind).toBe('simple');
  });

  it('retains a point selection and excludes its parent track while keeping another track accessible', async () => {
    const test = setup();
    test.runtime.collectTrackPointCandidates.mockReturnValue([
      {
        id: 'track-point:5:2',
        kind: 'track-point',
        sourceId: 'track-points-layer',
        label: 'Track point',
        detail: 'Track 5',
        coordinates: [7.52, 47.48],
        distancePx: 0,
        trackId: 5,
        pointIndex: 2,
      },
    ]);
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({
      trackIds: [5, 6],
      processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
    });
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    expect(test.runtime.openTrackPointCandidate).not.toHaveBeenCalled();
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    expect(test.runtime.mapInteraction?.candidates.map((target) => target.id)).toEqual(['track-point:5:2', '6']);
    test.runtime.selectMapTarget(mapTargetKey(test.runtime.mapInteraction!.candidates[0]));
    await flushMicrotasks();
    expect(test.runtime.openTrackPointCandidate).toHaveBeenCalledOnce();
    expect(test.runtime.mapInteraction?.selected?.kind).toBe('track-point');
    expect(test.runtime.openTrackDetails).not.toHaveBeenCalled();
  });

  it('cancels discovery on manual movement while keeping selected cluster loading and its original context', async () => {
    const test = setup();
    const tracks = deferred<Awaited<ReturnType<typeof fetchTrackIdsWithinDistanceOfPoint>>>();
    const overlay = deferred<MapTargetCandidate[]>();
    const selected = deferred<MediaOverlaySelection>();
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockReturnValueOnce(tracks.promise);
    test.runtime.identifySwissMobilityRoutes.mockReturnValueOnce(overlay.promise);
    test.media.collectCandidates.mockReturnValue([photoCandidate(9, 'cluster')]);
    test.media.selectCandidate.mockReturnValueOnce(selected.promise);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    const originalId = test.runtime.mapInteraction!.id;
    const trackSignal = vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mock.calls[0][3];
    const overlaySignal = test.runtime.identifySwissMobilityRoutes.mock.calls[0][2];
    expect(test.media.selectCandidate).not.toHaveBeenCalled();
    test.runtime.selectMapTarget(mapTargetKey(test.runtime.mapInteraction!.candidates[0]));
    expect(test.media.selectCandidate).toHaveBeenCalledOnce();

    test.fire('movestart', {});
    expect(trackSignal?.aborted).toBe(false);
    test.fire('movestart', { originalEvent: new MouseEvent('mousedown') });
    expect(trackSignal?.aborted).toBe(true);
    expect(overlaySignal.aborted).toBe(true);
    expect(test.runtime.mapInteraction?.id).toBe(originalId);
    expect(
      test.runtime.mapInteraction?.sources
        .filter((source) => source.kind !== 'local')
        .every((source) => source.status === 'cancelled')
    ).toBe(true);
    expect(
      test.runtime.mapInteraction?.sources
        .filter((source) => source.kind === 'local')
        .every((source) => source.status === 'complete')
    ).toBe(true);
    expect(test.runtime.mapInteraction?.context.point).toEqual({ x: 100, y: 80 });
    selected.resolve(photoSelection(9));
    tracks.resolve({
      trackIds: [6],
      processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
    });
    overlay.resolve([routeCandidate()]);
    await flushMicrotasks();
    expect(test.runtime.chooseMediaCollection).toHaveBeenCalledExactlyOnceWith('primary');
    expect(test.runtime.mapInteraction?.selected?.kind).toBe('photo');
    expect(test.runtime.mapInteraction?.candidates).toHaveLength(1);
  });

  it('confirms a single click before opening exploration or changing the active tool', () => {
    const test = setup({ confirmClicks: false });
    test.runtime.activeToolId = 'filter';
    test.runtime.captureActiveToolNavigation = vi.fn();
    test.runtime.closeAllToolsExcept = vi.fn();
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    vi.advanceTimersByTime(MAP_CLICK_CONFIRMATION_DELAY_MS - 1);
    expect(test.runtime.mapInteraction).toBeNull();
    expect(test.runtime.activeToolId).toBe('filter');
    expect(test.runtime.closeAllToolsExcept).not.toHaveBeenCalled();
    expect(test.runtime.identifySwissMobilityRoutes).not.toHaveBeenCalled();
    expect(markerMock.added).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.activeToolId).toBeNull();
    expect(test.runtime.closeAllToolsExcept).toHaveBeenCalledExactlyOnceWith(null);
    expect(test.runtime.identifySwissMobilityRoutes).toHaveBeenCalledOnce();
  });

  it('preserves double-click zoom without ever opening exploration for either click', () => {
    const test = setup({ confirmClicks: false });
    test.collectPois.mockReturnValue([poiCandidate()]);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    vi.advanceTimersByTime(MAP_CLICK_CONFIRMATION_DELAY_MS - 1);
    expect(test.runtime.mapInteraction).toBeNull();
    test.fire('click', clickEvent(2));
    const doubleClick = { preventDefault: vi.fn(), originalEvent: new MouseEvent('dblclick', { detail: 2 }) };
    test.fire('dblclick', doubleClick);
    vi.advanceTimersByTime(MAP_CLICK_CONFIRMATION_DELAY_MS);
    expect(doubleClick.preventDefault).not.toHaveBeenCalled();
    expect(test.runtime.mapInteraction).toBeNull();
    expect(test.runtime.identifySwissMobilityRoutes).not.toHaveBeenCalled();
    expect(test.collectPois).not.toHaveBeenCalled();
    expect(markerMock.added).not.toHaveBeenCalled();
    expect(test.runtime.closeMediaSelection).not.toHaveBeenCalled();

    test.fire('click', clickEvent());
    vi.advanceTimersByTime(MAP_CLICK_CONFIRMATION_DELAY_MS);
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    expect(test.runtime.identifySwissMobilityRoutes).toHaveBeenCalledOnce();
  });

  it('cancels first-tap exploration when touch double-tap zoom starts without a dblclick event', () => {
    const test = setup({ confirmClicks: false });
    test.runtime.installMapInteractions();
    test.canvas.dispatchEvent(new Event('touchstart'));
    test.fire('click', clickEvent());
    vi.advanceTimersByTime(MAP_CLICK_CONFIRMATION_DELAY_MS - 1);
    test.fire('movestart', { originalEvent: new Event('touchend') });
    vi.advanceTimersByTime(MAP_CLICK_CONFIRMATION_DELAY_MS);
    expect(test.runtime.mapInteraction).toBeNull();
    expect(test.runtime.identifySwissMobilityRoutes).not.toHaveBeenCalled();
    expect(markerMock.added).not.toHaveBeenCalled();

    test.canvas.dispatchEvent(new Event('touchstart'));
    test.fire('click', clickEvent());
    vi.advanceTimersByTime(MAP_CLICK_CONFIRMATION_DELAY_MS);
    expect(test.runtime.mapInteraction?.context.inputKind).toBe('touch');
  });

  it.each(['move', 'style', 'context', 'dispose', 'tool'])(
    'does not open a queued click after a %s change',
    (change) => {
      const test = setup({ confirmClicks: false });
      test.runtime.installMapInteractions();
      test.fire('click', clickEvent());
      if (change === 'move') test.fire('movestart', { originalEvent: new MouseEvent('mousedown') });
      if (change === 'style') test.fire('style.load', {});
      if (change === 'context') test.runtime.endMapInteraction();
      if (change === 'dispose') test.runtime.disposeMapInteractions();
      if (change === 'tool') test.runtime.plannerToolActive = true;
      vi.advanceTimersByTime(MAP_CLICK_CONFIRMATION_DELAY_MS);
      expect(test.runtime.mapInteraction).toBeNull();
      expect(test.runtime.identifySwissMobilityRoutes).not.toHaveBeenCalled();
      expect(markerMock.added).not.toHaveBeenCalled();
    }
  );

  it('keeps a newer queued click when a previous double-click event arrives late', () => {
    const test = setup({ confirmClicks: false });
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    test.fire('click', clickEvent(2));
    test.fire('click', { ...clickEvent(), point: { x: 400, y: 300 }, lngLat: { lng: 7.6, lat: 47.5 } });
    test.fire('dblclick', { originalEvent: new MouseEvent('dblclick', { detail: 2 }) });
    vi.advanceTimersByTime(MAP_CLICK_CONFIRMATION_DELAY_MS);
    expect(test.runtime.mapInteraction?.context.point).toEqual({ x: 400, y: 300 });
    expect(test.runtime.identifySwissMobilityRoutes).toHaveBeenCalledOnce();
  });

  it('keeps a newer single click when a previous double-click gesture finishes late', () => {
    const test = setup();
    const oldRoutes = deferred<MapTargetCandidate[]>();
    const newRoutes = deferred<MapTargetCandidate[]>();
    test.runtime.identifySwissMobilityRoutes
      .mockReturnValueOnce(oldRoutes.promise)
      .mockReturnValueOnce(newRoutes.promise);
    test.collectPois.mockReturnValue([poiCandidate()]);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    const oldId = test.runtime.mapInteraction!.id;
    test.fire('click', clickEvent(2));
    test.fire('click', { ...clickEvent(), point: { x: 400, y: 300 }, lngLat: { lng: 7.6, lat: 47.5 } });
    const newId = test.runtime.mapInteraction!.id;
    const newSignal = test.runtime.identifySwissMobilityRoutes.mock.calls[1][2];
    const closeCount = test.runtime.closeMediaSelection.mock.calls.length;

    test.fire('dblclick', { originalEvent: new MouseEvent('dblclick', { detail: 2 }) });
    expect(newId).toBeGreaterThan(oldId);
    expect(test.runtime.mapInteraction?.id).toBe(newId);
    expect(test.runtime.mapInteraction?.context.point).toEqual({ x: 400, y: 300 });
    expect(newSignal.aborted).toBe(false);
    expect(test.runtime.closeMediaSelection).toHaveBeenCalledTimes(closeCount);
    expect(markerMock.removed).toHaveBeenCalledOnce();
  });

  it('reprojects the original clicked location when selecting a known track after manual panning', async () => {
    const test = setup();
    const overlay = deferred<MapTargetCandidate[]>();
    test.runtime.identifySwissMobilityRoutes.mockReturnValueOnce(overlay.promise);
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({
      trackIds: [5, 6],
      processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
    });
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    const track = test.runtime.mapInteraction!.candidates.find((candidate) => candidate.kind === 'track')!;

    test.map.project.mockReturnValue({ x: -40, y: 240 });
    test.fire('movestart', { originalEvent: new MouseEvent('mousedown') });
    test.runtime.selectMapTarget(mapTargetKey(track));
    await flushMicrotasks();

    expect(test.map.project).toHaveBeenCalledWith({ lng: 7.52, lat: 47.48 });
    expect(test.runtime.showTrackLinePointPopup).toHaveBeenCalledExactlyOnceWith(
      { lngLat: { lng: 7.52, lat: 47.48 }, point: { x: -40, y: 240 } },
      5,
      { isCurrent: expect.any(Function), presentation: 'context' }
    );
    expect(test.runtime.mapInteraction?.context.point).toEqual({ x: 100, y: 80 });
    expect(test.runtime.mapInteraction?.selected?.id).toBe('5');
    expect(test.runtime.mapInteractionDetailError).toBeNull();
    expect(markerMock.removed).not.toHaveBeenCalled();
  });

  it('uses actual input kind for pointer, touch, and pen matching', () => {
    const test = setup();
    test.collectPois.mockReturnValue([poiCandidate()]);
    test.runtime.installMapInteractions();
    test.canvas.dispatchEvent(pointerEvent('mouse'));
    test.fire('click', clickEvent());
    expect(test.collectPois).toHaveBeenLastCalledWith({ x: 100, y: 80 }, MAP_POINTER_TOLERANCE_PX);
    expect(test.runtime.mapInteraction?.context.inputKind).toBe('pointer');
    test.canvas.dispatchEvent(new Event('touchstart'));
    test.fire('click', clickEvent());
    expect(test.collectPois).toHaveBeenLastCalledWith({ x: 100, y: 80 }, MAP_TOUCH_TOLERANCE_PX);
    expect(test.runtime.mapInteraction?.context.inputKind).toBe('touch');
    test.canvas.dispatchEvent(pointerEvent('pen'));
    test.fire('click', clickEvent());
    expect(test.collectPois).toHaveBeenLastCalledWith({ x: 100, y: 80 }, MAP_POINTER_TOLERANCE_PX);
    expect(test.runtime.mapInteraction?.context.inputKind).toBe('pen');
    test.fire('click', {
      ...clickEvent(),
      originalEvent: Object.assign(new MouseEvent('click'), { pointerType: 'mouse' }),
    });
    expect(test.runtime.mapInteraction?.context.inputKind).toBe('pointer');
  });

  it('respects tool ownership and shows the empty chooser when no visible layer is eligible', () => {
    const test = setup();
    test.runtime.installMapInteractions();
    test.runtime.measureToolActive = true;
    test.fire('click', clickEvent());
    test.runtime.measureToolActive = false;
    test.runtime.plannerToolActive = true;
    test.fire('click', clickEvent());
    test.runtime.plannerToolActive = false;
    test.runtime.geoDrawingParamDef = {};
    test.fire('click', clickEvent());
    expect(test.runtime.mapInteraction).toBeNull();
    expect(markerMock.added).not.toHaveBeenCalled();
    test.runtime.geoDrawingParamDef = null;
    test.runtime.layerOpacities.tracks = 0;
    test.runtime.mediaVisible = false;
    test.runtime.layerOpacities.trackpoints = 0;
    test.runtime.layerOpacities.wanderland = 0;
    test.deps.mapSettingsStore.poiEnabled = false;
    test.fire('click', clickEvent());
    expect(test.collectPois).not.toHaveBeenCalled();
    expect(test.media.collectCandidates).not.toHaveBeenCalled();
    expect(test.runtime.collectTrackPointCandidates).not.toHaveBeenCalled();
    expect(fetchTrackIdsWithinDistanceOfPoint).not.toHaveBeenCalled();
    expect(test.runtime.identifySwissMobilityRoutes).not.toHaveBeenCalled();
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    expect(test.runtime.mapInteraction?.candidates).toEqual([]);
    expect(test.runtime.mapInteraction?.sources).toEqual([]);
    expect(markerMock.added).toHaveBeenCalledOnce();
  });

  it.each(['photos', 'pois', 'points'] as const)(
    'omits the disabled %s source while retaining the other eligible empty local categories',
    async (sourceId) => {
      const test = setup();
      if (sourceId === 'photos') test.runtime.mediaVisible = false;
      if (sourceId === 'pois') test.deps.mapSettingsStore.poiEnabled = false;
      if (sourceId === 'points') test.runtime.layerOpacities.trackpoints = 0;
      test.runtime.installMapInteractions();
      test.fire('click', clickEvent());
      await flushMicrotasks();
      const expected = ['pois', 'points', 'photos'].filter((id) => id !== sourceId);
      expect(
        test.runtime.mapInteraction?.sources.filter((source) => source.kind === 'local').map((source) => source.id)
      ).toEqual(expected);
      expect(test.runtime.mapInteraction?.sources.map((source) => source.id)).not.toContain(sourceId);
      expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
      expect(test.runtime.mapInteraction?.candidates).toEqual([]);
    }
  );

  it('retains an empty chooser after successful discovery without opening any target', async () => {
    const test = setup();
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(
      test.runtime.mapInteraction?.sources
        .filter((source) => source.kind !== 'local')
        .every((source) => source.status === 'pending')
    ).toBe(true);
    expect(
      test.runtime.mapInteraction?.sources
        .filter((source) => source.kind === 'local')
        .map((source) => [source.id, source.status])
    ).toEqual([
      ['pois', 'complete'],
      ['points', 'complete'],
      ['photos', 'complete'],
    ]);
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    expect(test.runtime.mapInteraction?.candidates).toEqual([]);
    expect(test.runtime.mapInteraction?.sources.every((source) => source.status === 'complete')).toBe(true);
    expect(test.runtime.openMediaSelection).not.toHaveBeenCalled();
    expect(test.runtime.selectTrackById).not.toHaveBeenCalled();
    expect(test.runtime.openTrackPointCandidate).not.toHaveBeenCalled();
    expect(markerMock.added).toHaveBeenCalledOnce();
    expect(markerMock.removed).not.toHaveBeenCalled();
  });

  it('retains the empty chooser with failed provider status rather than successful empty status', async () => {
    const test = setup();
    test.runtime.identifySwissMobilityRoutes.mockRejectedValueOnce(new Error('Route service unavailable'));
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    expect(test.runtime.mapInteraction?.candidates).toEqual([]);
    expect(test.runtime.mapInteraction?.sources.map((source) => [source.id, source.status])).toEqual([
      ['pois', 'complete'],
      ['points', 'complete'],
      ['photos', 'complete'],
      ['tracks', 'complete'],
      ['routes', 'failed'],
    ]);
    expect(test.runtime.mapInteractionDetailKind).toBeNull();
    expect(test.runtime.openMediaSelection).not.toHaveBeenCalled();
    expect(test.runtime.selectTrackById).not.toHaveBeenCalled();
  });

  it('retains the empty chooser with cancelled status and ignores late discoveries after manual movement', async () => {
    const test = setup();
    const tracks = deferred<Awaited<ReturnType<typeof fetchTrackIdsWithinDistanceOfPoint>>>();
    const routes = deferred<MapTargetCandidate[]>();
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockReturnValueOnce(tracks.promise);
    test.runtime.identifySwissMobilityRoutes.mockReturnValueOnce(routes.promise);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    const interactionId = test.runtime.mapInteraction!.id;
    test.fire('movestart', { originalEvent: new MouseEvent('mousedown') });
    expect(test.runtime.mapInteraction?.id).toBe(interactionId);
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    expect(
      test.runtime.mapInteraction?.sources
        .filter((source) => source.kind !== 'local')
        .every((source) => source.status === 'cancelled')
    ).toBe(true);
    expect(
      test.runtime.mapInteraction?.sources
        .filter((source) => source.kind === 'local')
        .every((source) => source.status === 'complete')
    ).toBe(true);
    tracks.resolve({
      trackIds: [5],
      processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
    });
    routes.resolve([routeCandidate()]);
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.id).toBe(interactionId);
    expect(test.runtime.mapInteraction?.candidates).toEqual([]);
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(
      test.runtime.mapInteraction?.sources
        .filter((source) => source.kind !== 'local')
        .every((source) => source.status === 'cancelled')
    ).toBe(true);
    expect(
      test.runtime.mapInteraction?.sources
        .filter((source) => source.kind === 'local')
        .every((source) => source.status === 'complete')
    ).toBe(true);
    expect(test.runtime.openMediaSelection).not.toHaveBeenCalled();
    expect(test.runtime.selectTrackById).not.toHaveBeenCalled();
    expect(markerMock.removed).not.toHaveBeenCalled();
  });

  it('invalidates pending results on style or context changes and captures the next click filter values', async () => {
    const test = setup();
    const firstRoutes = deferred<MapTargetCandidate[]>();
    const nextRoutes = deferred<MapTargetCandidate[]>();
    test.runtime.identifySwissMobilityRoutes
      .mockReturnValueOnce(firstRoutes.promise)
      .mockReturnValueOnce(nextRoutes.promise);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    const firstId = test.runtime.mapInteraction!.id;
    const firstSignal = test.runtime.identifySwissMobilityRoutes.mock.calls[0][2];
    expect(test.runtime.mapInteraction?.context.trackSetRevision).toBe(4);
    test.deps.filterStore.trackSetRevision = 5;
    test.deps.mediaFilterStore.scope = 'all';
    test.runtime.endMapInteraction();
    expect(firstSignal.aborted).toBe(true);
    test.fire('click', clickEvent());
    expect(test.runtime.mapInteraction?.id).toBeGreaterThan(firstId);
    expect(test.runtime.mapInteraction?.context).toMatchObject({ trackSetRevision: 5, mediaScope: 'all' });
    firstRoutes.resolve([routeCandidate('old')]);
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.candidates).toEqual([]);
    const nextSignal = test.runtime.identifySwissMobilityRoutes.mock.calls[1][2];
    test.fire('style.load', {});
    expect(nextSignal.aborted).toBe(true);
    nextRoutes.resolve([routeCandidate('new')]);
    await flushMicrotasks();
    expect(test.runtime.mapInteraction).toBeNull();
  });

  it('retains the original media payload on successive clicks and rejects a superseded collection result', async () => {
    const test = setup();
    const firstSelection = deferred<MediaOverlaySelection>();
    const firstPhoto = photoCandidate(9);
    const secondPhoto = photoCandidate(10);
    test.media.collectCandidates.mockReturnValueOnce([firstPhoto]).mockReturnValueOnce([secondPhoto]);
    test.media.selectCandidate.mockReturnValueOnce(firstSelection.promise).mockResolvedValueOnce(photoSelection(10));
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    test.runtime.selectMapTarget(mapTargetKey(test.runtime.mapInteraction!.candidates[0]));
    test.fire('click', clickEvent());
    test.runtime.selectMapTarget(mapTargetKey(test.runtime.mapInteraction!.candidates[0]));
    await flushMicrotasks();
    expect(test.media.selectCandidate).toHaveBeenNthCalledWith(2, secondPhoto, expect.any(Function), 'primary');
    expect(test.runtime.chooseMediaCollection).toHaveBeenCalledExactlyOnceWith('primary');
    expect(test.runtime.mediaSheetMediaId).toBe(10);
    firstSelection.resolve(photoSelection(9));
    await flushMicrotasks();
    expect(test.runtime.chooseMediaCollection).toHaveBeenCalledExactlyOnceWith('primary');
  });

  it('removes listeners and aborts outstanding providers when disposed', () => {
    const test = setup();
    const overlay = deferred<MapTargetCandidate[]>();
    test.runtime.identifySwissMobilityRoutes.mockReturnValueOnce(overlay.promise);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    const signal = test.runtime.identifySwissMobilityRoutes.mock.calls[0][2];
    test.runtime.disposeMapInteractions();
    expect(signal.aborted).toBe(true);
    expect(test.listeners.size).toBe(0);
    expect(test.runtime.mapInteraction).toBeNull();
    expect(markerMock.removed).toHaveBeenCalledOnce();
  });

  it('excludes hidden groups and review-excluded tracks from remote proximity hits', async () => {
    const test = setup();
    test.runtime.gpsTrackIdToFeature.set(5, { properties: { reviewMapExcluded: true } });
    test.runtime.gpsTrackIdToFeature.set(6, { properties: { filterGroup: 'hidden' } });
    test.runtime.gpsTracksById.set(7, { reviewMapExcluded: true });
    test.runtime.hiddenGroups.add('hidden');
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({
      trackIds: [5, 6, 7, 8],
      processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
    });
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.candidates.map((candidate) => candidate.id)).toEqual(['8']);
    expect(test.runtime.mapInteraction?.presentation).toBe('chooser');
    expect(test.runtime.mapInteraction?.selected).toBeNull();
    expect(test.runtime.selectTrackById).not.toHaveBeenCalled();
    test.runtime.selectMapTarget(mapTargetKey(test.runtime.mapInteraction!.candidates[0]));
    await flushMicrotasks();
    expect(test.runtime.selectTrackById).toHaveBeenCalledExactlyOnceWith(8);
  });
});

describe('map exploration context invalidation', () => {
  function watchedInteraction(hiddenGroups = new Set<string>()) {
    const test = setup();
    const context = reactive({
      hiddenGroups,
      tracksEnabled: true,
      layerOpacities: { tracks: 100 },
      activeOverlays: ['wanderland'],
      poiCategories: { water: true },
      trackSetRevision: 4,
    });
    const scope = effectScope();
    scope.run(() =>
      watchMapInteractionContext(
        () => [
          context.hiddenGroups,
          context.tracksEnabled,
          context.layerOpacities,
          context.activeOverlays,
          context.poiCategories,
          context.trackSetRevision,
        ],
        () => test.runtime.endMapInteraction()
      )
    );
    disposers.push(() => scope.stop());
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    return { ...test, context };
  }

  it('preserves an accepted click when late initialization replaces an empty hidden-group Set', () => {
    const test = watchedInteraction();
    const interaction = test.runtime.mapInteraction;
    test.context.hiddenGroups = new Set();
    expect(test.runtime.mapInteraction).toBe(interaction);
  });

  it('preserves equivalent populated Set replacement regardless of insertion order', () => {
    const test = watchedInteraction(new Set(['Q1', 'Q2']));
    const interaction = test.runtime.mapInteraction;
    test.context.hiddenGroups = new Set(['Q2', 'Q1']);
    expect(test.runtime.mapInteraction).toBe(interaction);
  });

  it.each([
    'hide',
    'show',
    'clear',
    'different set',
    'disable layer',
    'opacity',
    'overlay',
    'POI category',
    'filter revision',
  ])('still cancels an accepted click for a real %s context change', (change) => {
    const test = watchedInteraction(new Set(['Q1']));
    if (change === 'hide') test.context.hiddenGroups.add('Q2');
    if (change === 'show') test.context.hiddenGroups.delete('Q1');
    if (change === 'clear') test.context.hiddenGroups.clear();
    if (change === 'different set') test.context.hiddenGroups = new Set(['Q2']);
    if (change === 'disable layer') test.context.tracksEnabled = false;
    if (change === 'opacity') test.context.layerOpacities.tracks = 0;
    if (change === 'overlay') test.context.activeOverlays.push('wmt-hiking');
    if (change === 'POI category') test.context.poiCategories.water = false;
    if (change === 'filter revision') test.context.trackSetRevision += 1;
    expect(test.runtime.mapInteraction).toBeNull();
    expect(markerMock.removed).toHaveBeenCalledOnce();
  });
});

describe('GPS discovery row facts', () => {
  it('shows known GPS rows immediately, ranks loaded geometry, and refines facts without moving or reopening the selected target', async () => {
    const test = setup();
    const facts = deferred<Awaited<ReturnType<typeof fetchTrackMediaOptionsWithinDistanceOfPoint>>>();
    vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mockReturnValueOnce(facts.promise);
    const processing = { state: TrackProcessingStatusDtoStateEnum.Running, stages: [], failedGpsFiles: 1 };
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({ trackIds: [6, 5], processing });
    test.map.project.mockImplementation((value: unknown) => {
      const coordinate = Array.isArray(value)
        ? { lng: value[0], lat: value[1] }
        : (value as { lng: number; lat: number });
      return { x: 100 + (coordinate.lng - 7.52) * 100_000, y: 80 + (coordinate.lat - 47.48) * 100_000 };
    });
    for (const [id, offset] of [
      [5, 0.00001],
      [6, 0.00005],
    ]) {
      test.runtime.gpsTrackIdToFeature.set(id, {
        properties: { id },
        geometry: {
          type: 'LineString',
          coordinates: [
            [7.5199, 47.48 + offset],
            [7.5201, 47.48 + offset],
          ],
        },
      });
    }
    test.runtime.gpsTracksById.set(5, { startDate: new Date('2025-01-01') });
    test.runtime.gpsTracksById.set(6, { startDate: new Date('2026-01-01') });
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    const candidates = test.runtime.mapInteraction!.candidates;
    expect(candidates.map((candidate) => candidate.id)).toEqual(['5', '6']);
    expect(rankMapCategoryCandidates(candidates).map((candidate) => candidate.id)).toEqual(['5', '6']);
    expect(candidates[0].distancePx).toBeCloseTo(1);
    expect(test.runtime.mapInteraction?.processing).toEqual(processing);
    expect(test.runtime.mapInteraction?.sources.find((source) => source.id === 'tracks')?.status).toBe('pending');
    test.runtime.selectMapTarget(mapTargetKey(candidates[0]));
    await flushMicrotasks();
    const selected = test.runtime.mapInteraction!.selected;
    facts.resolve([
      { trackId: 6, distanceMeters: 0, matchedMediaCount: 9 },
      { trackId: 5, distanceMeters: 5, matchedMediaCount: 3 },
    ]);
    await flushMicrotasks();
    expect(test.runtime.mapInteraction!.candidates.map((candidate) => candidate.id)).toEqual(['5', '6']);
    expect(test.runtime.mapInteraction?.candidates).toMatchObject([{ matchedMediaCount: 3 }, { matchedMediaCount: 9 }]);
    expect(test.runtime.mapInteraction?.selected).toBe(selected);
    expect(test.runtime.mapInteraction?.presentation).toBe('target');
    expect(test.runtime.selectTrackById).toHaveBeenCalledOnce();
    expect(test.runtime.showTrackLinePointPopup).toHaveBeenCalledOnce();
  });

  it('retains already returned GPS hits when movement cancels pending facts and ignores the late refinement', async () => {
    vi.useFakeTimers();
    const test = setup();
    const facts = deferred<Awaited<ReturnType<typeof fetchTrackMediaOptionsWithinDistanceOfPoint>>>();
    vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mockReturnValueOnce(facts.promise);
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({
      trackIds: [5],
      processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
    });
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    const candidate = test.runtime.mapInteraction!.candidates[0];
    expect(candidate.id).toBe('5');
    test.fire('movestart', { originalEvent: new MouseEvent('mousedown') });
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.candidates).toEqual([candidate]);
    expect(test.runtime.mapInteraction?.sources.find((source) => source.id === 'tracks')?.status).toBe('cancelled');
    facts.resolve([{ trackId: 5, distanceMeters: 0, matchedMediaCount: 99 }]);
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.candidates).toEqual([candidate]);
    expect(warnSanitized).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('publishes only enriched rows when optional facts are already ready before GPS IDs', async () => {
    const test = setup();
    const tracks = deferred<Awaited<ReturnType<typeof fetchTrackIdsWithinDistanceOfPoint>>>();
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockReturnValueOnce(tracks.promise);
    vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mockResolvedValueOnce([
      { trackId: 5, distanceMeters: 8, matchedMediaCount: 3 },
      { trackId: 6, distanceMeters: 1, matchedMediaCount: 9 },
    ]);
    let current: MapInteractionState | null = null;
    const published: MapInteractionState[] = [];
    Object.defineProperty(test.runtime, 'mapInteraction', {
      get: () => current,
      set: (value: MapInteractionState | null) => {
        current = value;
        if (value?.candidates.some((candidate) => candidate.kind === 'track')) published.push(value);
      },
    });
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    tracks.resolve({
      trackIds: [5, 6],
      processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
    });
    await flushMicrotasks();
    expect(published).toHaveLength(1);
    expect(published[0].candidates).toMatchObject([{ matchedMediaCount: 3 }, { matchedMediaCount: 9 }]);
    expect(rankMapCategoryCandidates(published[0].candidates).map((candidate) => candidate.id)).toEqual(['6', '5']);
  });

  it('starts both filtered queries in parallel and merges validated facts only for original GPS hits', async () => {
    const test = setup();
    const tracks = deferred<Awaited<ReturnType<typeof fetchTrackIdsWithinDistanceOfPoint>>>();
    const mediaFacts = deferred<Awaited<ReturnType<typeof fetchTrackMediaOptionsWithinDistanceOfPoint>>>();
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockReturnValueOnce(tracks.promise);
    vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mockReturnValueOnce(mediaFacts.promise);
    test.runtime.getTrackPopupMeta.mockImplementation((id) => ({
      id,
      name: `Track ${id}`,
      displayName: `Track ${id}`,
      date: '2026-10-12',
      description: 'River path',
      activityType: 'HIKING',
    }));
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());

    expect(fetchTrackIdsWithinDistanceOfPoint).toHaveBeenCalledOnce();
    expect(fetchTrackMediaOptionsWithinDistanceOfPoint).toHaveBeenCalledOnce();
    expect(vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mock.calls[0].slice(0, 3)).toEqual(
      vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mock.calls[0].slice(0, 3)
    );
    const processing = { state: TrackProcessingStatusDtoStateEnum.Running, stages: [], failedGpsFiles: 2 };
    tracks.resolve({ trackIds: [5, 6, 7], processing });
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.candidates.map((candidate) => candidate.id)).toEqual(['5', '6', '7']);
    expect(test.runtime.mapInteraction?.processing).toEqual(processing);
    expect(test.runtime.mapInteraction?.sources.find((source) => source.id === 'tracks')?.status).toBe('pending');
    mediaFacts.resolve([
      { trackId: 5, distanceMeters: 2.5, matchedMediaCount: 3.8 },
      { trackId: 7, distanceMeters: Number.NaN, matchedMediaCount: -1 },
      { trackId: 8, distanceMeters: 0, matchedMediaCount: 99 },
    ]);
    await flushMicrotasks();

    const candidates = test.runtime.mapInteraction!.candidates;
    expect(candidates.map((candidate) => candidate.id)).toEqual(['5', '6', '7']);
    expect(candidates[0]).toMatchObject({
      activityType: 'HIKING',
      description: 'River path',
      matchedMediaCount: 3,
      distanceMeters: 2.5,
    });
    expect(candidates[1]).toMatchObject({ activityType: 'HIKING', description: 'River path' });
    for (const candidate of candidates.slice(1)) {
      expect(candidate.kind === 'track' && candidate.matchedMediaCount).toBeUndefined();
      expect(candidate.kind === 'track' && candidate.distanceMeters).toBeUndefined();
    }
    expect(test.runtime.mapInteraction?.processing).toEqual(processing);
    expect(test.runtime.mapInteraction?.sources.find((source) => source.id === 'tracks')?.status).toBe('complete');
  });

  it('keeps explicit zero facts while rejecting negative, infinite, and missing values', async () => {
    const test = setup();
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({
      trackIds: [5, 6, 7],
      processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
    });
    vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mockResolvedValueOnce([
      { trackId: 5, distanceMeters: 0, matchedMediaCount: 0 },
      { trackId: 6, distanceMeters: -1, matchedMediaCount: Number.POSITIVE_INFINITY },
      { trackId: 7, distanceMeters: Number.POSITIVE_INFINITY, matchedMediaCount: Number.NaN },
    ]);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    const candidates = test.runtime.mapInteraction!.candidates;
    expect(candidates[0]).toMatchObject({ distanceMeters: 0, matchedMediaCount: 0 });
    for (const candidate of candidates.slice(1)) {
      expect(candidate.kind === 'track' && candidate.distanceMeters).toBeUndefined();
      expect(candidate.kind === 'track' && candidate.matchedMediaCount).toBeUndefined();
    }
  });

  it('preserves GPS targets and indexing notices when optional photo facts fail without inventing zero counts', async () => {
    const test = setup();
    const processing = { state: TrackProcessingStatusDtoStateEnum.Running, stages: [], failedGpsFiles: 1 };
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({ trackIds: [5], processing });
    vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mockRejectedValueOnce(
      new Error('Photo association lookup failed')
    );
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    expect(test.runtime.mapInteraction?.candidates).toMatchObject([{ id: '5', kind: 'track' }]);
    expect(test.runtime.mapInteraction?.processing).toEqual(processing);
    expect(test.runtime.mapInteraction?.sources.find((source) => source.id === 'tracks')?.status).toBe('complete');
    expect(
      test.runtime.mapInteraction?.candidates[0].kind === 'track' &&
        test.runtime.mapInteraction.candidates[0].matchedMediaCount
    ).toBeUndefined();
    expect(warnSanitized).toHaveBeenCalledWith(
      'Nearby GPS track photo facts are unavailable',
      expect.any(Error),
      expect.objectContaining({ topic3: 'trackMediaFacts' })
    );
  });

  it('bounds stalled optional facts while preserving successful GPS IDs and clears every lookup timer', async () => {
    vi.useFakeTimers();
    const test = setup();
    const mediaFacts = deferred<Awaited<ReturnType<typeof fetchTrackMediaOptionsWithinDistanceOfPoint>>>();
    const processing = { state: TrackProcessingStatusDtoStateEnum.Running, stages: [], failedGpsFiles: 1 };
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({ trackIds: [5], processing });
    vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mockReturnValueOnce(mediaFacts.promise);
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    const signal = vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mock.calls[0][3]!;
    await flushMicrotasks();
    await vi.advanceTimersByTimeAsync(MAP_TRACK_MEDIA_OPTIONS_TIMEOUT_MS);
    expect(signal.aborted).toBe(true);
    expect(test.runtime.mapInteraction?.candidates).toMatchObject([{ id: '5', kind: 'track' }]);
    expect(test.runtime.mapInteraction?.processing).toEqual(processing);
    expect(test.runtime.mapInteraction?.sources.find((source) => source.id === 'tracks')?.status).toBe('complete');
    expect(vi.getTimerCount()).toBe(0);
    mediaFacts.resolve([{ trackId: 5, distanceMeters: 1, matchedMediaCount: 99 }]);
    await flushMicrotasks();
    expect(
      test.runtime.mapInteraction?.candidates[0].kind === 'track' &&
        test.runtime.mapInteraction.candidates[0].matchedMediaCount
    ).toBeUndefined();
    expect(warnSanitized).toHaveBeenCalledTimes(1);
  });

  it.each(['manual move', 'dismissal', 'new click'])(
    'silently cancels optional facts on %s and ignores their delayed response',
    async (reason) => {
      vi.useFakeTimers();
      const test = setup();
      const mediaFacts = deferred<Awaited<ReturnType<typeof fetchTrackMediaOptionsWithinDistanceOfPoint>>>();
      vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({
        trackIds: [5],
        processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
      });
      vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mockReturnValueOnce(mediaFacts.promise);
      test.runtime.installMapInteractions();
      test.fire('click', clickEvent());
      const signal = vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mock.calls[0][3]!;
      if (reason === 'manual move') test.fire('movestart', { originalEvent: new MouseEvent('mousedown') });
      if (reason === 'dismissal') test.runtime.endMapInteraction();
      if (reason === 'new click') test.fire('click', clickEvent());
      await flushMicrotasks();
      expect(signal.aborted).toBe(true);
      expect(vi.getTimerCount()).toBe(0);
      mediaFacts.resolve([{ trackId: 5, distanceMeters: 1, matchedMediaCount: 99 }]);
      await flushMicrotasks();
      expect(test.runtime.mapInteraction?.candidates ?? []).toEqual([]);
      expect(warnSanitized).not.toHaveBeenCalled();
    }
  );

  it('cancels optional facts when GPS discovery itself fails without leaking timers', async () => {
    vi.useFakeTimers();
    const test = setup();
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockRejectedValueOnce(new Error('Track discovery failed'));
    vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mockReturnValueOnce(new Promise(() => {}));
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    const signal = vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mock.calls[0][3]!;
    await flushMicrotasks();
    expect(signal.aborted).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
    expect(test.runtime.mapInteraction?.sources.find((source) => source.id === 'tracks')?.status).toBe('failed');
    expect(vi.mocked(warnSanitized).mock.calls.some(([, , context]) => context?.topic3 === 'trackMediaFacts')).toBe(
      false
    );
  });

  it('skips optional photo facts while photos are ineligible and retains GPS discovery', async () => {
    const test = setup();
    test.runtime.mediaVisible = false;
    vi.mocked(fetchTrackIdsWithinDistanceOfPoint).mockResolvedValueOnce({
      trackIds: [5],
      processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 },
    });
    test.runtime.installMapInteractions();
    test.fire('click', clickEvent());
    await flushMicrotasks();
    expect(fetchTrackMediaOptionsWithinDistanceOfPoint).not.toHaveBeenCalled();
    expect(test.runtime.mapInteraction?.candidates).toMatchObject([{ id: '5', kind: 'track' }]);
  });
});

function setup({ confirmClicks = true } = {}) {
  const canvas = document.createElement('canvas');
  const listeners = new Map<string, (event: never) => void>();
  const map = {
    getCanvas: () => canvas,
    getLayer: (id: string) =>
      ['tracks-layer', 'wanderland-overlay', 'wmt-hiking-overlay', 'wmt-cycling-overlay', 'wmt-mtb-overlay'].includes(
        id
      )
        ? { id }
        : undefined,
    unproject: ([x, y]: [number, number]) => ({ lng: 7.52 + (x - 100) / 100_000, lat: 47.48 + (y - 80) / 100_000 }),
    project: vi.fn((_value: unknown) => ({ x: 100, y: 80 })),
    on: vi.fn((name: string, handler: (event: never) => void) => listeners.set(name, handler)),
    off: vi.fn((name: string) => listeners.delete(name)),
  };
  const collectPois = vi.fn<(_point: { x: number; y: number }, _radius: number) => PoiMapCandidate[]>(() => []);
  const deps = {
    filterStore: { trackSetRevision: 4 },
    mediaFilterStore: { scope: 'track-filter' },
    mapSettingsStore: { poiEnabled: true, poiOpacity: 100 },
    collectPois,
  };
  const media = {
    collectCandidates: vi.fn<(_point: { x: number; y: number }, _radius: number) => MediaOverlayCandidate[]>(() => []),
    selectCandidate: vi
      .fn<
        (
          _candidate: MediaOverlayCandidate,
          _isCurrent: () => boolean,
          _scope?: 'primary' | 'viewport'
        ) => Promise<MediaOverlaySelection | null>
      >()
      .mockResolvedValue(photoSelection(9)),
    releaseCandidateContext: vi.fn(),
  };
  const runtime = {
    overlayMap: map,
    mediaOverlay: media,
    mediaPendingSelection: null as MediaOverlaySelection | null,
    mediaSelectionSheetVisible: false,
    mediaSheetVisible: false,
    mediaSheetMediaId: null as number | null,
    mediaNavList: [] as Array<{ id: number; lat: number; lng: number }>,
    mediaNavTotal: 0,
    mediaNavOffset: 0,
    mediaNavClusterId: null as number | null,
    mediaNavPageSize: 0,
    mediaNavLoading: false,
    mediaNavScope: 'photo',
    mapInteraction: null as MapInteractionState | null,
    mapInteractionDetailKind: null as string | null,
    mapInteractionDetailLoading: false,
    mapInteractionDetailError: null as string | null,
    trackDetailsVisible: false,
    trackDetailsId: null as number | null,
    mapInteractionOverlayDetails: null as
      import('@/components/map/composables/mapControllerRuntime').MapOverlayRoute | null,
    measureToolActive: false,
    plannerToolActive: false,
    geoDrawingParamDef: null as Record<string, unknown> | null,
    tracksEnabled: true,
    mediaVisible: true,
    trackPointsVisible: true,
    activeOverlays: ['wanderland'],
    hiddenGroups: new Set<string>(),
    gpsTrackIdToFeature: new Map<number, { properties: Record<string, unknown>; geometry?: GeoJSON.Geometry }>(),
    gpsTracksById: new Map<number, { reviewMapExcluded?: boolean; startDate?: Date }>(),
    layerOpacities: {
      tracks: 100,
      media: 100,
      trackpoints: 100,
      wanderland: 100,
      'wmt-hiking': 100,
      'wmt-cycling': 100,
      'wmt-mtb': 100,
    },
    closeTrackPointPopup: vi.fn(),
    closeMediaSelection: vi.fn(() => {
      runtime.mediaPendingSelection = null;
      runtime.mediaSelectionSheetVisible = false;
    }),
    closeMediaSheet: vi.fn(),
    previewTrackById: vi.fn(),
    closeSelectionPopup: vi.fn(),
    closeSwissMobilityPopup: vi.fn(),
    clearFocusedMediaMarker: vi.fn(),
    selectTrackById: vi.fn(),
    deselectTrack: vi.fn(),
    showTrackLinePointPopup: vi.fn().mockResolvedValue(true),
    openTrackPointCandidate: vi.fn().mockResolvedValue(undefined),
    trackPointsPopupContext: {},
    openTrackDetails: vi.fn(),
    openMediaSelection: vi.fn().mockResolvedValue(undefined),
    chooseMediaCollection: vi.fn((scope: 'primary' | 'viewport') => {
      useMediaAndHeatmap({ mapSettingsStore: { setLayerEnabled: vi.fn() } }).chooseMediaCollection.call(
        runtime as never,
        scope
      );
    }),
    collectTrackPointCandidates: vi.fn(() => [] as unknown[]),
    getTrackPopupMeta: vi.fn((id: number) => ({
      id,
      name: `Track ${id}`,
      displayName: `Track ${id}`,
      date: '2026-10-12',
      activityType: '',
      description: '',
    })),
    identifySwissMobilityRoutes: vi
      .fn<(_lngLat: unknown, _point: unknown, _signal: AbortSignal, _radius: number) => Promise<MapTargetCandidate[]>>()
      .mockResolvedValue([]),
    ...useMapInteractions(deps as unknown as Parameters<typeof useMapInteractions>[0]),
  };
  const typedRuntime = runtime as typeof runtime & MapControllerRuntime;
  disposers.push(() => typedRuntime.disposeMapInteractions());
  return {
    runtime: typedRuntime,
    map,
    media,
    deps,
    collectPois,
    canvas,
    listeners,
    fire: (name: string, event: unknown) => {
      listeners.get(name)!(event as never);
      if (confirmClicks && name === 'click' && (event as ReturnType<typeof clickEvent>).originalEvent.detail <= 1) {
        vi.advanceTimersByTime(MAP_CLICK_CONFIRMATION_DELAY_MS);
      }
    },
  };
}

function clickEvent(detail = 1) {
  return {
    defaultPrevented: false,
    point: { x: 100, y: 80 },
    lngLat: { lng: 7.52, lat: 47.48 },
    originalEvent: new MouseEvent('click', { detail }),
  };
}

function pointerEvent(pointerType: string): Event {
  return Object.assign(new Event('pointerdown'), { pointerType });
}

function poiCandidate(): PoiMapCandidate {
  return {
    id: 'poi:1',
    kind: 'poi',
    sourceId: 'protomaps',
    label: 'Spring',
    detail: 'Drinking water',
    coordinates: [7.52, 47.48],
    distancePx: 0,
    payload: { kind: 'drinking_water' },
  };
}

function photoCandidate(id = 9, kind: 'location' | 'cluster' = 'location'): MediaOverlayCandidate {
  return {
    key: `media:${id}`,
    kind,
    clusterId: kind === 'cluster' ? 17 : null,
    totalMediaCount: kind === 'cluster' ? 3 : 1,
    mediaPoints: kind === 'cluster' ? [] : [{ id, lat: 47.48, lng: 7.52 }],
    viewportMediaPoints: [],
    clickPoint: { x: 100, y: 80 },
    clickLngLat: { lng: 7.52, lat: 47.48 },
  };
}

function photoSelection(id: number): MediaOverlaySelection {
  return {
    selectedMediaId: id,
    mediaIds: [id],
    mediaPoints: [{ id, lat: 47.48, lng: 7.52 }],
    totalMediaCount: 1,
    clusterId: null,
    offset: 0,
    kind: 'location',
    viewportMediaPoints: [],
    clickPoint: { x: 100, y: 80 },
    clickLngLat: { lng: 7.52, lat: 47.48 },
  };
}

function routeCandidate(id = '1'): MapTargetCandidate {
  return {
    id,
    kind: 'overlay',
    source: 'wanderland',
    sourceLabel: 'Trail overlay',
    label: 'Trail',
    route: { icon: '', type: 'Hiking', name: 'Trail' },
    match: 'identified',
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

async function flushMicrotasks() {
  for (let index = 0; index < 8; index++) await Promise.resolve();
}
