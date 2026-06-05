import { defineStore } from 'pinia';
import { reactive, ref, shallowRef } from 'vue';
import { REPLAY_DEFAULT_CAMERA_PRESET, REPLAY_DEFAULT_CAMERA_SMOOTHNESS } from '@/components/replay/trackReplayCamera';
import { REPLAY_DEFAULT_TARGET_DURATION_SECONDS } from '@/components/replay/trackReplayPath';
import type {
  MapRendererMode,
  MapSheetState,
  ReplaySourceData,
  ReplayViewState,
  SelectedTrackMetadata,
} from '@/components/map/mapRendererTypes';

const DEFAULT_REPLAY_VIEW_STATE: ReplayViewState = {
  active: false,
  loading: false,
  playing: false,
  progress: 0,
  durationSeconds: REPLAY_DEFAULT_TARGET_DURATION_SECONDS,
  activityElapsedSeconds: 0,
  activityDurationSeconds: 0,
  cameraPreset: REPLAY_DEFAULT_CAMERA_PRESET,
  cameraSmoothness: REPLAY_DEFAULT_CAMERA_SMOOTHNESS,
  showContextTracks: false,
  showTelemetry: true,
  autoFollow: true,
  currentTrackId: null,
  trackLabel: '',
  distanceLabel: '0 m',
  elapsedLabel: '0m 00s',
  remainingLabel: '0m 00s',
  totalLabel: '45s',
  speedFactorLabel: '-',
};

const DEFAULT_SHEET_STATE: MapSheetState = {
  locationSearchVisible: false,
  trackSelectionVisible: false,
  trackDetailsVisible: false,
  mediaVisible: false,
};

type Enter3DReplayOptions = {
  trackId: number;
  metadata?: SelectedTrackMetadata | null;
  trackLabel?: string;
  replaySource?: ReplaySourceData | null;
};

export const useMapStateStore = defineStore('mapState', () => {
  const mapMode = ref<MapRendererMode>('2d');
  const selectedTrackId = ref<number | null>(null);
  const selectedTrackMetadata = ref<SelectedTrackMetadata | null>(null);
  const activeToolId = ref<string | null>(null);
  const sheets = reactive<MapSheetState>({ ...DEFAULT_SHEET_STATE });
  const replay = reactive<ReplayViewState>({ ...DEFAULT_REPLAY_VIEW_STATE });
  const replaySource = shallowRef<ReplaySourceData | null>(null);

  function setMapMode(mode: MapRendererMode): void {
    mapMode.value = mode;
  }

  function setSelectedTrack(trackId: number | null, metadata: SelectedTrackMetadata | null = null): void {
    selectedTrackId.value = Number.isFinite(Number(trackId)) ? Number(trackId) : null;
    selectedTrackMetadata.value = selectedTrackId.value == null ? null : metadata;
  }

  function clearSelectedTrack(): void {
    selectedTrackId.value = null;
    selectedTrackMetadata.value = null;
  }

  function setActiveTool(toolId: string | null | undefined): void {
    activeToolId.value = toolId ?? null;
  }

  function setSheetState(nextSheets: Partial<MapSheetState>): void {
    Object.assign(sheets, nextSheets);
  }

  function patchReplayState(nextReplay: Partial<ReplayViewState>): void {
    Object.assign(replay, nextReplay);
  }

  function resetReplayState(overrides: Partial<ReplayViewState> = {}): void {
    Object.assign(replay, DEFAULT_REPLAY_VIEW_STATE, overrides);
  }

  function enter3DReplay(options: Enter3DReplayOptions): void {
    const trackId = Number(options.trackId);
    if (!Number.isFinite(trackId)) return;
    replaySource.value = options.replaySource ?? null;
    if (options.metadata) {
      setSelectedTrack(trackId, options.metadata);
    } else if (selectedTrackId.value !== trackId) {
      setSelectedTrack(trackId, null);
    }
    resetReplayState({
      active: true,
      loading: true,
      playing: false,
      progress: 0,
      currentTrackId: trackId,
      trackLabel: options.trackLabel ?? selectedTrackMetadata.value?.name ?? `Track ${trackId}`,
    });
    mapMode.value = '3d';
  }

  function exit3DReplay(): void {
    mapMode.value = '2d';
    replaySource.value = null;
    resetReplayState();
  }

  function resetSessionState(): void {
    mapMode.value = '2d';
    replaySource.value = null;
    clearSelectedTrack();
    setActiveTool(null);
    setSheetState(DEFAULT_SHEET_STATE);
    resetReplayState();
  }

  return {
    mapMode,
    selectedTrackId,
    selectedTrackMetadata,
    activeToolId,
    sheets,
    replay,
    replaySource,
    setMapMode,
    setSelectedTrack,
    clearSelectedTrack,
    setActiveTool,
    setSheetState,
    patchReplayState,
    resetReplayState,
    enter3DReplay,
    exit3DReplay,
    resetSessionState,
  };
});
