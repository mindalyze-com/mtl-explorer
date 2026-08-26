<template>
  <div>
    <BottomSheet
      v-model="active"
      :detents="sheetDetents"
      initial-detent="open"
      :selected-detent="selectedSheetDetent"
      no-backdrop
      :sheet-class="sheetClass"
      @detent-change="onSheetDetentChange"
      @closed="onSheetClosed"
    >
      <template #title>
        <div class="am-header">
          <i class="bi bi-play-circle am-header__icon" aria-hidden="true"></i>
          <h2 class="am-header__title compact-heading">Animate</h2>
        </div>
      </template>

      <div v-if="active" class="am-root" :class="{ 'am-root--compact': isPlaybackCompact }">
        <div v-if="!hasFeatures" class="am-empty" role="status">
          <span class="am-empty__icon"><i class="bi bi-signpost-split" aria-hidden="true"></i></span>
          <div>
            <h3>No tracks to animate</h3>
            <p>Change the filter or import tracks, then open Animate again.</p>
          </div>
        </div>

        <section
          v-else-if="isPlaybackCompact"
          class="am-compact"
          :class="`am-compact--${playbackPhase}`"
          aria-labelledby="am-compact-title"
          aria-live="polite"
        >
          <div class="am-compact__copy">
            <span class="am-eyebrow settings-eyebrow">{{ playbackStatusLabel }}</span>
            <div class="am-compact__summary">
              <h3 id="am-compact-title">{{ playbackHeadline }}</h3>
              <span>{{ playedTrackCount }} / {{ rangeTrackCount }}</span>
            </div>
          </div>

          <div class="am-compact__controls">
            <button
              type="button"
              class="am-compact__expand"
              aria-label="Open playback settings"
              @click="openPlaybackSettings"
            >
              <i class="bi bi-chevron-up" aria-hidden="true"></i>
            </button>
            <button
              v-if="playbackPhase === 'playing' || playbackPhase === 'paused'"
              type="button"
              class="am-stop-button"
              aria-label="Stop animation and open playback settings"
              @click="onStopAnimation"
            >
              <i class="bi bi-stop-fill" aria-hidden="true"></i>
              <span>Stop</span>
            </button>
            <button
              ref="compactPlayButton"
              type="button"
              class="am-play-button am-play-button--compact"
              :aria-label="playButtonAriaLabel"
              @click="onPlayPauseToggle"
            >
              <i :class="animationInProgress ? 'bi bi-pause-fill' : 'bi bi-play-fill'" aria-hidden="true"></i>
              <span>{{ playButtonLabel }}</span>
            </button>
          </div>

          <div class="am-compact__progress" aria-hidden="true">
            <span :style="{ width: playbackProgressPercent + '%' }"></span>
          </div>
        </section>

        <template v-else>
          <section
            class="am-playback status-rail"
            :class="`am-playback--${playbackPhase}`"
            aria-labelledby="am-playback-title"
            aria-live="polite"
          >
            <div class="am-playback__copy">
              <span class="am-eyebrow settings-eyebrow">{{ playbackStatusLabel }}</span>
              <h3 id="am-playback-title" class="am-playback__headline">{{ playbackHeadline }}</h3>
              <p class="am-playback__detail">{{ playbackDetail }}</p>
            </div>

            <div class="am-playback__controls">
              <button
                type="button"
                class="am-reset-button"
                :disabled="!playbackResetAvailable"
                aria-label="Reset animation to the beginning"
                @click="onResetAnimation"
              >
                <i class="bi bi-arrow-counterclockwise" aria-hidden="true"></i>
                <span>Reset</span>
              </button>
              <button type="button" class="am-play-button" :aria-label="playButtonAriaLabel" @click="onPlayPauseToggle">
                <i :class="animationInProgress ? 'bi bi-pause-fill' : 'bi bi-play-fill'" aria-hidden="true"></i>
                <span>{{ playButtonLabel }}</span>
              </button>
            </div>
          </section>

          <section class="am-settings" aria-labelledby="am-settings-title">
            <div class="am-settings__heading settings-section-heading">
              <div>
                <h3 id="am-settings-title">Playback settings</h3>
                <p>Choose which tracks play and how quickly they appear.</p>
              </div>
            </div>

            <div class="am-settings__grid">
              <section class="am-setting am-setting--range" aria-labelledby="am-range-title">
                <div class="am-setting__head">
                  <span class="am-setting__icon"><i class="bi bi-calendar-range" aria-hidden="true"></i></span>
                  <div class="am-setting__copy">
                    <h4 id="am-range-title">Date range</h4>
                    <p>{{ rangeSummary }}</p>
                  </div>
                  <span class="am-setting__value">{{ rangeTrackCount }} {{ trackCountLabel }}</span>
                </div>

                <div class="am-slider-wrap">
                  <MtlSlider
                    v-model="rangeValue"
                    :range="true"
                    :min="0"
                    :max="Math.max(totalCount - 1, 0)"
                    class="am-slider"
                    aria-label="Select animation date range"
                    :aria-value-text="[rangeDateStart, rangeDateEnd]"
                    @change="onRangeChange"
                  />
                  <div
                    v-if="showPlayhead"
                    class="am-playhead"
                    :style="{ left: playheadPercent + '%' }"
                    aria-hidden="true"
                  ></div>
                </div>
                <div class="am-range-labels" aria-hidden="true">
                  <span>{{ rangeDateStart }}</span>
                  <span>{{ rangeDateEnd }}</span>
                </div>
              </section>

              <section class="am-setting am-setting--speed" aria-labelledby="am-speed-title">
                <div class="am-setting__head">
                  <span class="am-setting__icon"><i class="bi bi-speedometer2" aria-hidden="true"></i></span>
                  <div class="am-setting__copy">
                    <h4 id="am-speed-title">Playback speed</h4>
                    <p>{{ estimatedDurationLabel }}</p>
                  </div>
                  <span class="am-setting__value">{{ speedLabel }}</span>
                </div>

                <MtlSlider
                  v-model="speedSliderPos"
                  :min="ANIMATION_SPEED_SLIDER_MIN"
                  :max="ANIMATION_SPEED_SLIDER_MAX"
                  :step="1"
                  class="am-slider"
                  aria-label="Adjust animation speed"
                  :aria-value-text="`${speedLabel}, ${animationSpeed} milliseconds per track`"
                />
                <div class="am-speed-labels" aria-hidden="true">
                  <span><i class="bi bi-hourglass" aria-hidden="true"></i> Slow</span>
                  <span class="am-speed-ms">{{ animationSpeed }} ms per track</span>
                  <span>Fast <i class="bi bi-lightning-charge-fill" aria-hidden="true"></i></span>
                </div>
              </section>
            </div>
          </section>
        </template>
      </div>
    </BottomSheet>
  </div>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, markRaw, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type * as maplibregl from 'maplibre-gl';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import MtlSlider from '@/components/ui/MtlSlider.vue';
import { formatDate } from '@/utils/Utils';
import { TRACK_COLOR } from '@/utils/trackColors';

const MOBILE_VIEWPORT_BREAKPOINT_PX = 769;
const ANIMATE_PLAYBACK_HEIGHT = 'clamp(9.75rem, 20vh, 10.5rem)';
const ANIMATE_DESKTOP_OPEN_HEIGHT = 'min(60vh, 27rem)';
const ANIMATE_MOBILE_OPEN_HEIGHT = 'min(74vh, 34rem)';
const ANIMATE_MAX_HEIGHT = '92vh';
const RANGE_LOOKBACK_TRACK_COUNT = 50;
const ANIMATION_SPEED_MIN_MS = 1;
const ANIMATION_SPEED_MAX_MS = 1000;
const ANIMATION_SPEED_SLIDER_MIN = 0;
const ANIMATION_SPEED_SLIDER_MAX = 100;
const MILLISECONDS_PER_SECOND = 1000;
const SECONDS_PER_MINUTE = 60;
const ANIMATION_SPEED_LABELS = [
  { minimumSliderPosition: 85, label: 'Very fast' },
  { minimumSliderPosition: 65, label: 'Fast' },
  { minimumSliderPosition: 40, label: 'Balanced' },
  { minimumSliderPosition: 20, label: 'Slow' },
  { minimumSliderPosition: ANIMATION_SPEED_SLIDER_MIN, label: 'Very slow' },
] as const;
const ANIMATION_LAYER_ID = 'animation-layer';
const ANIMATION_SOURCE_ID = 'animation-source';
const HIDDEN_TRACK_LAYER_OPACITY = 0;
const HIDDEN_TRACK_LAYER_VISIBILITY = 'none';
const TRACK_LAYER_PAINT_PROPERTIES = [
  { layerId: 'tracks-layer', property: 'line-opacity', fallbackValue: 1 },
  { layerId: 'tracks-highlight-layer', property: 'line-opacity', fallbackValue: 1 },
  { layerId: 'tracks-highlight-dash-layer', property: 'line-opacity', fallbackValue: 1 },
  { layerId: 'tracks-dot-layer', property: 'circle-opacity', fallbackValue: 1 },
  { layerId: 'tracks-dot-layer', property: 'circle-stroke-opacity', fallbackValue: 1 },
  { layerId: 'tracks-overview-dots', property: 'circle-opacity', fallbackValue: 0.85 },
  { layerId: 'tracks-overview-dots', property: 'circle-stroke-opacity', fallbackValue: 1 },
  { layerId: 'tracks-highlight-circle-layer', property: 'circle-opacity', fallbackValue: 1 },
  { layerId: 'track-points-layer', property: 'icon-opacity', fallbackValue: 0.9 },
] as const;
const TRACK_LAYER_VISIBILITY_IDS = [
  'tracks-layer',
  'tracks-highlight-layer',
  'tracks-highlight-dash-layer',
  'tracks-dot-layer',
  'tracks-overview-dots',
  'tracks-highlight-circle-layer',
  'track-points-layer',
] as const;

const EVENTS = {
  animate: 'animate',
  animationFinished: 'animationFinished',
  animationStart: 'animationStart',
  animationStop: 'animationStop',
} as const;

type MapLike = {
  getLayer: maplibregl.Map['getLayer'];
  getLayoutProperty?: maplibregl.Map['getLayoutProperty'];
  getPaintProperty?: maplibregl.Map['getPaintProperty'];
  setLayoutProperty?: maplibregl.Map['setLayoutProperty'];
  setPaintProperty: maplibregl.Map['setPaintProperty'];
  removeLayer: maplibregl.Map['removeLayer'];
  getSource: maplibregl.Map['getSource'];
  removeSource: maplibregl.Map['removeSource'];
  addSource: maplibregl.Map['addSource'];
  addLayer: maplibregl.Map['addLayer'];
};

type TrackFeature = {
  properties?: { startDate?: Date | number | string; [key: string]: unknown };
};

type PlaybackPhase = 'ready' | 'playing' | 'paused' | 'finished';
type SheetDetentId = 'playback' | 'open' | 'max';
type TrackLayerOpacity = number | maplibregl.ExpressionSpecification;
type TrackLayerVisibility = maplibregl.VisibilitySpecification;

type Emits = {
  (event: 'animate', payload: { animateIndexCurrent: number; animateIndexMax: number; currentDate: Date }): void;
  (event: 'animationFinished', message: string): void;
  (event: 'animationStart', message: string): void;
  (event: 'animationStop', message: string): void;
  (event: 'tool-opened'): void;
  (event: 'tool-closed'): void;
};

defineOptions({ name: 'AnimateMap' });

const props = defineProps<{
  map?: MapLike | null;
  geojson?: { features?: TrackFeature[] } | null;
}>();

const emit = defineEmits<Emits>();
const instance = getCurrentInstance();

const active = ref(false);
const animationInProgress = ref(false);
const animationSpeed = ref(20);
const sortedFeatures = ref<TrackFeature[]>([]);
const animationIndex = ref(0);
const rangeValue = ref<[number, number]>([0, 0]);
const playbackPhase = ref<PlaybackPhase>('ready');
const selectedSheetDetent = ref<SheetDetentId>('open');
const compactPlayButton = ref<HTMLButtonElement | null>(null);
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : MOBILE_VIEWPORT_BREAKPOINT_PX);

const color1 = [255, 0, 0] as const;
const color2 = [0, 0, 255] as const;
let timerId: ReturnType<typeof setInterval> | null = null;
let trackLayerPaintBeforeAnimation = new Map<string, TrackLayerOpacity>();
let trackLayerVisibilityBeforeAnimation = new Map<string, TrackLayerVisibility>();

const hasFeatures = computed(() => sortedFeatures.value.length > 0);
const sheetDetents = computed(() => [
  { id: 'playback', height: ANIMATE_PLAYBACK_HEIGHT },
  {
    id: 'open',
    height:
      viewportWidth.value < MOBILE_VIEWPORT_BREAKPOINT_PX ? ANIMATE_MOBILE_OPEN_HEIGHT : ANIMATE_DESKTOP_OPEN_HEIGHT,
  },
  { id: 'max', height: ANIMATE_MAX_HEIGHT },
]);
const isPlaybackCompact = computed(() => selectedSheetDetent.value === 'playback');
const sheetClass = computed(() =>
  isPlaybackCompact.value
    ? ['sheet--solid-over-map', 'sheet--animate', 'sheet--animate-playback']
    : ['sheet--solid-over-map', 'sheet--animate']
);
const totalCount = computed(() => sortedFeatures.value.length);
const rangeTrackCount = computed(() => {
  if (!sortedFeatures.value.length) return 0;
  return rangeValue.value[1] - rangeValue.value[0] + 1;
});
const trackCountLabel = computed(() => (rangeTrackCount.value === 1 ? 'track' : 'tracks'));
const playedTrackCount = computed(() => {
  if (!hasFeatures.value || playbackPhase.value === 'ready') return 0;
  return Math.min(rangeTrackCount.value, Math.max(1, animationIndex.value - rangeValue.value[0] + 1));
});
const playbackProgressPercent = computed(() => {
  if (!rangeTrackCount.value) return 0;
  return Math.min(100, Math.max(0, (playedTrackCount.value / rangeTrackCount.value) * 100));
});
const showPlayhead = computed(() => hasFeatures.value && playbackPhase.value !== 'ready');
const playheadPercent = computed(() => {
  if (sortedFeatures.value.length <= 1) return 0;
  const max = sortedFeatures.value.length - 1;
  return Math.min(100, Math.max(0, (animationIndex.value / max) * 100));
});
const currentDateLabel = computed(() => {
  if (!sortedFeatures.value.length) return '—';
  const idx =
    animationInProgress.value || animationIndex.value > rangeValue.value[0]
      ? animationIndex.value
      : rangeValue.value[0];
  const f = sortedFeatures.value[idx];
  const d = parseFeatureStartDate(f);
  return d ? formatDate(d) : '—';
});
const rangeDateStart = computed(() => {
  if (!sortedFeatures.value.length) return '—';
  const f = sortedFeatures.value[rangeValue.value[0]];
  const d = parseFeatureStartDate(f);
  return d ? formatDate(d) : '—';
});
const rangeDateEnd = computed(() => {
  if (!sortedFeatures.value.length) return '—';
  const f = sortedFeatures.value[rangeValue.value[1]];
  const d = parseFeatureStartDate(f);
  return d ? formatDate(d) : '—';
});
const rangeSummary = computed(() =>
  rangeDateStart.value === rangeDateEnd.value ? rangeDateStart.value : `${rangeDateStart.value} – ${rangeDateEnd.value}`
);
// Logarithmic speed slider: left is slow (long delay), right is fast (short delay).
const speedSliderPos = computed({
  get() {
    const normalizedDelay =
      (Math.log(animationSpeed.value) - Math.log(ANIMATION_SPEED_MIN_MS)) /
      (Math.log(ANIMATION_SPEED_MAX_MS) - Math.log(ANIMATION_SPEED_MIN_MS));
    const sliderSpan = ANIMATION_SPEED_SLIDER_MAX - ANIMATION_SPEED_SLIDER_MIN;
    const pos = Math.round(ANIMATION_SPEED_SLIDER_MIN + (1 - normalizedDelay) * sliderSpan);
    return Math.min(ANIMATION_SPEED_SLIDER_MAX, Math.max(ANIMATION_SPEED_SLIDER_MIN, pos));
  },
  set(pos: number) {
    const sliderSpan = ANIMATION_SPEED_SLIDER_MAX - ANIMATION_SPEED_SLIDER_MIN;
    const clampedPos = Math.min(ANIMATION_SPEED_SLIDER_MAX, Math.max(ANIMATION_SPEED_SLIDER_MIN, pos));
    const normalizedSlider = (clampedPos - ANIMATION_SPEED_SLIDER_MIN) / sliderSpan;
    const normalizedDelay = 1 - normalizedSlider;
    const ms = Math.round(
      Math.exp(
        Math.log(ANIMATION_SPEED_MIN_MS) +
          normalizedDelay * (Math.log(ANIMATION_SPEED_MAX_MS) - Math.log(ANIMATION_SPEED_MIN_MS))
      )
    );
    animationSpeed.value = Math.min(ANIMATION_SPEED_MAX_MS, Math.max(ANIMATION_SPEED_MIN_MS, ms));
  },
});
const speedLabel = computed(() => {
  const position = speedSliderPos.value;
  return (
    ANIMATION_SPEED_LABELS.find(({ minimumSliderPosition }) => position >= minimumSliderPosition)?.label ??
    ANIMATION_SPEED_LABELS[ANIMATION_SPEED_LABELS.length - 1].label
  );
});
const estimatedDurationLabel = computed(() => {
  const durationMs = rangeTrackCount.value * animationSpeed.value;
  if (durationMs < MILLISECONDS_PER_SECOND) return 'Plays in under 1 second';
  const totalSeconds = Math.ceil(durationMs / MILLISECONDS_PER_SECOND);
  if (totalSeconds < SECONDS_PER_MINUTE) return `Plays in about ${totalSeconds} seconds`;
  const minutes = Math.floor(totalSeconds / SECONDS_PER_MINUTE);
  const seconds = totalSeconds % SECONDS_PER_MINUTE;
  return seconds === 0 ? `Plays in about ${minutes} min` : `Plays in about ${minutes} min ${seconds} sec`;
});
const playbackStatusLabel = computed(() => {
  switch (playbackPhase.value) {
    case 'playing':
      return 'Playing';
    case 'paused':
      return 'Paused';
    case 'finished':
      return 'Finished';
    default:
      return 'Playback ready';
  }
});
const playbackHeadline = computed(() => {
  if (playbackPhase.value === 'finished') return 'Replay complete';
  if (playbackPhase.value === 'playing' || playbackPhase.value === 'paused') return currentDateLabel.value;
  return `${rangeTrackCount.value} ${trackCountLabel.value} ready`;
});
const playbackDetail = computed(() => {
  if (playbackPhase.value === 'playing') {
    return `${playedTrackCount.value} of ${rangeTrackCount.value} tracks replayed`;
  }
  if (playbackPhase.value === 'paused') {
    return `${playedTrackCount.value} of ${rangeTrackCount.value} tracks replayed · Resume when ready`;
  }
  if (playbackPhase.value === 'finished') {
    return `${rangeTrackCount.value} tracks replayed through ${rangeDateEnd.value}`;
  }
  return rangeSummary.value;
});
const playbackResetAvailable = computed(
  () => animationInProgress.value || playbackPhase.value === 'paused' || playbackPhase.value === 'finished'
);
const playButtonLabel = computed(() => {
  if (animationInProgress.value) return 'Pause';
  if (playbackPhase.value === 'paused') return 'Resume';
  if (playbackPhase.value === 'finished') return 'Replay';
  return 'Play';
});
const playButtonAriaLabel = computed(() => `${playButtonLabel.value} animation`);

watch(
  () => props.geojson,
  () => {
    if (!active.value) return;
    const shouldEmitStop = animationInProgress.value || timerId != null || hasAnimationLayer();
    stopAnimationPlayback({ renderStoppedRange: false, emitStop: shouldEmitStop });
    restoreTrackLayers();
    selectedSheetDetent.value = 'open';
    prepareSortedFeatures();
  }
);
watch(animationSpeed, () => {
  // Restart the interval with new speed while animation is running
  if (timerId) {
    clearInterval(timerId);
    timerId = setInterval(animationFunction, animationSpeed.value);
  }
});

onMounted(() => {
  if (typeof window !== 'undefined') window.addEventListener('resize', onViewportResize);
});

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') window.removeEventListener('resize', onViewportResize);
  if (hasAnimationStateToCleanUp()) closeAnimationSession();
});

function onViewportResize() {
  viewportWidth.value = window.innerWidth;
}

async function toggle() {
  active.value = !active.value;
  if (active.value) {
    selectedSheetDetent.value = 'open';
    emit('tool-opened');
    prepareSortedFeatures();
  } else {
    onClose();
  }
}

function open() {
  if (active.value) return;
  active.value = true;
  selectedSheetDetent.value = 'open';
  emit('tool-opened');
  prepareSortedFeatures();
}

function close() {
  if (!hasAnimationStateToCleanUp()) {
    active.value = false;
    return;
  }
  closeAnimationSession();
}

function onSheetClosed() {
  closeAnimationSession({ emitToolClosed: true });
}

function closeAnimationSession({ emitToolClosed = false } = {}) {
  const shouldEmitStop = animationInProgress.value || timerId != null || hasAnimationLayer();
  stopAnimationPlayback({ renderStoppedRange: false, emitStop: shouldEmitStop });
  restoreTrackLayers();
  selectedSheetDetent.value = 'open';
  active.value = false;
  if (emitToolClosed) {
    emit('tool-closed');
  }
}

function onClose() {
  closeAnimationSession();
}

function onSheetDetentChange(detentId: string) {
  if (detentId === 'playback' || detentId === 'open' || detentId === 'max') {
    selectedSheetDetent.value = detentId;
  }
}

function openPlaybackSettings() {
  selectedSheetDetent.value = 'open';
}

function stopAnimationPlayback({ renderStoppedRange = true, emitStop = true } = {}) {
  if (timerId) {
    clearInterval(timerId);
    timerId = null;
  }
  animationInProgress.value = false;
  animationIndex.value = rangeValue.value[0] || 0;
  playbackPhase.value = 'ready';
  if (renderStoppedRange && sortedFeatures.value.length) {
    ensureAnimationLayer();
    renderRangeFrame();
  }
  if (emitStop) {
    emit(EVENTS.animationStop, 'animation stopped');
  }
}

function prepareSortedFeatures() {
  const geojson =
    props.geojson ?? (instance?.proxy?.$parent as { geojson?: { features?: TrackFeature[] } } | undefined)?.geojson;
  if (!geojson?.features?.length) {
    sortedFeatures.value = [];
    rangeValue.value = [0, 0];
    playbackPhase.value = 'ready';
    return;
  }
  sortedFeatures.value = markRaw(
    [...geojson.features].sort((a: TrackFeature, b: TrackFeature) => {
      return featureStartMs(a) - featureStartMs(b);
    })
  );
  rangeValue.value = [0, sortedFeatures.value.length - 1];
  animationIndex.value = 0;
  playbackPhase.value = 'ready';
}

// ─── Shared layer helpers ──────────────────────────────────────

function ensureAnimationLayer() {
  const map = props.map;
  if (!map) return;
  const animationLayerExists = Boolean(map.getLayer(ANIMATION_LAYER_ID));
  if (!animationLayerExists) hideTrackLayersForAnimation();
  if (!map.getSource(ANIMATION_SOURCE_ID)) {
    map.addSource(ANIMATION_SOURCE_ID, {
      type: 'geojson',
      data: { type: 'FeatureCollection', features: [] },
    });
  }
  if (!animationLayerExists) {
    map.addLayer({
      id: ANIMATION_LAYER_ID,
      type: 'line',
      source: ANIMATION_SOURCE_ID,
      layout: { 'line-join': 'round', 'line-cap': 'butt' },
      paint: {
        'line-color': ['coalesce', ['get', '_animColor'], TRACK_COLOR],
        'line-width': 4,
        'line-opacity': 1,
      },
    });
  }
}

function removeAnimationLayer() {
  const map = props.map;
  if (!map) return;
  try {
    if (map.getLayer(ANIMATION_LAYER_ID)) map.removeLayer(ANIMATION_LAYER_ID);
    if (map.getSource(ANIMATION_SOURCE_ID)) map.removeSource(ANIMATION_SOURCE_ID);
  } catch {
    // The map can be disposed before this tool unmounts.
  }
}

function hasAnimationStateToCleanUp() {
  return (
    active.value ||
    animationInProgress.value ||
    timerId != null ||
    trackLayerPaintBeforeAnimation.size > 0 ||
    trackLayerVisibilityBeforeAnimation.size > 0 ||
    hasAnimationLayer()
  );
}

function hasAnimationLayer() {
  try {
    return Boolean(props.map?.getLayer(ANIMATION_LAYER_ID));
  } catch {
    return false;
  }
}

function hideTrackLayersForAnimation() {
  const map = props.map;
  if (!map) return;
  for (const layerId of TRACK_LAYER_VISIBILITY_IDS) {
    if (!map.getLayer(layerId)) continue;
    if (!trackLayerVisibilityBeforeAnimation.has(layerId)) {
      const visibility = map.getLayoutProperty?.(layerId, 'visibility') as TrackLayerVisibility | undefined;
      trackLayerVisibilityBeforeAnimation.set(layerId, visibility ?? 'visible');
    }
    map.setLayoutProperty?.(layerId, 'visibility', HIDDEN_TRACK_LAYER_VISIBILITY);
  }
  for (const { layerId, property, fallbackValue } of TRACK_LAYER_PAINT_PROPERTIES) {
    if (!map.getLayer(layerId)) continue;
    const snapshotKey = trackLayerPaintSnapshotKey(layerId, property);
    if (!trackLayerPaintBeforeAnimation.has(snapshotKey)) {
      const opacity = map.getPaintProperty?.(layerId, property) as TrackLayerOpacity | undefined;
      trackLayerPaintBeforeAnimation.set(snapshotKey, opacity ?? fallbackValue);
    }
    map.setPaintProperty(layerId, property, HIDDEN_TRACK_LAYER_OPACITY);
  }
}

function restoreTrackLayers() {
  const map = props.map;
  try {
    removeAnimationLayer();
    if (!map) return;
    for (const { layerId, property } of TRACK_LAYER_PAINT_PROPERTIES) {
      const snapshotKey = trackLayerPaintSnapshotKey(layerId, property);
      if (map.getLayer(layerId) && trackLayerPaintBeforeAnimation.has(snapshotKey)) {
        map.setPaintProperty(layerId, property, trackLayerPaintBeforeAnimation.get(snapshotKey));
      }
    }
    for (const layerId of TRACK_LAYER_VISIBILITY_IDS) {
      if (map.getLayer(layerId) && trackLayerVisibilityBeforeAnimation.has(layerId)) {
        map.setLayoutProperty?.(layerId, 'visibility', trackLayerVisibilityBeforeAnimation.get(layerId));
      }
    }
  } catch {
    // The map can be disposed before this tool unmounts.
  } finally {
    trackLayerPaintBeforeAnimation = new Map<string, TrackLayerOpacity>();
    trackLayerVisibilityBeforeAnimation = new Map<string, TrackLayerVisibility>();
  }
}

function trackLayerPaintSnapshotKey(layerId: string, property: string): string {
  return `${layerId}:${property}`;
}

function setAnimationSourceData(features: TrackFeature[]) {
  const source = props.map?.getSource(ANIMATION_SOURCE_ID) as { setData?: (data: unknown) => void } | undefined;
  if (source?.setData) {
    source.setData({ type: 'FeatureCollection', features });
  }
}

// ─── PLAY ──────────────────────────────────────────────────────

function onPlayPauseToggle() {
  if (animationInProgress.value) {
    onPauseAnimation();
  } else {
    onStartAnimation();
  }
}

async function onStartAnimation() {
  if (!props.map || !sortedFeatures.value.length) return;

  if (animationInProgress.value) {
    // Resume from current position
    timerId = setInterval(animationFunction, animationSpeed.value);
    return;
  }

  // If paused inside range, resume from current index; otherwise start at range start.
  if (animationIndex.value < rangeValue.value[0] || animationIndex.value >= rangeValue.value[1]) {
    animationIndex.value = rangeValue.value[0];
  }
  ensureAnimationLayer();
  animationInProgress.value = true;
  playbackPhase.value = 'playing';
  renderFrame();
  timerId = setInterval(animationFunction, animationSpeed.value);
  emit(EVENTS.animationStart, 'animation has started');
  selectedSheetDetent.value = 'playback';
  await nextTick();
  compactPlayButton.value?.focus({ preventScroll: true });
}

function onPauseAnimation(nextPhase: PlaybackPhase = 'paused') {
  if (timerId) {
    clearInterval(timerId);
    timerId = null;
  }
  animationInProgress.value = false;
  playbackPhase.value = nextPhase;
}

function onResetAnimation() {
  stopAnimationPlayback();
}

function onStopAnimation() {
  stopAnimationPlayback({ renderStoppedRange: false });
  restoreTrackLayers();
  selectedSheetDetent.value = 'open';
}

function animationFunction() {
  animationIndex.value++;
  if (animationIndex.value >= rangeValue.value[1]) {
    animationIndex.value = rangeValue.value[1];
    renderFrame();
    emit(EVENTS.animationFinished, 'animation has finished');
    onPauseAnimation('finished');
    restoreTrackLayers();
    return;
  }
  renderFrame();
}

function renderFrame() {
  const visibleFeatures: TrackFeature[] = [];
  const start = rangeValue.value[0];

  for (let i = Math.max(start, animationIndex.value - RANGE_LOOKBACK_TRACK_COUNT); i <= animationIndex.value; i++) {
    const feature = sortedFeatures.value[i];
    const relativeStep = animationIndex.value - i;
    const interpolatedColor = interpolateColor(color1, color2, RANGE_LOOKBACK_TRACK_COUNT, relativeStep);
    const rgbString = `rgb(${interpolatedColor[0]},${interpolatedColor[1]},${interpolatedColor[2]})`;
    visibleFeatures.push({
      ...feature,
      properties: { ...feature.properties, _animColor: rgbString },
    });
  }

  for (let i = start; i < Math.max(start, animationIndex.value - RANGE_LOOKBACK_TRACK_COUNT); i++) {
    visibleFeatures.push({
      ...sortedFeatures.value[i],
      properties: {
        ...sortedFeatures.value[i].properties,
        _animColor: `rgb(${color2[0]},${color2[1]},${color2[2]})`,
      },
    });
  }

  setAnimationSourceData(visibleFeatures);

  const currentFeature = sortedFeatures.value[animationIndex.value];
  const currentDate = parseFeatureStartDate(currentFeature) ?? new Date();
  emit(EVENTS.animate, {
    animateIndexCurrent: animationIndex.value,
    animateIndexMax: sortedFeatures.value.length,
    currentDate,
  });
}

function parseFeatureStartDate(feature: TrackFeature | undefined): Date | null {
  const value = feature?.properties?.startDate;
  if (value == null) return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function featureStartMs(feature: TrackFeature): number {
  return parseFeatureStartDate(feature)?.getTime() ?? Infinity;
}

function renderRangeFrame() {
  const [lo, hi] = rangeValue.value;
  const features = sortedFeatures.value.slice(lo, hi + 1);
  setAnimationSourceData(features);
}

// ─── RANGE ─────────────────────────────────────────────────────

function onRangeChange() {
  if (!sortedFeatures.value.length) return;
  if (animationInProgress.value) {
    onPauseAnimation();
  }
  animationIndex.value = rangeValue.value[0];
  playbackPhase.value = 'ready';
  ensureAnimationLayer();
  renderRangeFrame();
  emit(EVENTS.animationStart, 'range filter active');
}

// ─── Shared ────────────────────────────────────────────────────

function interpolateColor(
  colorA: readonly number[],
  colorB: readonly number[],
  steps: number,
  currentStep: number
): number[] {
  const color: number[] = [];
  for (let i = 0; i < colorA.length; i++) {
    const distance = colorB[i] - colorA[i];
    color[i] = Math.round(colorA[i] + (distance / steps) * currentStep);
  }
  return color;
}

defineExpose({
  open,
  toggle,
  close,
});
</script>

<style scoped>
:global(.sheet.sheet--animate-playback .sheet-fullscreen-btn) {
  display: none;
}

.am-header {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 0.45rem;
}

.am-header__icon {
  color: var(--accent-text);
  font-size: var(--text-sm-size);
}

.am-root {
  display: flex;
  width: 100%;
  margin: 0 auto;
  padding: 0.5rem 1rem 1.1rem;
  box-sizing: border-box;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 1.3rem;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
  color: var(--text-secondary);
  scrollbar-width: none;
}

.am-root::-webkit-scrollbar {
  display: none;
}

.am-root--compact {
  justify-content: center;
  gap: 0;
  padding: 0.25rem 1rem 0.6rem;
  overflow: hidden;
}

.am-compact {
  position: relative;
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.75rem;
  padding: 0.15rem 0 0.65rem;
}

.am-compact__copy {
  min-width: 0;
}

.am-compact__copy .am-eyebrow {
  margin-bottom: 0.1rem;
}

.am-compact__summary {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 0.45rem;
}

.am-compact__summary h3 {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-size: var(--text-base-size);
  font-weight: var(--font-bold);
  line-height: var(--text-base-lh);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.am-compact__summary span {
  flex: 0 0 auto;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-xs-lh);
  white-space: nowrap;
}

.am-compact__controls {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.am-compact__expand,
.am-stop-button,
.am-reset-button,
.am-play-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 0.6rem;
  background: var(--surface-hover);
  color: var(--text-secondary);
  font: inherit;
  font-size: var(--text-sm-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-sm-lh);
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease,
    opacity 0.15s ease;
}

.am-compact__expand,
.am-stop-button {
  min-height: 2.6rem;
}

.am-compact__expand {
  width: 2.6rem;
  padding: 0;
  border: 1px solid var(--border-medium);
  border-radius: 50%;
}

.am-stop-button {
  gap: 0.3rem;
  padding: 0.5rem 0.65rem;
}

.am-compact__expand:hover,
.am-stop-button:hover {
  background: var(--surface-active);
  color: var(--text-primary);
}

.am-compact__expand:focus-visible,
.am-stop-button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.am-compact__progress {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 3px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--border-default);
}

.am-compact__progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--accent);
  transition: width 0.18s linear;
}

.am-compact--finished .am-compact__progress span {
  background: var(--success);
}

.am-empty {
  display: flex;
  min-height: 14rem;
  align-items: center;
  justify-content: center;
  gap: 0.9rem;
  padding: 1rem;
  text-align: left;
}

.am-empty__icon {
  display: inline-flex;
  width: 2.7rem;
  height: 2.7rem;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--surface-hover);
  color: var(--text-muted);
  font-size: var(--text-lg-size);
}

.am-empty h3,
.am-empty p {
  margin: 0;
}

.am-empty h3 {
  color: var(--text-primary);
  font-size: var(--text-base-size);
  font-weight: var(--font-bold);
  line-height: var(--text-base-lh);
}

.am-empty p {
  margin-top: 0.2rem;
  line-height: var(--text-xs-lh);
  font-size: var(--text-xs-size);
  color: var(--text-muted);
}

.am-playback {
  position: relative;
  display: flex;
  min-height: 9.4rem;
  box-sizing: border-box;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1.25rem;
  padding: 0.8rem 0.15rem 0.8rem 1.05rem;
}

.am-playback--paused::before {
  background: var(--border-medium);
}

.am-playback--finished::before {
  background: var(--success);
}

.am-playback__copy {
  min-width: 0;
}

.am-playback__headline {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-lg-size);
  font-weight: var(--font-bold);
  line-height: var(--text-lg-lh);
}

.am-playback__detail {
  min-height: var(--text-sm-lh);
  margin: 0.3rem 0 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.am-playback__controls {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0.55rem;
  padding-top: 0.35rem;
}

.am-reset-button,
.am-play-button {
  min-height: 2.65rem;
  gap: 0.45rem;
}

.am-reset-button {
  padding: 0.5rem 0.7rem;
  background: transparent;
  color: var(--text-secondary);
}

.am-reset-button:hover:not(:disabled) {
  background: var(--surface-hover);
  color: var(--text-primary);
}

.am-reset-button:disabled {
  cursor: default;
  opacity: 0.38;
}

.am-play-button {
  min-width: 6.2rem;
  padding: 0.5rem 0.9rem;
  background: var(--accent);
  color: white;
}

.am-play-button:hover {
  background: var(--accent-hover);
}

.am-play-button .bi-play-fill {
  transform: translateX(1px);
}

.am-play-button--compact {
  min-width: 5.8rem;
  min-height: 2.6rem;
  padding: 0.45rem 0.75rem;
}

.am-play-button:focus-visible,
.am-reset-button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.am-settings {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.am-settings__heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 0 0.15rem;
}

.am-settings__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(16.75rem, 0.8fr);
  border-top: 1px solid var(--border-default);
  border-bottom: 1px solid var(--border-default);
}

.am-setting {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.8rem 0.15rem 0.75rem;
}

.am-setting--range {
  padding-right: 1.15rem;
  border-right: 1px solid var(--border-subtle, var(--border-default));
}

.am-setting--speed {
  padding-left: 1.15rem;
}

.am-setting__head {
  display: grid;
  min-width: 0;
  grid-template-columns: 1.5rem minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.6rem;
}

.am-setting__icon {
  display: inline-flex;
  width: 1.5rem;
  height: 1.5rem;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  font-size: var(--text-base-size);
}

.am-setting__copy {
  min-width: 0;
}

.am-setting__copy h4,
.am-setting__copy p {
  margin: 0;
}

.am-setting__copy h4 {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-sm-lh);
}

.am-setting__copy p {
  margin-top: 0.05rem;
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media screen and (min-width: 769px) {
  .am-setting--speed .am-setting__copy p {
    white-space: normal;
  }
}

.am-setting__value {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-xs-lh);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.am-slider-wrap {
  position: relative;
}

.am-playhead {
  position: absolute;
  top: 0.35rem;
  bottom: 0.35rem;
  width: 2px;
  background: var(--accent);
  transform: translateX(-1px);
  pointer-events: none;
  border-radius: 1px;
}

.am-playhead::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--accent);
  border: 2px solid var(--surface-sheet-solid);
  transform: translate(-50%, -50%);
}

.am-range-labels,
.am-speed-labels {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.65rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.am-speed-labels > span:last-child {
  text-align: right;
}

.am-speed-ms {
  text-align: center;
  white-space: nowrap;
}

.am-slider {
  --mtl-slider-track-height-default: 4px;
  --mtl-slider-track-height-coarse: 4px;
  --mtl-slider-handle-halo-active: 0 0 0 5px var(--accent-glow);
}

@media screen and (max-width: 768px) {
  :global(.sheet.sheet--animate .sheet-fullscreen-btn) {
    display: none;
  }

  .am-root {
    padding: 0.55rem 1rem calc(1.1rem + env(safe-area-inset-bottom));
    gap: 1.05rem;
  }

  .am-root--compact {
    padding: 0.2rem 0.85rem calc(0.55rem + env(safe-area-inset-bottom));
  }

  .am-compact {
    gap: 0.55rem;
  }

  .am-playback {
    min-height: 9.8rem;
    flex-direction: column;
    gap: 0.8rem;
    padding: 0.75rem 0.1rem 0.75rem 0.9rem;
  }

  .am-playback::before {
    top: 0.8rem;
    bottom: 0.8rem;
  }

  .am-playback__detail {
    min-height: calc(2 * var(--text-sm-lh));
  }

  .am-playback__controls {
    width: 100%;
    justify-content: flex-end;
    padding-top: 0;
  }

  .am-play-button {
    min-width: 6.5rem;
  }

  .am-settings__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .am-setting--range,
  .am-setting--speed {
    padding: 0.75rem 0.1rem 0.7rem;
    border-right: 0;
  }

  .am-setting--range {
    border-bottom: 1px solid var(--border-subtle, var(--border-default));
  }

  .am-setting__head {
    grid-template-columns: 1.45rem minmax(0, 1fr) auto;
  }
}

@media screen and (max-width: 420px) {
  .am-settings__heading p,
  .am-speed-ms {
    display: none;
  }

  .am-setting {
    gap: 0.2rem;
  }

  .am-setting__copy p {
    max-width: 11.5rem;
  }

  .am-reset-button,
  .am-play-button {
    min-height: 2.55rem;
  }

  .am-compact__expand,
  .am-stop-button,
  .am-play-button--compact {
    min-height: 2.55rem;
  }

  .am-compact__expand {
    width: 2.55rem;
  }

  .am-stop-button {
    padding-inline: 0.55rem;
  }

  .am-play-button--compact {
    min-width: 5.35rem;
    padding-inline: 0.65rem;
  }
}

@media screen and (max-width: 350px) {
  .am-compact__summary span,
  .am-stop-button span {
    display: none;
  }

  .am-stop-button {
    width: 2.55rem;
    padding: 0;
    border: 1px solid var(--border-medium);
    border-radius: 50%;
  }
}
</style>
