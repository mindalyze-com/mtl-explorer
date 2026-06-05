import { markRaw } from 'vue';
import { formatDistanceSmart, formatDurationSmart } from '@/utils/Utils';
import {
  formatReplaySpeedFactor,
  sanitizeReplayTargetDuration,
  sampleReplayPath,
} from '@/components/replay/trackReplayPath';
import { TrackReplayLayer, TRACK_REPLAY_LAYER_ID } from '@/components/replay/TrackReplayLayer';
import { clampReplayCameraSmoothness, replayCameraPreset } from '@/components/replay/trackReplayCamera';
import { ReplayCameraRailPlanner } from '@/components/replay/replayCameraRailPlanner';
import {
  computeReplayViewportPadding,
  observeReplayViewportOcclusion,
} from '@/components/replay/replayViewportOcclusion';
import { ReplayCameraScreenGuard } from '@/components/replay/replayCameraScreenGuard';
import { useMapStateStore } from '@/stores/mapStateStore';
import type {
  MapControllerMethodDefinitions,
  TrackReplayInteractionEvent,
  TrackReplayMethods,
} from './mapControllerRuntime';
import type { MapCameraState } from '@/components/map/mapRendererTypes';

const TRACK_DETAILS_MAP_DETENT = 'compact';
const TRACK_REPLAY_TRACK_OPACITY = 0.18;
const REPLAY_CAMERA_MAP_MARGIN_PX = 32;
const REPLAY_CAMERA_MIN_VISIBLE_WIDTH_PX = 180;
const REPLAY_CAMERA_MIN_VISIBLE_HEIGHT_PX = 160;
const REPLAY_CAMERA_VIEWPORT_KEY_STEP_PX = 8;

export function useTrackReplay(_deps: Record<string, never> = {}): MapControllerMethodDefinitions<TrackReplayMethods> {
  const methods: MapControllerMethodDefinitions<TrackReplayMethods> = {
    async start3dTrackReplay(payload = {}) {
      const trackId = Number(payload.trackId ?? payload.gpsTrack?.id ?? this.trackDetailsId);
      if (!Number.isFinite(trackId)) return;
      const gpsTrack = payload.gpsTrack ?? null;
      const trackLabel = gpsTrack?.trackName || gpsTrack?.trackDescription || `Track ${trackId}`;
      this.trackReplayLoading = true;
      this.trackReplayTrackId = trackId;
      this.trackReplayTrackLabel = trackLabel;
      this.trackReplayDurationSeconds = sanitizeReplayTargetDuration(this.trackReplayDurationSeconds);
      this.trackReplayProgress = 0;
      this.trackReplayPlaying = false;
      this.trackReplayAutoFollow = true;
      this.trackReplayShowTelemetry = true;
      this.trackReplayCameraPreset = replayCameraPreset(this.trackReplayCameraPreset).id;
      this.trackReplayActive = true;
      if (!this.trackDetailsVisible) {
        this.trackDetailsSelectedDetent = TRACK_DETAILS_MAP_DETENT;
      }
      this.trackDetailsVisible = true;

      useMapStateStore().enter3DReplay({
        trackId,
        trackLabel,
        replaySource:
          Array.isArray(payload.chartPoints) && Array.isArray(payload.renderedShapePoints) && gpsTrack
            ? {
                trackId,
                coordinates: Array.isArray(payload.coordinates) ? payload.coordinates : [],
                gpsTrack,
                chartPoints: payload.chartPoints,
                renderedShapePoints: payload.renderedShapePoints,
              }
            : null,
        metadata: {
          id: trackId,
          name: trackLabel,
          description: gpsTrack?.trackDescription || '',
          activityType: gpsTrack?.activityType || '',
        },
      });
    },

    prepare3dTrackReplayMap(trackId, path) {
      this._trackReplayRestoreState = this.captureTrackReplayRestoreState();
      if (!this.terrainEnabled) {
        this.setTerrainModeEnabled(true, {
          animate: false,
        });
        if (!this.terrainEnabled) {
          this.$toast?.add({
            severity: 'warn',
            summary: 'Terrain unavailable',
            detail: 'Replay will use the track elevation without DEM terrain.',
            life: 3500,
          });
          this.overlayMap?.easeTo?.({
            pitch: 55,
            duration: 350,
          });
        }
      }
      this.dimTracksForReplay();
      if (this.gpsTrackIdToFeature.has(trackId)) {
        this.selectTrackById(trackId);
      }
      this.addTrackReplayLayer(path);
      this.installTrackReplayViewportOcclusionObserver();
      this.applyTrackReplayCamera(0, true);
    },

    captureTrackReplayRestoreState() {
      const overlayMap = this.overlayMap;
      if (!overlayMap) return null;
      const center = overlayMap.getCenter();
      const camera: MapCameraState = {
        center: [center.lng, center.lat],
        zoom: overlayMap.getZoom(),
        bearing: overlayMap.getBearing(),
        pitch: overlayMap.getPitch(),
      };
      const roll = overlayMap.getRoll?.();
      if (roll != null && Number.isFinite(roll)) camera.roll = roll;
      const elevation = overlayMap.getCenterElevation?.();
      if (elevation != null && Number.isFinite(elevation)) camera.elevation = elevation;
      return {
        camera,
        selectedTrackId: this.selectedTrackId,
        terrainEnabled: this.terrainEnabled,
      };
    },

    dimTracksForReplay() {
      const opacity = this.trackReplayShowContextTracks ? TRACK_REPLAY_TRACK_OPACITY : 0;
      this.applyTrackRenderFilters();
      if (!this.overlayMap) return;
      for (const [layerId, property] of [
        ['tracks-layer', 'line-opacity'],
        ['tracks-highlight-layer', 'line-opacity'],
        ['tracks-highlight-dash-layer', 'line-opacity'],
        ['tracks-dot-layer', 'circle-opacity'],
        ['tracks-overview-dots', 'circle-opacity'],
      ]) {
        if (this.overlayMap.getLayer(layerId)) {
          this.overlayMap.setPaintProperty(layerId, property, opacity);
        }
      }
      if (this.overlayMap.getLayer('tracks-dot-layer')) {
        this.overlayMap.setPaintProperty('tracks-dot-layer', 'circle-stroke-opacity', opacity);
      }
      if (this.overlayMap.getLayer('tracks-overview-dots')) {
        this.overlayMap.setPaintProperty('tracks-overview-dots', 'circle-stroke-opacity', opacity);
      }
    },

    addTrackReplayLayer(path) {
      if (!this.overlayMap) return;
      this.removeTrackReplayLayer();
      const layer = markRaw(new TrackReplayLayer());
      layer.setData(path);
      layer.setProgress(this.trackReplayProgress);
      this._trackReplayLayer = layer;
      this.overlayMap.addLayer(layer);
    },

    removeTrackReplayLayer() {
      if (this.overlayMap?.getLayer(TRACK_REPLAY_LAYER_ID)) {
        this.overlayMap.removeLayer(TRACK_REPLAY_LAYER_ID);
      }
      this._trackReplayLayer = null;
    },

    onTrackReplayFrame(frame) {
      const path = this._trackReplayPath;
      if (!path) return;
      this.trackReplayProgress = frame.progress;
      this.trackReplayPlaying = frame.status === 'playing';
      this.trackReplayDurationSeconds = frame.targetDurationSeconds;
      this.trackReplaySpeedFactorLabel = formatReplaySpeedFactor(
        path.originalDurationSeconds,
        this.trackReplayDurationSeconds
      );
      this.trackReplayElapsedLabel = formatDurationSmart(
        frame.elapsedReplaySeconds * 1000,
        this.trackReplayDurationSeconds * 1000
      );
      this.trackReplayTotalLabel = formatDurationSmart(
        this.trackReplayDurationSeconds * 1000,
        this.trackReplayDurationSeconds * 1000
      );
      const sample = sampleReplayPath(path, frame.progress);
      if (sample) {
        this.trackReplayDistanceLabel = `${formatDistanceSmart(sample.distanceMeters, path.totalDistanceMeters)} / ${formatDistanceSmart(path.totalDistanceMeters, path.totalDistanceMeters)}`;
      }
      this._trackReplayLayer?.setProgress(frame.progress);
      this.applyTrackReplayCamera(frame.progress, false, frame.elapsedReplaySeconds);
    },

    rebuildTrackReplayCameraRail() {
      const path = this._trackReplayPath;
      if (!path) {
        this._trackReplayCameraRail = null;
        return null;
      }
      const viewport = this.trackReplayCameraViewport();
      this._trackReplayCameraViewportKey = this.trackReplayCameraViewportKey(viewport);
      const rail = ReplayCameraRailPlanner.build({
        path,
        durationSeconds: this.trackReplayDurationSeconds,
        preset: this.trackReplayCameraPreset,
        smoothness: this.trackReplayCameraSmoothness,
        viewport,
      });
      this._trackReplayCameraRail = rail ? markRaw(rail) : null;
      return this._trackReplayCameraRail;
    },

    trackReplayCameraViewport() {
      const canvas = this.overlayMap?.getCanvas?.();
      if (!canvas) return undefined;
      const width = canvas.clientWidth || canvas.width;
      const height = canvas.clientHeight || canvas.height;
      if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) return undefined;
      return {
        width,
        height,
        ...this.trackReplayCameraViewportPadding(REPLAY_CAMERA_MAP_MARGIN_PX),
      };
    },

    trackReplayCameraViewportPadding(baseMarginPx = REPLAY_CAMERA_MAP_MARGIN_PX) {
      const canvas = this.overlayMap?.getCanvas?.();
      return computeReplayViewportPadding({
        canvas,
        baseMarginPx,
        minVisibleWidthPx: REPLAY_CAMERA_MIN_VISIBLE_WIDTH_PX,
        minVisibleHeightPx: REPLAY_CAMERA_MIN_VISIBLE_HEIGHT_PX,
        layouts: [this.trackReplayControlsLayout],
      });
    },

    trackReplayMapPadding(baseMarginPx = REPLAY_CAMERA_MAP_MARGIN_PX) {
      const padding = this.trackReplayCameraViewportPadding(baseMarginPx);
      return {
        top: padding.paddingTop,
        right: padding.paddingRight,
        bottom: padding.paddingBottom,
        left: padding.paddingLeft,
      };
    },

    trackReplayCameraViewportKey(viewport) {
      if (!viewport) return 'none';
      return [
        viewport.width,
        viewport.height,
        viewport.paddingTop ?? 0,
        viewport.paddingRight ?? 0,
        viewport.paddingBottom ?? 0,
        viewport.paddingLeft ?? 0,
      ]
        .map((value) => Math.round(Number(value) / REPLAY_CAMERA_VIEWPORT_KEY_STEP_PX))
        .join(':');
    },

    onTrackReplayControlsLayoutChange(layout) {
      this.trackReplayControlsLayout = layout ?? null;
      this.scheduleTrackReplayCameraViewportRebuild();
    },

    installTrackReplayViewportOcclusionObserver() {
      this._trackReplayViewportOcclusionObserver?.disconnect?.();
      this._trackReplayViewportOcclusionObserver = observeReplayViewportOcclusion(() => {
        this.scheduleTrackReplayCameraViewportRebuild();
      });
    },

    scheduleTrackReplayCameraViewportRebuild() {
      if (!this.overlayMap || !this._trackReplayPath) return;
      if (this._trackReplayCameraViewportFrame != null) return;
      this._trackReplayCameraViewportFrame = window.requestAnimationFrame(() => {
        this._trackReplayCameraViewportFrame = null;
        const viewport = this.trackReplayCameraViewport();
        const key = this.trackReplayCameraViewportKey(viewport);
        if (key === this._trackReplayCameraViewportKey) return;
        this.rebuildTrackReplayCameraRail();
        this.applyTrackReplayCamera(this.trackReplayProgress, true);
      });
    },

    applyTrackReplayCamera(progress, force = false, elapsedReplaySeconds = null) {
      if (!this.overlayMap || !this._trackReplayPath) return;
      if (!this.trackReplayAutoFollow && !force) return;
      const rail = this._trackReplayCameraRail ?? this.rebuildTrackReplayCameraRail();
      const elapsedSeconds =
        elapsedReplaySeconds != null && Number.isFinite(elapsedReplaySeconds)
          ? elapsedReplaySeconds
          : progress * this.trackReplayDurationSeconds;
      const frame = rail?.sample(elapsedSeconds);
      if (!frame) return;
      // Discrete actions (seek/preset/recenter/rebuild) snap; continuous playback eases.
      if (!this._trackReplayScreenGuard) this._trackReplayScreenGuard = new ReplayCameraScreenGuard();
      if (force) this._trackReplayScreenGuard.reset();
      this._trackReplayApplyingCamera = true;
      try {
        this._trackReplayScreenGuard.apply({
          map: this.overlayMap,
          path: this._trackReplayPath,
          progress,
          frame,
          padding: this.trackReplayMapPadding(REPLAY_CAMERA_MAP_MARGIN_PX),
          applyFrame: (view) => {
            this.jumpOverlayCameraAndSyncBase(view);
          },
        });
      } finally {
        this._trackReplayApplyingCamera = false;
      }
    },

    toggle3dTrackReplayPlayback() {
      this._trackReplayController?.toggle();
    },

    reset3dTrackReplayPlayback() {
      this.trackReplayAutoFollow = true;
      if (this._trackReplayController) {
        this._trackReplayController.stop();
        return;
      }
      this.trackReplayProgress = 0;
      this.trackReplayPlaying = false;
      this._trackReplayLayer?.setProgress(0);
      this.applyTrackReplayCamera(0, true, 0);
    },

    seek3dTrackReplay(progress) {
      this._trackReplayController?.seek(progress);
    },

    set3dTrackReplayShowContextTracks(value) {
      this.trackReplayShowContextTracks = value !== false;
      if (this.trackReplayActive) {
        this.dimTracksForReplay();
      }
    },

    set3dTrackReplayShowTelemetry(value) {
      this.trackReplayShowTelemetry = value !== false;
    },

    set3dTrackReplayDuration(seconds) {
      const next = sanitizeReplayTargetDuration(seconds);
      this.trackReplayDurationSeconds = next;
      this.rebuildTrackReplayCameraRail();
      this._trackReplayController?.setTargetDuration(next);
      if (this._trackReplayPath) {
        this.trackReplaySpeedFactorLabel = formatReplaySpeedFactor(this._trackReplayPath.originalDurationSeconds, next);
      }
    },

    set3dTrackReplayCameraPreset(preset) {
      this.trackReplayCameraPreset = replayCameraPreset(preset).id;
      this.trackReplayAutoFollow = true;
      this.rebuildTrackReplayCameraRail();
      this.applyTrackReplayCamera(this.trackReplayProgress, true);
    },

    set3dTrackReplayCameraSmoothness(value) {
      this.trackReplayCameraSmoothness = clampReplayCameraSmoothness(value);
      this.trackReplayAutoFollow = true;
      this.rebuildTrackReplayCameraRail();
      this.applyTrackReplayCamera(this.trackReplayProgress, true);
    },

    recenter3dTrackReplayCamera() {
      this.trackReplayAutoFollow = true;
      this.applyTrackReplayCamera(this.trackReplayProgress, true);
    },

    installTrackReplayInteractionHandlers() {
      this.removeTrackReplayInteractionHandlers();
      if (!this.overlayMap) return;
      const disableAutoFollow = (event?: TrackReplayInteractionEvent) => {
        if (!this.trackReplayActive) return;
        if (this._trackReplayApplyingCamera) return;
        if (!event?.originalEvent && !this._trackReplayUserPointerActive) return;
        this.trackReplayAutoFollow = false;
      };
      const markUserPointerActive = () => {
        this._trackReplayUserPointerActive = true;
      };
      const clearUserPointerActive = () => {
        window.setTimeout(() => {
          this._trackReplayUserPointerActive = false;
        }, 0);
      };
      const disableAutoFollowFromWheel = (event: WheelEvent) =>
        disableAutoFollow({
          originalEvent: event,
        });
      const canvas = this.overlayMap.getCanvas();
      this.overlayMap.on('dragstart', disableAutoFollow);
      this.overlayMap.on('rotatestart', disableAutoFollow);
      this.overlayMap.on('pitchstart', disableAutoFollow);
      canvas.addEventListener('pointerdown', markUserPointerActive);
      canvas.addEventListener('wheel', disableAutoFollowFromWheel, {
        passive: true,
      });
      window.addEventListener('pointerup', clearUserPointerActive);
      window.addEventListener('pointercancel', clearUserPointerActive);
      this._trackReplayInteractionHandlers = {
        canvas,
        clearUserPointerActive,
        disableAutoFollow,
        disableAutoFollowFromWheel,
        markUserPointerActive,
      };
    },

    removeTrackReplayInteractionHandlers() {
      const handlers = this._trackReplayInteractionHandlers;
      if (!handlers || !this.overlayMap) return;
      this.overlayMap.off('dragstart', handlers.disableAutoFollow);
      this.overlayMap.off('rotatestart', handlers.disableAutoFollow);
      this.overlayMap.off('pitchstart', handlers.disableAutoFollow);
      handlers.canvas?.removeEventListener('pointerdown', handlers.markUserPointerActive);
      handlers.canvas?.removeEventListener('wheel', handlers.disableAutoFollowFromWheel);
      window.removeEventListener('pointerup', handlers.clearUserPointerActive);
      window.removeEventListener('pointercancel', handlers.clearUserPointerActive);
      this._trackReplayInteractionHandlers = null;
    },

    stop3dTrackReplay({ restore = true } = {}) {
      if (this._trackReplayCameraViewportFrame != null) {
        window.cancelAnimationFrame(this._trackReplayCameraViewportFrame);
        this._trackReplayCameraViewportFrame = null;
      }
      this._trackReplayController?.destroy();
      this._trackReplayController = null;
      this._trackReplayViewportOcclusionObserver?.disconnect?.();
      this._trackReplayViewportOcclusionObserver = null;
      this.removeTrackReplayInteractionHandlers();
      this.removeTrackReplayLayer();
      const restoreState = this._trackReplayRestoreState;
      this._trackReplayPath = null;
      this._trackReplayCameraRail = null;
      this._trackReplayCameraViewportKey = '';
      this._trackReplayRestoreState = null;
      this._trackReplayApplyingCamera = false;
      this._trackReplayUserPointerActive = false;
      this.trackReplayControlsLayout = null;
      this.trackReplayActive = false;
      this.trackReplayLoading = false;
      this.trackReplayPlaying = false;
      this.trackReplayAutoFollow = true;
      this.trackReplayProgress = 0;
      this.trackReplayTrackId = null;
      this.trackReplayTrackLabel = '';
      this.trackDetailsSelectedDetent = undefined;
      this.applyTrackRenderFilters();
      if (restore && restoreState) {
        if (this.terrainEnabled !== restoreState.terrainEnabled) {
          this.setTerrainModeEnabled(restoreState.terrainEnabled, {
            animate: false,
          });
        }
        this.applyTracksVisibility();
        this.applyLayerOpacity('tracks');
        if (restoreState.selectedTrackId != null && this.selectedTrackId !== restoreState.selectedTrackId) {
          this.selectTrackById(restoreState.selectedTrackId);
        } else if (restoreState.selectedTrackId != null) {
          this.applySelectedTrackHighlight();
        } else {
          this.deselectTrack();
        }
        if (this.overlayMap && restoreState.camera) {
          this.jumpOverlayCameraAndSyncBase({
            ...restoreState.camera,
            padding: { top: 0, right: 0, bottom: 0, left: 0 },
          });
        }
      }
    },
  };
  return methods;
}
