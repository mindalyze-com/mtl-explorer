<template>
  <section class="result-group-selector" aria-label="Included categories">
    <div class="result-group-selector__header">
      <p class="result-group-selector__summary">
        <strong>{{ matchingTrackSummary }}</strong>
        <span>{{ categorySelectionSummary }}</span>
      </p>
      <span v-if="loading" class="result-group-selector__loading"><i class="bi bi-arrow-repeat"></i> Updating</span>
    </div>

    <label class="result-group-selector__master">
      <input
        type="checkbox"
        :checked="allCategories"
        :indeterminate="masterIndeterminate"
        @change="setAllCategories(eventChecked($event))"
      />
      <span>
        <strong>All categories</strong>
        <small>Future categories are included automatically.</small>
      </span>
    </label>

    <div class="result-group-selector__actions" aria-label="Category selection actions">
      <button type="button" @click="selectAll">
        <i class="bi bi-check2-all" aria-hidden="true"></i>
        Select current
      </button>
      <button type="button" @click="selectNone">
        <i class="bi bi-x-lg" aria-hidden="true"></i>
        Clear selection
      </button>
    </div>

    <label v-if="allRows.length > SEARCH_THRESHOLD" class="result-group-selector__search">
      <i class="bi bi-search"></i>
      <input v-model="search" type="search" placeholder="Search categories" />
    </label>

    <template v-if="numericBandsEnabled">
      <div class="result-group-selector__bands">
        <label
          v-for="band in gradientBands"
          :key="band.key"
          class="result-group-selector__band"
          :class="{ 'result-group-selector__band--empty': band.entries.length === 0 }"
          :title="band.title"
        >
          <input
            type="checkbox"
            :checked="bandSelection(band).all"
            :indeterminate="bandSelection(band).partial"
            :disabled="band.entries.length === 0"
            @change="toggleBand(band)"
          />
          <span class="result-group-selector__band-label">{{ band.label }}</span>
          <span class="result-group-selector__band-gradient" :style="{ background: bandGradient(band) }"></span>
          <span class="result-group-selector__count">{{ band.count }}</span>
        </label>
      </div>

      <details class="result-group-selector__details" :open="search.trim().length > 0 || undefined">
        <summary>Individual buckets</summary>
        <div class="result-group-selector__list">
          <GroupRow
            v-for="groupRow in filteredRows"
            :key="groupRow.id"
            :row="groupRow"
            :checked="isSelected(groupRow.key)"
            @change="toggleRow(groupRow, $event)"
          />
        </div>
      </details>
    </template>

    <div v-else class="result-group-selector__list">
      <label
        v-for="groupRow in filteredRows"
        :key="groupRow.id"
        class="result-group-selector__row"
        :class="{ 'result-group-selector__row--missing': groupRow.missing }"
      >
        <input
          type="checkbox"
          :checked="isSelected(groupRow.key)"
          @change="toggleRow(groupRow, eventChecked($event))"
        />
        <span
          v-if="groupRow.color"
          class="result-group-selector__swatch"
          :style="{ backgroundColor: groupRow.color }"
        ></span>
        <span class="result-group-selector__label">
          {{ groupRow.label }}
          <small v-if="groupRow.missing">No matches with current parameters</small>
        </span>
        <span class="result-group-selector__count">{{ groupRow.count }}</span>
      </label>
      <p v-if="filteredRows.length === 0" class="result-group-selector__empty">No categories match the search.</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, ref, type PropType } from 'vue';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterResultGroupKey } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupKey';
import type { FilterResultGroupSelection } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupSelection';
import type { FilterResultGroupSummary } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupSummary';
import { ColorPalette } from '@/components/filter/ColorPalette';
import {
  colorForFilterGroup,
  formatFilterGroupLabel,
  gradientBucketCount,
  isSequentialGradientFilter,
} from '@/utils/filterMetadata';
import {
  buildNumericGradientBands,
  colorForNumericBucket,
  hasOnlyNumericBuckets,
  type NumericGradientBand,
} from '@/utils/numericGradientBands';
import {
  availableResultGroupKeys,
  isResultGroupSelected,
  resultGroupKeyId,
  selectAllAvailableResultGroups,
  selectNoResultGroups,
  unavailableSelectedResultGroups,
  updateResultGroupSelection,
} from '@/utils/resultGroupSelection';

const SEARCH_THRESHOLD = 12;

type GroupRowData = {
  id: string;
  key: FilterResultGroupKey;
  group: string | null;
  label: string;
  color: string | null;
  count: number;
  missing: boolean;
};
type NumericGroupRow = GroupRowData & { group: string };

const props = withDefaults(
  defineProps<{
    availableGroups?: FilterResultGroupSummary[];
    selection?: FilterResultGroupSelection;
    filterInfo?: FilterInfo;
    palette?: ColorPalette;
    effectiveCount: number;
    preSelectionCount: number;
    loading?: boolean;
  }>(),
  {
    availableGroups: () => [],
    selection: undefined,
    filterInfo: undefined,
    palette: undefined,
    loading: false,
  }
);

const emit = defineEmits<{
  (event: 'update:selection', value: FilterResultGroupSelection | undefined): void;
}>();

const search = ref('');
const availableKeys = computed(() => availableResultGroupKeys(props.availableGroups));
const availableRows = computed((): GroupRowData[] =>
  props.availableGroups
    .filter((summary): summary is FilterResultGroupSummary & { key: FilterResultGroupKey } => summary.key != null)
    .map((summary) => makeRow(summary.key, Number(summary.count ?? 0), false))
);
const missingRows = computed((): GroupRowData[] =>
  unavailableSelectedResultGroups(props.selection, availableKeys.value).map((key) => makeRow(key, 0, true))
);
const allRows = computed((): GroupRowData[] => [...availableRows.value, ...missingRows.value]);
const filteredRows = computed((): GroupRowData[] => {
  const query = search.value.trim().toLocaleLowerCase();
  if (!query) return allRows.value;
  return allRows.value.filter((row) => row.label.toLocaleLowerCase().includes(query));
});
const allCategories = computed((): boolean => props.selection == null);
const selectedAvailableCount = computed(
  (): number => availableKeys.value.filter((key) => isResultGroupSelected(key, props.selection)).length
);
const matchingTrackSummary = computed((): string => {
  const matchingLabel = props.preSelectionCount === 1 ? 'matching track' : 'matching tracks';
  return props.effectiveCount === props.preSelectionCount
    ? `${props.effectiveCount} ${matchingLabel}`
    : `${props.effectiveCount} of ${props.preSelectionCount} ${matchingLabel}`;
});
const categorySelectionSummary = computed((): string => {
  const total = availableRows.value.length;
  if (allCategories.value) return `All ${total} selected`;
  const currentSummary = `${selectedAvailableCount.value} of ${total} current selected`;
  const unavailableCount = missingRows.value.length;
  if (unavailableCount === 0) return currentSummary;
  return `${currentSummary} · ${unavailableCount} unavailable`;
});
const masterIndeterminate = computed((): boolean => !allCategories.value && selectedAvailableCount.value > 0);
const numericRows = computed(() => availableRows.value.filter((row): row is NumericGroupRow => row.group != null));
const numericBandsEnabled = computed(
  (): boolean =>
    isSequentialGradientFilter(props.filterInfo?.filterConfig) &&
    numericRows.value.length === availableRows.value.length &&
    hasOnlyNumericBuckets(numericRows.value.map((row) => ({ group: row.group, count: row.count })))
);
const numericBucketCount = computed((): number => gradientBucketCount(props.filterInfo));
const gradientBands = computed((): NumericGradientBand<NumericGroupRow>[] =>
  buildNumericGradientBands(numericRows.value, numericBucketCount.value)
);

function makeRow(key: FilterResultGroupKey, count: number, missing: boolean): GroupRowData {
  const group = key.value;
  const palette = props.palette;
  const color =
    group != null && palette && !palette.isEmptyColorPalette()
      ? colorForFilterGroup(palette, group, props.filterInfo)
      : null;
  return {
    id: resultGroupKeyId(key),
    key,
    group,
    label: group === null ? 'Ungrouped' : formatFilterGroupLabel(group, props.filterInfo),
    color,
    count,
    missing,
  };
}

function eventChecked(event: Event): boolean {
  return (event.target as HTMLInputElement).checked;
}

function isSelected(key: FilterResultGroupKey): boolean {
  return isResultGroupSelected(key, props.selection);
}

function setAllCategories(checked: boolean): void {
  emit('update:selection', checked ? undefined : selectNoResultGroups());
}

function selectAll(): void {
  emit('update:selection', selectAllAvailableResultGroups(props.selection, availableKeys.value));
}

function selectNone(): void {
  emit('update:selection', selectNoResultGroups());
}

function toggleRow(row: GroupRowData, checked: boolean): void {
  emit('update:selection', updateResultGroupSelection(props.selection, availableKeys.value, [row.key], checked));
}

function bandSelection(band: NumericGradientBand<NumericGroupRow>): { all: boolean; partial: boolean } {
  const selectedCount = band.entries.filter((row) => isSelected(row.key)).length;
  return {
    all: band.entries.length > 0 && selectedCount === band.entries.length,
    partial: selectedCount > 0 && selectedCount < band.entries.length,
  };
}

function toggleBand(band: NumericGradientBand<NumericGroupRow>): void {
  const nextChecked = !bandSelection(band).all;
  emit(
    'update:selection',
    updateResultGroupSelection(
      props.selection,
      availableKeys.value,
      band.entries.map((entry) => entry.key),
      nextChecked
    )
  );
}

function bandGradient(band: NumericGradientBand<NumericGroupRow>): string {
  const colors = props.palette?.pColors ?? [];
  return `linear-gradient(90deg, ${colorForNumericBucket(
    band.start,
    numericBucketCount.value,
    colors
  )}, ${colorForNumericBucket(band.end, numericBucketCount.value, colors)})`;
}

const GroupRow = defineComponent({
  name: 'FilterResultGroupRow',
  props: {
    row: { type: Object as PropType<GroupRowData>, required: true },
    checked: { type: Boolean, required: true },
  },
  emits: ['change'],
  setup(rowProps, { emit: rowEmit }) {
    return () =>
      h(
        'label',
        {
          class: ['result-group-selector__row', rowProps.row.missing && 'result-group-selector__row--missing'],
        },
        [
          h('input', {
            type: 'checkbox',
            checked: rowProps.checked,
            onChange: (event: Event) => rowEmit('change', eventChecked(event)),
          }),
          rowProps.row.color
            ? h('span', {
                class: 'result-group-selector__swatch',
                style: { backgroundColor: rowProps.row.color },
              })
            : null,
          h('span', { class: 'result-group-selector__label' }, [
            rowProps.row.label,
            rowProps.row.missing ? h('small', 'No matches with current parameters') : null,
          ]),
          h('span', { class: 'result-group-selector__count' }, String(rowProps.row.count)),
        ]
      );
  },
});
</script>

<style scoped>
.result-group-selector {
  display: grid;
  gap: 0;
}

.result-group-selector__header,
.result-group-selector__master,
.result-group-selector__row,
.result-group-selector__band {
  display: flex;
  align-items: center;
}

.result-group-selector__header {
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.7rem;
}

.result-group-selector__summary {
  display: grid;
  gap: 0.08rem;
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
}

.result-group-selector__summary strong {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: var(--font-semibold);
}

.result-group-selector__loading {
  display: inline-flex;
  gap: 0.3rem;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  white-space: nowrap;
}

.result-group-selector__loading i {
  animation: result-group-spin 0.9s linear infinite;
}

.result-group-selector__master {
  min-height: 3.25rem;
  gap: 0.65rem;
  padding: 0.55rem 0.15rem;
  border-top: 1px solid var(--border-subtle, var(--border-default));
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
  cursor: pointer;
}

.result-group-selector input[type='checkbox'] {
  width: 1.05rem;
  height: 1.05rem;
  flex: 0 0 auto;
  margin: 0;
  accent-color: var(--accent);
  cursor: pointer;
}

.result-group-selector__master span,
.result-group-selector__label {
  min-width: 0;
  display: grid;
  flex: 1;
}

.result-group-selector__master small,
.result-group-selector__label small {
  color: var(--text-secondary);
  font-size: var(--text-2xs-size);
}

.result-group-selector__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.2rem;
  padding: 0.35rem 0 0.65rem;
}

.result-group-selector__actions button {
  display: inline-flex;
  min-height: 2.25rem;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.55rem;
  border: 0;
  border-radius: 0.45rem;
  background: transparent;
  color: var(--accent-text);
  font: inherit;
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  cursor: pointer;
}

.result-group-selector__actions button:hover {
  background: var(--surface-hover);
}

.result-group-selector__actions button:focus-visible {
  outline: 2px solid var(--focus-ring, var(--accent));
  outline-offset: 1px;
}

.result-group-selector__search {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0 0.55rem;
  border: 1px solid var(--border-default);
  border-radius: 0.4rem;
  background: var(--surface-input, var(--surface-elevated));
  color: var(--text-secondary);
  margin-bottom: 0.65rem;
}

.result-group-selector__search input {
  width: 100%;
  min-height: 2.25rem;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-primary);
}

.result-group-selector__list {
  max-height: min(24rem, 46vh);
  overflow-y: auto;
  border-top: 1px solid var(--border-subtle, var(--border-default));
}

.result-group-selector__row,
.result-group-selector__band {
  gap: 0.5rem;
  min-height: 2.85rem;
  padding: 0.45rem 0.15rem;
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
  color: var(--text-primary);
  cursor: pointer;
}

.result-group-selector__row:hover,
.result-group-selector__band:not(.result-group-selector__band--empty):hover {
  background: var(--surface-hover);
}

.result-group-selector__row--missing {
  background: color-mix(in srgb, var(--warning) 8%, transparent);
}

.result-group-selector__swatch {
  width: 0.75rem;
  height: 0.75rem;
  border-radius: 50%;
  flex: 0 0 auto;
}

.result-group-selector__count {
  margin-left: auto;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-variant-numeric: tabular-nums;
}

.result-group-selector__bands {
  border-top: 1px solid var(--border-subtle, var(--border-default));
}

.result-group-selector__band--empty {
  opacity: 0.45;
  cursor: default;
}

.result-group-selector__band-label {
  width: 4.4rem;
  font-size: var(--text-xs-size);
  font-variant-numeric: tabular-nums;
}

.result-group-selector__band-gradient {
  height: 0.55rem;
  min-width: 4rem;
  flex: 1;
  border-radius: 999px;
}

.result-group-selector__details summary {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  cursor: pointer;
}

.result-group-selector__details[open] summary {
  margin-bottom: 0.45rem;
}

.result-group-selector__empty {
  margin: 0;
  padding: 0.75rem;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
}

@keyframes result-group-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
