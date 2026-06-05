import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { getMediaInfo, mediaContentUrl } from '@/repositories/mediaRepository';
import { formatDate } from '@/utils/Utils';
import type { MediaInfo } from '@/repositories/mediaRepository';

export const PREVIEW_MAX_SIZE = 4096;
export const CROSSFADE_MS = 190;

export interface MediaPreviewProps {
  mediaId: number | null;
  canGoPrev?: boolean;
  canGoNext?: boolean;
  navIndex?: number;
  navTotal?: number;
  prefetchIds?: (number | null)[];
}

export function useMediaPreview(props: MediaPreviewProps) {
  let loadToken = 0;
  let crossFadeTimer: ReturnType<typeof setTimeout> | null = null;
  const loading = ref(false);
  const isSwapPending = ref(false);
  const isCrossFading = ref(false);
  const backSrc = ref<string | null>(null);
  const activeMediaId = ref<number | null>(null);
  const displayUrl = ref<string>('');
  const info = ref<MediaInfo | null>(null);
  const loadError = ref('');

  const mediaUrl = computed(() =>
    activeMediaId.value != null ? mediaContentUrl(activeMediaId.value, PREVIEW_MAX_SIZE) : ''
  );
  const downloadUrl = computed(() => (activeMediaId.value != null ? mediaContentUrl(activeMediaId.value) : ''));
  const fileName = computed(() => info.value?.indexedFile?.name ?? '');
  const filePath = computed(() => info.value?.indexedFile?.path ?? '');
  const date = computed(() =>
    info.value?.exifDateImageTaken ? formatDate(new Date(info.value.exifDateImageTaken)) : ''
  );
  const camera = computed(() => [info.value?.cameraMake, info.value?.cameraModel].filter(Boolean).join(' '));
  const isVideo = computed(() => isVideoFileName(fileName.value));
  const hasActiveMedia = computed(() => activeMediaId.value != null);
  const showInitialLoading = computed(() => loading.value && !hasActiveMedia.value);
  const showNavigation = computed(() => (props.navTotal ?? 0) > 1 && (props.navIndex ?? 0) > 0);
  const canGoPrev = computed(() => props.canGoPrev ?? false);
  const canGoNext = computed(() => props.canGoNext ?? false);
  const hasLoadError = computed(() => loadError.value.length > 0);

  /** Fetch image bytes and return a blob URL that is cache-independent. */
  async function fetchImageBlobUrl(url: string): Promise<string> {
    const resp = await fetch(url);
    if (!resp.ok) throw new Error(`Failed to fetch image: ${resp.status}`);
    const blob = await resp.blob();
    if (blob.size === 0) throw new Error('Image response was empty');
    return URL.createObjectURL(blob);
  }

  function revokeBlobUrl(url: string | null) {
    if (url && url.startsWith('blob:')) URL.revokeObjectURL(url);
  }

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
    let nextInfo: MediaInfo | null = null;

    clearCrossFade();
    loadError.value = '';

    if (hasCurrentMedia) {
      isSwapPending.value = true;
    } else {
      loading.value = true;
    }

    try {
      nextInfo = await getMediaInfo(id).catch(() => null);
      const nextIsVideo = nextInfo?.indexedFile?.name ? isVideoFileName(nextInfo.indexedFile.name) : false;
      const readyUrl = nextIsVideo ? nextUrl : await fetchImageBlobUrl(nextUrl).catch(() => nextUrl);

      if (token !== loadToken) {
        if (!nextIsVideo) revokeBlobUrl(readyUrl);
        return;
      }

      const oldDisplayUrl = hasCurrentMedia ? displayUrl.value : null;

      info.value = nextInfo;
      activeMediaId.value = id;
      displayUrl.value = nextIsVideo ? '' : readyUrl;
      loadError.value = '';

      if (oldDisplayUrl) {
        backSrc.value = oldDisplayUrl;
        isCrossFading.value = true;
        await nextTick();

        crossFadeTimer = setTimeout(() => {
          if (token === loadToken) {
            clearCrossFade();
          }
        }, CROSSFADE_MS);
      }

      prefetchNeighbors();
    } catch (error) {
      if (token !== loadToken) return;
      info.value = nextInfo;
      activeMediaId.value = id;
      displayUrl.value = '';
      loadError.value = previewErrorMessage(error);
    } finally {
      if (token === loadToken) {
        loading.value = false;
        if (!isCrossFading.value) {
          isSwapPending.value = false;
        }
      }
    }
  }

  function warmCache(url: string) {
    new Image().src = url;
  }

  function prefetchNeighbors() {
    for (const id of props.prefetchIds ?? []) {
      if (id != null && id !== activeMediaId.value) {
        warmCache(mediaContentUrl(id, PREVIEW_MAX_SIZE));
      }
    }
  }

  function reset() {
    clearCrossFade();
    revokeBlobUrl(displayUrl.value);
    displayUrl.value = '';
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

  watch(
    () => props.prefetchIds,
    (ids) => {
      for (const id of ids ?? []) {
        if (id != null && id !== activeMediaId.value) {
          warmCache(mediaContentUrl(id, PREVIEW_MAX_SIZE));
        }
      }
    }
  );

  onBeforeUnmount(reset);

  return {
    loading,
    isSwapPending,
    isCrossFading,
    backSrc,
    activeMediaId,
    displayUrl,
    mediaUrl,
    downloadUrl,
    fileName,
    filePath,
    date,
    camera,
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

function isVideoFileName(fileName: string): boolean {
  return /\.(mp4|mov|m4v|3gp|avi|mkv)$/i.test(fileName);
}

function previewErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message;
  return 'The media file could not be loaded.';
}
