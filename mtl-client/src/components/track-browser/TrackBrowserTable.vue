<template>
  <div class="track-browser-table" :class="{ 'track-browser-table--compact': compact }">
    <div class="track-browser-table__toolbar">
      <slot name="summary" />
      <div class="track-browser-table__sort">
        <label class="track-browser-table__sort-field">
          <span>Sort by</span>
          <select class="control-field" :value="sortField" aria-label="Sort tracks by" @change="onSortFieldChange">
            <option v-for="option in sortOptions" :key="option.field" :value="option.field">{{ option.label }}</option>
          </select>
        </label>
        <button
          type="button"
          class="track-browser-table__sort-direction"
          :aria-label="sortOrder === 1 ? 'Ascending order. Sort descending' : 'Descending order. Sort ascending'"
          :title="sortOrder === 1 ? 'Ascending order. Sort descending' : 'Descending order. Sort ascending'"
          @click="sortOrder = sortOrder === 1 ? -1 : 1"
        >
          <i :class="sortOrder === 1 ? 'bi bi-sort-up' : 'bi bi-sort-down'" aria-hidden="true" />
        </button>
      </div>
    </div>
    <!-- Mobile: card list -->
    <div v-if="compact" ref="cardsEl" class="track-browser-cards">
      <div v-if="rows.length === 0" class="track-browser-cards__empty">
        <template v-if="query.trim()">No tracks match &ldquo;{{ query.trim() }}&rdquo;</template>
        <template v-else>No tracks match the current view.</template>
      </div>
      <template v-for="row in paginatedRows" :key="row.id">
        <div
          class="track-browser-card"
          :class="{ 'track-browser-card--active': row.id === selectedTrackId }"
          @click="openRowDetails(row, $event)"
        >
          <div class="track-browser-card__header">
            <button
              type="button"
              class="track-browser-card__name"
              @click.stop="row.id !== undefined && emit('open-details', row.id)"
            >
              {{ row.displayName }}
            </button>
            <ActivityTypeBadge v-if="row.activityType" :type="row.activityType" size="xs" />
            <span v-else class="track-browser-table__state" :title="activityStateMessage(row)">
              Activity: {{ activityStateLabel(row) }}
            </span>
          </div>
          <div class="track-browser-card__lower">
            <button
              type="button"
              class="track-browser-card__map"
              :aria-label="`Center ${row.displayName} on map`"
              @click.stop="row.id !== undefined && emit('select-track', row.id)"
            >
              <TrackShapePreview
                :track-id="row.id!"
                :width="48"
                :height="32"
                :padding="3"
                class="track-browser-card__shape"
              />
            </button>
            <div class="track-browser-card__details">
              <TrackDescription
                v-if="row.trackDescription"
                :text="row.trackDescription"
                class="track-browser-card__desc"
              />
              <div v-if="curationBadges(row).length" class="track-browser-card__curation">
                <component
                  :is="sourceReviewTarget(row, badge) ? RouterLink : 'span'"
                  v-for="badge in curationBadges(row)"
                  :key="badge.key"
                  :to="sourceReviewTarget(row, badge)"
                  class="track-browser-curation-badge"
                  :class="`track-browser-curation-badge--${badge.key}`"
                  :title="badge.title"
                  :data-test="`curation-badge-${badge.key}`"
                  @click="sourceReviewTarget(row, badge) && $event.stopPropagation()"
                >
                  {{ badge.label }}
                </component>
              </div>
              <div class="track-browser-card__meta">
                <div class="track-browser-card__meta-row">
                  <span v-if="row.startDate">{{ formatDateAndTimeValue(row.startDate) }}</span>
                  <span
                    v-if="row.trackLengthInMeter"
                    v-tooltip.top="{ value: formatDistanceTooltip(row.trackLengthInMeter), showDelay: 400 }"
                    >{{ formatDistanceSmart(row.trackLengthInMeter) }}</span
                  >
                  <span
                    v-if="row.durationMillis != null"
                    v-tooltip.top="{ value: formatDurationTooltip(row.durationMillis), showDelay: 400 }"
                    >{{ formatDurationSmart(row.durationMillis) }}
                    {{ row.trackDurationInMotionSecs != null ? 'moving' : 'elapsed' }}</span
                  >
                  <span v-else>Time unavailable</span>
                </div>
                <div class="track-browser-card__meta-row track-browser-card__meta-row--energy">
                  <span
                    >Energy:
                    <TrackEnergyValue
                      :track="row"
                      :processing-pending="energyProcessingPending"
                      @track-updated="emit('track-updated', $event)"
                  /></span>
                  <button
                    type="button"
                    class="track-browser-table__info-btn"
                    aria-label="About energy"
                    @click.stop="showEnergyInfo($event)"
                  >
                    <i class="bi bi-info-circle"></i>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
      <div v-if="rows.length > mobilePageSize" class="track-browser-cards__pager">
        <Button
          :disabled="mobilePage <= 0"
          icon="pi pi-chevron-left"
          aria-label="Previous page"
          text
          size="small"
          @click="changeMobilePage(-1)"
        />
        <span>{{ mobilePage + 1 }} / {{ totalMobilePages }}</span>
        <Button
          :disabled="mobilePage >= totalMobilePages - 1"
          icon="pi pi-chevron-right"
          aria-label="Next page"
          text
          size="small"
          @click="changeMobilePage(1)"
        />
      </div>
    </div>

    <!-- Desktop: sortable table -->
    <template v-else>
      <DataTable
        :value="rows"
        :first="first"
        scrollable
        scroll-height="flex"
        class="p-datatable-sm track-browser-table__datatable"
        paginator
        :rows="pageSize"
        :rows-per-page-options="[10, 25, 50, 100, 250, 1000]"
        removable-sort
        :sort-field="sortField ?? undefined"
        :sort-order="sortOrder"
        :row-class="rowClass"
        selection-mode="single"
        @page="onPage"
        @sort="onDesktopSort"
        @row-click="onRowClick"
      >
        <template #empty>
          <template v-if="query.trim()">No tracks match &ldquo;{{ query.trim() }}&rdquo;</template>
          <template v-else>No tracks match the current view.</template>
        </template>

        <template #paginatorstart>
          <Button
            v-if="selectedTrackId != null && !selectedOnCurrentPage && selectedRowIndex >= 0"
            icon="pi pi-arrow-up"
            label="Jump to selected"
            text
            size="small"
            @click="jumpToSelected"
          />
        </template>

        <Column
          header=""
          style="
            width: var(--track-table-shape-column-width);
            min-width: var(--track-table-shape-column-width);
            max-width: var(--track-table-shape-column-width);
          "
        >
          <template #body="slotProps">
            <TrackShapePreview
              v-tooltip.top="{ value: 'Center on map', showDelay: 600 }"
              :track-id="slotProps.data.id"
              :width="48"
              :height="32"
              :padding="3"
              class="track-browser-table__shape"
              @click.stop="emit('select-track', slotProps.data.id)"
            />
          </template>
        </Column>

        <Column field="startDate" header="Start" sortable style="min-width: var(--track-table-date-column-width)">
          <template #body="slotProps">
            {{ formatDateAndTimeValue(slotProps.data.startDate) }}
          </template>
        </Column>

        <Column field="displayName" header="Track" sortable style="min-width: var(--track-table-name-column-width)">
          <template #body="slotProps">
            <div class="track-browser-table__name-cell">
              <span class="track-browser-table__name" :title="slotProps.data.displayName">{{
                slotProps.data.displayName
              }}</span>
              <TrackDescription
                v-if="slotProps.data.trackDescription"
                :text="slotProps.data.trackDescription"
                class="track-browser-table__name-desc"
              />
              <span v-if="curationBadges(slotProps.data).length" class="track-browser-table__curation">
                <component
                  :is="sourceReviewTarget(slotProps.data, badge) ? RouterLink : 'span'"
                  v-for="badge in curationBadges(slotProps.data)"
                  :key="badge.key"
                  :to="sourceReviewTarget(slotProps.data, badge)"
                  class="track-browser-curation-badge"
                  :class="`track-browser-curation-badge--${badge.key}`"
                  :title="badge.title"
                  :data-test="`curation-badge-${badge.key}`"
                  @click="sourceReviewTarget(slotProps.data, badge) && $event.stopPropagation()"
                >
                  {{ badge.label }}
                </component>
              </span>
            </div>
          </template>
        </Column>

        <Column
          field="activityType"
          header="Activity"
          sortable
          style="min-width: var(--track-table-activity-column-width)"
        >
          <template #body="slotProps">
            <ActivityTypeBadge v-if="slotProps.data.activityType" :type="slotProps.data.activityType" size="xs" />
            <span v-else class="track-browser-table__state" :title="activityStateMessage(slotProps.data)">
              {{ activityStateLabel(slotProps.data) }}
            </span>
          </template>
        </Column>

        <Column
          field="trackLengthInMeter"
          header="Distance"
          sortable
          class="number-column"
          style="min-width: var(--track-table-number-column-width)"
        >
          <template #body="slotProps">
            <span
              v-tooltip.top="{ value: formatDistanceTooltip(slotProps.data.trackLengthInMeter || 0), showDelay: 400 }"
            >
              {{ formatDistanceSmart(slotProps.data.trackLengthInMeter || 0) }}
            </span>
          </template>
        </Column>

        <Column
          field="durationMillis"
          header="Moving time"
          sortable
          class="number-column"
          style="min-width: var(--track-table-duration-column-width)"
        >
          <template #body="slotProps">
            <span
              v-if="slotProps.data.durationMillis != null"
              v-tooltip.top="{ value: formatDurationTooltip(slotProps.data.durationMillis), showDelay: 400 }"
            >
              {{ formatDurationSmart(slotProps.data.durationMillis || 0) }}
            </span>
            <span v-else>Unavailable</span>
          </template>
        </Column>

        <Column
          field="avgSpeedKmh"
          :header="`Moving ${currentMeasurementUnit('speed')}`"
          sortable
          class="number-column"
          style="min-width: var(--track-table-compact-number-column-width)"
        >
          <template #body="slotProps">
            {{ formatSpeed(slotProps.data.avgSpeedKmh) }}
          </template>
        </Column>

        <Column
          field="energyNetTotalWh"
          sortable
          class="number-column"
          style="min-width: var(--track-table-compact-number-column-width)"
        >
          <template #header>
            <span>Energy</span>
            <button
              type="button"
              class="track-browser-table__info-btn track-browser-table__header-info"
              aria-label="About energy"
              @click.stop="showEnergyInfo($event)"
            >
              <i class="bi bi-info-circle"></i>
            </button>
          </template>
          <template #body="slotProps">
            <TrackEnergyValue
              :track="slotProps.data"
              :processing-pending="energyProcessingPending"
              @track-updated="emit('track-updated', $event)"
            />
          </template>
        </Column>

        <Column
          field="explorationScore"
          header="Exploration"
          sortable
          class="number-column"
          style="min-width: var(--track-table-exploration-column-width)"
        >
          <template #body="slotProps">
            <span
              v-if="slotProps.data.explorationScore != null"
              v-tooltip.top="{
                value: `Share of this track covering new ground (not within ${formatDistance(EXPLORATION_CORRIDOR_WIDTH_M, 0)} of any prior track)`,
                showDelay: 300,
              }"
            >
              {{ formatNumber(slotProps.data.explorationScore * 100, 1) }}%
            </span>
            <span
              v-else-if="['SCHEDULED', 'IN_PROGRESS', 'NEEDS_RECALCULATION'].includes(slotProps.data.explorationStatus)"
              v-tooltip.top="{ value: 'Exploration score is being calculated', showDelay: 300 }"
              class="track-browser-table__pending"
            >
              <i class="pi pi-spin pi-spinner" style="font-size: var(--text-xs-size)" />
            </span>
            <span v-else class="track-browser-table__na">—</span>
          </template>
        </Column>

        <Column field="createDate" header="Imported" sortable style="min-width: var(--track-table-date-column-width)">
          <template #body="slotProps">
            <span :title="slotProps.data.indexedFile?.name || undefined">
              {{ formatDateAndTimeValue(slotProps.data.createDate) }}
            </span>
          </template>
        </Column>
      </DataTable>
    </template>

    <Popover ref="energyInfoPopover" append-to="body">
      <p class="track-browser-table__info-text">{{ ENERGY_TOOLTIP }}</p>
    </Popover>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, provide, ref, watch } from 'vue';
import { ENERGY_STATUS_CACHE } from '@/composables/useEnergyCalculationStatus';
import {
  formatDateAndTime,
  formatNumber,
  formatDistance,
  formatDistanceSmart,
  formatDurationSmart,
  formatDistanceTooltip,
  formatDurationTooltip,
  currentMeasurementUnit,
} from '@/utils/Utils';
import { getMeasurementSystem } from '@/composables/useMeasurementSystem';
import { speedDisplayValue } from '@/utils/units';
import { curationBadges, type CurationBadge } from '@/utils/statisticsCuration';
import { RouterLink } from 'vue-router';
import type { TrackRowViewModel } from './trackBrowser.types';
import TrackShapePreview from '@/components/ui/TrackShapePreview.vue';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';
import TrackDescription from '@/components/trackdetails/TrackDescription.vue';
import TrackEnergyValue from './TrackEnergyValue.vue';
import { useIndexerStatus } from '@/composables/useIndexerStatus';
import type { TrackSelectionEvents } from '@/components/filter/filterEvents';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';

function sourceReviewTarget(track: TrackRowViewModel, badge: CurationBadge) {
  return track.indexedFile?.id && (badge.key === 'review' || badge.key === 'timing')
    ? { name: 'review', params: { sourceId: track.indexedFile.id } }
    : undefined;
}

const EXPLORATION_CORRIDOR_WIDTH_M = 25;

const props = defineProps<{
  rows: TrackRowViewModel[];
  selectedTrackId: number | string | null;
  query: string;
  compact?: boolean;
  sortResetKey?: number;
}>();

const emit = defineEmits<TrackSelectionEvents & { 'track-updated': [track: GpsTrack] }>();
// Keep completed checks across virtual row remounts; discard them with this table.
provide(ENERGY_STATUS_CACHE, new Map());
const { jobSummaries } = useIndexerStatus();
const energyProcessingPending = computed(() =>
  jobSummaries.value.some(
    (job) =>
      job.job === 'energy' &&
      (job.pending > 0 || ['RUNNING', 'QUEUED', 'WAITING', 'RETRYING'].includes(job.state ?? ''))
  )
);

function activityStateLabel(track: TrackRowViewModel): string {
  return track.activityTypeSource == null ? 'Pending' : 'Unavailable';
}

function activityStateMessage(track: TrackRowViewModel): string {
  return track.activityTypeSource == null
    ? 'Waiting for activity classification.'
    : 'Activity classification is unavailable. Select an activity in Track details.';
}

const mobilePageSize = 20;
const mobilePage = ref(0);
const cardsEl = ref<HTMLElement | null>(null);

async function changeMobilePage(direction: number) {
  mobilePage.value += direction;
  await nextTick();
  cardsEl.value?.scrollIntoView({ block: 'start' });
}
const ENERGY_TOOLTIP =
  'Estimated external mechanical work from GPS-derived physics, not metabolic calorie burn or measured power-sensor data.';
const energyInfoPopover = ref<{ toggle: (event: Event) => void } | null>(null);

// Share ordering across the toolbar, column headers, and responsive layouts.
const sortField = ref<string>('startDate');
const sortOrder = ref<1 | -1>(-1);
const nameCollator = new Intl.Collator(undefined, { numeric: true });

const sortOptions = [
  { field: 'startDate', label: 'Date' },
  { field: 'createDate', label: 'Imported' },
  { field: 'trackLengthInMeter', label: 'Distance' },
  { field: 'durationMillis', label: 'Moving time' },
  { field: 'displayName', label: 'Name' },
  { field: 'explorationScore', label: 'Exploration' },
  { field: 'activityType', label: 'Activity' },
  { field: 'avgSpeedKmh', label: 'Moving speed' },
  { field: 'energyNetTotalWh', label: 'Energy' },
];

function getSortState() {
  return { field: sortField.value, order: sortOrder.value };
}

function restoreSortState(state: unknown): void {
  if (!state || typeof state !== 'object' || !('field' in state) || !('order' in state)) return;
  if (typeof state.field !== 'string') return;
  if (!sortOptions.some((option) => option.field === state.field) || (state.order !== 1 && state.order !== -1)) return;
  sortField.value = state.field;
  sortOrder.value = state.order;
}

defineExpose({ getSortState, restoreSortState });

function onSortFieldChange(event: Event) {
  const field = (event.target as HTMLSelectElement).value;
  sortField.value = field;
  sortOrder.value = ['displayName', 'activityType'].includes(field) ? 1 : -1;
}

function resetSortToNewest() {
  sortField.value = 'startDate';
  sortOrder.value = -1;
  mobilePage.value = 0;
  first.value = 0;
}

function onDesktopSort(event: {
  sortField?: string | ((item: unknown) => string) | null | undefined;
  sortOrder?: number | null | undefined;
}) {
  if (!event.sortField || typeof event.sortField !== 'string') {
    sortField.value = 'startDate';
    sortOrder.value = -1;
  } else {
    sortField.value = event.sortField;
    sortOrder.value = ((event.sortOrder ?? -1) > 0 ? 1 : -1) as 1 | -1;
  }
}

// Desktop pagination state
const first = ref(0);
const pageSize = ref(25);

function openRowDetails(row: TrackRowViewModel, event?: Event) {
  const selection = window.getSelection();
  const rowElement = event?.currentTarget;
  if (
    event instanceof MouseEvent &&
    event.detail &&
    selection?.toString() &&
    rowElement instanceof Element &&
    Array.from({ length: selection.rangeCount }, (_, index) => selection.getRangeAt(index)).some((range) =>
      range.intersectsNode(rowElement)
    )
  )
    return;
  if (row.id !== undefined) {
    emit('open-details', row.id);
  }
}

function onRowClick(event: { data: TrackRowViewModel; originalEvent?: Event }) {
  openRowDetails(event.data, event.originalEvent);
}

// Start at the first result when searching, switching views, or reordering.
watch([() => props.rows, sortField, sortOrder], () => {
  mobilePage.value = 0;
  first.value = 0;
});

watch(
  () => props.sortResetKey,
  () => resetSortToNewest()
);

const selectedRowIndex = computed(() => {
  if (props.selectedTrackId == null) return -1;
  return sortedRows.value.findIndex((r) => r.id === props.selectedTrackId);
});

const selectedOnCurrentPage = computed(() => {
  const idx = selectedRowIndex.value;
  if (idx < 0) return true;
  return idx >= first.value && idx < first.value + pageSize.value;
});

function jumpToSelected() {
  const idx = selectedRowIndex.value;
  if (idx >= 0) first.value = Math.floor(idx / pageSize.value) * pageSize.value;
}

function onPage(event: { first: number; rows: number }) {
  first.value = event.first;
  pageSize.value = event.rows;
}

const totalMobilePages = computed(() => Math.max(1, Math.ceil(props.rows.length / mobilePageSize)));
const sortedRows = computed(() => {
  const field = sortField.value as keyof TrackRowViewModel;
  const asc = sortOrder.value === 1;
  return [...props.rows].sort((a, b) => {
    const av = a[field] as number | string | Date | null | undefined;
    const bv = b[field] as number | string | Date | null | undefined;
    // Match the table's natural text order and keep missing values last.
    const aEmpty = av == null || av === '';
    const bEmpty = bv == null || bv === '';
    if (aEmpty && bEmpty) return 0;
    if (aEmpty) return 1;
    if (bEmpty) return -1;
    const cmp =
      typeof av === 'string' && typeof bv === 'string' ? nameCollator.compare(av, bv) : av < bv ? -1 : av > bv ? 1 : 0;
    return asc ? cmp : -cmp;
  });
});
const paginatedRows = computed(() => {
  const start = mobilePage.value * mobilePageSize;
  return sortedRows.value.slice(start, start + mobilePageSize);
});

function formatDateAndTimeValue(value: Date | null | undefined) {
  return value ? formatDateAndTime(value) : '';
}

function formatSpeed(value: number | null) {
  return value == null ? '—' : formatNumber(speedDisplayValue(value, getMeasurementSystem()), 1);
}

function rowClass(row: TrackRowViewModel) {
  return row.id === props.selectedTrackId ? 'track-browser-table__row--active' : '';
}

function showEnergyInfo(event: Event) {
  energyInfoPopover.value?.toggle(event);
}
</script>

<style scoped>
.track-browser-table {
  --track-table-shape-column-width: 3rem;
  --track-table-date-column-width: 8rem;
  --track-table-name-column-width: 10.5rem;
  --track-table-activity-column-width: 6rem;
  --track-table-number-column-width: 5.5rem;
  --track-table-duration-column-width: 6rem;
  --track-table-compact-number-column-width: 5rem;
  --track-table-exploration-column-width: 6rem;

  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  padding: 0 var(--track-browser-gutter, var(--dlg-padding)) 1rem;
}

/* Compact cards scroll with TrackBrowserView. Let their table grow to its
   content height so the parent can measure and scroll the complete list. */
.track-browser-table--compact {
  flex: 0 0 auto;
  overflow: visible;
}

.track-browser-table__datatable {
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
}

/* PrimeVue 4 uses .p-datatable-table-container; keep the older class for compatibility. */
.track-browser-table__datatable :deep(:is(.p-datatable-table-container, .p-datatable-wrapper)) {
  min-height: 0;
  overflow: auto !important;
  scrollbar-color: var(--text-muted) var(--surface-glass-heavy);
  scrollbar-width: thin;
}

.track-browser-table__datatable :deep(:is(.p-datatable-table-container, .p-datatable-wrapper))::-webkit-scrollbar {
  height: 10px;
  width: 10px;
}
.track-browser-table__datatable
  :deep(:is(.p-datatable-table-container, .p-datatable-wrapper))::-webkit-scrollbar-track {
  background: var(--surface-glass-heavy);
  border-radius: 5px;
}
.track-browser-table__datatable
  :deep(:is(.p-datatable-table-container, .p-datatable-wrapper))::-webkit-scrollbar-thumb {
  background: var(--text-muted);
  border-radius: 5px;
  min-height: 30px;
}
.track-browser-table__datatable
  :deep(:is(.p-datatable-table-container, .p-datatable-wrapper))::-webkit-scrollbar-thumb:hover {
  background: var(--text-secondary);
}

.track-browser-table__datatable :deep(.p-paginator) {
  flex: 0 0 auto;
  min-width: 0;
}

.track-browser-table__datatable :deep(.p-datatable-thead) {
  position: sticky;
  top: 0;
  z-index: 2;
}

.track-browser-table__datatable :deep(.number-column) {
  text-align: right;
}

.track-browser-table__datatable
  :deep(.number-column :is(.p-column-header-content, .p-datatable-column-header-content)) {
  justify-content: flex-end;
}

.track-browser-table__name-cell {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  width: var(--track-table-name-column-width);
  max-width: 100%;
}

.track-browser-table__name {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  overflow-wrap: anywhere;
}

.track-browser-table__name-desc {
  --track-description-font-size: var(--text-xs-size);
  --track-description-color: var(--text-muted);
}

.track-browser-table__curation,
.track-browser-card__curation {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  min-width: 0;
}

.track-browser-curation-badge {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-radius: 6px;
  border: 1px solid var(--border-subtle);
  padding: 0.08rem 0.38rem;
  color: var(--text-secondary);
  background: var(--surface-hover);
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
  font-weight: 650;
}

a.track-browser-curation-badge {
  text-decoration: underline;
  text-underline-offset: 0.15em;
}

a.track-browser-curation-badge:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.track-browser-curation-badge--statistics {
  border-color: rgba(217, 119, 6, 0.24);
  background: var(--warning-bg);
  color: var(--warning-text);
}

.track-browser-curation-badge--highlight {
  color: var(--accent-text);
  border-color: var(--accent-subtle);
  background: var(--accent-bg);
}

.track-browser-table__actions {
  display: flex;
  gap: 0.15rem;
}

.track-browser-table__shape {
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.15s;
}

.track-browser-table__shape:hover {
  opacity: 1;
}

.track-browser-card__shape {
  cursor: pointer;
}

.track-browser-table__datatable :deep(.track-browser-table__row--active) {
  background: var(--table-row-active) !important;
}

.track-browser-table__datatable :deep(tr) {
  cursor: pointer;
}

.track-browser-table__toolbar {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem 1rem;
  padding: 0.65rem 0;
}

.track-browser-table__sort {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  margin-left: auto;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}

.track-browser-table__sort-field {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.track-browser-table__sort select {
  max-width: 10rem;
  min-height: var(--control-field-height);
  padding: 0.4rem 0.5rem;
  border: 1px solid var(--border-default);
  border-radius: 0.5rem;
  background: var(--surface-elevated);
  color: var(--text-primary);
  font: inherit;
  cursor: pointer;
}

.track-browser-table__sort-direction {
  display: grid;
  place-items: center;
  width: var(--control-field-height);
  height: var(--control-field-height);
  border: 1px solid var(--border-default);
  border-radius: 0.5rem;
  background: var(--surface-elevated);
  color: var(--text-secondary);
  cursor: pointer;
}

.track-browser-table__sort-direction:hover {
  background: var(--surface-hover);
  color: var(--accent-text);
}

.track-browser-table__sort select:focus-visible,
.track-browser-table__sort-direction:focus-visible,
.track-browser-card__name:focus-visible,
.track-browser-card__map:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

@container track-browser (max-width: 600px) {
  .track-browser-table__toolbar {
    align-items: stretch;
    flex-direction: column;
    gap: 0.65rem;
  }

  .track-browser-table__sort {
    justify-content: space-between;
    margin-left: 0;
  }

  .track-browser-table__sort-field {
    flex: 1;
    justify-content: space-between;
  }
}

.track-browser-table__na {
  color: var(--text-faint);
}

.track-browser-table__state {
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}

/* ---- Mobile card list ---- */
.track-browser-cards {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0 0 0.75rem;
}

.track-browser-cards__empty {
  text-align: center;
  padding: 2rem 0;
  color: var(--text-muted);
}

.track-browser-card {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.65rem 0.75rem;
  border-radius: 0.6rem;
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  cursor: pointer;
  transition: background 0.15s;
}

.track-browser-card:active {
  background: var(--accent-bg);
}

.track-browser-card--active {
  border-color: var(--accent);
  background: var(--accent-bg);
}

.track-browser-card__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem;
}

.track-browser-card__lower {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  min-width: 0;
}

.track-browser-card__details {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.track-browser-card__name {
  flex: 1 1 100%;
  min-height: 2.75rem;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  font-weight: 600;
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;
}

@container track-browser (min-width: 601px) {
  .track-browser-card__name {
    flex-basis: 0;
  }
}

.track-browser-card__map {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  min-width: 3rem;
  min-height: 2.75rem;
  padding: 0;
  border: 0;
  border-radius: 0.4rem;
  background: transparent;
  cursor: pointer;
}

.track-browser-card__shape {
  cursor: pointer;
  flex-shrink: 0;
  align-self: flex-start;
  opacity: 0.72;
  transition: opacity 0.15s;
}

.track-browser-card:active .track-browser-card__shape,
.track-browser-card__shape:hover {
  opacity: 1;
}

.track-browser-card__meta {
  display: flex;
  flex-direction: column;
  gap: 0.12rem;
  font-size: var(--text-sm-size);
  color: var(--text-muted);
  min-width: 0;
}

.track-browser-card__meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.5rem;
  min-width: 0;
  line-height: 1.2;
}

.track-browser-card__meta-row--energy {
  gap: 0.25rem;
}

.track-browser-table__header-info {
  margin-left: 0.2rem;
}

.track-browser-table__info-text {
  max-width: min(240px, calc(100vw - 2rem));
}

.track-browser-card__desc {
  --track-description-font-size: var(--text-sm-size);
  --track-description-color: var(--text-muted);
  font-style: italic;
}

.track-browser-card__actions {
  display: flex;
  gap: 0.25rem;
  margin-top: 0.15rem;
}

.track-browser-cards__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.5rem 0;
  font-size: var(--text-sm-size);
  color: var(--text-muted);
}

.track-browser-cards__group-header {
  font-size: var(--text-xs-size);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--accent-text);
  padding: 0.6rem 0.15rem 0.15rem;
}
</style>
