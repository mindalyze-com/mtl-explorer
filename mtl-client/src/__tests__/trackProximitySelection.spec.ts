import { describe, expect, it, vi } from 'vitest';
import { applyTrackProximityResponse } from '@/components/map/trackProximitySelection';
import { useMapTools } from '@/components/map/composables/useMapTools';
import { TrackProcessingStatusDtoStateEnum, type TrackProcessingStatusDto } from 'x8ing-mtl-api-typescript-fetch';

const ready: TrackProcessingStatusDto = {
  state: TrackProcessingStatusDtoStateEnum.Ready,
  stages: [],
  failedGpsFiles: 0,
};

function actions() {
  return {
    deselect: vi.fn(),
    showResults: vi.fn(),
    showPoint: vi.fn().mockResolvedValue(false),
    select: vi.fn(),
    openDetails: vi.fn(),
  };
}

describe('nearby track selection', () => {
  it.each([{ trackIds: [] }, { trackIds: [42] }, { trackIds: [42, 43] }])(
    'shows running status with $trackIds results',
    async ({ trackIds }) => {
      const processing = { ...ready, state: TrackProcessingStatusDtoStateEnum.Running };
      const target = actions();
      await applyTrackProximityResponse({ trackIds, processing }, new AbortController().signal, target);
      expect(target.showResults).toHaveBeenCalledWith(trackIds, processing);
      expect(target.openDetails).not.toHaveBeenCalled();
      expect(target.showPoint).not.toHaveBeenCalled();
      expect(target.deselect).toHaveBeenCalledTimes(trackIds.length === 0 ? 1 : 0);
    }
  );

  it.each([
    { ...ready, state: TrackProcessingStatusDtoStateEnum.Unavailable },
    { ...ready, failedGpsFiles: 2 },
  ])('shows uncertain or failed processing even for zero matches', async (processing) => {
    const target = actions();
    await applyTrackProximityResponse({ trackIds: [], processing }, new AbortController().signal, target);
    expect(target.showResults).toHaveBeenCalledWith([], processing);
  });

  it('keeps completed empty searches quiet', async () => {
    const target = actions();
    await applyTrackProximityResponse({ trackIds: [], processing: ready }, new AbortController().signal, target);
    expect(target.deselect).toHaveBeenCalledOnce();
    expect(target.showResults).not.toHaveBeenCalled();
  });

  it.each([false, true])('keeps completed single-track selection and point popups (%s)', async (pointShown) => {
    const target = actions();
    target.showPoint.mockResolvedValue(pointShown);
    await applyTrackProximityResponse({ trackIds: [42], processing: ready }, new AbortController().signal, target);
    expect(target.showPoint).toHaveBeenCalledWith(42);
    expect(target.select).toHaveBeenCalledWith(42);
    expect(target.openDetails).toHaveBeenCalledTimes(pointShown ? 0 : 1);
    expect(target.showResults).not.toHaveBeenCalled();
  });

  it('keeps completed multiple-track selection', async () => {
    const target = actions();
    await applyTrackProximityResponse({ trackIds: [42, 43], processing: ready }, new AbortController().signal, target);
    expect(target.showResults).toHaveBeenCalledWith([42, 43], ready);
  });

  it('clears processing metadata when closing or replacing the selection sheet', () => {
    const methods = useMapTools();
    const target = {
      selectionPopupTrackIds: [] as number[],
      selectionPopupProcessing: null as TrackProcessingStatusDto | null,
      selectionPopupMediaOptions: [],
      trackSelectionPurpose: 'details',
      trackSelectionSheetVisible: false,
      closeSelectionPopup: () => methods.closeSelectionPopup.call(target as never),
    };
    const processing = { ...ready, state: TrackProcessingStatusDtoStateEnum.Running };
    methods.showTrackSelectionPopup.call(target as never, { x: 1, y: 1 }, [], processing);
    expect(target.selectionPopupProcessing).toBe(processing);
    expect(target.trackSelectionSheetVisible).toBe(true);
    target.closeSelectionPopup();
    expect(target.selectionPopupProcessing).toBeNull();
    methods.showTrackSelectionPopup.call(target as never, { x: 1, y: 1 }, [42]);
    expect(target.selectionPopupProcessing).toBeNull();
  });

  it('ignores superseded responses and selections after an async point lookup', async () => {
    const abort = new AbortController();
    abort.abort();
    const target = actions();
    await applyTrackProximityResponse({ trackIds: [42, 43], processing: ready }, abort.signal, target);
    expect(target.showResults).not.toHaveBeenCalled();

    const nextAbort = new AbortController();
    target.showPoint.mockImplementation(async () => {
      nextAbort.abort();
      return false;
    });
    await applyTrackProximityResponse({ trackIds: [42], processing: ready }, nextAbort.signal, target);
    expect(target.select).not.toHaveBeenCalled();
    expect(target.openDetails).not.toHaveBeenCalled();
  });
});
