<template>
  <div class="mp">
    <!-- Loading -->
    <div v-if="showInitialLoading" class="mp__loading">
      <span>Loading…</span>
    </div>

    <!-- Content -->
    <template v-else-if="hasActiveMedia">
      <div class="mp__media-wrap" :class="{ 'mp__media-wrap--pending': isSwapPending }">
        <div v-if="isSwapPending" class="mp__loading-rail" aria-hidden="true">
          <span class="mp__loading-rail-bar"></span>
        </div>
        <div v-if="showNavigation" class="mp__nav-dock" aria-label="Photo navigation">
          <button
            class="mp__nav-btn"
            :disabled="!canGoPrev"
            @click.stop="emit('prev')"
            aria-label="Previous photo"
          >
            <i class="bi bi-chevron-left"></i>
          </button>
          <span class="mp__nav-counter">{{ navIndex }} / {{ navTotal }}</span>
          <button
            class="mp__nav-btn"
            :disabled="!canGoNext"
            @click.stop="emit('next')"
            aria-label="Next photo"
          >
            <i class="bi bi-chevron-right"></i>
          </button>
        </div>
        <!-- Cross-dissolve: back layer shows old image fading out -->
        <img
          v-if="backSrc"
          :src="backSrc"
          class="mp__media mp__media--back"
          aria-hidden="true"
        />
        <!-- Front layer: current media -->
        <video
          v-if="isVideo"
          :src="mediaUrl"
          controls
          preload="metadata"
          class="mp__media mp__media--video"
          :class="{ 'mp__media--entering': isCrossFading }"
        />
        <img
          v-else
          :src="displayUrl"
          :alt="fileName"
          class="mp__media mp__media--image"
          :class="{ 'mp__media--entering': isCrossFading }"
        />
      </div>

      <div class="mp__meta">
        <div class="mp__meta-text">
          <div v-if="fileName" class="mp__filename">{{ fileName }}</div>
          <div v-if="date" class="mp__meta-line">📅 {{ date }}</div>
          <div v-if="camera" class="mp__meta-line">📷 {{ camera }}</div>
          <div v-if="filePath" class="mp__meta-line mp__path">{{ filePath }}</div>
        </div>
        <a
          :href="downloadUrl"
          :download="fileName || `media-${mediaId}`"
          class="mp__download-btn"
          title="Download original"
        >
          <i class="bi bi-download"></i>
        </a>
      </div>
    </template>

    <!-- Lightbox removed — BottomSheet fullscreen button handles that -->
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';
import { getMediaInfo, mediaContentUrl } from '@/repositories/mediaRepository';
import { formatDate } from '@/utils/Utils';
import type { MediaInfo } from '@/repositories/mediaRepository';

const props = withDefaults(defineProps<{
  mediaId: number | null;
  canGoPrev?: boolean;
  canGoNext?: boolean;
  navIndex?: number;
  navTotal?: number;
  prefetchIds?: (number | null)[];
}>(), {
  canGoPrev: false,
  canGoNext: false,
  navIndex: 0,
  navTotal: 0,
  prefetchIds: () => [],
});

const emit = defineEmits<{
  prev: [];
  next: [];
}>();

let loadToken = 0;
let crossFadeTimer: ReturnType<typeof setTimeout> | null = null;
const loading = ref(false);
const isSwapPending = ref(false);
const isCrossFading = ref(false);
const backSrc = ref<string | null>(null);
const activeMediaId = ref<number | null>(null);
const displayUrl = ref<string>('');
const info = ref<MediaInfo | null>(null);

const PREVIEW_MAX_SIZE = 4096;
const mediaUrl = computed(() => activeMediaId.value != null ? mediaContentUrl(activeMediaId.value, PREVIEW_MAX_SIZE) : '');
const downloadUrl = computed(() => activeMediaId.value != null ? mediaContentUrl(activeMediaId.value) : '');
const fileName  = computed(() => info.value?.indexedFile?.name ?? '');
const filePath  = computed(() => info.value?.indexedFile?.path ?? '');
const date      = computed(() => info.value?.exifDateImageTaken ? formatDate(new Date(info.value.exifDateImageTaken)) : '');
const camera    = computed(() => [info.value?.cameraMake, info.value?.cameraModel].filter(Boolean).join(' '));
const isVideo   = computed(() => /\.(mp4|mov|m4v|3gp|avi|mkv)$/i.test(fileName.value));
const hasActiveMedia = computed(() => activeMediaId.value != null);
const showInitialLoading = computed(() => loading.value && !hasActiveMedia.value);
const showNavigation = computed(() => props.navTotal > 1 && props.navIndex > 0);
const canGoPrev = computed(() => props.canGoPrev);
const canGoNext = computed(() => props.canGoNext);

/** Fetch image bytes and return a blob URL that is cache-independent. */
async function fetchImageBlobUrl(url: string): Promise<string> {
  const resp = await fetch(url);
  if (!resp.ok) throw new Error(`Failed to fetch image: ${resp.status}`);
  const blob = await resp.blob();
  return URL.createObjectURL(blob);
}

function revokeBlobUrl(url: string | null) {
  if (url && url.startsWith('blob:')) URL.revokeObjectURL(url);
}

const CROSSFADE_MS = 190;

function clearCrossFade() {
  if (crossFadeTimer) {
    clearTimeout(crossFadeTimer);
    crossFadeTimer = null;
  }
  isCrossFading.value = false;
  isSwapPending.value = false;
  const old = backSrc.value;
  backSrc.value = null;
  revokeBlobUrl(old);
}

async function load(id: number) {
  const token = ++loadToken;
  const nextUrl = mediaContentUrl(id, PREVIEW_MAX_SIZE);
  const hasCurrentMedia = activeMediaId.value != null;

  // Cancel any in-progress cross-fade
  clearCrossFade();

  if (hasCurrentMedia) {
    isSwapPending.value = true;
  } else {
    loading.value = true;
  }

  try {
    const infoPromise = getMediaInfo(id).catch(() => null);
    // Only blob-fetch images — videos use the direct URL via mediaUrl computed
    const nextInfo = await infoPromise;
    const nextIsVideo = nextInfo?.indexedFile?.name
      ? /\.(mp4|mov|m4v|3gp|avi|mkv)$/i.test(nextInfo.indexedFile.name)
      : false;
    const readyUrl = nextIsVideo
      ? nextUrl
      : await fetchImageBlobUrl(nextUrl).catch(() => nextUrl);

    if (token !== loadToken) {
      // Another load started — discard the blob we just fetched
      if (!nextIsVideo) revokeBlobUrl(readyUrl);
      return;
    }

    // Snapshot current display URL for the back layer
    const oldDisplayUrl = hasCurrentMedia ? displayUrl.value : null;

    // Swap data — front layer gets blob URL (instantly available, images only)
    info.value = nextInfo;
    activeMediaId.value = id;
    if (!nextIsVideo) displayUrl.value = readyUrl;

    // Start simultaneous cross-dissolve
    if (oldDisplayUrl) {
      backSrc.value = oldDisplayUrl; // old blob URL stays valid until revoked in clearCrossFade
      isCrossFading.value = true;
      await nextTick();

      // After the animation duration, clean up back layer
      crossFadeTimer = setTimeout(() => {
        if (token === loadToken) {
          clearCrossFade();
        }
      }, CROSSFADE_MS);
    }

    // Now that the current image is shown, prefetch neighbours
    prefetchNeighbors();
  } catch {
    if (token !== loadToken) return;
    info.value = null;
    activeMediaId.value = id;
    displayUrl.value = nextUrl; // fallback to direct URL
  } finally {
    if (token === loadToken) {
      loading.value = false;
      // isSwapPending is cleared by clearCrossFade() when the dissolve ends
      if (!isCrossFading.value) {
        isSwapPending.value = false;
      }
    }
  }
}

// Fire-and-forget: just set .src so the browser fetches & HTTP-caches it
function warmCache(url: string) {
  new Image().src = url;
}

function prefetchNeighbors() {
  for (const id of (props.prefetchIds ?? [])) {
    if (id != null && id !== activeMediaId.value) {
      warmCache(mediaContentUrl(id, PREVIEW_MAX_SIZE));
    }
  }
}

watch(() => props.mediaId, (id) => {
  if (id == null) {
    // Preview closed — reset so stale image doesn't flash on next open
    clearCrossFade();
    revokeBlobUrl(displayUrl.value);
    displayUrl.value = '';
    activeMediaId.value = null;
    info.value = null;
    loading.value = false;
    return;
  }
  if (id === activeMediaId.value) return;
  void load(id);
}, { immediate: true });

// Independent watcher: warm the browser cache for neighbours whenever they change
watch(() => props.prefetchIds, (ids) => {
  for (const id of (ids ?? [])) {
    if (id != null && id !== activeMediaId.value) {
      warmCache(mediaContentUrl(id, PREVIEW_MAX_SIZE));
    }
  }
});
</script>

<style scoped>
/* ── Container — slot root, MUST propagate height constraint from BottomSheet ── */
.mp {
  display: flex;
  flex-direction: column;
  width: 100%;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}

/* ── Loading ── */
.mp__loading {
  padding: 32px;
  text-align: center;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}

/* ── Media ── */
.mp__media-wrap {
  position: relative;
  background: var(--surface-glass-heavy);
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px 12px 72px;
}

.mp__media-wrap--pending::after {
  content: '';
  position: absolute;
  inset: 12px 12px 72px;
  background: transparent;
  opacity: 0;
  pointer-events: none;
}

:global([data-theme="dark"] .mp__media-wrap) {
  background: var(--surface-glass-heavy);
}

:global([data-theme="dark"] .mp__media-wrap--pending::after) {
  background: transparent;
}

.mp__media {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  display: block;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.18);
}

.mp__media--image,
.mp__media--video {
  position: relative;
  z-index: 1;
}

/* Back layer: old image dissolving out */
.mp__media--back {
  position: absolute;
  max-width: calc(100% - 24px);
  max-height: calc(100% - 84px);
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
  .mp__nav-btn,
  .mp__download-btn {
    animation: none !important;
    transition: none !important;
  }
}

.mp__loading-rail {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 72px;
  height: 2px;
  overflow: hidden;
  background: color-mix(in srgb, var(--text-muted) 10%, transparent);
  z-index: 1;
  pointer-events: none;
}

.mp__loading-rail-bar {
  position: absolute;
  inset: 0;
  width: 42%;
  border-radius: inherit;
  background: linear-gradient(90deg,
    color-mix(in srgb, var(--accent) 0%, transparent) 0%,
    color-mix(in srgb, var(--accent) 68%, white) 45%,
    color-mix(in srgb, var(--accent) 0%, transparent) 100%);
  animation: mp-loading-rail-slide 980ms cubic-bezier(0.4, 0, 0.2, 1) infinite;
}

:global([data-theme="dark"] .mp__loading-rail) {
  background: color-mix(in srgb, white 12%, transparent);
}

.mp__nav-dock {
  position: absolute;
  left: 50%;
  bottom: 14px;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0;
  z-index: 1;
}

.mp__nav-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--border-medium);
  border-radius: 999px;
  background: var(--surface-hover);
  color: var(--text-muted);
  font-size: var(--text-base-size);
  cursor: pointer;
  padding: 0;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.mp__nav-btn:not(:disabled):hover {
  color: var(--text-primary);
  background: var(--surface-active);
  border-color: var(--border-hover);
}

.mp__nav-btn:disabled {
  opacity: 0.3;
  cursor: default;
}

.mp__nav-counter {
  min-width: 48px;
  padding: 0 6px;
  text-align: center;
  font-size: var(--text-xs-size);
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0.02em;
}

/* ── Metadata bar ── */
.mp__meta {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px 10px 16px;
}

.mp__meta-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mp__filename {
  font-size: var(--text-base-size);
  font-weight: 600;
  color: var(--text-primary);
  word-break: break-word;
}

.mp__meta-line {
  font-size: var(--text-sm-size);
  color: var(--text-muted);
  line-height: var(--text-sm-lh);
}

.mp__path {
  font-size: var(--text-xs-size);
  word-break: break-all;
  color: var(--text-faint);
}

/* ── Download button in meta bar ── */
.mp__download-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: var(--surface-elevated);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: var(--text-base-size);
  text-decoration: none;
  transition: background 0.15s;
}

.mp__download-btn:active {
  background: var(--surface-active);
}

@media (max-width: 640px) {
  .mp__media-wrap {
    padding-bottom: 68px;
  }

  .mp__nav-dock {
    bottom: 10px;
    width: min(calc(100% - 20px), 220px);
    justify-content: center;
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
