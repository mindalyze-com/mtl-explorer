import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useMapStateStore } from '@/stores/mapStateStore';

describe('useMapStateStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('starts in the current 2D renderer mode with no selected track', () => {
    const store = useMapStateStore();

    expect(store.mapMode).toBe('2d');
    expect(store.selectedTrackId).toBeNull();
    expect(store.selectedTrackMetadata).toBeNull();
    expect(store.replay.active).toBe(false);
    expect(store.replay.showContextTracks).toBe(false);
    expect(store.replay.showTelemetry).toBe(true);
  });

  it('stores selected track metadata without renderer internals', () => {
    const store = useMapStateStore();

    store.setSelectedTrack(42, {
      id: 42,
      name: 'Evening ridge',
      description: 'Synthetic test track',
      activityType: 'HIKING',
    });

    expect(store.selectedTrackId).toBe(42);
    expect(store.selectedTrackMetadata).toMatchObject({ id: 42, name: 'Evening ridge' });

    store.clearSelectedTrack();
    expect(store.selectedTrackId).toBeNull();
    expect(store.selectedTrackMetadata).toBeNull();
  });

  it('patches and resets replay UI state', () => {
    const store = useMapStateStore();

    store.patchReplayState({
      active: true,
      loading: true,
      playing: true,
      progress: 0.4,
      currentTrackId: 7,
      autoFollow: false,
    });

    expect(store.replay).toMatchObject({
      active: true,
      loading: true,
      playing: true,
      progress: 0.4,
      currentTrackId: 7,
      autoFollow: false,
    });

    store.resetReplayState();
    expect(store.replay.active).toBe(false);
    expect(store.replay.progress).toBe(0);
    expect(store.replay.currentTrackId).toBeNull();
    expect(store.replay.autoFollow).toBe(true);
    expect(store.replay.showContextTracks).toBe(false);
    expect(store.replay.showTelemetry).toBe(true);
  });

  it('transitions into and out of 3D replay without renderer internals', () => {
    const store = useMapStateStore();

    store.setSelectedTrack(12, {
      id: 12,
      name: 'Ridge loop',
      description: 'Synthetic test track',
      activityType: 'RUNNING',
    });

    store.enter3DReplay({ trackId: 12, trackLabel: 'Ridge loop' });
    expect(store.mapMode).toBe('3d');
    expect(store.selectedTrackId).toBe(12);
    expect(store.replay).toMatchObject({
      active: true,
      loading: true,
      playing: false,
      progress: 0,
      currentTrackId: 12,
      trackLabel: 'Ridge loop',
      showContextTracks: false,
      showTelemetry: true,
    });

    store.exit3DReplay();
    expect(store.mapMode).toBe('2d');
    expect(store.selectedTrackId).toBe(12);
    expect(store.replay.active).toBe(false);
    expect(store.replay.currentTrackId).toBeNull();
  });

  it('keeps active tool and sheet state renderer-independent', () => {
    const store = useMapStateStore();

    store.setActiveTool('filter');
    store.setSheetState({ trackDetailsVisible: true, mediaVisible: true });

    expect(store.activeToolId).toBe('filter');
    expect(store.sheets.trackDetailsVisible).toBe(true);
    expect(store.sheets.mediaVisible).toBe(true);
  });
});
