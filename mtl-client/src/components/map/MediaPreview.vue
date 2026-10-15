<template>
  <div
    class="mp"
    :class="{
      'mp--details-open': sidebarVisible,
      'mp--filmstrip-open': showFilmstrip,
      'mp--map-only': !detailsVisible && showMap,
      'mp--controls-hidden': controlsHidden,
    }"
  >
    <div v-if="showInitialLoading" class="mp__loading">
      <span>Loading…</span>
    </div>

    <div v-else-if="hasActiveMedia" class="mp__layout">
      <main class="mp__main">
        <div
          ref="viewportEl"
          class="mp__media-wrap"
          :class="{
            'mp__media-wrap--pending': isSwapPending,
            'mp__media-wrap--zoomed': isZoomed,
            'mp__media-wrap--image': !isVideo,
          }"
          @wheel="onWheel"
          @dblclick="onDoubleClick"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerEnd"
          @pointercancel="onPointerEnd"
        >
          <div v-if="isSwapPending" class="mp__loading-rail" aria-hidden="true">
            <span class="mp__loading-rail-bar"></span>
          </div>
          <div
            v-if="isHighResolutionPending && !isSwapPending"
            class="mp__resolution-status"
            role="status"
            aria-live="polite"
          >
            <i class="bi bi-arrow-repeat" aria-hidden="true"></i>
            <span>Loading full resolution…</span>
          </div>
          <div v-if="hasLoadError" class="mp__error" role="alert">
            <i class="bi bi-exclamation-triangle"></i>
            <span class="mp__error-title">Preview unavailable</span>
            <span class="mp__error-detail">{{ loadError }}</span>
            <div class="mp__error-actions">
              <button class="mp__error-btn mp__error-btn--primary" type="button" @click.stop="retryCurrentMedia">
                <i class="bi bi-arrow-clockwise"></i>
                Retry
              </button>
              <a :href="downloadUrl" :download="fileName || `media-${mediaId}`" class="mp__error-btn">
                <i class="bi bi-download"></i>
                Download
              </a>
            </div>
          </div>
          <template v-else>
            <div
              v-if="isZoomed"
              class="mp__tools"
              :inert="controlsHidden ? true : undefined"
              :aria-hidden="controlsHidden ? 'true' : undefined"
              data-media-control
              aria-label="Media viewer controls"
            >
              <button
                type="button"
                class="mp__tool-btn"
                aria-label="Reset zoom"
                title="Reset zoom"
                @click.stop="resetViewport"
              >
                <i class="bi bi-zoom-out" aria-hidden="true"></i>
              </button>
            </div>

            <div
              v-if="showNavigation"
              class="mp__nav-dock"
              :inert="controlsHidden ? true : undefined"
              :aria-hidden="controlsHidden ? 'true' : undefined"
              data-media-control
              aria-label="Media navigation"
            >
              <button
                type="button"
                class="mp__nav-btn mp__nav-btn--previous"
                :disabled="!canGoPrev"
                aria-label="Previous media"
                aria-keyshortcuts="ArrowLeft"
                title="Previous media (←)"
                @dblclick.stop
                @pointerdown.stop
                @pointerup.stop
                @pointercancel.stop
                @click.stop="goPrevious"
              >
                <i class="bi bi-chevron-left" aria-hidden="true"></i>
              </button>
              <button
                type="button"
                class="mp__nav-btn mp__nav-btn--next"
                :disabled="!canGoNext"
                aria-label="Next media"
                aria-keyshortcuts="ArrowRight"
                title="Next media (→)"
                @dblclick.stop
                @pointerdown.stop
                @pointerup.stop
                @pointercancel.stop
                @click.stop="goNext"
              >
                <i class="bi bi-chevron-right" aria-hidden="true"></i>
              </button>
            </div>

            <img v-if="backSrc" :src="backSrc" class="mp__media mp__media--back" aria-hidden="true" />
            <CompatibleVideoPlayer
              v-if="isVideo"
              :media-id="videoMediaId"
              :src="mediaUrl"
              :poster="posterUrl"
              :label="fileName || 'Selected video'"
              :entering="isCrossFading"
              @media-error="onMediaError"
            />
            <img
              v-else
              ref="imageEl"
              :src="displayUrl"
              :alt="fileName || 'Selected photo'"
              class="mp__media mp__media--image"
              :class="{ 'mp__media--entering': isCrossFading }"
              :style="transformStyle"
              draggable="false"
              @error="onMediaError"
            />
          </template>
        </div>

        <MediaFilmstrip
          v-if="fileName || navTotal > 1"
          v-show="showFilmstrip"
          ref="filmstrip"
          :collection-label="collectionLabel"
          :file-name="fileName"
          :media-id="mediaId"
          :media-ids="mediaIds"
          :video-media-ids="videoMediaIds"
          :current-media-is-video="activeMediaId === mediaId && isVideo"
          :media-offset="mediaOffset"
          :nav-index="navIndex"
          :nav-total="navTotal"
          :page-loading="pageLoading"
          @select="emit('select', $event)"
          @request-page="emit('request-page', $event)"
        />
      </main>

      <aside v-if="sidebarVisible" class="mp__details" data-test="media-metadata" aria-label="Photo information">
        <template v-if="detailsVisible">
          <header class="mp__details-header">
            <div>
              <h2 id="media-details-title">Details</h2>
              <span>{{ isVideo ? 'Video' : 'Photo' }} {{ navIndex }} of {{ navTotal }}</span>
            </div>
            <button type="button" aria-label="Hide media details" @click="emit('update:detailsVisible', false)">
              Hide
            </button>
          </header>

          <dl class="mp__details-list">
            <div v-if="date" class="mp__details-row">
              <dt><i class="bi bi-calendar3" aria-hidden="true"></i><span>Taken</span></dt>
              <dd>
                {{ date }}
                <small v-if="captureTimeSource">{{ captureTimeSource }}</small>
                <button
                  v-if="hasSavedCameraCorrection"
                  type="button"
                  class="mp__clear-correction"
                  :disabled="clearingTimeCorrection"
                  @pointerdown.stop
                  @pointerup.stop
                  @click.stop="clearTimeCorrection"
                >
                  <i
                    :class="clearingTimeCorrection ? 'bi bi-arrow-repeat mp__spin' : 'bi bi-arrow-counterclockwise'"
                    aria-hidden="true"
                  ></i>
                  {{ clearingTimeCorrection ? 'Clearing…' : 'Clear clock correction' }}
                </button>
                <small v-if="timeCorrectionError" class="mp__details-error" role="alert">{{
                  timeCorrectionError
                }}</small>
              </dd>
            </div>
            <div v-if="camera" class="mp__details-row">
              <dt><i class="bi bi-camera" aria-hidden="true"></i><span>Source</span></dt>
              <dd>{{ camera }}</dd>
            </div>
            <div v-if="fileSummary" class="mp__details-row" data-test="media-file-summary">
              <dt><i class="bi bi-file-earmark" aria-hidden="true"></i><span>File</span></dt>
              <dd>{{ fileSummary }}</dd>
            </div>
            <div v-if="positionSourceLabel" class="mp__details-row" data-test="media-position-source">
              <dt><i :class="positionSourceIcon" aria-hidden="true"></i><span>Location</span></dt>
              <dd>
                {{ positionDetailLabel }}
                <small v-if="positionAmbiguous">Multiple activities matched</small>
              </dd>
            </div>
          </dl>

          <details
            v-if="hasMoreDetails"
            :key="mediaId ?? undefined"
            :open="moreDetailsOpen"
            class="mp__more-details"
            data-test="media-more-details"
          >
            <summary @click.prevent="moreDetailsOpen = !moreDetailsOpen">
              <span><i class="bi bi-info-circle" aria-hidden="true"></i>More</span>
              <i class="bi bi-chevron-down mp__more-chevron" aria-hidden="true"></i>
            </summary>
            <dl class="mp__details-list mp__details-list--secondary">
              <div v-if="fileName" class="mp__details-row">
                <dt><i class="bi bi-file-earmark-text" aria-hidden="true"></i><span>Name</span></dt>
                <dd class="mp__path">{{ fileName }}</dd>
              </div>
              <div v-if="!isVideo && dimensions" class="mp__details-row">
                <dt><i class="bi bi-aspect-ratio" aria-hidden="true"></i><span>Dimensions</span></dt>
                <dd>{{ dimensions }}</dd>
              </div>
              <div v-if="!isVideo && exposure" class="mp__details-row" data-test="media-photo-exposure">
                <dt><i class="bi bi-brightness-high" aria-hidden="true"></i><span>Exposure</span></dt>
                <dd>{{ exposure }}</dd>
              </div>
              <div v-if="!isVideo && lens" class="mp__details-row">
                <dt><i class="bi bi-camera2" aria-hidden="true"></i><span>Lens</span></dt>
                <dd>{{ lens }}</dd>
              </div>
              <div v-if="isVideo && videoDetails" class="mp__details-row" data-test="media-video-details">
                <dt><i class="bi bi-film" aria-hidden="true"></i><span>Video</span></dt>
                <dd>{{ videoDetails }}</dd>
              </div>
              <div v-if="isVideo && codecs" class="mp__details-row">
                <dt><i class="bi bi-badge-hd" aria-hidden="true"></i><span>Format</span></dt>
                <dd>{{ codecs }}</dd>
              </div>
              <div v-if="coordinatesLabel" class="mp__details-row" data-test="media-coordinates">
                <dt><i class="bi bi-crosshair" aria-hidden="true"></i><span>Coordinates</span></dt>
                <dd class="mp__coordinates">{{ coordinatesLabel }}</dd>
              </div>
              <div v-if="gpsAltitude" class="mp__details-row">
                <dt><i class="bi bi-triangle" aria-hidden="true"></i><span>GPS altitude</span></dt>
                <dd>{{ gpsAltitude }}</dd>
              </div>
              <div v-if="modified" class="mp__details-row">
                <dt><i class="bi bi-clock-history" aria-hidden="true"></i><span>Modified</span></dt>
                <dd>{{ modified }}</dd>
              </div>
              <div v-if="filePath" class="mp__details-row">
                <dt><i class="bi bi-folder2" aria-hidden="true"></i><span>Folder</span></dt>
                <dd class="mp__path">{{ filePath }}</dd>
              </div>
            </dl>
          </details>
        </template>
        <section v-if="showMap" class="mp__location-section" aria-labelledby="media-location-title">
          <header class="mp__details-header">
            <h3 id="media-location-title">Location</h3>
            <button type="button" aria-label="Hide location map" @click="emit('update:mapVisible', false)">Hide</button>
          </header>
          <MediaLocationMiniMap
            v-if="positionCoordinates"
            :latitude="positionCoordinates.latitude"
            :longitude="positionCoordinates.longitude"
            :position-source="positionSource"
            :position-estimated="positionEstimated"
            :track-coordinates="trackCoordinates"
            :overview-bounds="overviewBounds"
          />
          <button
            v-if="positionCoordinates"
            type="button"
            class="mp__open-map-btn"
            @pointerdown.stop
            @pointerup.stop
            @click.stop="emit('open-on-map')"
          >
            Open on main map
          </button>
          <p v-if="!positionCoordinates" class="mp__location-unavailable">This media has no location.</p>
        </section>

        <a
          v-if="detailsVisible"
          :href="downloadUrl"
          :download="fileName || `media-${mediaId}`"
          class="mp__download-btn"
          aria-label="Download original"
        >
          <i class="bi bi-download" aria-hidden="true"></i>
          <span>Download original</span>
        </a>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { warnSanitized } from '@/utils/safeLogging';

import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import MediaFilmstrip from '@/components/map/MediaFilmstrip.vue';
import CompatibleVideoPlayer from '@/components/map/CompatibleVideoPlayer.vue';
import { useMediaPreview } from '@/components/map/useMediaPreview';
import { useMediaViewport } from '@/components/map/useMediaViewport';
import MediaLocationMiniMap from '@/components/map/MediaLocationMiniMap.vue';
import { saveMediaTimeCorrections } from '@/repositories/mediaRepository';
import { sheetControlsKey } from '@/components/ui/useImmersiveSheetControls';

const props = withDefaults(
  defineProps<{
    mediaId: number | null;
    canGoPrev?: boolean;
    canGoNext?: boolean;
    navIndex?: number;
    navTotal?: number;
    prefetchIds?: (number | null)[];
    mediaIds?: number[];
    videoMediaIds?: number[];
    mediaOffset?: number;
    pageLoading?: boolean;
    collectionLabel?: string;
    positionSource?: string | null;
    positionEstimated?: boolean;
    positionAmbiguous?: boolean;
    positionUnknown?: boolean;
    positionTimeDeltaSeconds?: number | null;
    positionLat?: number | null;
    positionLng?: number | null;
    trackCoordinates?: number[][];
    overviewBounds?: [[number, number], [number, number]] | null;
    detailsVisible?: boolean;
    mapVisible?: boolean;
    photosVisible?: boolean;
    takenAt?: Date | string | null;
    timeSource?: string | null;
    appliedCameraOffsetSeconds?: number | null;
  }>(),
  {
    canGoPrev: false,
    canGoNext: false,
    navIndex: 0,
    navTotal: 0,
    prefetchIds: () => [],
    mediaIds: () => [],
    videoMediaIds: () => [],
    mediaOffset: 0,
    pageLoading: false,
    collectionLabel: 'Nearby media',
    positionSource: null,
    positionEstimated: false,
    positionAmbiguous: false,
    positionUnknown: false,
    positionTimeDeltaSeconds: null,
    positionLat: null,
    positionLng: null,
    trackCoordinates: () => [],
    overviewBounds: null,
    detailsVisible: true,
    mapVisible: undefined,
    photosVisible: undefined,
    takenAt: null,
    timeSource: null,
    appliedCameraOffsetSeconds: null,
  }
);

const emit = defineEmits<{
  prev: [];
  next: [];
  select: [mediaId: number];
  'request-page': [direction: -1 | 1];
  'update:detailsVisible': [visible: boolean];
  'update:mapVisible': [visible: boolean];
  'open-on-map': [];
  'time-correction-cleared': [mediaId: number];
}>();

const viewportEl = ref<HTMLElement | null>(null);
const showMap = computed(() => props.mapVisible ?? (props.detailsVisible && Boolean(positionCoordinates.value)));
const showFilmstrip = computed(() => props.photosVisible ?? props.detailsVisible);
const sidebarVisible = computed(() => props.detailsVisible || showMap.value);
const sheetControls = inject(sheetControlsKey, null);
const controlsHidden = computed(() => sheetControls?.hidden.value ?? false);
const imageEl = ref<HTMLImageElement | null>(null);
const filmstrip = ref<InstanceType<typeof MediaFilmstrip> | null>(null);
const moreDetailsOpen = ref(false);
const clearingTimeCorrection = ref(false);
const timeCorrectionCleared = ref(false);
const timeCorrectionError = ref('');
const positionCoordinates = computed(() => {
  const latitude = props.positionLat;
  const longitude = props.positionLng;
  if (
    latitude == null ||
    longitude == null ||
    !Number.isFinite(latitude) ||
    !Number.isFinite(longitude) ||
    Math.abs(latitude) > 90 ||
    Math.abs(longitude) > 180
  ) {
    return null;
  }
  return { latitude, longitude };
});
const coordinatesLabel = computed(() => {
  const coordinates = positionCoordinates.value;
  return coordinates ? `${coordinates.latitude.toFixed(5)}, ${coordinates.longitude.toFixed(5)}` : '';
});
const positionSourceLabel = computed(() => {
  if (props.positionSource === 'USER_ASSIGNED') return 'Set by you';
  if (props.positionSource === 'TRACK_INTERPOLATED' || props.positionEstimated) {
    return `Estimated from ${isVideo.value ? 'video' : 'photo'} time and activity track`;
  }
  if (props.positionSource === 'EXIF_EMBEDDED') return isVideo.value ? 'Video GPS' : 'Photo GPS';
  if (props.positionUnknown || props.positionTimeDeltaSeconds != null) return 'Position unknown';
  return '';
});
const positionSourceIcon = computed(() => {
  if (props.positionSource === 'USER_ASSIGNED') return 'bi bi-pencil-fill';
  if (props.positionSource === 'TRACK_INTERPOLATED' || props.positionEstimated) return 'bi bi-signpost-split';
  return 'bi bi-geo-alt-fill';
});
const positionDeltaLabel = computed(() => {
  const seconds = props.positionTimeDeltaSeconds;
  if (seconds == null || !Number.isFinite(seconds)) return '';
  const rounded = Math.round(Math.abs(seconds));
  return rounded === 0 ? 'Matched track time' : `Nearest track point ${rounded}s away`;
});
const positionDetailLabel = computed(() => {
  if (props.positionSource === 'USER_ASSIGNED') return 'Set by you';
  if (props.positionSource === 'EXIF_EMBEDDED') return isVideo.value ? 'Video GPS' : 'Photo GPS';
  if (props.positionSource === 'TRACK_INTERPOLATED' || props.positionEstimated) {
    return positionDeltaLabel.value || 'Matched to activity track';
  }
  return positionSourceLabel.value || 'Position unknown';
});
const hasSavedCameraCorrection = computed(() => {
  const seconds = props.appliedCameraOffsetSeconds;
  return !timeCorrectionCleared.value && seconds != null && Number.isFinite(seconds) && seconds !== 0;
});

const {
  activeMediaId,
  isSwapPending,
  isCrossFading,
  isHighResolutionPending,
  backSrc,
  displayUrl,
  mediaUrl,
  posterUrl,
  downloadUrl,
  fileName,
  filePath,
  date,
  captureTimeSource,
  camera,
  fileSummary,
  dimensions,
  exposure,
  lens,
  videoDetails,
  codecs,
  modified,
  gpsAltitude,
  isVideo,
  hasActiveMedia,
  showInitialLoading,
  showNavigation,
  canGoPrev,
  canGoNext,
  hasLoadError,
  loadError,
  retryLoad,
  onMediaError,
} = useMediaPreview(props);

const videoMediaId = computed(() => activeMediaId.value ?? 0);

function retryCurrentMedia(): void {
  retryLoad();
  filmstrip.value?.retryThumbnail(props.mediaId);
}

async function clearTimeCorrection(): Promise<void> {
  const mediaId = props.mediaId;
  if (mediaId == null || clearingTimeCorrection.value || !hasSavedCameraCorrection.value) return;
  clearingTimeCorrection.value = true;
  timeCorrectionError.value = '';
  try {
    await saveMediaTimeCorrections({ mediaIds: [mediaId], offsetSeconds: 0 });
    timeCorrectionCleared.value = true;
    emit('time-correction-cleared', mediaId);
  } catch (error) {
    warnSanitized(
      '[media-preview] clock correction could not be cleared',
      { mediaId, error },
      { topic1: 'media', topic2: 'MediaPreview', topic3: 'clearTimeCorrection' }
    );
    timeCorrectionError.value = 'Clock correction could not be cleared. Try again.';
  } finally {
    clearingTimeCorrection.value = false;
  }
}

watch(
  () => [props.mediaId, props.appliedCameraOffsetSeconds] as const,
  () => {
    timeCorrectionCleared.value = false;
    timeCorrectionError.value = '';
  }
);

const hasMoreDetails = computed(() =>
  Boolean(
    fileName.value ||
    dimensions.value ||
    exposure.value ||
    lens.value ||
    videoDetails.value ||
    codecs.value ||
    coordinatesLabel.value ||
    gpsAltitude.value ||
    modified.value ||
    filePath.value
  )
);

function goPrevious(): void {
  if (canGoPrev.value) emit('prev');
}

function goNext(): void {
  if (canGoNext.value) emit('next');
}

const {
  isZoomed,
  transformStyle,
  reset: resetViewport,
  onWheel,
  onDoubleClick,
  onPointerDown,
  onPointerMove,
  onPointerEnd,
} = useMediaViewport({
  mediaId: () => props.mediaId,
  imageEl,
  viewportEl,
  canGoPrev: () => canGoPrev.value,
  canGoNext: () => canGoNext.value,
  previous: goPrevious,
  next: goNext,
});

function isEditableTarget(target: EventTarget | null): boolean {
  return (
    target instanceof Element &&
    Boolean(target.closest('input, textarea, select, [contenteditable="true"], .media-viewer-panel-menu[open]'))
  );
}

function onKeyDown(event: KeyboardEvent): void {
  if (!hasActiveMedia.value || isEditableTarget(event.target)) return;
  if (event.key === 'ArrowLeft' && canGoPrev.value) {
    event.preventDefault();
    goPrevious();
  }
  if (event.key === 'ArrowRight' && canGoNext.value) {
    event.preventDefault();
    goNext();
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeyDown);
});
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeyDown);
});
</script>

<style scoped>
/* ── Container — slot root, MUST propagate height constraint from BottomSheet ── */
.mp {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  color: var(--mp-text);
  background: var(--mp-shell);
}

.mp__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 340px);
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}

.mp:not(.mp--details-open) .mp__layout {
  grid-template-columns: minmax(0, 1fr);
}

.mp__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

/* ── Loading ── */
.mp__loading {
  padding: 32px;
  text-align: center;
  color: var(--mp-text-muted);
  font-size: var(--text-sm-size);
}

.mp__resolution-status {
  position: absolute;
  top: 0.75rem;
  left: 0.75rem;
  z-index: 3;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.55rem;
  color: var(--mp-text);
  background: color-mix(in srgb, var(--mp-surface) 88%, transparent);
  border: 1px solid var(--mp-border);
  border-radius: 999px;
  font-size: var(--text-xs-size);
  pointer-events: none;
}

.mp__resolution-status i {
  animation: mp-resolution-spin 0.9s linear infinite;
}

@keyframes mp-resolution-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ── Media ── */
.mp__media-wrap {
  position: relative;
  background: var(--mp-stage);
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  user-select: none;
}

.mp__media-wrap--image {
  touch-action: none;
}

.mp__media-wrap--zoomed {
  cursor: grab;
}

.mp__media-wrap--zoomed:active {
  cursor: grabbing;
}

.mp__media-wrap--pending::after {
  content: '';
  position: absolute;
  inset: 0;
  background: transparent;
  opacity: 0;
  pointer-events: none;
}

.mp__media {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  display: block;
  border-radius: 0;
  box-shadow: none;
}

.mp__media--image,
.mp__media--video {
  position: relative;
  z-index: 1;
}

.mp__media--image {
  transform-origin: center;
  will-change: transform;
}

.mp__error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  max-width: min(30rem, 100%);
  text-align: center;
  color: var(--mp-text);
  padding: 1rem;
  z-index: 2;
}

.mp__error > i {
  font-size: var(--text-3xl-size);
  color: var(--warning);
}

.mp__error-title {
  font-size: var(--text-base-size);
  font-weight: 700;
  color: var(--mp-text-strong);
}

.mp__error-detail {
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.mp__error-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-top: 0.25rem;
}

.mp__error-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  min-height: 2rem;
  padding: 0.25rem 0.65rem;
  border: 1px solid var(--mp-border-strong);
  border-radius: 6px;
  background: var(--mp-control-bg);
  color: var(--mp-text-strong);
  font-size: var(--text-sm-size);
  text-decoration: none;
  cursor: pointer;
}

.mp__error-btn:not(.mp__error-btn--primary):hover {
  border-color: var(--accent);
  color: var(--accent);
}

.mp__error-btn--primary {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-contrast);
}

.mp__error-btn--primary:hover:not(:disabled) {
  background: var(--accent-hover);
}

/* Back layer: old image dissolving out */
.mp__media--back {
  position: absolute;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  z-index: 0;
  pointer-events: none;
  animation: mp-dissolve-out 190ms ease-in forwards;
}

/* Front layer entering: dissolves in */
.mp__media--entering {
  animation: mp-dissolve-in 190ms ease-out;
}

@keyframes mp-dissolve-out {
  from {
    opacity: 1;
    transform: scale(1);
  }
  to {
    opacity: 0;
    transform: scale(0.992);
  }
}

@keyframes mp-dissolve-in {
  from {
    opacity: 0;
    transform: scale(1.008);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .mp__media--back,
  .mp__media--entering,
  .mp__loading-rail-bar,
  .mp__resolution-status i,
  .mp__nav-btn,
  .mp__tool-btn,
  .mp__download-btn {
    animation: none !important;
    transition: none !important;
  }
}

.mp__loading-rail {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  overflow: hidden;
  background: color-mix(in srgb, var(--mp-text-muted) 18%, transparent);
  z-index: 1;
  pointer-events: none;
}

.mp__loading-rail-bar {
  position: absolute;
  inset: 0;
  width: 42%;
  border-radius: inherit;
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--accent) 0%, transparent) 0%,
    color-mix(in srgb, var(--accent) 68%, var(--mp-text-strong)) 45%,
    color-mix(in srgb, var(--accent) 0%, transparent) 100%
  );
  animation: mp-loading-rail-slide 980ms cubic-bezier(0.4, 0, 0.2, 1) infinite;
}

.mp__nav-dock {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
}

.mp__tools {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.mp__tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  padding: 0;
  color: var(--mp-overlay-text);
  background: var(--mp-overlay-bg);
  border: 1px solid var(--mp-overlay-border);
  border-radius: 999px;
  box-shadow: var(--mp-overlay-shadow);
  backdrop-filter: blur(10px) saturate(120%);
  -webkit-backdrop-filter: blur(10px) saturate(120%);
  cursor: pointer;
  transition:
    color 0.15s,
    background 0.15s,
    border-color 0.15s,
    transform 0.15s;
}

.mp__tool-btn:hover {
  color: var(--mp-overlay-text);
  background: var(--mp-overlay-hover);
  border-color: var(--mp-overlay-border-hover);
}

.mp__tool-btn:active {
  transform: scale(0.96);
}

.mp__nav-btn {
  position: absolute;
  top: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border: 1px solid var(--mp-overlay-border);
  border-radius: 999px;
  background: var(--mp-overlay-bg);
  color: var(--mp-overlay-text);
  box-shadow: var(--mp-overlay-shadow);
  font-size: var(--text-xl-size);
  cursor: pointer;
  padding: 0;
  transform: translateY(-50%);
  pointer-events: auto;
  touch-action: manipulation;
  transition:
    background 0.15s,
    color 0.15s,
    border-color 0.15s;
}

.mp__nav-btn--previous {
  left: 12px;
}

.mp__nav-btn--next {
  right: 12px;
}

.mp__nav-btn:not(:disabled):hover {
  color: var(--mp-overlay-text);
  background: var(--mp-overlay-hover);
  border-color: var(--mp-overlay-border-hover);
}

.mp__nav-btn:disabled {
  opacity: 0.3;
  cursor: default;
}

.mp__nav-btn:focus-visible,
.mp__tool-btn:focus-visible,
.mp__download-btn:focus-visible,
.mp__error-btn:focus-visible,
.mp__more-details summary:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

/* ── Details panel ── */
.mp__details {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: 1rem 1.1rem 1.15rem;
  overflow-y: auto;
  color: var(--mp-text);
  background: var(--mp-surface);
  border-left: 1px solid var(--mp-border);
  overscroll-behavior-y: contain;
  touch-action: pan-y;
  -webkit-overflow-scrolling: touch;
}

.mp__details-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.6rem;
}

.mp__details-header h2 {
  margin: 0;
  color: var(--mp-text-strong);
  font-size: var(--text-lg-size);
}

.mp__details-header span {
  display: none;
  margin-top: 0.15rem;
  color: var(--mp-text-muted);
  font-size: var(--text-sm-size);
}

.mp__details-header button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 2rem;
  padding: 0.25rem 0.65rem;
  color: var(--mp-text);
  background: var(--mp-control-bg);
  border: 1px solid var(--mp-border-strong);
  border-radius: 8px;
  font: inherit;
  font-size: var(--text-xs-size);
  cursor: pointer;
}

.mp__details-list {
  margin: 0;
  border-top: 1px solid var(--mp-border);
}

.mp__details-row {
  display: grid;
  grid-template-columns: minmax(7.25rem, 0.9fr) minmax(0, 1.1fr);
  gap: 0.65rem;
  align-items: start;
  padding: 0.8rem 0.15rem;
  border-bottom: 1px solid var(--mp-border);
  font-size: var(--text-sm-size);
}

.mp__details-row dt {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  color: var(--mp-text);
}

.mp__details-row dt i {
  width: 1.1rem;
  color: var(--mp-text-muted);
  font-size: var(--text-base-size);
  text-align: center;
}

.mp__details-row dd {
  min-width: 0;
  margin: 0;
  color: var(--mp-text-strong);
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.mp__details-row dd small {
  display: block;
  margin-top: 0.2rem;
  color: var(--mp-text-muted);
}

.mp__clear-correction {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  min-height: 2rem;
  margin-top: 0.55rem;
  padding: 0.3rem 0.55rem;
  color: var(--mp-text-strong);
  background: var(--mp-control-bg);
  border: 1px solid var(--mp-border-strong);
  border-radius: 6px;
  font: inherit;
  cursor: pointer;
}

.mp__clear-correction:hover:not(:disabled) {
  background: var(--mp-control-hover);
}

.mp__clear-correction:disabled {
  cursor: wait;
  opacity: 0.65;
}

.mp__details-row dd .mp__details-error {
  color: var(--error);
}

.mp__spin {
  animation: mp-resolution-spin 0.9s linear infinite;
}

.mp__path {
  font-size: var(--text-xs-size);
  color: var(--mp-text-muted);
}

.mp__coordinates {
  font-variant-numeric: tabular-nums;
  user-select: text;
}

.mp__more-details {
  margin-top: 0.75rem;
  border: 1px solid var(--mp-border-strong);
  border-radius: 8px;
  overflow: clip;
}

.mp__more-details summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  min-height: 44px;
  padding: 0.55rem 0.75rem;
  color: var(--mp-text-strong);
  background: var(--mp-control-bg);
  font-size: var(--text-sm-size);
  font-weight: 600;
  cursor: pointer;
  list-style: none;
  touch-action: manipulation;
}

.mp__more-details summary::-webkit-details-marker {
  display: none;
}

.mp__more-details summary > span {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
}

.mp__more-details summary:hover {
  background: var(--mp-control-hover);
}

.mp__more-chevron {
  color: var(--mp-text-muted);
  transition: transform 0.15s ease;
}

.mp__more-details[open] .mp__more-chevron {
  transform: rotate(180deg);
}

.mp__details-list--secondary {
  border-top: 1px solid var(--mp-border);
}

.mp__details-list--secondary .mp__details-row {
  padding-inline: 0.75rem;
}

.mp__details-list--secondary .mp__details-row:last-child {
  border-bottom: 0;
}

.mp__location-section {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  margin-top: 1rem;
}

.mp__location-section h3 {
  margin: 0;
  color: var(--mp-text-strong);
  font-size: var(--text-base-size);
}

.mp__location-section :deep(.media-location-map) {
  width: 100%;
  height: 210px;
}

.mp__open-map-btn {
  align-self: center;
  padding: 0.25rem 0.5rem;
  color: var(--mp-link);
  background: transparent;
  border: 0;
  font: inherit;
  font-size: var(--text-sm-size);
  cursor: pointer;
}

.mp__download-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.55rem;
  min-height: 44px;
  margin-top: 1rem;
  padding: 0.65rem 0.85rem;
  color: var(--mp-text-strong);
  background: transparent;
  border: 1px solid var(--mp-border-strong);
  border-radius: 8px;
  font-size: var(--text-sm-size);
  font-weight: 600;
  text-decoration: none;
  transition:
    background 0.15s,
    border-color 0.15s;
}

.mp__download-btn:hover,
.mp__download-btn:active {
  background: var(--mp-control-hover);
  border-color: var(--mp-border-emphasis);
}

@media (min-width: 769px) {
  :global(.sheet.media-viewer-sheet.sheet--immersive) .mp__details {
    padding-top: calc(56px + 1rem);
  }
  .mp__location-section {
    margin-top: auto;
  }
}

:global(.media-preview-sheet-title) {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
  color: var(--mp-text-strong);
  font-weight: 700;
  white-space: nowrap;
}

:global(.media-preview-sheet-title__counter) {
  color: var(--mp-text-muted);
  font-size: var(--text-xs-size);
  font-weight: 500;
}

:global(.media-viewer-theme-toggle) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  min-height: 2rem;
  padding: 0.25rem 0.65rem;
  color: var(--mp-text);
  background: var(--mp-control-bg);
  border: 1px solid var(--mp-border-strong);
  border-radius: 8px;
  font: inherit;
  font-size: var(--text-xs-size);
  font-weight: 600;
  cursor: pointer;
}

:global(.media-viewer-theme-toggle) {
  width: 2rem;
  padding-inline: 0;
}

:global(.media-viewer-theme-toggle:hover) {
  color: var(--mp-text-strong);
  background: var(--mp-control-hover);
  border-color: var(--mp-border-emphasis);
}

:global(.media-viewer-theme-toggle:focus-visible),
:global(.sheet.media-viewer-sheet .sheet-back-btn:focus-visible),
:global(.sheet.media-viewer-sheet .sheet-fullscreen-btn:focus-visible),
:global(.sheet.media-viewer-sheet .sheet-native-fullscreen-btn:focus-visible),
:global(.sheet.media-viewer-sheet .sheet-close-btn:focus-visible) {
  outline: 2px solid var(--mp-link, var(--accent));
  outline-offset: 2px;
}

:global(.sheet.media-viewer-sheet) {
  --bs-body-pb: 0px;
  --mp-shell: #070b12;
  --mp-surface: #0b111b;
  --mp-stage: #070b12;
  --mp-text-strong: rgba(255, 255, 255, 0.94);
  --mp-text: rgba(255, 255, 255, 0.76);
  --mp-text-muted: rgba(255, 255, 255, 0.54);
  --mp-border: rgba(255, 255, 255, 0.1);
  --mp-border-strong: rgba(255, 255, 255, 0.17);
  --mp-border-emphasis: rgba(255, 255, 255, 0.32);
  --mp-control-bg: rgba(255, 255, 255, 0.05);
  --mp-control-hover: rgba(255, 255, 255, 0.1);
  --mp-control-active: rgba(255, 255, 255, 0.13);
  --mp-thumb-bg: #182133;
  --mp-scrollbar: rgba(255, 255, 255, 0.3);
  --mp-link: #8bb4ff;
  --mp-overlay-text: #fff;
  --mp-overlay-bg: rgba(9, 14, 24, 0.64);
  --mp-overlay-hover: rgba(9, 14, 24, 0.82);
  --mp-overlay-border: rgba(255, 255, 255, 0.22);
  --mp-overlay-border-hover: rgba(255, 255, 255, 0.4);
  --mp-overlay-shadow: 0 4px 16px rgba(0, 0, 0, 0.24);
  --mp-panel-shadow: 0 -8px 28px rgba(0, 0, 0, 0.2);
  color: var(--mp-text);
  background: var(--mp-shell);
  padding-bottom: 0;
}

:global(.sheet.media-viewer-sheet--light) {
  --mp-shell: #fff;
  --mp-surface: #fff;
  --mp-stage: #fff;
  --mp-text-strong: #172033;
  --mp-text: #42516a;
  --mp-text-muted: #68758a;
  --mp-border: rgba(15, 23, 42, 0.12);
  --mp-border-strong: rgba(15, 23, 42, 0.2);
  --mp-border-emphasis: rgba(15, 23, 42, 0.34);
  --mp-control-bg: rgba(15, 23, 42, 0.045);
  --mp-control-hover: rgba(15, 23, 42, 0.09);
  --mp-control-active: rgba(41, 98, 255, 0.1);
  --mp-thumb-bg: #d9e0e8;
  --mp-scrollbar: rgba(15, 23, 42, 0.28);
  --mp-link: #245fc7;
  --mp-overlay-text: #172033;
  --mp-overlay-bg: rgba(255, 255, 255, 0.78);
  --mp-overlay-hover: rgba(255, 255, 255, 0.94);
  --mp-overlay-border: rgba(15, 23, 42, 0.2);
  --mp-overlay-border-hover: rgba(15, 23, 42, 0.38);
  --mp-overlay-shadow: 0 4px 16px rgba(15, 23, 42, 0.16);
  --mp-panel-shadow: 0 -8px 28px rgba(15, 23, 42, 0.16);
}

:global(.sheet.media-viewer-sheet .sheet-drag-zone) {
  grid-template-rows: auto auto;
  color: var(--mp-text);
  background: var(--mp-surface);
  border-bottom: 1px solid var(--mp-border);
}

:global(.sheet.media-viewer-sheet .sheet-handle-zone) {
  grid-row: 1;
  padding: 0.45rem 0 0.3rem;
}
:global(.sheet.media-viewer-sheet .sheet-header) {
  grid-row: 2;
}

:global(.sheet.media-viewer-sheet .sheet-handle) {
  background: var(--mp-text-muted);
}

:global(.sheet.media-viewer-sheet .sheet-title) {
  color: var(--mp-text-strong);
}

:global(.sheet.media-viewer-sheet .sheet-back-btn),
:global(.sheet.media-viewer-sheet .sheet-fullscreen-btn),
:global(.sheet.media-viewer-sheet .sheet-native-fullscreen-btn),
:global(.sheet.media-viewer-sheet .sheet-close-btn) {
  color: var(--mp-text);
  background: var(--mp-control-bg);
  border-color: var(--mp-border-strong);
}

:global(.sheet.media-viewer-sheet .sheet-back-btn:hover),
:global(.sheet.media-viewer-sheet .sheet-fullscreen-btn:hover),
:global(.sheet.media-viewer-sheet .sheet-native-fullscreen-btn:hover),
:global(.sheet.media-viewer-sheet .sheet-close-btn:hover) {
  color: var(--mp-text-strong);
  background: var(--mp-control-hover);
  border-color: var(--mp-border-emphasis);
}

:global(.sheet.media-viewer-sheet.sheet--fullscreen:not(.sheet--shared-layout)),
:global(.sheet.media-viewer-sheet.sheet--native-fullscreen) {
  left: 0 !important;
  right: 0 !important;
  bottom: 0 !important;
  width: 100vw !important;
  max-width: none !important;
  margin: 0 !important;
  border-radius: 0;
}

@media (max-width: 768px) {
  :global(.sheet.media-viewer-sheet.sheet--header-compact) {
    --bs-btn-size: 2.75rem;
    --bs-header-px: 0.5rem;
    --bs-actions-gap: 0.25rem;
  }

  :global(.sheet.media-viewer-sheet .sheet-header) {
    display: grid;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: 0.25rem;
  }

  :global(.sheet.media-viewer-sheet .sheet-header-content) {
    grid-column: 2 / 5;
    grid-row: 1;
  }
  :global(.sheet.media-viewer-sheet .sheet-header:not(:has(.sheet-back-btn)) .sheet-header-content) {
    grid-column: 1 / 5;
  }

  :global(.sheet.media-viewer-sheet .sheet-back-btn) {
    grid-column: 1;
    grid-row: 1;
  }

  :global(.sheet.media-viewer-sheet .sheet-window-actions) {
    display: contents;
  }

  :global(.sheet.media-viewer-sheet .media-viewer-view-toggle) {
    grid-column: 1 / 3;
    grid-row: 2;
    justify-self: start;
  }

  :global(.sheet.media-viewer-sheet .media-viewer-theme-toggle) {
    grid-column: 5;
    grid-row: 1;
  }

  :global(.sheet.media-viewer-sheet .sheet-close-btn) {
    grid-column: 6;
    grid-row: 1;
  }

  :global(.sheet.media-viewer-sheet .sheet-fullscreen-btn) {
    grid-column: 3 / 5;
    grid-row: 2;
    justify-self: start;
  }

  :global(.sheet.media-viewer-sheet .sheet-native-fullscreen-btn) {
    grid-column: 5 / 7;
    grid-row: 2;
    justify-self: end;
  }

  :global(.sheet.media-viewer-sheet .sheet-labeled-size-btn) {
    min-height: 44px;
    padding-inline: 0.45rem;
    max-width: 100%;
    white-space: normal;
  }

  :global(.media-preview-sheet-title) {
    flex-wrap: wrap;
    white-space: normal;
  }

  .mp__layout {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(120px, 1fr) minmax(0, 1.2fr);
  }

  .mp.mp--filmstrip-open .mp__layout {
    grid-template-rows: minmax(120px, 1fr) auto minmax(0, 1.2fr);
  }

  .mp__main {
    display: contents;
  }

  .mp:not(.mp--details-open) .mp__layout {
    grid-template-rows: minmax(0, 1fr);
  }
  .mp.mp--filmstrip-open:not(.mp--details-open) .mp__layout {
    grid-template-rows: minmax(0, 1fr) auto;
  }

  .mp__nav-dock {
    inset: 0;
  }

  .mp__nav-btn,
  .mp__tool-btn {
    width: 44px;
    height: 44px;
  }

  .mp__nav-btn {
    top: 50%;
    border-radius: 999px;
  }

  .mp__nav-btn--previous {
    left: 8px;
  }

  .mp__nav-btn--next {
    right: 8px;
  }

  .mp__details {
    padding: 0.6rem 1rem max(1.25rem, env(safe-area-inset-bottom, 0px));
    background: var(--mp-surface);
    border-top: 1px solid var(--mp-border);
    border-left: 0;
  }

  .mp__details-header {
    margin-bottom: 0.8rem;
  }

  .mp__details-header h2 {
    font-size: var(--text-xl-size);
  }

  .mp__details-header span,
  .mp__details-header button {
    display: flex;
  }
  .mp__details-header button {
    min-height: 44px;
  }

  .mp__details-row {
    grid-template-columns: minmax(6.8rem, 0.8fr) minmax(0, 1.2fr);
    padding-block: 0.9rem;
  }

  .mp__more-details summary {
    min-height: 48px;
  }

  .mp__location-section :deep(.media-location-map) {
    height: min(250px, 32vh);
  }

  .mp__download-btn {
    min-height: 48px;
    margin-bottom: 0.5rem;
  }

  :global(.media-viewer-theme-toggle) {
    width: 2.75rem;
    min-height: 2.75rem;
  }
}

.mp__nav-dock,
.mp__tools {
  transition: opacity 0.2s ease;
}

.mp--map-only .mp__location-section {
  display: flex;
  flex-direction: column;
  flex: 1;
  margin-top: 0;
}
.mp--map-only .mp__location-section :deep(.media-location-map) {
  flex: 1;
  min-height: 160px;
  height: auto;
}
.mp__location-unavailable {
  color: var(--mp-text-muted);
  font-size: var(--text-sm-size);
}

.mp--controls-hidden .mp__nav-dock,
.mp--controls-hidden .mp__tools {
  opacity: 0;
  pointer-events: none;
}

@media (prefers-reduced-motion: reduce) {
  .mp__nav-dock,
  .mp__tools {
    transition: none;
  }
}

@keyframes mp-loading-rail-slide {
  0% {
    transform: translateX(-130%);
    opacity: 0;
  }
  18% {
    opacity: 1;
  }
  82% {
    opacity: 1;
  }
  100% {
    transform: translateX(300%);
    opacity: 0;
  }
}
</style>
