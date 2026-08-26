<template>
  <BottomSheet
    v-model="visible"
    :detents="mosaicDetents"
    initial-detent="large"
    :z-index="5400"
    :title="sheetTitle"
    icon="bi bi-images"
    @closed="onMosaicClosed"
  >
    <div class="media-mosaic">
      <div class="media-mosaic__toolbar">
        <div class="media-mosaic__filters" aria-label="Media kind">
          <button
            v-for="option in kindOptions"
            :key="option.value"
            type="button"
            class="media-mosaic__chip"
            :class="{ 'media-mosaic__chip--active': selectedKind === option.value }"
            :aria-pressed="selectedKind === option.value"
            @click="setKind(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
        <span v-if="!loading" class="media-mosaic__count">{{ itemCountLabel(totalItems) }}</span>
      </div>

      <div v-if="loading && items.length === 0" class="media-mosaic__state" role="status">
        <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
        <span>Loading media…</span>
      </div>
      <div
        v-else-if="loadError && items.length === 0"
        class="media-mosaic__state media-mosaic__state--error"
        role="alert"
      >
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <span>Media could not be loaded.</span>
        <button type="button" @click="loadFirstPage">Retry</button>
      </div>
      <div v-else-if="items.length === 0" class="media-mosaic__state">
        <i class="bi bi-images" aria-hidden="true"></i>
        <span>No media in this selection.</span>
      </div>

      <div v-else class="media-mosaic__grid" data-test="media-trend-mosaic-grid">
        <article v-for="item in items" :key="item.id" class="media-mosaic-card">
          <button type="button" class="media-mosaic-card__preview" @click="openViewer(item.id)">
            <span class="media-mosaic-card__image-wrap">
              <img
                v-if="!viewerVisible"
                :src="mediaContentUrl(item.id, MOSAIC_THUMBNAIL_SIZE)"
                :alt="item.fileName || 'Media preview'"
                loading="lazy"
                fetchpriority="low"
              />
              <span v-if="item.mediaKind === 'VIDEO'" class="media-mosaic-card__video" aria-label="Video">
                <i class="bi bi-play-fill" aria-hidden="true"></i>
              </span>
            </span>
            <span class="media-mosaic-card__text">
              <strong>{{ item.fileName || `Media ${item.id}` }}</strong>
              <span>{{ captureTimeLabel(item.effectiveCapturedAt) }}</span>
            </span>
          </button>
          <button
            v-if="scope === 'MATCHED_ACTIVITIES' && item.trackId != null"
            type="button"
            class="media-mosaic-card__activity"
            :aria-label="`Open activity ${item.trackId} at Photos`"
            @click="openActivity(item.trackId)"
          >
            <i class="bi bi-signpost-split" aria-hidden="true"></i>
            Open activity
          </button>
        </article>
      </div>

      <div v-if="items.length > 0 && (loadError || hasMore)" class="media-mosaic__more">
        <span v-if="loadError" role="alert">More media could not be loaded.</span>
        <button v-if="hasMore" type="button" :disabled="loading" data-test="media-mosaic-load-more" @click="loadMore">
          <i v-if="loading" class="pi pi-spin pi-spinner" aria-hidden="true"></i>
          {{ loading ? 'Loading…' : `Load more (${items.length.toLocaleString()} of ${totalItems.toLocaleString()})` }}
        </button>
      </div>
    </div>
  </BottomSheet>

  <BottomSheet
    v-model="viewerVisible"
    :detents="viewerDetents"
    initial-detent="large"
    :z-index="5500"
    :title="selectedItem?.mediaKind === 'VIDEO' ? 'Video' : 'Photo'"
    :icon="selectedItem?.mediaKind === 'VIDEO' ? 'bi bi-camera-video' : 'bi bi-image'"
    header-mode="compact"
    native-fullscreen
    viewport-centered
    :sheet-class="['media-viewer-sheet', mediaViewerThemeClass]"
    @closed="viewerVisible = false"
  >
    <template #header-actions>
      <button
        type="button"
        class="media-preview-details-toggle"
        :aria-pressed="viewerDetailsVisible"
        @click.stop="viewerDetailsVisible = !viewerDetailsVisible"
      >
        <i class="bi bi-info-circle" aria-hidden="true"></i>
        <span>Details</span>
      </button>
      <MediaViewerThemeToggle />
    </template>
    <MediaPreview
      :media-id="viewerVisible ? selectedMediaId : null"
      :can-go-prev="previousMediaId != null"
      :can-go-next="nextMediaId != null"
      :nav-index="selectedIndex >= 0 ? selectedIndex + 1 : 0"
      :nav-total="items.length"
      :prefetch-ids="[nextMediaId, previousMediaId]"
      :media-ids="mediaIds"
      :video-media-ids="videoMediaIds"
      :position-source="selectedItem?.positionOrigin"
      :position-estimated="selectedItem?.estimatedPosition ?? false"
      :position-ambiguous="selectedItem?.ambiguousMatch ?? false"
      :position-unknown="selectedItem != null && selectedItem.positionOrigin == null"
      :position-time-delta-seconds="selectedItem?.trackPointTimeDeltaSeconds"
      :position-lat="selectedItem?.resolvedLat"
      :position-lng="selectedItem?.resolvedLng"
      :details-visible="viewerDetailsVisible"
      :taken-at="selectedItem?.effectiveCapturedAt"
      :time-source="selectedItem?.timeSource"
      :applied-camera-offset-seconds="selectedItem?.appliedCameraOffsetSeconds"
      @prev="navigateViewer(previousMediaId)"
      @next="navigateViewer(nextMediaId)"
      @select="navigateViewer"
      @update:details-visible="viewerDetailsVisible = $event"
      @open-on-map="openSelectedMediaOnMap"
      @time-correction-cleared="onViewerTimeCorrectionCleared"
    />
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import {
  MediaTrendItemsRequestKindEnum,
  type MediaTrendBucketDto,
  type MediaTrendItemDto,
  type MediaTrendItemsRequestGroupingEnum,
  type MediaTrendItemsRequestScopeEnum,
} from 'x8ing-mtl-api-typescript-fetch';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import MediaPreview from '@/components/map/MediaPreview.vue';
import MediaViewerThemeToggle from '@/components/map/MediaViewerThemeToggle.vue';
import { useMediaViewerTheme } from '@/composables/useMediaViewerTheme';
import { getMediaTrendItems, mediaContentUrl } from '@/repositories/mediaRepository';
import { isAbortLikeError } from '@/utils/errors';
import { formatDateAndTime } from '@/utils/Utils';

const MOSAIC_PAGE_SIZE = 60;
const MEDIA_PREVIEW_DESKTOP_MIN_WIDTH = 769;
const MOSAIC_THUMBNAIL_SIZE = 720;
type MosaicItem = MediaTrendItemDto & { id: number };

const props = defineProps<{
  bucket: MediaTrendBucketDto | null;
  grouping: MediaTrendItemsRequestGroupingEnum;
  scope: MediaTrendItemsRequestScopeEnum;
  trackIds: number[];
}>();

const emit = defineEmits<{
  'open-activity': [trackId: number];
  'open-media-on-map': [target: { id: number; lat: number; lng: number }];
}>();

const visible = defineModel<boolean>({ default: false });
const selectedKind = ref<MediaTrendItemsRequestKindEnum>(MediaTrendItemsRequestKindEnum.All);
const items = ref<MosaicItem[]>([]);
const totalItems = ref(0);
const nextPage = ref(0);
const loading = ref(false);
const loadError = ref('');
const viewerVisible = ref(false);
const viewerDetailsVisible = ref(window.innerWidth >= MEDIA_PREVIEW_DESKTOP_MIN_WIDTH);
const { mediaViewerThemeClass } = useMediaViewerTheme();
const selectedMediaId = ref<number | null>(null);
let requestGeneration = 0;
let abortController: AbortController | null = null;

const mosaicDetents = [{ height: '68vh' }, { height: '92vh' }];
const viewerDetents = [{ height: '70vh' }, { height: '92vh' }];
const kindOptions: Array<{ label: string; value: MediaTrendItemsRequestKindEnum }> = [
  { label: 'All', value: MediaTrendItemsRequestKindEnum.All },
  { label: 'Photos', value: MediaTrendItemsRequestKindEnum.Image },
  { label: 'Videos', value: MediaTrendItemsRequestKindEnum.Video },
];

const sheetTitle = computed(() => {
  const label = props.bucket?.label || 'Media';
  const count = (props.bucket?.imageCount ?? 0) + (props.bucket?.videoCount ?? 0);
  return `${label} · ${itemCountLabel(count)}`;
});
const hasMore = computed(() => items.value.length < totalItems.value);
const mediaIds = computed(() => items.value.map((item) => item.id).filter((id): id is number => id != null));
const videoMediaIds = computed(() => items.value.filter((item) => item.mediaKind === 'VIDEO').map((item) => item.id));
const selectedIndex = computed(() => mediaIds.value.indexOf(selectedMediaId.value ?? -1));
const selectedItem = computed(() => items.value.find((item) => item.id === selectedMediaId.value) ?? null);
const previousMediaId = computed(() =>
  selectedIndex.value > 0 ? (mediaIds.value[selectedIndex.value - 1] ?? null) : null
);
const nextMediaId = computed(() =>
  selectedIndex.value >= 0 && selectedIndex.value < mediaIds.value.length - 1
    ? (mediaIds.value[selectedIndex.value + 1] ?? null)
    : null
);

watch(
  () => [visible.value, props.bucket?.bucketKey, props.grouping, props.scope] as const,
  ([isVisible]) => {
    if (!isVisible) return;
    selectedKind.value = MediaTrendItemsRequestKindEnum.All;
    void loadFirstPage();
  },
  { immediate: true }
);

function setKind(kind: MediaTrendItemsRequestKindEnum): void {
  if (selectedKind.value === kind) return;
  selectedKind.value = kind;
  void loadFirstPage();
}

async function loadFirstPage(): Promise<void> {
  items.value = [];
  totalItems.value = 0;
  nextPage.value = 0;
  selectedMediaId.value = null;
  viewerVisible.value = false;
  await loadPage(0);
}

async function loadMore(): Promise<void> {
  if (loading.value || !hasMore.value) return;
  await loadPage(nextPage.value);
}

async function loadPage(page: number): Promise<void> {
  const bucketKey = props.bucket?.bucketKey;
  if (!visible.value || !bucketKey) return;
  const generation = ++requestGeneration;
  abortController?.abort();
  abortController = new AbortController();
  loading.value = true;
  loadError.value = '';
  try {
    const response = await getMediaTrendItems(
      {
        grouping: props.grouping,
        scope: props.scope,
        bucketKey,
        kind: selectedKind.value,
        trackIds: props.scope === 'MATCHED_ACTIVITIES' ? props.trackIds : undefined,
        page,
        pageSize: MOSAIC_PAGE_SIZE,
      },
      abortController.signal
    );
    if (generation !== requestGeneration) return;
    const pageItems = (response.items ?? []).filter((item): item is MosaicItem => item.id != null);
    items.value = page === 0 ? pageItems : [...items.value, ...pageItems];
    totalItems.value = response.totalItems ?? items.value.length;
    nextPage.value = page + 1;
  } catch (error) {
    if (generation !== requestGeneration || isAbortLikeError(error, abortController.signal)) return;
    loadError.value = error instanceof Error ? error.message : String(error);
  } finally {
    if (generation === requestGeneration) loading.value = false;
  }
}

function openViewer(mediaId: number | undefined): void {
  if (mediaId == null) return;
  selectedMediaId.value = mediaId;
  viewerDetailsVisible.value = window.innerWidth >= MEDIA_PREVIEW_DESKTOP_MIN_WIDTH;
  viewerVisible.value = true;
}

function navigateViewer(mediaId: number | null): void {
  if (mediaId != null) selectedMediaId.value = mediaId;
}

async function onViewerTimeCorrectionCleared(mediaId: number): Promise<void> {
  await loadPage(0);
  if (!items.value.some((item) => item.id === mediaId)) {
    viewerVisible.value = false;
    selectedMediaId.value = null;
  }
}

function openSelectedMediaOnMap(): void {
  const item = selectedItem.value;
  if (item?.id == null || item.resolvedLat == null || item.resolvedLng == null) return;
  viewerVisible.value = false;
  visible.value = false;
  void nextTick(() => emit('open-media-on-map', { id: item.id, lat: item.resolvedLat!, lng: item.resolvedLng! }));
}

function openActivity(trackId: number | undefined): void {
  if (trackId == null) return;
  viewerVisible.value = false;
  visible.value = false;
  void nextTick(() => emit('open-activity', trackId));
}

function onMosaicClosed(): void {
  abortController?.abort();
  requestGeneration++;
  viewerVisible.value = false;
  selectedMediaId.value = null;
}

function captureTimeLabel(value: Date | string | null | undefined): string {
  if (value == null) return 'Undated';
  const date = value instanceof Date ? value : new Date(value);
  return Number.isFinite(date.getTime()) ? formatDateAndTime(date) : 'Undated';
}

function itemCountLabel(count: number): string {
  return `${count.toLocaleString()} ${count === 1 ? 'item' : 'items'}`;
}
</script>

<style scoped>
.media-mosaic {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  padding: 0 var(--dlg-padding) var(--dlg-padding);
}

.media-mosaic__toolbar {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 0;
  background: var(--surface-card);
  border-bottom: 1px solid var(--border-default);
}

.media-mosaic__filters {
  display: flex;
  gap: 0.4rem;
}

.media-mosaic__chip,
.media-mosaic__more button,
.media-mosaic__state button {
  border: 1px solid var(--border-default);
  border-radius: 999px;
  background: var(--surface-elevated);
  color: var(--text-secondary);
  cursor: pointer;
  padding: 0.45rem 0.8rem;
  font: inherit;
}

.media-mosaic__chip--active {
  border-color: var(--accent);
  background: var(--accent-bg);
  color: var(--accent-text);
  font-weight: 650;
}

.media-mosaic__count {
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  white-space: nowrap;
}

.media-mosaic__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 0.85rem;
  padding-top: 1rem;
}

.media-mosaic-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: 0.8rem;
  background: var(--surface-elevated);
}

.media-mosaic-card__preview {
  display: flex;
  width: 100%;
  flex-direction: column;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 0;
  text-align: left;
}

.media-mosaic-card__image-wrap {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: var(--surface-ground);
}

.media-mosaic-card__image-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.18s ease;
}

.media-mosaic-card__preview:hover img {
  transform: scale(1.025);
}

.media-mosaic-card__video {
  position: absolute;
  inset: 50% auto auto 50%;
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.72);
  color: white;
  font-size: 1.35rem;
  transform: translate(-50%, -50%);
}

.media-mosaic-card__text {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0.65rem 0.7rem;
}

.media-mosaic-card__text strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-mosaic-card__text span {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.media-mosaic-card__activity {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  border: 0;
  border-top: 1px solid var(--border-default);
  background: transparent;
  color: var(--accent);
  cursor: pointer;
  padding: 0.55rem;
  font: inherit;
  font-size: var(--text-xs-size);
  font-weight: 650;
}

.media-mosaic__state {
  display: grid;
  min-height: 14rem;
  place-content: center;
  justify-items: center;
  gap: 0.7rem;
  color: var(--text-muted);
  text-align: center;
}

.media-mosaic__state--error {
  color: var(--error);
}

.media-mosaic__more {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 1.25rem 0 0.5rem;
  color: var(--error);
  font-size: var(--text-sm-size);
}

.media-mosaic__more button {
  color: var(--accent);
}

@media (max-width: 640px) {
  .media-mosaic {
    padding-inline: 0.7rem;
  }

  .media-mosaic__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0.55rem;
  }

  .media-mosaic__toolbar {
    gap: 0.5rem;
  }

  .media-mosaic__chip {
    padding-inline: 0.65rem;
  }
}
</style>
