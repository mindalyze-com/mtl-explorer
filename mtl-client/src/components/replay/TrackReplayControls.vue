<template>
  <BottomSheet
    v-model="sheetOpen"
    title="3D Replay"
    icon="bi bi-badge-3d"
    :detents="sheetDetents"
    initial-detent="open"
    no-backdrop
    :z-index="5450"
    sheet-class="track-replay-sheet"
    fit-content-initial
    @layout-change="onSheetLayoutChange"
  >
    <div class="trc">
      <section class="trc__primary">
        <div class="trc__transport">
          <button
            class="trc__play"
            type="button"
            :disabled="loading"
            :aria-label="playing ? 'Pause 3D replay' : 'Play 3D replay'"
            @click="emit('toggle-play')"
          >
            <i :class="playing ? 'bi bi-pause-fill' : 'bi bi-play-fill'"></i>
          </button>
          <button
            class="trc__icon-btn"
            type="button"
            :disabled="loading"
            aria-label="Stop 3D replay"
            title="Stop 3D replay"
            @click="emit('stop')"
          >
            <i class="bi bi-stop-fill"></i>
          </button>
        </div>

        <div class="trc__timeline">
          <div class="trc__timeline-head">
            <span>{{ elapsedLabel }}</span>
            <strong>{{ speedFactorLabel }}</strong>
            <span>{{ remainingLabelDisplay }}</span>
          </div>
          <MtlSlider v-model="progressSlider" :min="0" :max="1000" :step="1" aria-label="Replay progress" />
          <div class="trc__timeline-meta">
            <span>elapsed</span>
            <span>{{ distanceLabel }}</span>
            <span>remaining</span>
          </div>
        </div>

        <div class="trc__camera trc__camera--desktop">
          <div class="trc__group-head">
            <span>Camera View</span>
          </div>
          <ReplayCameraPresetSelector :model-value="cameraPreset" @select="emit('update-camera-preset', $event)" />
        </div>
      </section>

      <section class="trc__controls">
        <div class="trc__group trc__group--duration">
          <div class="trc__group-head">
            <span>Duration</span>
            <strong>{{ speedFactorLabel }}</strong>
          </div>
          <div class="trc__segments trc__segments--duration">
            <button
              v-for="duration in durationOptions"
              :key="duration"
              type="button"
              :class="['trc__seg replay-choice', { 'trc__seg--active': durationSeconds === duration }]"
              @click="emit('update-duration', duration)"
            >
              {{ duration }}s
            </button>
          </div>
        </div>

        <div class="trc__group trc__camera trc__camera--mobile">
          <div class="trc__group-head">
            <span>Camera Mode</span>
          </div>
          <ReplayCameraPresetSelector :model-value="cameraPreset" @select="emit('update-camera-preset', $event)" />
        </div>

        <div class="trc__group trc__group--adjust">
          <div class="trc__group-head">
            <span>Adjustments</span>
            <button
              class="trc__icon-btn trc__icon-btn--recenter"
              type="button"
              :disabled="autoFollow"
              aria-label="Recenter replay camera"
              title="Recenter replay camera"
              @click="emit('recenter')"
            >
              <i class="bi bi-crosshair"></i>
            </button>
          </div>

          <div class="trc__adjust-row">
            <i class="bi bi-wind"></i>
            <span>Smoothness</span>
            <MtlSlider
              v-model="cameraSmoothnessSlider"
              :min="REPLAY_CAMERA_SMOOTHNESS_MIN"
              :max="REPLAY_CAMERA_SMOOTHNESS_MAX"
              :step="REPLAY_CAMERA_SMOOTHNESS_STEP"
              class="trc__smooth-slider"
              aria-label="Camera smoothing"
            />
            <strong>{{ cameraSmoothnessLabel }}</strong>
          </div>

          <label class="trc__adjust-row trc__toggle">
            <i class="bi bi-speedometer2"></i>
            <span>Telemetry</span>
            <span class="trc__toggle-subtitle">Map data</span>
            <input
              class="trc__toggle-input"
              type="checkbox"
              aria-label="Show replay telemetry"
              :checked="showTelemetry"
              @change="onShowTelemetryChange"
            />
            <span class="trc__toggle-track" aria-hidden="true"></span>
          </label>

          <label class="trc__adjust-row trc__toggle">
            <i class="bi bi-layers"></i>
            <span>Context</span>
            <span class="trc__toggle-subtitle">Map surroundings</span>
            <input
              class="trc__toggle-input"
              type="checkbox"
              aria-label="Show context tracks"
              :checked="showContextTracks"
              @change="onShowContextTracksChange"
            />
            <span class="trc__toggle-track" aria-hidden="true"></span>
          </label>
        </div>
      </section>
    </div>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import type { BottomSheetLayoutState } from '@/components/ui/BottomSheet.vue';
import MtlSlider from '@/components/ui/MtlSlider.vue';
import ReplayCameraPresetSelector from '@/components/replay/ReplayCameraPresetSelector.vue';
import { REPLAY_TARGET_DURATION_PRESETS_SECONDS, type ReplayCameraPresetId } from '@/components/replay/trackReplayPath';
import {
  REPLAY_CAMERA_SMOOTHNESS_MAX,
  REPLAY_CAMERA_SMOOTHNESS_MIN,
  REPLAY_CAMERA_SMOOTHNESS_STEP,
  clampReplayCameraSmoothness,
  replayCameraSmoothnessLabel,
} from '@/components/replay/trackReplayCamera';
import { formatDurationSmart } from '@/utils/Utils';

defineOptions({ name: 'TrackReplayControls' });

const props = defineProps<{
  active: boolean;
  autoFollow: boolean;
  cameraPreset: ReplayCameraPresetId;
  cameraSmoothness: number;
  distanceLabel: string;
  durationSeconds: number;
  elapsedLabel: string;
  loading?: boolean;
  playing: boolean;
  progress: number;
  remainingLabel?: string;
  showContextTracks: boolean;
  showTelemetry: boolean;
  speedFactorLabel: string;
  totalLabel: string;
}>();

const emit = defineEmits<{
  'toggle-play': [];
  stop: [];
  close: [];
  seek: [progress: number];
  'update-show-context-tracks': [value: boolean];
  'update-show-telemetry': [value: boolean];
  'update-duration': [seconds: number];
  'update-camera-preset': [preset: ReplayCameraPresetId];
  'update-camera-smoothness': [smoothness: number];
  'sheet-layout-change': [layout: BottomSheetLayoutState];
  recenter: [];
}>();

const isMobileViewport = ref(false);
const desktopSheetDetents = [
  { id: 'collapsed', height: '128px' },
  { id: 'open', height: '318px' },
  { id: 'max', height: '430px' },
];
const mobileSheetDetents = [
  { id: 'collapsed', height: '150px' },
  { id: 'open', height: '56dvh' },
  { id: 'max', height: '86dvh' },
];
const sheetDetents = computed(() => (isMobileViewport.value ? mobileSheetDetents : desktopSheetDetents));
const durationOptions = REPLAY_TARGET_DURATION_PRESETS_SECONDS;

onMounted(() => {
  syncViewportMode();
  window.addEventListener('resize', syncViewportMode);
});

onUnmounted(() => {
  window.removeEventListener('resize', syncViewportMode);
});

const remainingLabelDisplay = computed(() => {
  if (props.remainingLabel) return props.remainingLabel;
  const durationMs = Math.max(0, props.durationSeconds * 1000);
  const remainingMs = Math.max(0, durationMs * (1 - Math.max(0, Math.min(1, props.progress))));
  return formatDurationSmart(remainingMs, durationMs);
});

const sheetOpen = computed({
  get: () => props.active,
  set: (value: boolean) => {
    if (!value) emit('close');
  },
});

const progressSlider = computed({
  get: () => Math.round(Math.max(0, Math.min(1, props.progress)) * 1000),
  set: (value: number | number[]) => {
    const raw = Array.isArray(value) ? value[0] : value;
    emit('seek', Math.max(0, Math.min(1, Number(raw) / 1000)));
  },
});

const cameraSmoothnessSlider = computed({
  get: () => clampReplayCameraSmoothness(props.cameraSmoothness),
  set: (value: number | number[]) => {
    const raw = Array.isArray(value) ? value[0] : value;
    emit('update-camera-smoothness', clampReplayCameraSmoothness(Number(raw)));
  },
});
const cameraSmoothnessLabel = computed(() => replayCameraSmoothnessLabel(cameraSmoothnessSlider.value));

function onShowContextTracksChange(event: Event) {
  emit('update-show-context-tracks', (event.target as HTMLInputElement | null)?.checked === true);
}

function onShowTelemetryChange(event: Event) {
  emit('update-show-telemetry', (event.target as HTMLInputElement | null)?.checked === true);
}

function onSheetLayoutChange(layout: BottomSheetLayoutState) {
  emit('sheet-layout-change', layout);
}

function syncViewportMode() {
  isMobileViewport.value = window.innerWidth <= 768;
}
</script>

<style scoped>
.trc {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  min-height: 0;
  overflow: hidden;
  padding: 0.1rem 1rem 0.65rem;
  color: var(--text-secondary);
}

.trc__primary {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) minmax(16rem, 0.56fr);
  align-items: end;
  gap: 1.35rem;
  min-width: 0;
}

.trc__transport {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  min-width: 0;
}

.trc__play,
.trc__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border: 1px solid var(--border-medium);
  background: rgba(255, 255, 255, 0.82);
  color: var(--text-primary);
  cursor: pointer;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.08);
  transition:
    transform 0.16s ease,
    background 0.16s ease,
    border-color 0.16s ease;
}

.trc__play {
  width: 3.3rem;
  height: 3.3rem;
  border-radius: 50%;
  border: none;
  background: var(--replay-accent-gradient);
  color: var(--accent-contrast);
  font-size: var(--text-2xl-size);
  box-shadow: 0 14px 32px rgba(85, 72, 221, 0.28);
}

.trc__icon-btn {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 50%;
  font-size: var(--text-base-size);
}

.trc__play:not(:disabled):hover,
.trc__icon-btn:not(:disabled):hover {
  transform: translateY(-1px);
  background: var(--surface-active);
}

.trc__play:disabled,
.trc__icon-btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.trc__timeline {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 0.28rem;
  min-width: 0;
}

.trc__timeline-head,
.trc__timeline-meta {
  display: flex;
  justify-content: space-between;
  gap: 0.7rem;
  min-width: 0;
}

.trc__timeline-head {
  align-items: center;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  font-weight: 700;
  line-height: var(--text-sm-lh);
}

.trc__timeline-head strong {
  color: var(--replay-accent);
  font-size: var(--text-xs-size);
  font-weight: 780;
}

.trc__timeline-head span,
.trc__timeline-meta span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trc__timeline-head span:last-child,
.trc__timeline-meta span:last-child {
  text-align: right;
}

.trc__timeline-meta {
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
  font-weight: 650;
  line-height: var(--text-2xs-lh);
  text-transform: lowercase;
}

.trc__timeline-meta span:nth-child(2) {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: 720;
  line-height: var(--text-xs-lh);
  text-align: center;
  text-transform: none;
}

.trc__controls {
  display: grid;
  grid-template-columns: minmax(18rem, 1fr) minmax(20rem, 1.35fr);
  gap: 0.9rem 1.1rem;
  min-height: 0;
  min-width: 0;
  padding-top: 0.65rem;
  border-top: 1px solid var(--border-subtle);
}

.trc__group {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  min-width: 0;
}

.trc__group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.7rem;
  min-width: 0;
}

.trc__group-head span {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 780;
  letter-spacing: 0.08em;
  line-height: var(--text-2xs-lh);
  text-transform: uppercase;
}

.trc__group-head strong {
  color: var(--replay-accent);
  font-size: var(--text-xs-size);
  font-weight: 780;
  line-height: var(--text-xs-lh);
}

.trc__segments {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  gap: 0;
  min-width: 0;
}

.trc__segments--duration {
  align-items: center;
  gap: 0.45rem;
  overflow-x: auto;
  padding-bottom: 0.05rem;
}

.trc__seg {
  border: 1px solid var(--border-medium);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
}

.trc__smooth-slider {
  flex: 1 1 auto;
  min-width: 8rem;
}

.trc__seg--active {
  border-color: var(--replay-accent-bright);
  background: color-mix(in srgb, var(--replay-accent-bright) 12%, transparent);
  color: var(--replay-accent-text);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--replay-accent-bright) 18%, transparent);
}

.trc__group--adjust {
  gap: 0.45rem;
}

.trc__adjust-row {
  display: grid;
  grid-template-columns: 1.8rem minmax(5.8rem, 0.45fr) minmax(8rem, 1fr) 3rem;
  align-items: center;
  gap: 0.6rem;
  min-height: 2.35rem;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.trc__adjust-row > i {
  color: var(--text-muted);
  font-size: 1.1rem;
}

.trc__adjust-row > strong {
  color: var(--replay-accent);
  font-size: var(--text-sm-size);
  font-weight: 760;
  text-align: right;
}

.trc__toggle {
  grid-template-columns: 1.8rem minmax(5.8rem, 0.45fr) minmax(8rem, 1fr) 3rem;
  cursor: pointer;
}

.trc__toggle-input {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
}

.trc__toggle-subtitle {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.trc__toggle-track {
  position: relative;
  width: 2.8rem;
  height: 1.6rem;
  border-radius: 999px;
  background: var(--border-medium);
  transition: background 0.16s ease;
}

.trc__toggle-track::after {
  content: '';
  position: absolute;
  top: 0.18rem;
  left: 0.18rem;
  width: 1.24rem;
  height: 1.24rem;
  border-radius: 50%;
  background: var(--accent-contrast);
  box-shadow: 0 3px 10px rgba(15, 23, 42, 0.18);
  transition: transform 0.16s ease;
}

.trc__toggle-input:checked + .trc__toggle-track {
  background: var(--replay-accent);
}

.trc__toggle-input:checked + .trc__toggle-track::after {
  transform: translateX(1.18rem);
}

.trc__toggle-input:focus-visible + .trc__toggle-track {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.trc__icon-btn--recenter {
  width: 2.1rem;
  height: 2.1rem;
  border-radius: 0.65rem;
  font-size: var(--text-sm-size);
}

.trc__camera--mobile {
  display: none;
}

@media (max-width: 768px) {
  .trc {
    gap: 0.95rem;
    overflow-y: auto;
    padding: 0.15rem 1rem 0.85rem;
  }

  .trc__primary {
    grid-template-columns: auto minmax(0, 1fr);
    align-items: center;
    gap: 0.9rem;
  }

  .trc__transport {
    flex-direction: column;
    gap: 0.45rem;
  }

  .trc__play {
    width: 3.15rem;
    height: 3.15rem;
  }

  .trc__icon-btn {
    width: 2.35rem;
    height: 2.35rem;
  }

  .trc__timeline-head {
    font-size: var(--text-xs-size);
    line-height: var(--text-xs-lh);
  }

  .trc__timeline-meta {
    display: grid;
    grid-template-columns: 1fr 1.3fr 1fr;
  }

  .trc__camera--desktop {
    display: none;
  }

  .trc__camera--mobile {
    display: flex;
  }

  .trc__controls {
    grid-template-columns: 1fr;
    gap: 0.95rem;
    padding-top: 0.2rem;
    border-top: 0;
  }

  .trc__segments--duration {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0.55rem;
    overflow: visible;
  }

  .trc__seg {
    min-height: 2.65rem;
  }

  .trc__adjust-row,
  .trc__toggle {
    grid-template-columns: 1.7rem minmax(5.2rem, 0.5fr) minmax(5rem, 1fr) 3rem;
  }

  .trc__toggle-subtitle {
    white-space: normal;
  }
}

@media (min-width: 769px) and (max-width: 1080px) {
  .trc__primary {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .trc__camera--desktop {
    display: none;
  }

  .trc__camera--mobile {
    display: flex;
  }

  .trc__controls {
    grid-template-columns: minmax(18rem, 0.9fr) minmax(22rem, 1.1fr);
  }
}
</style>
