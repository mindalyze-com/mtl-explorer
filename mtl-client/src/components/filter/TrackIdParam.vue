<template>
  <div class="track-id-param">
    <div class="track-id-param__head">
      <label class="track-id-param__label" :for="inputId">{{ label }}</label>
      <span class="track-id-param__head-meta">
        <span v-if="optional" class="track-id-param__optional">Optional</span>
        <span class="track-id-param__count">{{ selectedTrackIds.length }} selected</span>
        <span v-if="originTitle" class="track-id-param__origin" :title="originTitle">
          <i class="bi bi-diagram-3"></i>
        </span>
      </span>
    </div>

    <textarea
      :id="inputId"
      class="track-id-param__textarea"
      :value="modelValue || ''"
      rows="2"
      placeholder="Paste track IDs"
      @input="onTextInput"
      @blur="normalizeText"
    ></textarea>

    <div v-if="selectedTrackIds.length > 0" class="track-id-param__selected" aria-label="Selected tracks">
      <button
        v-for="trackId in selectedTrackIds"
        :key="trackId"
        type="button"
        class="track-id-param__chip"
        :title="selectedTrackLabel(trackId)"
        @click="removeTrack(trackId)"
      >
        <span>{{ selectedTrackLabel(trackId) }}</span>
        <i class="bi bi-x"></i>
      </button>
      <button type="button" class="track-id-param__clear" @click="emit('update:modelValue', '')">Clear</button>
    </div>

    <button type="button" class="track-id-param__open" @click="dialogVisible = true">
      <i class="bi bi-list-check"></i>
      <span>Choose tracks</span>
    </button>

    <Dialog
      v-model:visible="dialogVisible"
      modal
      :header="label"
      class="track-id-param__dialog"
      :style="{ width: 'min(92vw, 72rem)' }"
    >
      <div class="track-id-param__dialog-body">
        <div class="track-id-param__search-row">
          <span class="track-id-param__search-icon"><i class="bi bi-search"></i></span>
          <InputText v-model="query" class="track-id-param__search" placeholder="Search tracks" />
        </div>

        <div v-if="loading" class="track-id-param__state">
          <i class="pi pi-spin pi-spinner"></i>
          <span>Loading tracks...</span>
        </div>
        <div v-else-if="rows.length === 0" class="track-id-param__state">
          <i class="bi bi-list-check"></i>
          <span>No tracks match the current search.</span>
        </div>
        <DataTable
          v-else
          :value="rows"
          class="p-datatable-sm track-id-param__table"
          paginator
          :rows="10"
          :rows-per-page-options="[10, 25, 50, 100]"
          removable-sort
          scrollable
          scroll-height="52vh"
        >
          <Column header="" style="width: 3rem; min-width: 3rem; max-width: 3rem">
            <template #body="slotProps">
              <button
                type="button"
                class="track-id-param__toggle"
                :class="{ 'track-id-param__toggle--selected': isSelected(slotProps.data.id) }"
                :aria-label="isSelected(slotProps.data.id) ? 'Remove track' : 'Add track'"
                @click="toggleTrack(slotProps.data.id)"
              >
                <i :class="isSelected(slotProps.data.id) ? 'bi bi-check-lg' : 'bi bi-plus-lg'"></i>
              </button>
            </template>
          </Column>

          <Column field="id" header="ID" sortable style="width: 5rem; min-width: 5rem" />
          <Column field="displayName" header="Track" sortable style="min-width: 14rem">
            <template #body="slotProps">
              <div class="track-id-param__name-cell">
                <span>{{ slotProps.data.displayName }}</span>
                <span v-if="slotProps.data.trackDescription" class="track-id-param__description">
                  {{ slotProps.data.trackDescription }}
                </span>
              </div>
            </template>
          </Column>
          <Column field="startDate" header="Start" sortable style="min-width: 10rem">
            <template #body="slotProps">
              {{ formatDateAndTimeValue(slotProps.data.startDate) }}
            </template>
          </Column>
        </DataTable>
      </div>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, toRef } from 'vue';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import type { ParamDefinition } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';
import { formatDateAndTime } from '@/utils/Utils';
import { addTrackIdToText, formatTrackIds, parseTrackIdText, removeTrackIdFromText } from '@/utils/trackIdFilter';
import { useTrackBrowser } from '@/components/track-browser/useTrackBrowser';

defineOptions({ name: 'TrackIdParam' });

const props = defineProps<{
  paramDef?: ParamDefinition;
  modelValue?: string | null;
  tracks: GpsTrack[];
  loading?: boolean;
  optional?: boolean;
  originTitle?: string;
}>();

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void;
}>();

const inputId = computed(() => props.paramDef?.name ?? 'TRACK_IDS');
const label = computed(() => props.paramDef?.label || 'Selected tracks');
const dialogVisible = ref(false);
const selectedTrackIds = computed(() => parseTrackIdText(props.modelValue));
const selectedTrackIdSet = computed(() => new Set(selectedTrackIds.value));
const tracksById = computed(() => new Map(props.tracks.map((track) => [Number(track.id), track])));
const { query, rows } = useTrackBrowser(toRef(props, 'tracks'));

function isSelected(trackId: unknown): boolean {
  const id = Number(trackId);
  return Number.isFinite(id) && selectedTrackIdSet.value.has(id);
}

function selectedTrackLabel(trackId: number): string {
  const track = tracksById.value.get(trackId);
  const name = String(track?.trackName || track?.metaName || track?.trackDescription || '').trim();
  return name ? `#${trackId} ${name}` : `#${trackId}`;
}

function toggleTrack(trackId: unknown): void {
  const id = Number(trackId);
  if (!Number.isSafeInteger(id) || id <= 0) return;
  emit(
    'update:modelValue',
    isSelected(id) ? removeTrackIdFromText(props.modelValue, id) : addTrackIdToText(props.modelValue, id)
  );
}

function removeTrack(trackId: number): void {
  emit('update:modelValue', removeTrackIdFromText(props.modelValue, trackId));
}

function onTextInput(event: Event): void {
  const target = event.target as HTMLTextAreaElement | null;
  emit('update:modelValue', target?.value ?? '');
}

function normalizeText(): void {
  emit('update:modelValue', formatTrackIds(selectedTrackIds.value));
}

function formatDateAndTimeValue(value: Date | string | undefined | null): string {
  if (!value) return '';
  return formatDateAndTime(value instanceof Date ? value : new Date(value));
}
</script>

<style scoped>
.track-id-param {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  min-width: 0;
}

.track-id-param__head,
.track-id-param__search-row {
  display: flex;
  align-items: center;
  gap: 0.55rem;
}

.track-id-param__head {
  justify-content: space-between;
}

.track-id-param__head-meta {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
}

.track-id-param__label,
.track-id-param__count {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: var(--text-xs-lh);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.track-id-param__count {
  opacity: 0.75;
  white-space: nowrap;
}

.track-id-param__optional {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 1.25rem;
  border-radius: 999px;
  padding: 0.12rem 0.45rem;
  color: var(--text-muted);
  background: var(--surface-glass-heavy, var(--surface-ground));
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.track-id-param__origin {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1rem;
  height: 1rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  opacity: 0.68;
}

.track-id-param__textarea {
  width: 100%;
  min-width: 0;
  resize: vertical;
  border: 1px solid var(--border-default);
  border-radius: 0.5rem;
  padding: 0.55rem 0.65rem;
  color: var(--text-primary);
  background: var(--surface-glass-heavy);
  font: inherit;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.track-id-param__selected {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.track-id-param__chip,
.track-id-param__clear,
.track-id-param__toggle {
  border: 1px solid var(--border-default);
  background: var(--surface-glass-subtle);
  color: var(--text-secondary);
  cursor: pointer;
  font: inherit;
}

.track-id-param__chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  max-width: 18rem;
  min-height: 2rem;
  border-radius: 999px;
  padding: 0.3rem 0.55rem 0.3rem 0.75rem;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.track-id-param__chip span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.track-id-param__clear {
  min-height: 2rem;
  border-radius: 999px;
  padding: 0.3rem 0.7rem;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.track-id-param__open {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-height: 2.25rem;
  border: 1px solid var(--border-default);
  border-radius: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: var(--surface-glass-subtle);
  color: var(--text-secondary);
  font: inherit;
  font-size: var(--text-sm-size);
  font-weight: 600;
  line-height: var(--text-sm-lh);
  cursor: pointer;
}

.track-id-param__open:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}

.track-id-param__dialog-body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-width: 0;
}

.track-id-param__search-row {
  position: relative;
}

.track-id-param__search-icon {
  position: absolute;
  left: 0.7rem;
  z-index: 1;
  color: var(--text-muted);
  pointer-events: none;
}

.track-id-param__search {
  width: 100%;
  padding-left: 2rem;
}

.track-id-param__state {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 3rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}

.track-id-param__table {
  border: 1px solid var(--border-subtle);
  border-radius: 0.5rem;
  overflow: hidden;
}

.track-id-param__toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
}

.track-id-param__toggle--selected {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--text-inverse);
}

.track-id-param__name-cell {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 0.15rem;
}

.track-id-param__description {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
