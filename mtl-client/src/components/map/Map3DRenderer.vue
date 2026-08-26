<template>
  <div class="map3d-renderer">
    <div ref="mapContainer" class="map3d-canvas"></div>

    <transition name="fade">
      <div v-if="startupMessage" class="map3d-status">
        <i class="bi bi-badge-3d"></i>
        <span>{{ startupMessage }}</span>
      </div>
    </transition>

    <ReplayTelemetryOverlay
      v-if="replay.active && replay.showTelemetry"
      :current-speed-kmh="telemetryCurrentSpeedKmh"
      :max-speed-kmh="telemetryMaxSpeedKmh"
      :elapsed-label="replay.elapsedLabel"
      :remaining-label="telemetryRemainingLabel"
      :distance-current-label="telemetryDistanceCurrentLabel"
      :distance-total-label="telemetryDistanceTotalLabel"
      :elevation-gain-current-label="telemetryElevationGainCurrentLabel"
      :elevation-max-label="telemetryElevationMaxLabel"
    />

    <TrackReplayControls
      :active="replay.active"
      :auto-follow="replay.autoFollow"
      :camera-preset="replay.cameraPreset"
      :camera-smoothness="replay.cameraSmoothness"
      :distance-label="replayDistanceLabel"
      :duration-seconds="replay.durationSeconds"
      :elapsed-label="replay.elapsedLabel"
      :loading="replay.loading"
      :playing="replay.playing"
      :progress="replay.progress"
      :remaining-label="replay.remainingLabel"
      :show-context-tracks="replay.showContextTracks"
      :show-telemetry="replay.showTelemetry"
      :speed-factor-label="replay.speedFactorLabel"
      :total-label="replay.totalLabel"
      @toggle-play="togglePlayback"
      @stop="resetPlayback"
      @close="requestModeClose"
      @seek="seekReplay"
      @update-show-context-tracks="setShowContextTracks"
      @update-show-telemetry="setShowTelemetry"
      @update-duration="setDuration"
      @update-camera-preset="setCameraPreset"
      @update-camera-smoothness="setCameraSmoothness"
      @sheet-layout-change="onReplayControlsLayoutChange"
      @recenter="recenterCamera"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, inject, markRaw, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import * as maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import TrackReplayControls from '@/components/replay/TrackReplayControls.vue';
import ReplayTelemetryOverlay from '@/components/replay/ReplayTelemetryOverlay.vue';
import { useMapStateStore } from '@/stores/mapStateStore';
import { useMapSettingsStore } from '@/stores/mapSettingsStore';
import { fetchMapConfig, mainTileArchiveUrl, MapConfigDtoTileModeEnum, type MapConfig } from '@/utils/mapConfigService';
import { fetchMapStatus } from '@/utils/mapStatusService';
import { resolveConfiguredMapStyle } from '@/components/map/mapStyleResolver';
import { registerCachingPMTilesArchive } from '@/utils/maplibrePmtilesProtocol';
import { enableTerrainView, TERRAIN_TARGET_PITCH } from '@/components/map/terrainMode';
import { MAP_OVERLAYS } from '@/utils/mapStyle';
import { TrackReplayController, type ReplayPlaybackFrame } from '@/components/replay/trackReplayController';
import { TrackReplayLayer, TRACK_REPLAY_LAYER_ID } from '@/components/replay/TrackReplayLayer';
import {
  ReplayCameraRailPlanner,
  type ReplayCameraFrame,
  type ReplayCameraViewport,
} from '@/components/replay/replayCameraRailPlanner';
import {
  computeReplayViewportPadding,
  observeReplayViewportOcclusion,
  replayCameraViewportKey,
  replayMapPadding as toReplayMapPadding,
  resolveReplayCameraViewport,
  type ReplayMapPadding,
  type ReplayViewportPadding,
} from '@/components/replay/replayViewportOcclusion';
import { ReplayCameraScreenGuard } from '@/components/replay/replayCameraScreenGuard';
import { clampReplayCameraSmoothness, replayCameraPreset } from '@/components/replay/trackReplayCamera';
import {
  buildTimedReplayPath,
  buildReplayPath,
  distanceProgressForReplaySample,
  formatReplaySpeedFactor,
  sampleReplayPathAtElapsedSeconds,
  sanitizeReplayTargetDuration,
  type ReplayPath,
} from '@/components/replay/trackReplayPath';
import {
  buildReplayTelemetry,
  sampleReplayTelemetryAtElapsedSeconds,
  type ReplayTelemetry,
} from '@/components/replay/trackReplayTelemetry';
import { DETAIL_TRACK_PRECISION } from '@/utils/tracks/trackConstants';
import { fetchDetailTrackAtPrecision, loadCachedTrackCollection } from '@/utils/tracks/trackCollectionLoader';
import { formatDistanceSmart, formatDurationSmart, formatElevation } from '@/utils/Utils';
import { TRACK_COLOR } from '@/utils/trackColors';
import {
  chartSeriesToPoints,
  fetchChartSeries,
  MetricKey,
  XMode,
  type MetricKey as ChartMetricKey,
} from '@/utils/chartSeriesAdapter';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { unwrapLngLatCoordinates } from '@/components/map/mapGeometry';
import { configureExternalAttributionLinks } from '@/utils/externalAttributionLinks';
import type { BottomSheetLayoutState } from '@/components/ui/BottomSheet.vue';
import type { ToastService } from '@/types/ui';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { mapScaleUnitForMeasurementSystem, syncMapScaleControlUnit } from '@/components/map/mapScaleControl';

type InteractionHandlers = {
  canvas: HTMLCanvasElement;
  clearUserPointerActive: () => void;
  disableAutoFollow: (event?: { originalEvent?: Event }) => void;
  disableAutoFollowFromWheel: (event: WheelEvent) => void;
  markUserPointerActive: () => void;
};
const CONTEXT_SOURCE_ID = 'map3d-context-tracks';
const CONTEXT_LINE_LAYER_ID = 'map3d-context-tracks-line';
const CONTEXT_DOT_LAYER_ID = 'map3d-context-tracks-dots';
const MAP_LOAD_WATCHDOG_MS = 7000;
const DEFAULT_3D_ZOOM = 14;
const CONTEXT_TRACK_OPACITY = 0.18;
const MAP_NAVIGATION_CONTROL_OPTIONS = Object.freeze({ showCompass: true, showZoom: true, visualizePitch: true });
const REPLAY_TELEMETRY_BUCKETS = 320;
const REPLAY_MAP_THEME = 'light-topo';
const REPLAY_CAMERA_MAP_MARGIN_PX = 32;
const REPLAY_CAMERA_INITIAL_FIT_MARGIN_PX = 72;
const REPLAY_CAMERA_MIN_VISIBLE_WIDTH_PX = 180;
const REPLAY_CAMERA_MIN_VISIBLE_HEIGHT_PX = 160;
const REPLAY_TELEMETRY_METRICS: ChartMetricKey[] = [
  MetricKey.DistanceM,
  MetricKey.DurationS,
  MetricKey.AltitudeM,
  MetricKey.AscentM,
  MetricKey.SpeedWindowKmh,
  MetricKey.SpeedBucketAvgKmh,
  MetricKey.SpeedMovingWindowKmh,
  MetricKey.SlopePercent,
];

defineOptions({
  name: 'Map3DRenderer',
});

const emit = defineEmits<{
  ready: [];
  'track-selected': [trackId: number];
  'track-details-requested': [trackId: number];
  'mode-close-requested': [];
  'load-failed': [];
}>();

const toast = inject<ToastService>('toast', { add: () => undefined });
const mapStateStore = useMapStateStore();
const mapSettingsStore = useMapSettingsStore();
const { measurementSystem } = useMeasurementSystem();
const { replay, replaySource, selectedTrackId } = storeToRefs(mapStateStore);
const mapContainer = ref<HTMLElement | null>(null);
const startupMessage = ref('Preparing 3D replay');
const replayTelemetry = ref<ReplayTelemetry | null>(null);
const replayControlsLayout = ref<BottomSheetLayoutState | null>(null);

const telemetrySample = computed(() => {
  const telemetry = replayTelemetry.value;
  if (!telemetry) return null;
  return sampleReplayTelemetryAtElapsedSeconds(telemetry, replay.value.activityElapsedSeconds);
});
const telemetryCurrentSpeedKmh = computed(() => telemetrySample.value?.speedKmh ?? null);
const telemetryMaxSpeedKmh = computed(() => replayTelemetry.value?.maxSpeedKmh ?? 40);
const telemetryRemainingLabel = computed(() => {
  return replay.value.remainingLabel;
});
const telemetryDistanceCurrentLabel = computed(() => {
  const total = replayTelemetry.value?.totalDistanceMeters ?? 0;
  const distance = telemetrySample.value?.distanceMeters ?? replay.value.progress * total;
  return formatDistanceSmart(distance, total);
});
const telemetryDistanceTotalLabel = computed(() => {
  const total = replayTelemetry.value?.totalDistanceMeters ?? 0;
  return formatDistanceSmart(total, total);
});
const telemetryElevationGainCurrentLabel = computed(() => formatOptionalElevation(telemetrySample.value?.ascentMeters));
const telemetryElevationMaxLabel = computed(() => formatOptionalElevation(replayTelemetry.value?.maxElevationMeters));

let map: maplibregl.Map | null = null;
let scaleControl: maplibregl.ScaleControl | null = null;
let replayLayer: TrackReplayLayer | null = null;
let replayPath: ReplayPath | null = null;
let replayCameraRail: ReturnType<typeof ReplayCameraRailPlanner.build> | null = null;
const replayScreenGuard = new ReplayCameraScreenGuard();
let replayController: TrackReplayController | null = null;
let interactionHandlers: InteractionHandlers | null = null;
let applyingCamera = false;
let userPointerActive = false;
let loadGeneration = 0;
let closed = false;
let telemetryWarningShown = false;
let lastReplayCameraViewportKey = '';
let replayCameraViewportFrame: number | null = null;
let replayViewportOcclusionObserver: ReturnType<typeof observeReplayViewportOcclusion> | null = null;
let attributionLinkCleanup: (() => void) | null = null;

const replayDistanceLabel = computed(() => {
  const path = replayPath;
  if (!path) return replay.value.distanceLabel;
  const activityDurationSeconds = replayActivityDurationSeconds(path);
  const sample = sampleReplayPathAtElapsedSeconds(path, replay.value.activityElapsedSeconds, activityDurationSeconds);
  return sample
    ? `${formatDistanceSmart(sample.distanceMeters, path.totalDistanceMeters)} / ${formatDistanceSmart(path.totalDistanceMeters, path.totalDistanceMeters)}`
    : replay.value.distanceLabel;
});

const replayTrackId = computed(() => {
  const trackId = Number(replay.value.currentTrackId ?? selectedTrackId.value);
  return Number.isFinite(trackId) ? trackId : null;
});

onMounted(() => {
  void initialize3DReplay();
});

onBeforeUnmount(() => {
  cleanup3DResources();
});

watch(measurementSystem, (system) => syncMapScaleControlUnit(scaleControl, system));

async function initialize3DReplay() {
  const generation = ++loadGeneration;
  await nextTick();
  const trackId = replayTrackId.value;
  if (!trackId || !mapContainer.value) {
    failReplay('3D replay failed', 'No track is selected for replay.');
    return;
  }

  mapSettingsStore.hydrate();
  mapStateStore.patchReplayState({
    active: true,
    loading: true,
    playing: false,
    progress: 0,
    currentTrackId: trackId,
  });
  telemetryWarningShown = false;
  replayTelemetry.value = null;

  try {
    startupMessage.value = 'Loading track';
    const source = replaySource.value?.trackId === trackId ? replaySource.value : null;
    let gpsTrack = source?.gpsTrack ?? null;
    let path = source
      ? buildTimedReplayPath({
          chartPoints: source.chartPoints,
          renderedShapePoints: source.renderedShapePoints,
        })
      : { points: [], totalDistanceMeters: 0, originalDurationSeconds: null, timingMode: 'time' as const };

    if (path.points.length < 2 || path.totalDistanceMeters <= 0 || !gpsTrack) {
      const detailTrack = await fetchDetailTrackAtPrecision(trackId, DETAIL_TRACK_PRECISION);
      if (generation !== loadGeneration) return;
      gpsTrack = detailTrack.gpsTrack;
      path = buildReplayPath({
        coordinates: detailTrack.coordinates,
        track: detailTrack.gpsTrack,
      });
    }

    if (path.points.length < 2 || path.totalDistanceMeters <= 0) {
      failReplay('3D replay unavailable', 'This track does not have enough geometry for replay.');
      return;
    }
    replayPath = markRaw(path);
    replayTelemetry.value = buildReplayTelemetry({
      chartPoints: source?.chartPoints ?? [],
      track: gpsTrack,
      pathTotalDistanceMeters: path.totalDistanceMeters,
    });
    if (!source) {
      void loadReplayTelemetry(trackId, gpsTrack, path.totalDistanceMeters, generation);
    }
    mapStateStore.setSelectedTrack(trackId, {
      id: trackId,
      name: gpsTrack?.trackName || gpsTrack?.trackDescription || `Track ${trackId}`,
      description: gpsTrack?.trackDescription || '',
      activityType: gpsTrack?.activityType || '',
    });
    const activityDurationSeconds = replayActivityDurationSeconds(path);
    mapStateStore.patchReplayState({
      trackLabel: gpsTrack?.trackName || gpsTrack?.trackDescription || `Track ${trackId}`,
      durationSeconds: sanitizeReplayTargetDuration(replay.value.durationSeconds),
      activityElapsedSeconds: 0,
      activityDurationSeconds,
      cameraPreset: replayCameraPreset(replay.value.cameraPreset).id,
      speedFactorLabel: formatReplaySpeedFactor(path.originalDurationSeconds, replay.value.durationSeconds),
      distanceLabel: `${formatDistanceSmart(0, path.totalDistanceMeters)} / ${formatDistanceSmart(path.totalDistanceMeters, path.totalDistanceMeters)}`,
      elapsedLabel: '0m 00s',
      remainingLabel: formatDurationSmart(activityDurationSeconds * 1000, activityDurationSeconds * 1000),
      totalLabel: formatDurationSmart(activityDurationSeconds * 1000, activityDurationSeconds * 1000),
    });

    startupMessage.value = 'Loading terrain';
    const mapConfig = await fetchMapConfig();
    if (generation !== loadGeneration) return;
    await createMap(mapConfig, path);
    if (generation !== loadGeneration) return;
    await addContextTracks(trackId);
    addReplayLayer(path);
    rebuildCameraRail();
    installInteractionHandlers();
    applyReplayCamera(0, true, 0);

    replayController = markRaw(
      new TrackReplayController({
        targetDurationSeconds: replay.value.durationSeconds,
        activityDurationSeconds,
        onFrame: onReplayFrame,
      })
    );
    replayController.play();
    mapStateStore.patchReplayState({ loading: false });
    startupMessage.value = '';
    emit('ready');
  } catch (error) {
    console.warn('Failed to initialize 3D replay', error);
    failReplay('3D replay failed', 'The replay could not be started for this track.');
  }
}

async function loadReplayTelemetry(
  trackId: number,
  track: Pick<GpsTrack, 'trackLengthInMeter' | 'ascentInMeter' | 'maxAltitude' | 'speedInKmh30sMax'> | null | undefined,
  pathTotalDistanceMeters: number,
  generation: number
) {
  try {
    const response = await fetchChartSeries(trackId, {
      xMode: XMode.Distance,
      maxBuckets: REPLAY_TELEMETRY_BUCKETS,
      metrics: REPLAY_TELEMETRY_METRICS,
    });
    if (generation !== loadGeneration || closed) return;
    const telemetry = buildReplayTelemetry({
      chartPoints: chartSeriesToPoints(response),
      track,
      pathTotalDistanceMeters,
    });
    replayTelemetry.value = telemetry;
    if (!telemetry.hasSpeedData) {
      warnTelemetryUnavailable(
        'Speed telemetry unavailable',
        'The replay controls are running, but this track has no speed series.'
      );
    }
  } catch (error) {
    console.warn('Could not load replay telemetry', error);
    if (generation !== loadGeneration || closed) return;
    warnTelemetryUnavailable(
      'Speed telemetry unavailable',
      'The replay controls are running without the speed overlay data.'
    );
  }
}

function warnTelemetryUnavailable(summary: string, detail: string) {
  if (telemetryWarningShown) return;
  telemetryWarningShown = true;
  toast.add({ severity: 'warn', summary, detail, life: 3500 });
}

async function createMap(mapConfig: MapConfig, path: ReplayPath) {
  const localTilesReady = await resolveLocalTilesReady(mapConfig);
  if (
    mapSettingsStore.mapSourceMode !== 'remote' &&
    mapConfig.tileMode === MapConfigDtoTileModeEnum.Local &&
    localTilesReady
  ) {
    registerCachingPMTilesArchive(mainTileArchiveUrl(mapConfig));
  }
  const resolved = resolveConfiguredMapStyle({
    config: mapConfig,
    theme: REPLAY_MAP_THEME,
    mapSourceMode: mapSettingsStore.mapSourceMode,
    localTilesReady,
  });
  const start = path.points[0];
  map = markRaw(
    new maplibregl.Map({
      container: mapContainer.value as HTMLElement,
      style: resolved.style,
      center: [start.lng, start.lat],
      zoom: DEFAULT_3D_ZOOM,
      bearing: 0,
      pitch: TERRAIN_TARGET_PITCH,
      minZoom: 1,
      attributionControl: false,
      dragRotate: true,
      pitchWithRotate: true,
      touchPitch: true,
    })
  );
  map.addControl(new maplibregl.NavigationControl(MAP_NAVIGATION_CONTROL_OPTIONS), 'top-left');
  scaleControl = markRaw(
    new maplibregl.ScaleControl({
      maxWidth: 100,
      unit: mapScaleUnitForMeasurementSystem(measurementSystem.value),
    })
  );
  map.addControl(scaleControl, 'bottom-left');
  map.addControl(new maplibregl.AttributionControl({ compact: true }), 'bottom-right');
  attributionLinkCleanup?.();
  attributionLinkCleanup = configureExternalAttributionLinks(
    typeof map.getContainer === 'function' ? map.getContainer() : (mapContainer.value as HTMLElement),
    {
      onBlocked: (url) => {
        toast.add({
          severity: 'warn',
          summary: 'External link blocked',
          detail: `Open this link in your browser: ${url}`,
          life: 7000,
        });
      },
    }
  );

  await waitForMapLoad(map);
  enableTerrainView(map, {
    allowAddSource: mapConfig.offline !== true,
    exaggeration: mapSettingsStore.terrainExaggeration,
    targetPitch: TERRAIN_TARGET_PITCH,
  });
  addActiveOverlays();
  installReplayViewportOcclusionObserver();
  fitPathBounds(path);
}

async function resolveLocalTilesReady(mapConfig: MapConfig): Promise<boolean> {
  if (mapSettingsStore.mapSourceMode === 'remote') return false;
  if (mapConfig.tileMode !== MapConfigDtoTileModeEnum.Local) return true;
  if (mapConfig.offline) return false;
  try {
    const status = await fetchMapStatus();
    return status.ready === true;
  } catch {
    return false;
  }
}

function waitForMapLoad(mapInstance: maplibregl.Map): Promise<void> {
  return new Promise((resolve) => {
    if (mapInstance.loaded()) {
      resolve();
      return;
    }
    const watchdog = window.setTimeout(() => resolve(), MAP_LOAD_WATCHDOG_MS);
    mapInstance.once('load', () => {
      clearTimeout(watchdog);
      resolve();
    });
  });
}

function addActiveOverlays() {
  if (!map) return;
  for (const overlay of MAP_OVERLAYS) {
    if (!mapSettingsStore.activeOverlays.includes(overlay.id)) continue;
    if (!map.getSource(overlay.id)) {
      map.addSource(overlay.id, {
        type: 'raster',
        tiles: [overlay.url],
        tileSize: 256,
        attribution: overlay.attribution,
      });
    }
    if (!map.getLayer(`${overlay.id}-overlay`)) {
      map.addLayer({
        id: `${overlay.id}-overlay`,
        type: 'raster',
        source: overlay.id,
        minzoom: 0,
        maxzoom: 22,
        paint: {
          'raster-opacity': (mapSettingsStore.layerOpacities[overlay.id] ?? 100) / 100,
        },
      });
    }
  }
}

async function addContextTracks(trackId: number) {
  if (!map) return;
  let contextGeojson: GeoJSON.FeatureCollection = { type: 'FeatureCollection', features: [] };
  try {
    const cached = await loadCachedTrackCollection();
    if (cached?.geojson?.features?.length) {
      contextGeojson = {
        type: 'FeatureCollection',
        features: cached.geojson.features.filter((feature) => Number(feature.properties?.id) !== trackId),
      };
    }
  } catch (error) {
    console.warn('Could not load cached context tracks for 3D replay', error);
  }
  if (!map.getSource(CONTEXT_SOURCE_ID)) {
    map.addSource(CONTEXT_SOURCE_ID, {
      type: 'geojson',
      data: contextGeojson,
    });
  }
  if (!map.getLayer(CONTEXT_LINE_LAYER_ID)) {
    map.addLayer({
      id: CONTEXT_LINE_LAYER_ID,
      type: 'line',
      source: CONTEXT_SOURCE_ID,
      layout: {
        'line-join': 'round',
        'line-cap': 'round',
      },
      paint: {
        'line-color': TRACK_COLOR,
        'line-width': ['interpolate', ['linear'], ['zoom'], 0, 4, 10, 3, 18, 2],
        'line-opacity': CONTEXT_TRACK_OPACITY,
      },
    });
  }
  if (!map.getLayer(CONTEXT_DOT_LAYER_ID)) {
    map.addLayer({
      id: CONTEXT_DOT_LAYER_ID,
      type: 'circle',
      source: CONTEXT_SOURCE_ID,
      filter: ['==', ['geometry-type'], 'Point'],
      paint: {
        'circle-color': TRACK_COLOR,
        'circle-radius': 4,
        'circle-opacity': CONTEXT_TRACK_OPACITY,
      },
    });
  }
  applyContextTrackVisibility();
}

function applyContextTrackVisibility() {
  if (!map) return;
  // Context tracks are plain 2D line/dot layers; MapLibre drapes them over the
  // terrain mesh automatically, so no separate WebGL layer or warmup is required.
  const visible = replay.value.showContextTracks && mapSettingsStore.tracksEnabled;
  const visibility = visible ? 'visible' : 'none';
  if (map.getLayer(CONTEXT_LINE_LAYER_ID)) map.setLayoutProperty(CONTEXT_LINE_LAYER_ID, 'visibility', visibility);
  if (map.getLayer(CONTEXT_DOT_LAYER_ID)) map.setLayoutProperty(CONTEXT_DOT_LAYER_ID, 'visibility', visibility);
}

function addReplayLayer(path: ReplayPath) {
  if (!map) return;
  if (map.getLayer(TRACK_REPLAY_LAYER_ID)) {
    map.removeLayer(TRACK_REPLAY_LAYER_ID);
  }
  replayLayer = markRaw(new TrackReplayLayer());
  replayLayer.setData(path);
  replayLayer.setProgress(replay.value.progress);
  map.addLayer(replayLayer);
}

function fitPathBounds(path: ReplayPath) {
  if (!map || path.points.length < 2) return;
  const coordinates = unwrapLngLatCoordinates(path.points.map((point) => [point.lng, point.lat] as [number, number]));
  const first = coordinates[0];
  const bounds = new maplibregl.LngLatBounds(first, first);
  for (const coordinate of coordinates) {
    bounds.extend(coordinate);
  }
  if (!bounds.isEmpty()) {
    map.fitBounds(bounds, {
      padding: replayMapPadding(REPLAY_CAMERA_INITIAL_FIT_MARGIN_PX),
      maxZoom: DEFAULT_3D_ZOOM,
      duration: 0,
    });
  }
}

function onReplayFrame(frame: ReplayPlaybackFrame) {
  const path = replayPath;
  if (!path) return;
  const activityDurationSeconds = replayActivityDurationSeconds(path, frame.activityDurationSeconds);
  const activityElapsedSeconds = Math.max(0, Math.min(activityDurationSeconds, frame.elapsedActivitySeconds));
  const sample = sampleReplayPathAtElapsedSeconds(path, activityElapsedSeconds, activityDurationSeconds);
  const distanceProgress = distanceProgressForReplaySample(path, sample);
  const remainingActivitySeconds = Math.max(0, activityDurationSeconds - activityElapsedSeconds);
  mapStateStore.patchReplayState({
    progress: frame.progress,
    playing: frame.status === 'playing',
    durationSeconds: frame.targetDurationSeconds,
    activityElapsedSeconds,
    activityDurationSeconds,
    speedFactorLabel: formatReplaySpeedFactor(path.originalDurationSeconds, frame.targetDurationSeconds),
    elapsedLabel: formatDurationSmart(activityElapsedSeconds * 1000, activityDurationSeconds * 1000),
    remainingLabel: formatDurationSmart(remainingActivitySeconds * 1000, activityDurationSeconds * 1000),
    totalLabel: formatDurationSmart(activityDurationSeconds * 1000, activityDurationSeconds * 1000),
    distanceLabel: sample
      ? `${formatDistanceSmart(sample.distanceMeters, path.totalDistanceMeters)} / ${formatDistanceSmart(path.totalDistanceMeters, path.totalDistanceMeters)}`
      : replay.value.distanceLabel,
  });
  replayLayer?.setProgress(distanceProgress);
  applyReplayCamera(frame.progress, false, frame.elapsedReplaySeconds);
}

function replayActivityDurationSeconds(path: ReplayPath, fallbackSeconds = replay.value.durationSeconds): number {
  const duration = Number(path.originalDurationSeconds);
  if (Number.isFinite(duration) && duration > 0) return duration;
  return fallbackSeconds;
}

function rebuildCameraRail() {
  if (!replayPath) {
    replayCameraRail = null;
    return null;
  }
  const viewport = replayCameraViewport();
  lastReplayCameraViewportKey = replayCameraViewportKey(viewport);
  const rail = ReplayCameraRailPlanner.build({
    path: replayPath,
    durationSeconds: replay.value.durationSeconds,
    preset: replay.value.cameraPreset,
    smoothness: replay.value.cameraSmoothness,
    viewport,
  });
  replayCameraRail = rail ? markRaw(rail) : null;
  return replayCameraRail;
}

function replayCameraViewport(): ReplayCameraViewport | undefined {
  const canvas = map?.getCanvas?.();
  return resolveReplayCameraViewport(canvas, replayCameraViewportPadding(REPLAY_CAMERA_MAP_MARGIN_PX));
}

function replayCameraViewportPadding(baseMarginPx = REPLAY_CAMERA_MAP_MARGIN_PX): ReplayViewportPadding {
  const canvas = map?.getCanvas?.();
  return computeReplayViewportPadding({
    canvas,
    baseMarginPx,
    minVisibleWidthPx: REPLAY_CAMERA_MIN_VISIBLE_WIDTH_PX,
    minVisibleHeightPx: REPLAY_CAMERA_MIN_VISIBLE_HEIGHT_PX,
    layouts: [replayControlsLayout.value],
  });
}

function replayMapPadding(baseMarginPx = REPLAY_CAMERA_MAP_MARGIN_PX): ReplayMapPadding {
  return toReplayMapPadding(replayCameraViewportPadding(baseMarginPx));
}

function onReplayControlsLayoutChange(layout: BottomSheetLayoutState) {
  replayControlsLayout.value = layout;
  scheduleReplayCameraViewportRebuild();
}

function installReplayViewportOcclusionObserver() {
  replayViewportOcclusionObserver?.disconnect();
  replayViewportOcclusionObserver = observeReplayViewportOcclusion(scheduleReplayCameraViewportRebuild);
}

function scheduleReplayCameraViewportRebuild() {
  if (!map || !replayPath) return;
  if (replayCameraViewportFrame !== null) return;
  replayCameraViewportFrame = window.requestAnimationFrame(() => {
    replayCameraViewportFrame = null;
    const nextViewport = replayCameraViewport();
    const nextKey = replayCameraViewportKey(nextViewport);
    if (nextKey === lastReplayCameraViewportKey) return;
    rebuildCameraRail();
    applyReplayCamera(replay.value.progress, true);
  });
}

function applyReplayCamera(progress: number, force = false, elapsedReplaySeconds: number | null = null) {
  if (!map || !replayPath) return;
  if (!replay.value.autoFollow && !force) return;
  const rail = replayCameraRail ?? rebuildCameraRail();
  const elapsedSeconds = Number.isFinite(elapsedReplaySeconds)
    ? (elapsedReplaySeconds as number)
    : progress * replay.value.durationSeconds;
  const frame: ReplayCameraFrame | null | undefined = rail?.sample(elapsedSeconds);
  if (!frame) return;
  // Discrete actions (seek/preset/recenter/rebuild) snap; continuous playback eases.
  if (force) replayScreenGuard.reset();
  applyingCamera = true;
  try {
    replayScreenGuard.apply({
      map,
      path: replayPath,
      progress,
      frame,
      padding: replayMapPadding(REPLAY_CAMERA_MAP_MARGIN_PX),
      applyFrame: (view) => {
        map?.jumpTo(view);
      },
    });
    map.triggerRepaint();
  } finally {
    applyingCamera = false;
  }
}

function togglePlayback() {
  replayController?.toggle();
}

function resetPlayback() {
  mapStateStore.patchReplayState({ autoFollow: true });
  if (replayController) {
    replayController.stop();
    return;
  }
  mapStateStore.patchReplayState({ progress: 0, playing: false });
  replayLayer?.setProgress(0);
  applyReplayCamera(0, true, 0);
}

function seekReplay(progress: number) {
  replayController?.seek(progress);
}

function setShowContextTracks(value: boolean) {
  mapStateStore.patchReplayState({ showContextTracks: value !== false });
  applyContextTrackVisibility();
}

function setShowTelemetry(value: boolean) {
  mapStateStore.patchReplayState({ showTelemetry: value !== false });
}

function setDuration(seconds: number) {
  const next = sanitizeReplayTargetDuration(seconds);
  mapStateStore.patchReplayState({ durationSeconds: next });
  replayController?.setTargetDuration(next);
  rebuildCameraRail();
  if (replayPath) {
    const activityDurationSeconds = replayActivityDurationSeconds(replayPath);
    const activityElapsedSeconds = Math.max(
      0,
      Math.min(activityDurationSeconds, replay.value.progress * activityDurationSeconds)
    );
    mapStateStore.patchReplayState({
      speedFactorLabel: formatReplaySpeedFactor(replayPath.originalDurationSeconds, next),
      activityElapsedSeconds,
      activityDurationSeconds,
      remainingLabel: formatDurationSmart(
        Math.max(0, activityDurationSeconds - activityElapsedSeconds) * 1000,
        activityDurationSeconds * 1000
      ),
      totalLabel: formatDurationSmart(activityDurationSeconds * 1000, activityDurationSeconds * 1000),
    });
  }
}

function setCameraPreset(preset: string) {
  mapStateStore.patchReplayState({
    cameraPreset: replayCameraPreset(preset).id,
    autoFollow: true,
  });
  rebuildCameraRail();
  applyReplayCamera(replay.value.progress, true);
}

function setCameraSmoothness(value: number) {
  mapStateStore.patchReplayState({
    cameraSmoothness: clampReplayCameraSmoothness(value),
    autoFollow: true,
  });
  rebuildCameraRail();
  applyReplayCamera(replay.value.progress, true);
}

function recenterCamera() {
  mapStateStore.patchReplayState({ autoFollow: true });
  applyReplayCamera(replay.value.progress, true);
}

function installInteractionHandlers() {
  removeInteractionHandlers();
  if (!map) return;
  const disableAutoFollow = (event?: { originalEvent?: Event }) => {
    if (!replay.value.active) return;
    if (applyingCamera) return;
    if (!event?.originalEvent && !userPointerActive) return;
    mapStateStore.patchReplayState({ autoFollow: false });
  };
  const markUserPointerActive = () => {
    userPointerActive = true;
  };
  const clearUserPointerActive = () => {
    window.setTimeout(() => {
      userPointerActive = false;
    }, 0);
  };
  const disableAutoFollowFromWheel = (event: WheelEvent) => disableAutoFollow({ originalEvent: event });
  const canvas = map.getCanvas();
  map.on('dragstart', disableAutoFollow);
  map.on('rotatestart', disableAutoFollow);
  map.on('pitchstart', disableAutoFollow);
  canvas.addEventListener('pointerdown', markUserPointerActive);
  canvas.addEventListener('wheel', disableAutoFollowFromWheel, { passive: true });
  window.addEventListener('pointerup', clearUserPointerActive);
  window.addEventListener('pointercancel', clearUserPointerActive);
  interactionHandlers = {
    canvas,
    clearUserPointerActive,
    disableAutoFollow,
    disableAutoFollowFromWheel,
    markUserPointerActive,
  };
}

function removeInteractionHandlers() {
  if (!interactionHandlers || !map) return;
  map.off('dragstart', interactionHandlers.disableAutoFollow);
  map.off('rotatestart', interactionHandlers.disableAutoFollow);
  map.off('pitchstart', interactionHandlers.disableAutoFollow);
  interactionHandlers.canvas.removeEventListener('pointerdown', interactionHandlers.markUserPointerActive);
  interactionHandlers.canvas.removeEventListener('wheel', interactionHandlers.disableAutoFollowFromWheel);
  window.removeEventListener('pointerup', interactionHandlers.clearUserPointerActive);
  window.removeEventListener('pointercancel', interactionHandlers.clearUserPointerActive);
  interactionHandlers = null;
}

function requestModeClose() {
  closed = true;
  cleanup3DResources();
  emit('mode-close-requested');
}

function failReplay(summary: string, detail: string) {
  if (closed) return;
  toast.add({ severity: 'error', summary, detail, life: 4000 });
  mapStateStore.patchReplayState({ loading: false, playing: false });
  emit('load-failed');
  emit('mode-close-requested');
}

function cleanup3DResources() {
  loadGeneration += 1;
  if (replayCameraViewportFrame !== null) {
    window.cancelAnimationFrame(replayCameraViewportFrame);
    replayCameraViewportFrame = null;
  }
  replayController?.destroy();
  replayController = null;
  replayViewportOcclusionObserver?.disconnect();
  replayViewportOcclusionObserver = null;
  attributionLinkCleanup?.();
  attributionLinkCleanup = null;
  removeInteractionHandlers();
  if (map?.getLayer(TRACK_REPLAY_LAYER_ID)) {
    map.removeLayer(TRACK_REPLAY_LAYER_ID);
  }
  replayLayer = null;
  replayPath = null;
  replayTelemetry.value = null;
  replayCameraRail = null;
  replayControlsLayout.value = null;
  lastReplayCameraViewportKey = '';
  if (map) {
    map.remove();
    map = null;
  }
  scaleControl = null;
}

function formatOptionalElevation(value: number | null | undefined): string {
  if (!Number.isFinite(value)) return '--';
  return formatElevation(Number(value));
}
</script>

<style scoped>
.map3d-renderer {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
  height: 100%;
  background: var(--map-container-bg);
}

.map3d-canvas {
  position: absolute;
  inset: 0;
}

.map3d-status {
  position: fixed;
  z-index: var(--z-map-overlay);
  top: calc(0.75rem + var(--safe-top, 0px));
  left: 50%;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.45rem 0.75rem;
  border: 1px solid var(--border-medium);
  border-radius: 999px;
  background: var(--surface-glass);
  color: var(--text-primary);
  box-shadow: var(--shadow-md);
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
  font-size: var(--text-sm-size);
  font-weight: 650;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
