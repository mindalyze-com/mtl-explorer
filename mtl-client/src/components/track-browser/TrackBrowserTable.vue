<template>
  <div class="track-browser-table" :class="compact ? '' : 'panel-scroll'">
    <!-- Mobile: card list -->
    <div v-if="compact" class="track-browser-cards">
      <!-- Sort bar -->
      <div class="track-browser-cards__sort-bar">
        <span class="track-browser-cards__sort-label"><i class="bi bi-sort-down"></i> Sort:</span>
        <div class="track-browser-cards__sort-options">
          <button
            v-for="opt in sortOptions"
            :key="opt.field"
            class="sort-chip"
            :class="{ 'sort-chip--active': mobileSortField === opt.field }"
            @click="onMobileSortChange(opt.field)"
          >{{ opt.label }}<i v-if="mobileSortField === opt.field" :class="mobileSortAsc ? 'bi bi-arrow-up' : 'bi bi-arrow-down'" class="sort-chip__dir"></i></button>
        </div>
      </div>
      <div v-if="rows.length === 0" class="track-browser-cards__empty">
        <template v-if="query.trim()">No tracks match &ldquo;{{ query.trim() }}&rdquo;</template>
        <template v-else>No tracks match the current view.</template>
      </div>
      <template v-for="row in paginatedRows" :key="row.id">
        <div
          class="track-browser-card"
          :class="{ 'track-browser-card--active': row.id === selectedTrackId }"
          @click="row.id !== undefined && emit('open-details', row.id)"
        >
          <div class="track-browser-card__header">
            <strong class="track-browser-card__name">{{ row.displayName }}</strong>
            <ActivityTypeBadge v-if="row.activityType" :type="row.activityType" size="xs" />
          </div>
          <div class="track-browser-card__lower">
            <TrackShapePreview
              :trackId="row.id!"
              :width="48"
              :height="32"
              :padding="3"
              class="track-browser-card__shape"
              @click.stop="row.id !== undefined && emit('select-track', row.id)"
            />
            <div class="track-browser-card__details">
              <div v-if="row.trackDescription" class="track-browser-card__desc">{{ row.trackDescription }}</div>
              <div class="track-browser-card__meta">
                <div class="track-browser-card__meta-row">
                  <span v-if="row.startDate">{{ formatDateAndTimeValue(row.startDate) }}</span>
                  <span v-if="row.trackLengthInMeter"
                        v-tooltip.top="{ value: formatDistanceTooltip(row.trackLengthInMeter), showDelay: 400 }">{{ formatDistanceSmart(row.trackLengthInMeter) }}</span>
                  <span v-if="row.durationMillis"
                        v-tooltip.top="{ value: formatDurationTooltip(row.durationMillis), showDelay: 400 }">{{ formatDurationSmart(row.durationMillis) }}</span>
                </div>
                <div v-if="row.energyNetTotalWh" class="track-browser-card__meta-row track-browser-card__meta-row--energy">
                  <span>{{ formatEnergy(row.energyNetTotalWh) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
      <div v-if="rows.length > mobilePageSize" class="track-browser-cards__pager">
        <Button :disabled="mobilePage <= 0" icon="pi pi-chevron-left" text size="small" @click="mobilePage--" />
        <span>{{ mobilePage + 1 }} / {{ totalMobilePages }}</span>
        <Button :disabled="mobilePage >= totalMobilePages - 1" icon="pi pi-chevron-right" text size="small" @click="mobilePage++" />
      </div>
    </div>

    <!-- Desktop: sort bar + DataTable -->
    <template v-else>
    <div class="track-browser-table__desktop-sort-bar">
      <span class="track-browser-cards__sort-label"><i class="bi bi-sort-down"></i> Sort:</span>
      <div class="track-browser-cards__sort-options">
        <button
          v-for="opt in sortOptions"
          :key="opt.field"
          class="sort-chip"
          :class="{ 'sort-chip--active': desktopSortField === opt.field }"
          @click="onDesktopSortChipChange(opt.field)"
        >{{ opt.label }}<i v-if="desktopSortField === opt.field" :class="desktopSortOrder === 1 ? 'bi bi-arrow-up' : 'bi bi-arrow-down'" class="sort-chip__dir"></i></button>
      </div>
    </div>
    <DataTable
      :value="rows"
      :first="first"
      scrollable
      scrollHeight="flex"
      class="p-datatable-sm track-browser-table__datatable"
      paginator
      :rows="25"
      :rowsPerPageOptions="[10, 25, 50, 100, 250, 1000]"
      removable-sort
      :sortField="desktopSortField ?? undefined"
      :sortOrder="desktopSortOrder"
      :rowClass="rowClass"
      selectionMode="single"
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

      <Column header="" style="width: 3.5rem; min-width: 3.5rem; max-width: 3.5rem">
        <template #body="slotProps">
          <TrackShapePreview :trackId="slotProps.data.id" :width="48" :height="32" :padding="3" class="track-browser-table__shape" v-tooltip.top="{ value: 'Center on map', showDelay: 600 }" @click.stop="emit('select-track', slotProps.data.id)" />
        </template>
      </Column>

      <Column field="startDate" header="Start" sortable style="min-width: 10rem">
        <template #body="slotProps">
          {{ formatDateAndTimeValue(slotProps.data.startDate) }}
        </template>
      </Column>

      <Column field="displayName" header="Track" sortable style="min-width: 16rem">
        <template #body="slotProps">
          <div class="track-browser-table__name-cell">
            <strong>{{ slotProps.data.displayName }}</strong>
            <span v-if="slotProps.data.trackDescription">{{ slotProps.data.trackDescription }}</span>
          </div>
        </template>
      </Column>

      <Column field="activityType" header="Activity" sortable style="min-width: 8rem">
        <template #body="slotProps">
          <ActivityTypeBadge v-if="slotProps.data.activityType" :type="slotProps.data.activityType" size="xs" />
        </template>
      </Column>

      <Column field="trackLengthInMeter" header="Distance" sortable class="number-column" style="min-width: 8rem">
        <template #body="slotProps">
          <span v-tooltip.top="{ value: formatDistanceTooltip(slotProps.data.trackLengthInMeter || 0), showDelay: 400 }">
            {{ formatDistanceSmart(slotProps.data.trackLengthInMeter || 0) }}
          </span>
        </template>
      </Column>

      <Column field="durationMillis" header="Duration" sortable class="number-column" style="min-width: 8rem">
        <template #body="slotProps">
          <span v-tooltip.top="{ value: formatDurationTooltip(slotProps.data.durationMillis || 0), showDelay: 400 }">
            {{ formatDurationSmart(slotProps.data.durationMillis || 0) }}
          </span>
        </template>
      </Column>

      <Column field="avgSpeedKmh" header="Avg km/h" sortable class="number-column" style="min-width: 7rem">
        <template #body="slotProps">
          {{ formatSpeed(slotProps.data.avgSpeedKmh) }}
        </template>
      </Column>

      <Column field="energyNetTotalWh" header="Energy" sortable class="number-column" style="min-width: 7rem">
        <template #body="slotProps">
          {{ formatEnergy(slotProps.data.energyNetTotalWh) }}
        </template>
      </Column>

      <Column field="explorationScore" header="Exploration" sortable class="number-column" style="min-width: 7rem">
        <template #body="slotProps">
          <span v-if="slotProps.data.explorationScore != null"
                v-tooltip.top="{ value: 'Share of this track covering new ground (not within 25m of any prior track)', showDelay: 300 }">
            {{ formatNumber(slotProps.data.explorationScore * 100, 1) }}%
          </span>
          <span v-else-if="['SCHEDULED', 'IN_PROGRESS', 'NEEDS_RECALCULATION'].includes(slotProps.data.explorationStatus)"
                v-tooltip.top="{ value: 'Exploration score is being calculated', showDelay: 300 }"
                class="track-browser-table__pending">
            <i class="pi pi-spin pi-spinner" style="font-size: 0.75rem" />
          </span>
          <span v-else class="track-browser-table__na">—</span>
        </template>
      </Column>

      <Column field="createDate" header="Imported" sortable style="min-width: 10rem">
        <template #body="slotProps">
          <span :title="slotProps.data.indexedFile?.name || undefined">
            {{ formatDateAndTimeValue(slotProps.data.createDate) }}
          </span>
        </template>
      </Column>
    </DataTable>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { formatDateAndTime, formatDistance, formatDuration, formatNumber, formatDistanceSmart, formatDurationSmart, formatDistanceTooltip, formatDurationTooltip } from '@/utils/Utils';
import type { TrackRowViewModel } from './trackBrowser.types';
import TrackShapePreview from '@/components/ui/TrackShapePreview.vue';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';

const props = defineProps<{
  rows: TrackRowViewModel[];
  selectedTrackId: number | string | null;
  query: string;
  compact?: boolean;
}>();

const emit = defineEmits<{
  (event: 'select-track', value: number | string): void;
  (event: 'open-details', value: number | string): void;
}>();

const mobilePageSize = 20;
const mobilePage = ref(0);

// Mobile sort state
const mobileSortField = ref<string>('startDate');
const mobileSortAsc = ref(false);

const sortOptions = [
  { field: 'startDate',          label: 'Date' },
  { field: 'createDate',         label: 'Imported' },
  { field: 'trackLengthInMeter', label: 'Distance' },
  { field: 'durationMillis',     label: 'Duration' },
  { field: 'displayName',        label: 'Name' },
  { field: 'explorationScore',   label: 'Exploration' },
];

function onMobileSortChange(field: string) {
  if (mobileSortField.value === field) {
    mobileSortAsc.value = !mobileSortAsc.value;
  } else {
    mobileSortField.value = field;
    mobileSortAsc.value = false;
  }
  mobilePage.value = 0;
}

// Desktop sort state
const desktopSortField = ref<string>('startDate');
const desktopSortOrder = ref<1 | -1>(-1);

function onDesktopSortChipChange(field: string) {
  if (desktopSortField.value === field) {
    desktopSortOrder.value = desktopSortOrder.value === 1 ? -1 : 1;
  } else {
    desktopSortField.value = field;
    desktopSortOrder.value = -1;
  }
}

function onDesktopSort(event: { sortField?: string | ((item: unknown) => string) | null | undefined; sortOrder?: number | null | undefined }) {
  if (!event.sortField || typeof event.sortField !== 'string') {
    desktopSortField.value = 'startDate';
    desktopSortOrder.value = -1;
  } else {
    desktopSortField.value = event.sortField;
    desktopSortOrder.value = ((event.sortOrder ?? -1) > 0 ? 1 : -1) as 1 | -1;
  }
}

// Desktop pagination state
const first = ref(0);
const pageSize = ref(25);

function onRowClick(event: { data: TrackRowViewModel }) {
  if (event.data.id !== undefined) {
    emit('open-details', event.data.id);
  }
}

// Reset all pages when filter results change
watch(() => props.rows.length, () => {
  mobilePage.value = 0;
  first.value = 0;
});

const selectedRowIndex = computed(() => {
  if (props.selectedTrackId == null) return -1;
  return props.rows.findIndex((r) => r.id === props.selectedTrackId);
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
const sortedMobileRows = computed(() => {
  const field = mobileSortField.value as keyof TrackRowViewModel;
  const asc = mobileSortAsc.value;
  return [...props.rows].sort((a, b) => {
    const av = a[field] as number | string | Date | null | undefined;
    const bv = b[field] as number | string | Date | null | undefined;
    if (av == null && bv == null) return 0;
    if (av == null) return asc ? -1 : 1;
    if (bv == null) return asc ? 1 : -1;
    const cmp = av < bv ? -1 : av > bv ? 1 : 0;
    return asc ? cmp : -cmp;
  });
});
const paginatedRows = computed(() => {
  const start = mobilePage.value * mobilePageSize;
  return sortedMobileRows.value.slice(start, start + mobilePageSize);
});

function formatDateAndTimeValue(value: Date | null | undefined) {
  return value ? formatDateAndTime(value) : '';
}

function formatSpeed(value: number | null) {
  return value == null ? '' : formatNumber(value, 1);
}

function formatEnergy(value: number | null) {
  return value == null ? '' : formatNumber(value, 0) + ' Wh';
}

function rowClass(row: TrackRowViewModel) {
  return row.id === props.selectedTrackId ? 'track-browser-table__row--active' : '';
}
</script>

<style scoped>
.track-browser-table {
  min-height: 0;
  padding: 0 var(--dlg-padding) 1rem;
}

/* ---- Always-visible horizontal scrollbar ---- */
.track-browser-table__datatable :deep(.p-datatable-wrapper) {
  overflow-x: auto !important;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

/* Force scrollbar to always show (WebKit / Blink) */
.track-browser-table__datatable :deep(.p-datatable-wrapper)::-webkit-scrollbar {
  height: 10px;
}
.track-browser-table__datatable :deep(.p-datatable-wrapper)::-webkit-scrollbar-track {
  background: var(--surface-ground, #f0f0f0);
  border-radius: 5px;
}
.track-browser-table__datatable :deep(.p-datatable-wrapper)::-webkit-scrollbar-thumb {
  background: var(--text-muted, #aaa);
  border-radius: 5px;
  min-height: 30px;
}
.track-browser-table__datatable :deep(.p-datatable-wrapper)::-webkit-scrollbar-thumb:hover {
  background: var(--text-secondary, #888);
}

/* Firefox: force always-visible scrollbar */
.track-browser-table__datatable :deep(.p-datatable-wrapper) {
  scrollbar-width: auto;
  scrollbar-color: var(--text-muted, #aaa) var(--surface-ground, #f0f0f0);
}


.track-browser-table__datatable :deep(.p-datatable-thead) {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--table-header-bg);
}

.track-browser-table__datatable :deep(.number-column) {
  text-align: right;
}

.track-browser-table__datatable :deep(.number-column .p-column-header-content) {
  justify-content: flex-end;
}

.track-browser-table__name-cell {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.track-browser-table__name-cell span {
  font-size: 0.8rem;
  color: var(--text-muted);
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

/* ---- Mobile sort bar ---- */
.track-browser-cards__sort-bar {
  display: flex;
  align-items: flex-start;
  gap: 0.4rem;
  padding: 0.3rem 0 0.4rem;
  flex-wrap: nowrap;
}

.track-browser-cards__sort-label {
  flex-shrink: 0;
  font-size: 0.72rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--text-faint);
  padding-top: 0.3rem;
}

.track-browser-cards__sort-options {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.sort-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  padding: 0.2rem 0.55rem;
  border-radius: 1rem;
  border: 1px solid var(--border-default);
  background: var(--surface-elevated);
  font-size: 0.75rem;
  color: var(--text-muted);
  cursor: pointer;
  transition: background 0.12s, color 0.12s, border-color 0.12s;
  white-space: nowrap;
}

.sort-chip:active {
  background: var(--accent-bg);
}

.sort-chip--active {
  background: var(--accent-bg);
  border-color: var(--accent-subtle);
  color: var(--accent-text);
  font-weight: 600;
}

.sort-chip__dir {
  font-size: 0.65rem;
}

.track-browser-table__na {
  color: var(--text-faint);
}

/* ---- Desktop sort bar ---- */
.track-browser-table__desktop-sort-bar {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.45rem var(--dlg-padding) 0.3rem;
  flex-wrap: wrap;
}

/* ---- Mobile card list ---- */
.track-browser-cards {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.5rem var(--dlg-padding) 0.75rem;
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
  flex: 1;
  font-size: 0.92rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  font-size: 0.8rem;
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
  gap: 0;
}

.track-browser-card__desc {
  font-size: 0.8rem;
  color: var(--text-muted);
  font-style: italic;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  font-size: 0.85rem;
  color: var(--text-muted);
}

.track-browser-cards__group-header {
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--accent-text);
  padding: 0.6rem 0.15rem 0.15rem;
}
</style>