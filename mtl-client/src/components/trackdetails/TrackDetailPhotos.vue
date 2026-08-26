<template>
  <section ref="panelEl" class="photos-panel" aria-labelledby="track-photos-heading">
    <div class="photos-toolbar">
      <div class="photos-toolbar__summary">
        <h2 id="track-photos-heading">Activity media</h2>
        <span v-if="!loading" class="photos-toolbar__count"
          >{{ formattedTotalItems }} {{ effectiveTotalItems === 1 ? 'item' : 'items' }}</span
        >
      </div>

      <button
        type="button"
        class="photo-tools-toggle"
        data-test="photo-tools-toggle"
        :aria-expanded="photoToolsOpen"
        aria-controls="track-photo-tools"
        @click="togglePhotoTools"
      >
        <i class="bi bi-sliders" aria-hidden="true"></i>
        <span>Photo tools</span>
        <span v-if="hasActiveCorrection" class="photo-tools-toggle__status">Active</span>
        <i :class="photoToolsOpen ? 'bi bi-chevron-up' : 'bi bi-chevron-down'" aria-hidden="true"></i>
      </button>
    </div>

    <section
      v-show="photoToolsOpen"
      id="track-photo-tools"
      class="photo-tools"
      data-test="photo-tools"
      aria-label="Photo tools"
    >
      <div class="photo-tools__section">
        <div class="photo-tools__heading">
          <i class="bi bi-clock-history" aria-hidden="true"></i>
          <div>
            <h3>Camera clock</h3>
            <p>Use this only when the camera time differs from the activity time.</p>
          </div>
        </div>
        <form class="photo-offset" @submit.prevent="applyOffset">
          <label for="track-photo-offset-hours">Time difference</label>
          <div class="photo-offset__controls">
            <span class="photo-offset__sign" aria-hidden="true">±</span>
            <input
              id="track-photo-offset-hours"
              v-model.number="draftOffsetHours"
              data-test="photo-offset-hours"
              type="number"
              step="0.25"
              min="-24"
              max="24"
              inputmode="decimal"
              aria-describedby="track-photo-offset-help"
            />
            <span class="photo-offset__unit">hours</span>
            <button type="submit" :disabled="loading || saving || !offsetChanged">Preview</button>
            <button type="button" :disabled="loading || saving || offsetSeconds === 0" @click="resetOffset">
              Reset
            </button>
          </div>
          <p id="track-photo-offset-help">Preview before saving. Embedded GPS times are never changed.</p>
          <div v-if="offsetSeconds !== 0" class="photo-offset__preview" data-test="photo-offset-preview">
            <span>
              Unsaved preview. {{ correctablePreviewMedia.length }}
              {{ correctablePreviewMedia.length === 1 ? 'item uses' : 'items use' }} the camera clock
              {{ effectiveTotalItems > media.length ? 'on this page' : '' }}.
            </span>
            <button
              type="button"
              data-test="save-time-correction"
              :disabled="saving || correctablePreviewMedia.length === 0"
              @click="saveTimeCorrection"
            >
              Save correction
            </button>
          </div>
        </form>
      </div>

      <div class="photo-tools__section photo-tools__section--location">
        <div class="photo-tools__heading">
          <i class="bi bi-geo-alt" aria-hidden="true"></i>
          <div>
            <h3>Media locations</h3>
            <p>Correct an item only when its displayed position is wrong.</p>
          </div>
        </div>
        <button
          type="button"
          class="photo-location-mode-toggle"
          data-test="photo-location-mode-toggle"
          :aria-pressed="locationEditMode"
          @click="toggleLocationEditMode"
        >
          <i :class="locationEditMode ? 'bi bi-check-lg' : 'bi bi-pencil'" aria-hidden="true"></i>
          {{ locationEditMode ? 'Finish adjusting locations' : 'Adjust locations' }}
        </button>
      </div>
    </section>

    <p v-if="mutationError" class="photos-mutation-error" role="alert">{{ mutationError }}</p>

    <div v-if="loading" class="photos-state" data-test="track-photos-loading" role="status" aria-live="polite">
      <i class="bi bi-arrow-repeat photos-state__spinner" aria-hidden="true"></i>
      <span>Loading activity media…</span>
    </div>

    <div v-else-if="error" class="photos-state photos-state--error" data-test="track-photos-error" role="alert">
      <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
      <span>{{ error }}</span>
      <button type="button" @click="emit('retry')">
        <i class="bi bi-arrow-clockwise" aria-hidden="true"></i> Retry
      </button>
    </div>

    <div v-else-if="media.length === 0" class="photos-state photos-state--empty" data-test="track-photos-empty">
      <i class="bi bi-images" aria-hidden="true"></i>
      <strong>No media matched this activity</strong>
      <span>If the camera clock was wrong, you can preview a time difference.</span>
      <button type="button" data-test="empty-photo-tools" @click="openPhotoTools">Open Photo tools</button>
    </div>

    <nav
      v-if="!loading && !error && media.length > 0 && showPaginationControls"
      class="photo-pagination"
      aria-label="Activity media pages"
    >
      <span class="photo-pagination__range" data-test="photo-page-range">{{ pageRangeLabel }}</span>
      <div class="photo-pagination__buttons">
        <button
          type="button"
          aria-label="First page"
          :disabled="loading || page === 0"
          data-test="photo-page-first"
          @click="changePage(0)"
        >
          First
        </button>
        <button
          type="button"
          aria-label="Previous page"
          :disabled="loading || page === 0"
          data-test="photo-page-previous"
          @click="changePage(page - 1)"
        >
          Previous
        </button>
        <span>Page {{ page + 1 }} of {{ formattedTotalPages }}</span>
        <button
          type="button"
          aria-label="Next page"
          :disabled="loading || page >= effectiveTotalPages - 1"
          data-test="photo-page-next"
          @click="changePage(page + 1)"
        >
          Next
        </button>
        <button
          type="button"
          aria-label="Last page"
          :disabled="loading || page >= effectiveTotalPages - 1"
          data-test="photo-page-last"
          @click="changePage(effectiveTotalPages - 1)"
        >
          Last
        </button>
      </div>
      <label>
        Per page
        <select :value="pageSize" data-test="photo-page-size" :disabled="loading" @change="changePageSize">
          <option v-for="size in TRACK_MEDIA_PAGE_SIZE_OPTIONS" :key="size" :value="size">{{ size }}</option>
        </select>
      </label>
    </nav>

    <ol v-if="!loading && !error && media.length > 0" class="photo-timeline" aria-label="Media in activity order">
      <li
        v-for="item in media"
        :key="item.id"
        class="photo-timeline__item"
        @mouseenter="emit('highlight-media', item.id)"
        @focusin="emit('highlight-media', item.id)"
      >
        <div class="photo-card-row">
          <button
            type="button"
            class="photo-card"
            :class="{
              'photo-card--selected': item.id === selectedMediaId,
              'photo-card--highlighted': item.id === highlightedMediaId,
            }"
            :aria-current="item.id === selectedMediaId ? 'true' : undefined"
            :data-media-id="item.id"
            @click="emit('select-media', item.id)"
          >
            <span class="photo-card__rail" aria-hidden="true">
              <span class="photo-card__dot">
                <i :class="isVideo(item) ? 'bi bi-camera-video-fill' : 'bi bi-camera-fill'"></i>
              </span>
            </span>
            <span class="photo-card__thumb-wrap">
              <img
                v-if="thumbnailsEnabled"
                :src="mediaContentUrl(item.id, THUMBNAIL_MAX_SIZE)"
                :alt="item.fileName || 'Activity media'"
                class="photo-card__thumb"
                loading="lazy"
                fetchpriority="low"
              />
              <span v-if="isVideo(item)" class="photo-card__video" aria-label="Video">
                <i class="bi bi-play-fill" aria-hidden="true"></i>
              </span>
            </span>
            <span class="photo-card__body">
              <span class="photo-card__header">
                <strong>{{ item.fileName || `Media ${item.id}` }}</strong>
                <span class="photo-card__badge" :class="originBadgeClass(item)">{{ originLabel(item) }}</span>
                <span v-if="item.preview" class="photo-card__badge photo-card__badge--preview">Preview</span>
                <span v-if="item.ambiguousMatch" class="photo-card__badge photo-card__badge--ambiguous">
                  Ambiguous ({{ item.alternativeMatchCount ?? 2 }})
                </span>
              </span>
              <span v-if="captureTimeLabel(item)" class="photo-card__time">{{ captureTimeLabel(item) }}</span>
              <span class="photo-card__meta">
                <span v-if="distanceLabel(item)"
                  ><i class="bi bi-signpost-split" aria-hidden="true"></i>{{ distanceLabel(item) }}</span
                >
                <span v-if="savedOffsetLabel(item)" data-test="saved-time-correction"
                  ><i class="bi bi-clock-history" aria-hidden="true"></i>{{ savedOffsetLabel(item) }}</span
                >
              </span>
            </span>
            <i class="bi bi-chevron-right photo-card__open" aria-hidden="true"></i>
          </button>
          <div v-if="showItemActions(item)" class="photo-card__actions">
            <button
              v-if="locationEditMode"
              type="button"
              class="photo-card__edit"
              :aria-expanded="editingMediaId === item.id"
              :aria-controls="`photo-location-editor-${item.id}`"
              @click="toggleLocationEditor(item)"
            >
              <i class="bi bi-pencil" aria-hidden="true"></i>
              {{ item.positionOrigin === 'USER_ASSIGNED' ? 'Edit location' : 'Set location' }}
            </button>
            <button
              v-if="photoToolsOpen && !item.preview && (item.appliedCameraOffsetSeconds ?? 0) !== 0"
              type="button"
              class="photo-card__edit"
              data-test="clear-time-correction"
              :disabled="saving"
              @click="emit('save-time-correction', [item.id], 0)"
            >
              Clear clock correction
            </button>
          </div>
        </div>

        <form
          v-if="editingMediaId === item.id"
          :id="`photo-location-editor-${item.id}`"
          class="photo-location-editor"
          data-test="photo-location-editor"
          @submit.prevent="saveManualLocation(item.id)"
        >
          <p>This changes the displayed position. Original GPS and track correlation data stay preserved.</p>
          <label>
            Latitude
            <input v-model.number="manualLatitude" type="number" min="-90" max="90" step="any" required />
          </label>
          <label>
            Longitude
            <input v-model.number="manualLongitude" type="number" min="-180" max="180" step="any" required />
          </label>
          <label class="photo-location-editor__note">
            Note
            <input v-model="manualNote" type="text" maxlength="1000" placeholder="Optional" />
          </label>
          <div class="photo-location-editor__actions">
            <button type="submit" :disabled="saving || !manualLocationValid">Save location</button>
            <button
              v-if="item.positionOrigin === 'USER_ASSIGNED'"
              type="button"
              :disabled="saving"
              data-test="clear-manual-location"
              @click="clearManualLocation(item.id)"
            >
              Clear assignment
            </button>
            <button type="button" :disabled="saving" @click="editingMediaId = null">Cancel</button>
          </div>
        </form>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { mediaContentUrl, type TrackMediaDto } from '@/repositories/mediaRepository';
import { formatDateAndTime, formatDistanceSmart } from '@/utils/Utils';
import { isVideoMedia } from '@/utils/mediaKind';
import { TRACK_MEDIA_DEFAULT_PAGE_SIZE, TRACK_MEDIA_PAGE_SIZE_OPTIONS } from './trackMediaPaging';

defineOptions({ name: 'TrackDetailPhotos' });

const THUMBNAIL_MAX_SIZE = 480;
const SECONDS_PER_HOUR = 3600;

const props = withDefaults(
  defineProps<{
    media?: TrackMediaDto[];
    page?: number;
    pageSize?: number;
    totalItems?: number | null;
    totalPages?: number | null;
    selectedMediaId?: number | null;
    highlightedMediaId?: number | null;
    loading?: boolean;
    error?: string | null;
    offsetSeconds?: number;
    saving?: boolean;
    mutationError?: string | null;
    thumbnailsEnabled?: boolean;
  }>(),
  {
    media: () => [],
    page: 0,
    pageSize: TRACK_MEDIA_DEFAULT_PAGE_SIZE,
    totalItems: null,
    totalPages: null,
    selectedMediaId: null,
    highlightedMediaId: null,
    loading: false,
    error: null,
    offsetSeconds: 0,
    saving: false,
    mutationError: null,
    thumbnailsEnabled: true,
  }
);

const emit = defineEmits<{
  'select-media': [mediaId: number];
  'highlight-media': [mediaId: number | null];
  'apply-offset': [offsetSeconds: number];
  'save-time-correction': [mediaIds: number[], offsetSeconds: number];
  'save-manual-location': [mediaId: number, latitude: number, longitude: number, note?: string];
  'clear-manual-location': [mediaId: number];
  'change-page': [page: number];
  'change-page-size': [pageSize: number];
  retry: [];
}>();

const panelEl = ref<HTMLElement | null>(null);
const draftOffsetHours = ref(props.offsetSeconds / SECONDS_PER_HOUR);
const photoToolsOpen = ref(props.offsetSeconds !== 0);
const locationEditMode = ref(false);
const editingMediaId = ref<number | null>(null);
const manualLatitude = ref<number | null>(null);
const manualLongitude = ref<number | null>(null);
const manualNote = ref('');

const correctablePreviewMedia = computed(() =>
  props.media.filter((item) => item.preview && item.timeSource === 'EXIF_DATE_TAKEN')
);
const hasActiveCorrection = computed(
  () =>
    props.offsetSeconds !== 0 ||
    props.media.some(
      (item) =>
        item.positionOrigin === 'USER_ASSIGNED' || (!item.preview && (item.appliedCameraOffsetSeconds ?? 0) !== 0)
    )
);
const effectiveTotalItems = computed(() => props.totalItems ?? props.media.length);
const effectiveTotalPages = computed(
  () =>
    props.totalPages ?? (effectiveTotalItems.value === 0 ? 0 : Math.ceil(effectiveTotalItems.value / props.pageSize))
);
const formattedTotalItems = computed(() => effectiveTotalItems.value.toLocaleString());
const formattedTotalPages = computed(() => effectiveTotalPages.value.toLocaleString());
const showPaginationControls = computed(
  () => effectiveTotalPages.value > 1 || effectiveTotalItems.value > TRACK_MEDIA_PAGE_SIZE_OPTIONS[0]
);
const pageRangeLabel = computed(() => {
  const first = props.page * props.pageSize + 1;
  const last = Math.min(first + props.media.length - 1, effectiveTotalItems.value);
  return `${first.toLocaleString()}–${last.toLocaleString()} of ${formattedTotalItems.value}`;
});

const manualLocationValid = computed(
  () =>
    manualLatitude.value != null &&
    Number.isFinite(manualLatitude.value) &&
    manualLatitude.value >= -90 &&
    manualLatitude.value <= 90 &&
    manualLongitude.value != null &&
    Number.isFinite(manualLongitude.value) &&
    manualLongitude.value >= -180 &&
    manualLongitude.value <= 180
);

const normalizedDraftOffsetSeconds = computed(() => {
  const hours = Number(draftOffsetHours.value);
  if (!Number.isFinite(hours)) return props.offsetSeconds;
  return Math.round(Math.max(-24, Math.min(24, hours)) * SECONDS_PER_HOUR);
});

const offsetChanged = computed(() => normalizedDraftOffsetSeconds.value !== props.offsetSeconds);

watch(
  () => props.offsetSeconds,
  (seconds) => {
    draftOffsetHours.value = seconds / SECONDS_PER_HOUR;
    if (seconds !== 0) photoToolsOpen.value = true;
  }
);

watch(
  () => props.selectedMediaId,
  (selectedId) => {
    if (selectedId == null) return;
    requestAnimationFrame(() => {
      panelEl.value
        ?.querySelector<HTMLElement>(`[data-media-id="${selectedId}"]`)
        ?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    });
  }
);

function applyOffset(): void {
  if (!offsetChanged.value) return;
  emit('apply-offset', normalizedDraftOffsetSeconds.value);
}

function resetOffset(): void {
  draftOffsetHours.value = 0;
  emit('apply-offset', 0);
}

function openPhotoTools(): void {
  photoToolsOpen.value = true;
}

function togglePhotoTools(): void {
  photoToolsOpen.value = !photoToolsOpen.value;
  if (photoToolsOpen.value) return;
  locationEditMode.value = false;
  editingMediaId.value = null;
}

function toggleLocationEditMode(): void {
  locationEditMode.value = !locationEditMode.value;
  if (!locationEditMode.value) editingMediaId.value = null;
}

function showItemActions(item: TrackMediaDto): boolean {
  return (
    locationEditMode.value || (photoToolsOpen.value && !item.preview && (item.appliedCameraOffsetSeconds ?? 0) !== 0)
  );
}

function changePage(page: number): void {
  if (page < 0 || page >= effectiveTotalPages.value || page === props.page) return;
  emit('change-page', page);
}

function changePageSize(event: Event): void {
  const pageSize = Number((event.target as HTMLSelectElement).value);
  if (
    !TRACK_MEDIA_PAGE_SIZE_OPTIONS.includes(pageSize as (typeof TRACK_MEDIA_PAGE_SIZE_OPTIONS)[number]) ||
    pageSize === props.pageSize
  )
    return;
  emit('change-page-size', pageSize);
}

function saveTimeCorrection(): void {
  emit(
    'save-time-correction',
    correctablePreviewMedia.value.map((item) => item.id),
    props.offsetSeconds
  );
}

function toggleLocationEditor(item: TrackMediaDto): void {
  if (editingMediaId.value === item.id) {
    editingMediaId.value = null;
    return;
  }
  editingMediaId.value = item.id;
  manualLatitude.value = item.manualLat ?? item.resolvedLat ?? item.originalLat ?? item.routeLat ?? null;
  manualLongitude.value = item.manualLng ?? item.resolvedLng ?? item.originalLng ?? item.routeLng ?? null;
  manualNote.value = item.manualNote ?? '';
}

function saveManualLocation(mediaId: number): void {
  if (!manualLocationValid.value || manualLatitude.value == null || manualLongitude.value == null) return;
  const note = manualNote.value.trim();
  emit('save-manual-location', mediaId, manualLatitude.value, manualLongitude.value, note || undefined);
  editingMediaId.value = null;
}

function clearManualLocation(mediaId: number): void {
  emit('clear-manual-location', mediaId);
  editingMediaId.value = null;
}

function originLabel(item: TrackMediaDto): string {
  if (item.positionOrigin === 'USER_ASSIGNED') return 'Set by you';
  if (item.positionOrigin === 'TRACK_INTERPOLATED') return 'Estimated';
  if (item.positionOrigin === 'EXIF_EMBEDDED') return isVideo(item) ? 'Video GPS' : 'Photo GPS';
  return 'Position unknown';
}

function originBadgeClass(item: TrackMediaDto): string {
  if (item.positionOrigin === 'USER_ASSIGNED') return 'photo-card__badge--manual';
  if (item.positionOrigin === 'TRACK_INTERPOLATED') return 'photo-card__badge--estimated';
  if (item.positionOrigin === 'EXIF_EMBEDDED') return '';
  return 'photo-card__badge--unknown';
}

function savedOffsetLabel(item: TrackMediaDto): string {
  const seconds = item.appliedCameraOffsetSeconds ?? 0;
  if (item.preview || seconds === 0) return '';
  const hours = seconds / SECONDS_PER_HOUR;
  const value = Number.isInteger(hours) ? hours.toFixed(0) : hours.toFixed(2).replace(/\.?0+$/, '');
  return `Saved clock correction ${hours > 0 ? '+' : ''}${value}h`;
}

function captureTimeLabel(item: TrackMediaDto): string {
  return formatDateAndTime(item.adjustedCapturedAt ?? item.capturedAt);
}

function distanceLabel(item: TrackMediaDto): string {
  return item.distanceInMeterSinceStart == null ? '' : formatDistanceSmart(item.distanceInMeterSinceStart);
}

function isVideo(item: TrackMediaDto): boolean {
  return isVideoMedia(item.fileName, item.mediaKind);
}
</script>

<style scoped>
.photos-panel {
  padding: 0.75rem;
}

.photos-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.75rem 1rem;
  margin-bottom: 0.75rem;
  padding: 0.75rem;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  background: var(--surface-card);
}

.photos-toolbar__summary {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 0.4rem 0.65rem;
}

.photos-toolbar h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-base-size);
}

.photos-toolbar__count {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.photo-tools-toggle,
.photo-location-mode-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  min-height: 2.25rem;
  padding: 0.35rem 0.65rem;
  color: var(--text-secondary);
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 6px;
  font: inherit;
  font-size: var(--text-xs-size);
  font-weight: 700;
  cursor: pointer;
}

.photo-tools-toggle__status {
  padding: 0.1rem 0.4rem;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  border-radius: 999px;
  font-size: var(--text-2xs-size);
  text-transform: uppercase;
}

.photo-tools-toggle:focus-visible,
.photo-location-mode-toggle:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.photo-tools {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(15rem, 0.7fr);
  gap: 1rem;
  margin: -0.25rem 0 0.75rem;
  padding: 0.85rem;
  background: var(--surface-card);
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
}

.photo-tools__section {
  display: grid;
  align-content: start;
  gap: 0.65rem;
  min-width: 0;
}

.photo-tools__section--location {
  padding-left: 1rem;
  border-left: 1px solid var(--border-subtle);
}

.photo-tools__heading {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  color: var(--text-secondary);
}

.photo-tools__heading > i {
  margin-top: 0.15rem;
  color: var(--text-muted);
}

.photo-tools__heading h3,
.photo-tools__heading p {
  margin: 0;
}

.photo-tools__heading h3 {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
}

.photo-tools__heading p {
  margin-top: 0.15rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.photo-location-mode-toggle[aria-pressed='true'] {
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 10%, var(--surface-elevated));
  border-color: color-mix(in srgb, var(--accent) 45%, var(--border-default));
}

.photo-offset {
  display: grid;
  gap: 0.3rem;
}

.photo-offset > label {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: 700;
}

.photo-offset__controls {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.photo-offset__sign,
.photo-offset__unit,
.photo-offset p {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.photo-offset input {
  width: 5rem;
  min-height: 2.25rem;
  padding: 0.35rem 0.5rem;
  color: var(--text-primary);
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 6px;
  font: inherit;
}

.photo-offset button,
.photos-state button {
  min-height: 2.25rem;
  padding: 0.35rem 0.65rem;
  color: var(--text-secondary);
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 6px;
  font: inherit;
  font-size: var(--text-xs-size);
  font-weight: 700;
  cursor: pointer;
}

.photo-offset button:disabled {
  opacity: 0.45;
  cursor: default;
}

.photo-offset p {
  margin: 0;
  max-width: 26rem;
}

.photo-offset__preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.45rem;
  color: var(--warning-text);
  background: var(--warning-bg);
  border-radius: 6px;
  font-size: var(--text-xs-size);
}

.photos-mutation-error {
  margin: 0 0 0.75rem;
  padding: 0.6rem 0.75rem;
  color: var(--warning-text);
  background: var(--warning-bg);
  border-radius: 6px;
  font-size: var(--text-sm-size);
}

.photos-state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 0.55rem;
  min-height: 8rem;
  padding: 1rem;
  text-align: center;
  color: var(--text-secondary);
}

.photos-state--empty {
  flex-direction: column;
  color: var(--text-muted);
}

.photos-state--empty > i {
  font-size: var(--text-3xl-size);
}

.photos-state--empty strong {
  color: var(--text-primary);
}

.photos-state--error {
  color: var(--warning-text);
  background: var(--warning-bg);
  border: 1px solid color-mix(in srgb, var(--warning) 35%, transparent);
  border-radius: 8px;
}

.photos-state__spinner {
  animation: photos-spin 0.9s linear infinite;
}

.photo-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.5rem 0.75rem;
  margin: 0 0 0.75rem;
  padding: 0.6rem 0.75rem;
  color: var(--text-secondary);
  background: var(--surface-card);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  font-size: var(--text-xs-size);
}

.photo-pagination__range {
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

.photo-pagination__buttons {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.photo-pagination button,
.photo-pagination select {
  min-height: 2rem;
  padding: 0.25rem 0.5rem;
  color: var(--text-secondary);
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 6px;
  font: inherit;
}

.photo-pagination button {
  cursor: pointer;
}

.photo-pagination button:disabled {
  opacity: 0.45;
  cursor: default;
}

.photo-pagination label {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.photo-timeline {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.photo-timeline__item {
  min-width: 0;
}

.photo-card-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.4rem;
}

.photo-card {
  width: 100%;
  display: grid;
  grid-template-columns: 2.1rem 6rem minmax(0, 1fr) 1.25rem;
  align-items: center;
  gap: 0.7rem;
  min-height: 6.5rem;
  padding: 0;
  text-align: left;
  color: var(--text-secondary);
  background: transparent;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
}

.photo-card:hover,
.photo-card--selected,
.photo-card--highlighted {
  background: var(--surface-hover);
}

.photo-timeline__item:hover .photo-card,
.photo-timeline__item:focus-within .photo-card,
.photo-card--highlighted {
  box-shadow: inset 3px 0 0 color-mix(in srgb, var(--accent) 78%, transparent);
}

.photo-card:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.photo-card__rail {
  align-self: stretch;
  position: relative;
  display: grid;
  place-items: center;
}

.photo-card__rail::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 2px;
  transform: translateX(-50%);
  background: var(--border-default);
}

.photo-timeline__item:first-child .photo-card__rail::before {
  top: 50%;
}

.photo-timeline__item:last-child .photo-card__rail::before {
  bottom: 50%;
}

.photo-card__dot {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 1.8rem;
  height: 1.8rem;
  color: var(--accent-contrast);
  background: var(--accent);
  border: 3px solid var(--surface-card);
  border-radius: 999px;
  font-size: var(--text-xs-size);
}

.photo-card__thumb-wrap {
  position: relative;
  display: block;
  width: 6rem;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  border-radius: 7px;
  background: var(--surface-elevated);
}

.photo-card__thumb {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.photo-card__video {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: white;
  background: rgba(0, 0, 0, 0.25);
  font-size: var(--text-2xl-size);
}

.photo-card__body {
  min-width: 0;
  display: grid;
  gap: 0.3rem;
}

.photo-card__header,
.photo-card__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.35rem 0.65rem;
}

.photo-card__header strong {
  min-width: 0;
  overflow: hidden;
  color: var(--text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.photo-card__badge {
  flex: 0 0 auto;
  padding: 0.12rem 0.4rem;
  color: var(--success-text);
  background: var(--success-bg);
  border-radius: 999px;
  font-size: var(--text-2xs-size);
  font-weight: 700;
  text-transform: uppercase;
}

.photo-card__badge--estimated {
  color: var(--warning-text);
  background: var(--warning-bg);
}

.photo-card__badge--manual {
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
}

.photo-card__badge--unknown {
  color: var(--text-secondary);
  background: var(--surface-elevated);
}

.photo-card__badge--preview,
.photo-card__badge--ambiguous {
  color: var(--text-secondary);
  background: var(--surface-elevated);
}

.photo-card__time {
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
}

.photo-card__meta {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.photo-card__meta > span {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.photo-card__open {
  color: var(--text-faint);
}

.photo-card__edit,
.photo-location-editor button {
  min-height: 2.25rem;
  padding: 0.35rem 0.6rem;
  color: var(--text-secondary);
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 6px;
  font: inherit;
  font-size: var(--text-xs-size);
  font-weight: 700;
  cursor: pointer;
}

.photo-card__actions {
  display: grid;
  justify-items: stretch;
  gap: 0.35rem;
}

.photo-card__edit:focus-visible,
.photo-location-editor button:focus-visible,
.photo-location-editor input:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.photo-location-editor {
  display: grid;
  grid-template-columns: minmax(8rem, 1fr) minmax(8rem, 1fr) minmax(10rem, 2fr);
  gap: 0.65rem;
  margin: 0 0 0.65rem 2.1rem;
  padding: 0.75rem;
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 8px;
}

.photo-location-editor > p,
.photo-location-editor__actions {
  grid-column: 1 / -1;
}

.photo-location-editor > p {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.photo-location-editor label {
  display: grid;
  gap: 0.25rem;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: 700;
}

.photo-location-editor input {
  min-width: 0;
  min-height: 2.25rem;
  padding: 0.35rem 0.5rem;
  color: var(--text-primary);
  background: var(--surface-card);
  border: 1px solid var(--border-default);
  border-radius: 6px;
  font: inherit;
}

.photo-location-editor__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.photo-location-editor button:disabled {
  opacity: 0.45;
  cursor: default;
}

@keyframes photos-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 640px) {
  .photos-toolbar {
    align-items: stretch;
  }

  .photo-tools-toggle {
    width: 100%;
    min-height: 2.75rem;
  }

  .photo-tools {
    grid-template-columns: minmax(0, 1fr);
  }

  .photo-tools__section--location {
    padding-top: 1rem;
    padding-left: 0;
    border-top: 1px solid var(--border-subtle);
    border-left: 0;
  }

  .photo-offset__controls {
    flex-wrap: wrap;
  }

  .photo-offset__preview {
    align-items: stretch;
    flex-direction: column;
  }

  .photo-offset button,
  .photo-offset input,
  .photo-location-mode-toggle {
    min-height: 2.75rem;
  }

  .photo-pagination {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .photo-pagination__range,
  .photo-pagination__buttons {
    grid-column: 1 / -1;
  }

  .photo-pagination__buttons {
    width: 100%;
    justify-content: space-between;
  }

  .photo-pagination__buttons button {
    width: 2.75rem;
    font-size: 0;
  }

  .photo-pagination button,
  .photo-pagination select {
    min-height: 2.75rem;
  }

  .photo-pagination__buttons button::before {
    font-size: var(--text-base-size);
    line-height: 1;
  }

  [data-test='photo-page-first']::before {
    content: '«';
  }

  [data-test='photo-page-previous']::before {
    content: '‹';
  }

  [data-test='photo-page-next']::before {
    content: '›';
  }

  [data-test='photo-page-last']::before {
    content: '»';
  }

  .photo-pagination label {
    grid-column: 1 / -1;
  }

  .photo-card {
    grid-template-columns: 1.7rem 5rem minmax(0, 1fr) 1rem;
    gap: 0.5rem;
    min-height: 5.75rem;
  }

  .photo-card-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .photo-card__actions {
    width: calc(100% - 1.7rem);
    justify-self: end;
  }

  .photo-card__edit {
    min-height: 2.75rem;
  }

  .photo-location-editor {
    grid-template-columns: minmax(0, 1fr);
    margin-left: 1.7rem;
  }

  .photo-location-editor__note,
  .photo-location-editor > p,
  .photo-location-editor__actions {
    grid-column: 1;
  }

  .photo-card__thumb-wrap {
    width: 5rem;
  }

  .photo-card__meta {
    display: grid;
  }
}

@media (prefers-reduced-motion: reduce) {
  .photos-state__spinner {
    animation: none;
  }
}
</style>
