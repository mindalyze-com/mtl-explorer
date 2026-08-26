import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { getMediaInfo, mediaContentUrl } from '@/repositories/mediaRepository';
import { formatDateAndTime } from '@/utils/Utils';
import type { MediaInfo } from '@/repositories/mediaRepository';
import { useAsyncState } from '@/composables/useAsyncState';
import { isAbortLikeError } from '@/utils/errors';
import { isVideoMedia } from '@/utils/mediaKind';
import {
  formatMediaAltitude,
  formatMediaCodecs,
  formatMediaDimensions,
  formatMediaFileSummary,
  formatMediaModified,
  formatPhotoExposure,
  formatVideoDetails,
} from '@/utils/mediaDetails';

export const INITIAL_PREVIEW_MAX_SIZE = 2048;
export const FULL_PREVIEW_MAX_SIZE = 4096;
export const VIDEO_POSTER_MAX_SIZE = 1280;
export const HIGH_RESOLUTION_IDLE_DELAY_MS = 750;
export const NEXT_PREVIEW_PREFETCH_IDLE_DELAY_MS = 1500;
export const NEXT_FULL_RESOLUTION_PREFETCH_IDLE_DELAY_MS = 1500;
export const MAX_DECODED_PREVIEW_CACHE_ENTRIES = 3;
export const CROSSFADE_MS = 190;
const SECONDS_PER_HOUR = 3_600;

export interface MediaPreviewProps {
  mediaId: number | null;
  canGoPrev?: boolean;
  canGoNext?: boolean;
  navIndex?: number;
  navTotal?: number;
  prefetchIds?: readonly (number | null)[];
  takenAt?: Date | string | null;
  timeSource?: string | null;
  appliedCameraOffsetSeconds?: number | null;
}

type DecodedPreview = {
  url: string;
  maxSize: number;
};

type PreviewCacheEntry = {
  id: number;
  controller: AbortController;
  info: MediaInfo | null | undefined;
  infoPromise: Promise<MediaInfo | null> | null;
  initial: DecodedPreview | null;
  initialPromise: Promise<DecodedPreview> | null;
  full: DecodedPreview | null;
  fullPromise: Promise<DecodedPreview> | null;
  lastUsed: number;
};

export function useMediaPreview(props: MediaPreviewProps) {
  let loadToken = 0;
  let cacheUseSequence = 0;
  let prefetchGeneration = 0;
  let crossFadeTimer: ReturnType<typeof setTimeout> | null = null;
  let crossFadeCompletion: Promise<void> | null = null;
  let resolveCrossFade: (() => void) | null = null;
  let highResolutionTimer: ReturnType<typeof setTimeout> | null = null;
  let nextPreviewPrefetchTimer: ReturnType<typeof setTimeout> | null = null;
  let prefetchDelayAbortController: AbortController | null = null;
  const activeLoadTokens = new Map<number, number>();
  const previewCache = new Map<number, PreviewCacheEntry>();
  const { loading, error: loadError } = useAsyncState('');
  const isSwapPending = ref(false);
  const isCrossFading = ref(false);
  const isHighResolutionPending = ref(false);
  const backSrc = ref<string | null>(null);
  const activeMediaId = ref<number | null>(null);
  const displayUrl = ref<string>('');
  const info = ref<MediaInfo | null>(null);

  const mediaUrl = computed(() => (activeMediaId.value != null ? mediaContentUrl(activeMediaId.value) : ''));
  const downloadUrl = computed(() => (activeMediaId.value != null ? mediaContentUrl(activeMediaId.value) : ''));
  const fileName = computed(() => info.value?.fileName ?? '');
  const filePath = computed(() => info.value?.folderPath ?? '');
  const date = computed(() => {
    const takenAt = props.takenAt ?? info.value?.exifGpsDate ?? info.value?.exifDateImageTaken;
    return takenAt ? formatDateAndTime(takenAt) : '';
  });
  const captureTimeSource = computed(() => {
    const source = props.timeSource;
    if (source === 'EXIF_GPS' || (!source && info.value?.exifGpsDate)) return 'Embedded GPS time';
    if (source === 'EXIF_DATE_TAKEN' || (!source && info.value?.exifDateImageTaken)) {
      const offset = formatCameraOffset(props.appliedCameraOffsetSeconds);
      return offset ? `Camera clock · ${offset} correction` : 'Camera clock';
    }
    return '';
  });
  const camera = computed(() => [info.value?.cameraMake, info.value?.cameraModel].filter(Boolean).join(' '));
  const fileSummary = computed(() => formatMediaFileSummary(info.value));
  const dimensions = computed(() => formatMediaDimensions(info.value));
  const exposure = computed(() => formatPhotoExposure(info.value));
  const lens = computed(() => info.value?.lensModel ?? '');
  const videoDetails = computed(() => formatVideoDetails(info.value));
  const codecs = computed(() => formatMediaCodecs(info.value));
  const modified = computed(() => formatMediaModified(info.value));
  const gpsAltitude = computed(() => formatMediaAltitude(info.value));
  const isVideo = computed(() => info.value?.mediaKind === 'VIDEO' || isVideoMedia(fileName.value));
  const posterUrl = computed(() =>
    activeMediaId.value != null && isVideo.value
      ? mediaContentUrl(activeMediaId.value, VIDEO_POSTER_MAX_SIZE)
      : undefined
  );
  const hasActiveMedia = computed(() => activeMediaId.value != null);
  const showInitialLoading = computed(() => loading.value && !hasActiveMedia.value);
  const showNavigation = computed(() => (props.navTotal ?? 0) > 1 && (props.navIndex ?? 0) > 0);
  const canGoPrev = computed(() => props.canGoPrev ?? false);
  const canGoNext = computed(() => props.canGoNext ?? false);
  const hasLoadError = computed(() => loadError.value.length > 0);

  async function fetchDecodedImageBlobUrl(url: string, signal: AbortSignal): Promise<string> {
    const response = await fetch(url, { signal });
    if (!response.ok) throw new Error(`Failed to fetch image: ${response.status}`);
    const blob = await response.blob();
    if (blob.size === 0) throw new Error('Image response was empty');
    const objectUrl = URL.createObjectURL(blob);

    try {
      await decodeImageUrl(objectUrl, signal);
      return objectUrl;
    } catch (error) {
      URL.revokeObjectURL(objectUrl);
      throw error;
    }
  }

  async function decodeImageUrl(url: string, signal: AbortSignal): Promise<void> {
    if (signal.aborted) throw abortError();
    const image = new Image();
    image.decoding = 'async';
    image.src = url;
    if (typeof image.decode !== 'function') return;
    await waitForPromiseOrAbort(image.decode(), signal);
  }

  function waitForPromiseOrAbort<T>(promise: Promise<T>, signal: AbortSignal): Promise<T> {
    if (signal.aborted) return Promise.reject(abortError());
    return new Promise<T>((resolve, reject) => {
      const onAbort = () => reject(abortError());
      signal.addEventListener('abort', onAbort, { once: true });
      promise.then(
        (value) => {
          signal.removeEventListener('abort', onAbort);
          resolve(value);
        },
        (error) => {
          signal.removeEventListener('abort', onAbort);
          reject(error);
        }
      );
    });
  }

  function revokeBlobUrl(url: string | null) {
    if (url?.startsWith('blob:')) URL.revokeObjectURL(url);
  }

  function touchEntry(entry: PreviewCacheEntry) {
    entry.lastUsed = ++cacheUseSequence;
  }

  function getOrCreateEntry(id: number): PreviewCacheEntry {
    const cached = previewCache.get(id);
    if (cached) {
      touchEntry(cached);
      return cached;
    }

    const entry: PreviewCacheEntry = {
      id,
      controller: new AbortController(),
      info: undefined,
      infoPromise: null,
      initial: null,
      initialPromise: null,
      full: null,
      fullPromise: null,
      lastUsed: ++cacheUseSequence,
    };
    previewCache.set(id, entry);
    trimPreviewCache();
    return entry;
  }

  function disposeEntry(entry: PreviewCacheEntry) {
    entry.controller.abort();
    revokeBlobUrl(entry.initial?.url ?? null);
    if (entry.full?.url !== entry.initial?.url) revokeBlobUrl(entry.full?.url ?? null);
    previewCache.delete(entry.id);
  }

  function entryIsDisplayed(entry: PreviewCacheEntry): boolean {
    const urls = [entry.initial?.url, entry.full?.url];
    return urls.includes(displayUrl.value) || urls.includes(backSrc.value ?? undefined);
  }

  function trimPreviewCache() {
    while (previewCache.size > MAX_DECODED_PREVIEW_CACHE_ENTRIES) {
      const candidate = [...previewCache.values()]
        .filter((entry) => !activeLoadTokens.has(entry.id) && !entryIsDisplayed(entry))
        .sort((left, right) => left.lastUsed - right.lastUsed)[0];
      if (!candidate) return;
      disposeEntry(candidate);
    }
  }

  function cancelPendingEntryRequests(entry: PreviewCacheEntry) {
    if (!entry.infoPromise && !entry.initialPromise && !entry.fullPromise) return;
    entry.controller.abort();
    entry.controller = new AbortController();
    entry.infoPromise = null;
    entry.initialPromise = null;
    entry.fullPromise = null;
  }

  function cancelSupersededRequests(activeId: number) {
    for (const entry of previewCache.values()) {
      if (entry.id !== activeId) cancelPendingEntryRequests(entry);
    }
  }

  function clearPreviewCache() {
    for (const entry of [...previewCache.values()]) disposeEntry(entry);
  }

  function discardInitialWhenUnused(entry: PreviewCacheEntry) {
    const initialUrl = entry.initial?.url;
    if (!initialUrl || !entry.full || initialUrl === displayUrl.value || initialUrl === backSrc.value) return;
    entry.initial = null;
    revokeBlobUrl(initialUrl);
  }

  async function ensureMediaInfo(entry: PreviewCacheEntry): Promise<MediaInfo | null> {
    touchEntry(entry);
    if (entry.info !== undefined) return entry.info;
    if (entry.infoPromise) return entry.infoPromise;

    const controller = entry.controller;
    const request = getMediaInfo(entry.id, controller.signal).catch((error) => {
      if (isAbortLikeError(error, controller.signal)) throw error;
      return null;
    });
    entry.infoPromise = request;

    try {
      const loadedInfo = await request;
      if (controller !== entry.controller || controller.signal.aborted) throw abortError();
      entry.info = loadedInfo;
      return entry.info;
    } finally {
      if (entry.infoPromise === request) entry.infoPromise = null;
    }
  }

  async function loadDecodedPreview(entry: PreviewCacheEntry, maxSize: number): Promise<DecodedPreview> {
    const controller = entry.controller;
    const url = await fetchDecodedImageBlobUrl(mediaContentUrl(entry.id, maxSize), controller.signal);
    if (controller !== entry.controller || controller.signal.aborted || previewCache.get(entry.id) !== entry) {
      revokeBlobUrl(url);
      throw abortError();
    }
    touchEntry(entry);
    return { url, maxSize };
  }

  async function ensureInitialPreview(entry: PreviewCacheEntry): Promise<DecodedPreview> {
    touchEntry(entry);
    if (entry.initial) return entry.initial;
    if (entry.initialPromise) return entry.initialPromise;

    const request = loadDecodedPreview(entry, INITIAL_PREVIEW_MAX_SIZE);
    entry.initialPromise = request;
    try {
      entry.initial = await request;
      return entry.initial;
    } finally {
      if (entry.initialPromise === request) entry.initialPromise = null;
    }
  }

  async function ensureFullPreview(entry: PreviewCacheEntry): Promise<DecodedPreview> {
    touchEntry(entry);
    if (entry.full) return entry.full;
    if (entry.fullPromise) return entry.fullPromise;

    const request = loadDecodedPreview(entry, FULL_PREVIEW_MAX_SIZE);
    entry.fullPromise = request;
    try {
      entry.full = await request;
      return entry.full;
    } finally {
      if (entry.fullPromise === request) entry.fullPromise = null;
    }
  }

  function clearCrossFade() {
    if (crossFadeTimer) {
      clearTimeout(crossFadeTimer);
      crossFadeTimer = null;
    }
    isCrossFading.value = false;
    isSwapPending.value = false;
    backSrc.value = null;
    const resolve = resolveCrossFade;
    resolveCrossFade = null;
    crossFadeCompletion = null;
    resolve?.();
    for (const entry of previewCache.values()) discardInitialWhenUnused(entry);
    trimPreviewCache();
  }

  function cancelScheduledWork() {
    if (highResolutionTimer) {
      clearTimeout(highResolutionTimer);
      highResolutionTimer = null;
    }
    if (nextPreviewPrefetchTimer) {
      clearTimeout(nextPreviewPrefetchTimer);
      nextPreviewPrefetchTimer = null;
    }
    prefetchDelayAbortController?.abort();
    prefetchDelayAbortController = null;
    prefetchGeneration++;
    isHighResolutionPending.value = false;
  }

  async function replaceDisplayedImage(readyUrl: string, token: number, crossFade: boolean) {
    if (!crossFade) {
      const pendingTransition = crossFadeCompletion;
      if (pendingTransition) await pendingTransition;
      if (token !== loadToken) return;
      displayUrl.value = readyUrl;
      isSwapPending.value = false;
      return;
    }

    const oldDisplayUrl = displayUrl.value || null;
    displayUrl.value = readyUrl;
    if (!oldDisplayUrl || oldDisplayUrl === readyUrl) {
      isSwapPending.value = false;
      return;
    }

    backSrc.value = oldDisplayUrl;
    isCrossFading.value = true;
    crossFadeCompletion = new Promise<void>((resolve) => {
      resolveCrossFade = resolve;
    });
    await nextTick();

    crossFadeTimer = setTimeout(() => {
      if (token === loadToken) clearCrossFade();
    }, CROSSFADE_MS);
  }

  function scheduleHighResolutionLoad(entry: PreviewCacheEntry, token: number) {
    highResolutionTimer = setTimeout(() => {
      highResolutionTimer = null;
      void loadHighResolution(entry, token);
    }, HIGH_RESOLUTION_IDLE_DELAY_MS);
  }

  async function loadHighResolution(entry: PreviewCacheEntry, token: number) {
    if (token === loadToken) isHighResolutionPending.value = true;
    try {
      const ready = await ensureFullPreview(entry);
      if (token !== loadToken || entry.controller.signal.aborted) return;
      await replaceDisplayedImage(ready.url, token, false);
      discardInitialWhenUnused(entry);
    } catch (error) {
      if (isAbortLikeError(error, entry.controller.signal)) return;
      // Keep the usable initial preview when the optional full-resolution request fails.
    } finally {
      if (token === loadToken) {
        isHighResolutionPending.value = false;
        scheduleNeighborPrefetch(token);
      }
    }
  }

  function normalizedPrefetchIds(): number[] {
    return [
      ...new Set((props.prefetchIds ?? []).filter((id): id is number => id != null && id !== activeMediaId.value)),
    ];
  }

  function scheduleNeighborPrefetch(token: number) {
    const ids = normalizedPrefetchIds();
    if (ids.length === 0) return;
    const generation = ++prefetchGeneration;
    nextPreviewPrefetchTimer = setTimeout(() => {
      nextPreviewPrefetchTimer = null;
      if (token !== loadToken || generation !== prefetchGeneration) return;
      void prefetchNeighbors(ids, generation);
    }, NEXT_PREVIEW_PREFETCH_IDLE_DELAY_MS);
  }

  async function prefetchNeighbors(ids: number[], generation: number) {
    for (const id of ids) {
      if (generation !== prefetchGeneration) return;
      const entry = await prefetchInitialPreview(id);
      if (!entry?.initial || generation !== prefetchGeneration) continue;

      const delayController = new AbortController();
      prefetchDelayAbortController = delayController;
      try {
        await waitForAbortableDelay(NEXT_FULL_RESOLUTION_PREFETCH_IDLE_DELAY_MS, delayController.signal);
      } catch (error) {
        if (isAbortLikeError(error, delayController.signal)) return;
        throw error;
      } finally {
        if (prefetchDelayAbortController === delayController) prefetchDelayAbortController = null;
      }
      if (generation !== prefetchGeneration) return;

      try {
        await ensureFullPreview(entry);
        discardInitialWhenUnused(entry);
        trimPreviewCache();
      } catch (error) {
        if (!isAbortLikeError(error, entry.controller.signal)) {
          // Prefetch failures do not affect the displayed photo.
        }
      }
    }
  }

  async function prefetchInitialPreview(id: number): Promise<PreviewCacheEntry | null> {
    const entry = getOrCreateEntry(id);
    try {
      const nextInfo = await ensureMediaInfo(entry);
      if (!nextInfo?.fileName || nextInfo.mediaKind === 'VIDEO' || isVideoMedia(nextInfo.fileName)) return null;
      await ensureInitialPreview(entry);
      return entry;
    } catch (error) {
      if (!isAbortLikeError(error, entry.controller.signal)) {
        // Prefetch failures do not affect the displayed photo.
      }
      return null;
    }
  }

  function waitForAbortableDelay(delayMs: number, signal: AbortSignal): Promise<void> {
    if (signal.aborted) return Promise.reject(abortError());
    return new Promise((resolve, reject) => {
      const onAbort = () => {
        clearTimeout(timer);
        reject(abortError());
      };
      const timer = setTimeout(() => {
        signal.removeEventListener('abort', onAbort);
        resolve();
      }, delayMs);
      signal.addEventListener('abort', onAbort, { once: true });
    });
  }

  async function load(id: number) {
    const token = ++loadToken;
    const hasCurrentMedia = activeMediaId.value != null;
    let nextInfo: MediaInfo | null = null;
    cancelSupersededRequests(id);
    const entry = getOrCreateEntry(id);
    activeLoadTokens.set(id, token);

    cancelScheduledWork();
    clearCrossFade();
    loadError.value = '';

    if (hasCurrentMedia) {
      isSwapPending.value = true;
    } else {
      loading.value = true;
    }

    try {
      nextInfo = await ensureMediaInfo(entry);
      const nextIsVideo = nextInfo?.mediaKind === 'VIDEO' || isVideoMedia(nextInfo?.fileName);
      if (token !== loadToken || entry.controller.signal.aborted) return;

      if (nextIsVideo) {
        info.value = nextInfo;
        activeMediaId.value = id;
        displayUrl.value = '';
        scheduleNeighborPrefetch(token);
        return;
      }

      const ready = entry.full ?? entry.initial ?? (await ensureInitialPreview(entry));
      if (token !== loadToken || entry.controller.signal.aborted) return;

      info.value = nextInfo;
      activeMediaId.value = id;
      loadError.value = '';
      await replaceDisplayedImage(ready.url, token, hasCurrentMedia);

      if (entry.full) {
        discardInitialWhenUnused(entry);
        scheduleNeighborPrefetch(token);
      } else if (entry.fullPromise) {
        void loadHighResolution(entry, token);
      } else {
        scheduleHighResolutionLoad(entry, token);
      }
    } catch (error) {
      if (token !== loadToken || isAbortLikeError(error, entry.controller.signal)) return;
      info.value = nextInfo;
      activeMediaId.value = id;
      displayUrl.value = '';
      loadError.value = previewErrorMessage(error);
    } finally {
      if (activeLoadTokens.get(id) === token) activeLoadTokens.delete(id);
      trimPreviewCache();
      if (token === loadToken) {
        loading.value = false;
        if (!isCrossFading.value) isSwapPending.value = false;
      }
    }
  }

  function reset() {
    loadToken++;
    cancelScheduledWork();
    clearCrossFade();
    displayUrl.value = '';
    backSrc.value = null;
    clearPreviewCache();
    activeLoadTokens.clear();
    activeMediaId.value = null;
    info.value = null;
    loadError.value = '';
    loading.value = false;
  }

  function retryLoad() {
    const id = activeMediaId.value ?? props.mediaId;
    if (id == null) return;
    void load(id);
  }

  function onMediaError() {
    if (activeMediaId.value == null || loadError.value) return;
    loadError.value = 'The media file could not be decoded by the browser.';
  }

  watch(
    () => props.mediaId,
    (id) => {
      if (id == null) {
        reset();
        return;
      }
      if (id === activeMediaId.value) return;
      void load(id);
    },
    { immediate: true }
  );

  onBeforeUnmount(reset);

  return {
    loading,
    isSwapPending,
    isCrossFading,
    isHighResolutionPending,
    backSrc,
    activeMediaId,
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
  };
}

function abortError(): DOMException {
  return new DOMException('Aborted', 'AbortError');
}

function formatCameraOffset(offsetSeconds: number | null | undefined): string {
  if (offsetSeconds == null || !Number.isFinite(offsetSeconds) || offsetSeconds === 0) return '';
  const sign = offsetSeconds > 0 ? '+' : '−';
  const absoluteSeconds = Math.abs(Math.round(offsetSeconds));
  const hours = absoluteSeconds / SECONDS_PER_HOUR;
  const value = Number.isInteger(hours) ? hours.toFixed(0) : hours.toFixed(2).replace(/\.?0+$/, '');
  return `${sign}${value}h`;
}

function previewErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message;
  return 'The media file could not be loaded.';
}
