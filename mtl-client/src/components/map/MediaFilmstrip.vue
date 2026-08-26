<template>
  <section
    class="mp__filmstrip-section"
    :class="{ 'mp__filmstrip-section--collapsed': navTotal > 1 && collapsed }"
    :aria-label="`Media information and ${collectionLabel.toLowerCase()}`"
  >
    <div class="mp__media-dock">
      <span v-if="fileName" class="mp__dock-filename" :title="fileName">{{ fileName }}</span>
      <button
        v-if="navTotal > 1"
        type="button"
        class="mp__filmstrip-heading"
        :aria-expanded="!collapsed"
        :aria-label="`${collapsed ? 'Expand' : 'Collapse'} ${collectionLabel.toLowerCase()}, item ${navIndex} of ${navTotal}`"
        aria-controls="nearby-photos-strip"
        @click="collapsed = !collapsed"
      >
        <span class="mp__filmstrip-label">
          <span class="mp__filmstrip-label-full">{{ collectionLabel }}</span>
          <span class="mp__filmstrip-label-compact">{{ compactCollectionLabel }}</span>
        </span>
        <span class="mp__filmstrip-heading-meta">
          <span aria-live="polite">{{ navIndex }}/{{ navTotal }}</span>
          <i :class="collapsed ? 'bi bi-chevron-up' : 'bi bi-chevron-down'" aria-hidden="true"></i>
        </span>
      </button>
    </div>
    <div
      v-if="navTotal > 1"
      v-show="!collapsed"
      id="nearby-photos-strip"
      ref="filmstripEl"
      class="mp__filmstrip"
      :class="{ 'mp__filmstrip--dragging': dragging }"
      aria-label="Media filmstrip"
      :aria-busy="pageLoading"
      @dragstart.prevent
      @mousedown="onMouseDown"
      @scroll.passive="onScroll"
    >
      <div class="mp__filmstrip-track" :style="filmstripTrackStyle">
        <button
          v-for="item in visibleMediaItems"
          :key="item.id"
          type="button"
          class="mp__filmstrip-item"
          :class="{ 'mp__filmstrip-item--active': item.id === mediaId }"
          :style="filmstripItemStyle(item.index)"
          :aria-current="item.id === mediaId ? 'true' : undefined"
          :aria-label="itemAriaLabel(item.id)"
          :data-media-id="item.id"
          :data-media-index="item.index"
          @click="onItemClick($event, item.id)"
        >
          <img :src="thumbnailUrl(item.id)" alt="" loading="lazy" fetchpriority="low" draggable="false" />
          <span v-if="isVideoItem(item.id)" class="mp__filmstrip-video" aria-hidden="true">
            <i class="bi bi-play-fill"></i>
          </span>
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { getMediaInfo, mediaContentUrl } from '@/repositories/mediaRepository';
import { isVideoMedia } from '@/utils/mediaKind';

defineOptions({ name: 'MediaFilmstrip' });

const FILMSTRIP_OVERSCAN_ITEMS = 4;
const FILMSTRIP_FALLBACK_VISIBLE_ITEMS = 12;
const FILMSTRIP_SCROLL_BOUNDARY_PX = 32;
const FILMSTRIP_FALLBACK_ITEM_STRIDE_PX = 96;
const FILMSTRIP_THUMBNAIL_SIZE = 192;
const FILMSTRIP_DRAG_THRESHOLD_PX = 5;

const props = withDefaults(
  defineProps<{
    fileName?: string | null;
    mediaId: number | null;
    mediaIds?: number[];
    videoMediaIds?: number[];
    currentMediaIsVideo?: boolean;
    mediaOffset?: number;
    navIndex?: number;
    navTotal?: number;
    pageLoading?: boolean;
    collectionLabel?: string;
  }>(),
  {
    fileName: null,
    mediaIds: () => [],
    videoMediaIds: () => [],
    currentMediaIsVideo: false,
    mediaOffset: 0,
    navIndex: 0,
    navTotal: 0,
    pageLoading: false,
    collectionLabel: 'Nearby media',
  }
);

const emit = defineEmits<{
  select: [mediaId: number];
  'request-page': [direction: -1 | 1];
}>();

const filmstripEl = ref<HTMLElement | null>(null);
const collapsed = ref(false);
const dragging = ref(false);
const renderStart = ref(0);
const renderEnd = ref(FILMSTRIP_FALLBACK_VISIBLE_ITEMS);
const discoveredVideoMediaIds = ref(new Set<number>());
const resolvedMediaKinds = new Set<number>();
const mediaKindControllers = new Map<number, AbortController>();
let requestedPageDirection: -1 | 1 | null = null;
let dragStartX = 0;
let dragStartScrollLeft = 0;
let suppressItemClick = false;
let filmstripResizeObserver: ResizeObserver | null = null;

const mediaIds = computed(() => [...new Set(props.mediaIds.filter((id) => Number.isSafeInteger(id) && id > 0))]);
const videoMediaIds = computed(() => new Set(props.videoMediaIds.filter((id) => Number.isSafeInteger(id) && id > 0)));
const compactCollectionLabel = computed(() =>
  props.collectionLabel === 'Nearby media' ? 'Nearby' : props.collectionLabel
);
const visibleMediaItems = computed(() =>
  mediaIds.value.slice(renderStart.value, renderEnd.value).map((id, localIndex) => ({
    id,
    index: renderStart.value + localIndex,
  }))
);
const filmstripTrackStyle = computed(() => ({ '--filmstrip-item-count': mediaIds.value.length }));

function filmstripItemStyle(index: number) {
  return { '--filmstrip-item-index': index };
}

function itemStride(element: HTMLElement): number {
  const items = element.querySelectorAll<HTMLElement>('.mp__filmstrip-item');
  if (items.length < 2) return FILMSTRIP_FALLBACK_ITEM_STRIDE_PX;
  const firstIndex = Number(items[0].dataset.mediaIndex);
  const secondIndex = Number(items[1].dataset.mediaIndex);
  const indexDelta = secondIndex - firstIndex;
  const stride = indexDelta > 0 ? (items[1].offsetLeft - items[0].offsetLeft) / indexDelta : 0;
  return stride > 0 ? stride : FILMSTRIP_FALLBACK_ITEM_STRIDE_PX;
}

function updateRenderWindow(): void {
  const element = filmstripEl.value;
  if (!element) return;
  const stride = itemStride(element);
  const firstVisibleIndex = Math.max(0, Math.floor(element.scrollLeft / stride));
  const visibleCount =
    element.clientWidth > 0
      ? Math.ceil(element.clientWidth / stride)
      : FILMSTRIP_FALLBACK_VISIBLE_ITEMS - FILMSTRIP_OVERSCAN_ITEMS * 2;
  renderStart.value = Math.max(0, firstVisibleIndex - FILMSTRIP_OVERSCAN_ITEMS);
  renderEnd.value = Math.min(mediaIds.value.length, firstVisibleIndex + visibleCount + FILMSTRIP_OVERSCAN_ITEMS);
}

function centerActiveMedia(): void {
  const element = filmstripEl.value;
  const activeIndex = props.mediaId == null ? -1 : mediaIds.value.indexOf(props.mediaId);
  if (!element || activeIndex < 0) {
    updateRenderWindow();
    return;
  }
  const stride = itemStride(element);
  element.scrollLeft = Math.max(0, activeIndex * stride - Math.max(0, element.clientWidth - stride) / 2);
  updateRenderWindow();
}

function requestPage(direction: -1 | 1): void {
  if (props.pageLoading || requestedPageDirection === direction) return;
  const hasPage = direction < 0 ? props.mediaOffset > 0 : props.mediaOffset + mediaIds.value.length < props.navTotal;
  if (!hasPage) return;
  requestedPageDirection = direction;
  emit('request-page', direction);
}

function onScroll(): void {
  const element = filmstripEl.value;
  if (!element) return;
  updateRenderWindow();
  const atStart = element.scrollLeft <= FILMSTRIP_SCROLL_BOUNDARY_PX;
  const atEnd = element.scrollWidth - element.clientWidth - element.scrollLeft <= FILMSTRIP_SCROLL_BOUNDARY_PX;

  if (atStart) {
    requestPage(-1);
    return;
  }
  if (atEnd) requestPage(1);
}

function thumbnailUrl(id: number): string {
  return mediaContentUrl(id, FILMSTRIP_THUMBNAIL_SIZE);
}

function isVideoItem(id: number): boolean {
  return (
    videoMediaIds.value.has(id) ||
    discoveredVideoMediaIds.value.has(id) ||
    (id === props.mediaId && props.currentMediaIsVideo)
  );
}

async function resolveMediaKind(id: number): Promise<void> {
  if (isVideoItem(id) || resolvedMediaKinds.has(id) || mediaKindControllers.has(id)) return;
  const controller = new AbortController();
  mediaKindControllers.set(id, controller);
  try {
    const info = await getMediaInfo(id, controller.signal);
    if (controller.signal.aborted) return;
    resolvedMediaKinds.add(id);
    if (isVideoMedia(info.fileName, info.mediaKind)) {
      discoveredVideoMediaIds.value = new Set([...discoveredVideoMediaIds.value, id]);
    }
  } catch {
    if (!controller.signal.aborted) resolvedMediaKinds.add(id);
  } finally {
    if (mediaKindControllers.get(id) === controller) mediaKindControllers.delete(id);
  }
}

function itemAriaLabel(id: number): string {
  const mediaLabel = isVideoItem(id) ? 'video' : 'photo';
  return id === props.mediaId ? `Current ${mediaLabel}` : `Open ${mediaLabel} ${id}`;
}

function onMouseDown(event: MouseEvent): void {
  if (event.button !== 0) return;
  const element = filmstripEl.value;
  if (!element) return;
  dragStartX = event.clientX;
  dragStartScrollLeft = element.scrollLeft;
  suppressItemClick = false;
  window.addEventListener('mousemove', onMouseMove);
  window.addEventListener('mouseup', onMouseUp, { once: true });
}

function onMouseMove(event: MouseEvent): void {
  const element = filmstripEl.value;
  if (!element) return;
  const delta = dragStartX - event.clientX;
  if (!dragging.value && Math.abs(delta) < FILMSTRIP_DRAG_THRESHOLD_PX) return;
  dragging.value = true;
  suppressItemClick = true;
  element.scrollLeft = dragStartScrollLeft + delta;
  event.preventDefault();
}

function onMouseUp(): void {
  window.removeEventListener('mousemove', onMouseMove);
  dragging.value = false;
  window.setTimeout(() => {
    suppressItemClick = false;
  }, 0);
}

function onItemClick(event: MouseEvent, id: number): void {
  if (suppressItemClick) {
    suppressItemClick = false;
    event.preventDefault();
    event.stopPropagation();
    return;
  }
  emit('select', id);
}

watch(
  () => [props.mediaId, props.mediaIds, props.mediaOffset] as const,
  async () => {
    requestedPageDirection = null;
    await nextTick();
    centerActiveMedia();
  },
  { deep: false }
);

watch(
  () => visibleMediaItems.value.map((item) => item.id),
  (ids) => ids.forEach((id) => void resolveMediaKind(id)),
  { immediate: true }
);

watch(
  () => props.pageLoading,
  (loading) => {
    if (!loading) requestedPageDirection = null;
  }
);

onMounted(() => {
  nextTick(centerActiveMedia);
  if (typeof ResizeObserver === 'undefined' || !filmstripEl.value) return;
  filmstripResizeObserver = new ResizeObserver(updateRenderWindow);
  filmstripResizeObserver.observe(filmstripEl.value);
});

onBeforeUnmount(() => {
  filmstripResizeObserver?.disconnect();
  window.removeEventListener('mousemove', onMouseMove);
  window.removeEventListener('mouseup', onMouseUp);
  for (const controller of mediaKindControllers.values()) controller.abort();
  mediaKindControllers.clear();
});
</script>

<style scoped>
.mp__filmstrip-section {
  flex: 0 0 auto;
  padding: 0.6rem 1rem 0.9rem;
  color: var(--mp-text-strong);
  background: var(--mp-surface);
  border-top: 1px solid var(--mp-border);
}

.mp__filmstrip-section--collapsed {
  padding-bottom: 0.6rem;
}

.mp__media-dock {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  min-width: 0;
}

.mp__dock-filename {
  min-width: 0;
  overflow: hidden;
  color: var(--mp-text-strong);
  font-size: var(--text-sm-size);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mp__filmstrip-heading {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0.65rem;
  margin-bottom: 0.55rem;
  padding: 0;
  color: var(--mp-text-strong);
  background: transparent;
  border: 0;
  font: inherit;
  font-size: var(--text-sm-size);
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}

.mp__media-dock .mp__filmstrip-heading,
.mp__filmstrip-section--collapsed .mp__filmstrip-heading {
  margin-bottom: 0;
}

.mp__filmstrip-heading-meta {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  color: var(--mp-text-muted);
  font-variant-numeric: tabular-nums;
}

.mp__filmstrip-heading-meta i {
  width: 1rem;
  color: var(--mp-text);
  text-align: center;
}

.mp__filmstrip-label-compact {
  display: none;
}

.mp__filmstrip {
  --filmstrip-item-width: 88px;
  --filmstrip-item-height: 64px;
  --filmstrip-item-gap: 0.55rem;
  --filmstrip-item-stride: calc(var(--filmstrip-item-width) + var(--filmstrip-item-gap));

  position: relative;
  min-height: 72px;
  padding: 0.65rem 0.2rem 0.15rem;
  overflow-x: auto;
  overscroll-behavior-x: contain;
  scrollbar-width: thin;
  scrollbar-color: var(--mp-scrollbar) transparent;
  cursor: grab;
  user-select: none;
}

.mp__filmstrip-track {
  position: relative;
  width: max(100%, calc(var(--filmstrip-item-count) * var(--filmstrip-item-stride) - var(--filmstrip-item-gap)));
  height: var(--filmstrip-item-height);
}

.mp__filmstrip--dragging {
  cursor: grabbing;
}

.mp__filmstrip::-webkit-scrollbar {
  height: 5px;
}

.mp__filmstrip::-webkit-scrollbar-track {
  background: transparent;
}

.mp__filmstrip::-webkit-scrollbar-thumb {
  background: var(--mp-scrollbar);
  border-radius: 999px;
}

.mp__filmstrip-item {
  position: absolute;
  top: 0;
  left: calc(var(--filmstrip-item-index) * var(--filmstrip-item-stride));
  width: var(--filmstrip-item-width);
  height: var(--filmstrip-item-height);
  padding: 0;
  overflow: hidden;
  background: var(--mp-thumb-bg);
  border: 2px solid var(--mp-border);
  border-radius: 8px;
  opacity: 0.72;
  cursor: pointer;
}

.mp__filmstrip-item:hover,
.mp__filmstrip-item--active {
  opacity: 1;
  border-color: var(--accent);
}

.mp__filmstrip-item--active {
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 35%, transparent);
}

.mp__filmstrip-item img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  pointer-events: none;
  user-select: none;
}

.mp__filmstrip-video {
  position: absolute;
  inset: 50% auto auto 50%;
  display: grid;
  width: 1.9rem;
  height: 1.9rem;
  place-items: center;
  color: white;
  background: rgba(15, 23, 42, 0.76);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 999px;
  font-size: 1.15rem;
  line-height: 1;
  pointer-events: none;
  transform: translate(-50%, -50%);
}

.mp__filmstrip-heading:focus-visible,
.mp__filmstrip-item:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

@media (max-width: 768px) {
  .mp__filmstrip-section {
    padding: 0.7rem 0.7rem max(0.8rem, env(safe-area-inset-bottom, 0px));
  }

  .mp__filmstrip-section--collapsed {
    padding-bottom: max(0.7rem, env(safe-area-inset-bottom, 0px));
  }

  .mp__media-dock {
    gap: 0.65rem;
  }

  .mp__dock-filename {
    font-size: var(--text-xs-size);
  }

  .mp__filmstrip-heading {
    gap: 0.4rem;
    margin-bottom: 0.45rem;
    min-height: 2.75rem;
  }

  .mp__filmstrip-heading-meta {
    gap: 0.35rem;
  }

  .mp__filmstrip-label-full {
    display: none;
  }

  .mp__filmstrip-label-compact {
    display: inline;
  }

  .mp__filmstrip {
    --filmstrip-item-width: 82px;
    --filmstrip-item-height: 60px;
    --filmstrip-item-gap: 0.45rem;

    min-height: 68px;
    padding-inline: 0.1rem;
  }
}
</style>
