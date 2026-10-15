import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useMediaAndHeatmap } from '@/components/map/composables/useMediaAndHeatmap';
import { fetchTrackMediaOptionsWithinDistanceOfPoint } from '@/utils/ServiceHelper';
import type { MediaOverlaySelection } from '@/layers/MediaOverlay';

vi.mock('@/utils/ServiceHelper', () => ({ fetchTrackMediaOptionsWithinDistanceOfPoint: vi.fn() }));
vi.mock('@/utils/safeLogging', () => ({ warnSanitized: vi.fn() }));
beforeEach(() => vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mockReset().mockResolvedValue([]));

const selection: MediaOverlaySelection = {
  selectedMediaId: 10,
  mediaIds: [10],
  mediaPoints: [{ id: 10, lat: 47, lng: 8 }],
  totalMediaCount: 1,
  clusterId: null,
  offset: 0,
  kind: 'location',
  viewportMediaPoints: [{ id: 10, lat: 47, lng: 8 }],
  clickPoint: { x: 100, y: 80 },
  clickLngLat: { lng: 8, lat: 47 },
};

describe('photo collection nearby GPS lookup after map movement', () => {
  it.each([
    { movement: 'sheet reveal pan', inputKind: 'pointer', movedPoint: { x: 100, y: 20 } },
    { movement: 'manual pan', inputKind: 'touch', movedPoint: { x: 340, y: 240 } },
  ] as const)('keeps the physical radius and geographic anchor after $movement', async ({ inputKind, movedPoint }) => {
    vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mockReset().mockResolvedValue([]);
    const methods = useMediaAndHeatmap({ mapSettingsStore: { setLayerEnabled: vi.fn() } });
    let projectedLocation = { x: 100, y: 80 };
    const project = vi.fn(() => projectedLocation);
    const unproject = vi.fn(([x, y]: [number, number]) => ({
      lng: 8 + (x - projectedLocation.x) / 100_000,
      lat: 47 + (y - projectedLocation.y) / 100_000,
    }));
    const runtime = {
      overlayMap: { project, unproject },
      mapInteraction: { context: { inputKind } },
      mediaSelectionRequestToken: 0,
      mediaSelectionAbortController: null,
      mediaSelectionSheetVisible: false,
      mediaPendingSelection: null,
      mediaSelectionTrackOptions: [],
      mediaSelectionTracksLoading: false,
      closeMediaSelection: methods.closeMediaSelection,
    };
    await methods.openMediaSelection.call(runtime as never, selection);
    const before = vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mock.calls[0];
    projectedLocation = { ...movedPoint };
    await methods.openMediaSelection.call(runtime as never, selection);
    const after = vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mock.calls[1];
    expect(project).toHaveBeenCalledWith(selection.clickLngLat);
    expect(before.slice(0, 2)).toEqual([8, 47]);
    expect(after.slice(0, 2)).toEqual([8, 47]);
    expect(before[2]).toBeGreaterThan(0);
    expect(after[2]).toBeCloseTo(before[2], 6);
    expect(after[3]).toBeInstanceOf(AbortSignal);
    expect(selection.clickPoint).toEqual({ x: 100, y: 80 });
    expect(runtime.mediaSelectionTracksLoading).toBe(false);
  });

  it('distinguishes a lookup failure and retries the retained selection without replacing its photo scope', async () => {
    const methods = useMediaAndHeatmap({ mapSettingsStore: { setLayerEnabled: vi.fn() } });
    const retry =
      Promise.withResolvers<Array<{ trackId: number; distanceMeters: number; matchedMediaCount: number }>>();
    vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint)
      .mockRejectedValueOnce(new Error('network failure'))
      .mockReturnValueOnce(retry.promise);
    const runtime = {
      overlayMap: { project: vi.fn(() => ({ x: 100, y: 80 })), unproject: vi.fn(() => ({ lng: 8.00016, lat: 47 })) },
      mapInteraction: { context: { inputKind: 'pointer' } },
      mediaSelectionRequestToken: 0,
      mediaSelectionAbortController: null,
      mediaSelectionSheetVisible: false,
      mediaPendingSelection: null,
      mediaSelectionTrackOptions: [],
      mediaSelectionTracksLoading: false,
      mediaSelectionTracksError: null,
      closeMediaSelection: methods.closeMediaSelection,
      openMediaSelection: methods.openMediaSelection,
    };
    await methods.openMediaSelection.call(runtime as never, selection);
    expect(runtime.mediaSelectionTracksError).toBe('Nearby GPS tracks are unavailable.');
    expect(runtime.mediaPendingSelection).toBe(selection);
    const pending = methods.retryMediaSelectionActivities.call(runtime as never);
    expect(runtime.mediaSelectionTracksLoading).toBe(true);
    expect(runtime.mediaSelectionTracksError).toBeNull();
    expect(runtime.mediaPendingSelection).toBe(selection);
    retry.resolve([{ trackId: 42, distanceMeters: 20, matchedMediaCount: 0 }]);
    await pending;
    expect(runtime.mediaSelectionTracksLoading).toBe(false);
    expect(runtime.mediaSelectionTracksError).toBeNull();
    expect(runtime.mediaSelectionTrackOptions).toEqual([{ trackId: 42, distanceMeters: 20, matchedMediaCount: 0 }]);
    expect(vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mock.calls.map((call) => call.slice(0, 2))).toEqual([
      [8, 47],
      [8, 47],
    ]);
  });

  it('does not restore an error from a discarded lookup and ignores retry after its selection closes', async () => {
    const methods = useMediaAndHeatmap({ mapSettingsStore: { setLayerEnabled: vi.fn() } });
    const lookup =
      Promise.withResolvers<Array<{ trackId: number; distanceMeters: number; matchedMediaCount: number }>>();
    vi.mocked(fetchTrackMediaOptionsWithinDistanceOfPoint).mockReturnValueOnce(lookup.promise);
    const runtime = {
      overlayMap: { project: () => ({ x: 100, y: 80 }), unproject: () => ({ lng: 8.00016, lat: 47 }) },
      mediaSelectionRequestToken: 0,
      mediaSelectionAbortController: null,
      mediaSelectionSheetVisible: false,
      mediaPendingSelection: null,
      mediaSelectionTrackOptions: [],
      mediaSelectionTracksLoading: false,
      mediaSelectionTracksError: null,
      closeMediaSelection: methods.closeMediaSelection,
      openMediaSelection: methods.openMediaSelection,
    };
    const pending = methods.openMediaSelection.call(runtime as never, selection);
    methods.closeMediaSelection.call(runtime as never);
    lookup.reject(new Error('late network failure'));
    await pending;
    expect(runtime.mediaSelectionTracksError).toBeNull();
    expect(runtime.mediaPendingSelection).toBeNull();
    await methods.retryMediaSelectionActivities.call(runtime as never);
    expect(fetchTrackMediaOptionsWithinDistanceOfPoint).toHaveBeenCalledOnce();
  });
});
