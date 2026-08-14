import type { ReplayCameraPresetId } from '@/components/replay/trackReplayPath';
import type { ChartPoint } from '@/utils/ServiceHelper';
import type { GpsTrack, GpsTrackDataPoint } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

export type MapRendererMode = '2d' | '3d';

export type MapCameraState = {
  center: [number, number];
  zoom: number;
  bearing: number;
  pitch: number;
  roll?: number;
  elevation?: number;
};

type CameraStateMap = {
  getCenter: () => { lng: number; lat: number };
  getZoom: () => number;
  getBearing: () => number;
  getPitch: () => number;
  getRoll?: () => number | null | undefined;
  getCenterElevation?: () => number | null | undefined;
};

export function readMapCameraState(map: CameraStateMap, includeElevation = false): MapCameraState {
  const center = map.getCenter();
  const camera: MapCameraState = {
    center: [center.lng, center.lat],
    zoom: map.getZoom(),
    bearing: map.getBearing(),
    pitch: map.getPitch(),
  };
  const roll = map.getRoll?.();
  if (Number.isFinite(roll)) camera.roll = roll as number;
  if (includeElevation) {
    const elevation = map.getCenterElevation?.();
    if (Number.isFinite(elevation)) camera.elevation = elevation as number;
  }
  return camera;
}

export type SelectedTrackMetadata = {
  id: number | null;
  name: string;
  description: string;
  activityType: string;
};

export type ReplayViewState = {
  active: boolean;
  loading: boolean;
  playing: boolean;
  progress: number;
  durationSeconds: number;
  activityElapsedSeconds: number;
  activityDurationSeconds: number;
  cameraPreset: ReplayCameraPresetId;
  cameraSmoothness: number;
  showContextTracks: boolean;
  showTelemetry: boolean;
  autoFollow: boolean;
  currentTrackId: number | null;
  trackLabel: string;
  distanceLabel: string;
  elapsedLabel: string;
  remainingLabel: string;
  totalLabel: string;
  speedFactorLabel: string;
};

export type ReplaySourceData = {
  trackId: number;
  coordinates: number[][];
  gpsTrack: GpsTrack;
  chartPoints: ChartPoint[];
  renderedShapePoints: GpsTrackDataPoint[];
};

export type MapSheetState = {
  locationSearchVisible: boolean;
  trackSelectionVisible: boolean;
  trackDetailsVisible: boolean;
  mediaVisible: boolean;
};
