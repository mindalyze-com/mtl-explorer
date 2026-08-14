<template>
  <section class="category-quick" aria-labelledby="category-quick-title">
    <div class="category-quick__heading">
      <span class="category-quick__icon filter-section-icon">
        <i class="bi bi-list-check" aria-hidden="true"></i>
      </span>
      <div class="category-quick__copy">
        <h3 id="category-quick-title" class="category-quick__title filter-section-title">Included categories</h3>
        <p class="category-quick__help filter-section-help">Choose which categories remain in the current result.</p>
      </div>
      <span v-if="hasCategories" class="category-quick__summary">
        <i v-if="loading" class="pi pi-spin pi-spinner" aria-hidden="true"></i>
        {{ selectionSummary }}
      </span>
    </div>

    <div v-if="hasCategories" class="category-quick__controls">
      <div class="category-quick__chips" role="group" aria-label="Quick category selection">
        <button
          type="button"
          class="category-chip category-chip--all"
          :class="{ 'category-chip--included': allCategories }"
          :aria-pressed="allCategories"
          @click="emit('update:selection', undefined)"
        >
          <i :class="allCategories ? 'bi bi-check-circle-fill' : 'bi bi-circle'"></i>
          <span>All</span>
        </button>

        <button
          v-for="(row, index) in quickRows"
          :key="row.id"
          type="button"
          class="category-chip"
          :class="[
            isSelected(row.key) ? 'category-chip--included' : 'category-chip--excluded',
            { 'category-chip--phone-overflow': index >= PHONE_CATEGORY_LIMIT },
          ]"
          :aria-pressed="isSelected(row.key)"
          :title="`${row.label}: ${row.count} matching tracks`"
          @click="toggleRow(row.key)"
        >
          <span
            class="category-chip__swatch"
            :class="{ 'category-chip__swatch--hollow': !isSelected(row.key) }"
            :style="row.color ? { '--category-color': row.color } : undefined"
            aria-hidden="true"
          ></span>
          <span class="category-chip__label">{{ row.label }}</span>
          <span class="category-chip__count">{{ row.count }}</span>
          <i
            :class="isSelected(row.key) ? 'bi bi-check-circle-fill' : 'bi bi-circle'"
            class="category-chip__state"
            aria-hidden="true"
          ></i>
        </button>
      </div>

      <button type="button" class="category-manage" @click="emit('manage')">
        <i class="bi bi-sliders"></i>
        <span>Manage</span>
      </button>
    </div>

    <div v-else class="category-quick__status" role="status">
      <i :class="loading ? 'pi pi-spin pi-spinner' : 'bi bi-info-circle'" aria-hidden="true"></i>
      <span>{{ statusText || 'Included categories are not available for this view.' }}</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterResultGroupKey } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupKey';
import type { FilterResultGroupSelection } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupSelection';
import type { FilterResultGroupSummary } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupSummary';
import type { ColorPalette } from '@/components/filter/ColorPalette';
import { colorForFilterGroup, formatFilterGroupLabel } from '@/utils/filterMetadata';
import {
  availableResultGroupKeys,
  isResultGroupSelected,
  resultGroupKeyId,
  updateResultGroupSelection,
} from '@/utils/resultGroupSelection';

const DESKTOP_CATEGORY_LIMIT = 8;
const PHONE_CATEGORY_LIMIT = 5;

type QuickRow = {
  id: string;
  key: FilterResultGroupKey;
  label: string;
  count: number;
  color: string | null;
};

const props = withDefaults(
  defineProps<{
    availableGroups: FilterResultGroupSummary[];
    selection?: FilterResultGroupSelection;
    filterInfo?: FilterInfo;
    palette?: ColorPalette;
    loading?: boolean;
    statusText?: string;
  }>(),
  {
    selection: undefined,
    filterInfo: undefined,
    palette: undefined,
    loading: false,
    statusText: '',
  }
);

const emit = defineEmits<{
  (event: 'update:selection', value: FilterResultGroupSelection | undefined): void;
  (event: 'manage'): void;
}>();

const availableKeys = computed(() => availableResultGroupKeys(props.availableGroups));

const rows = computed((): QuickRow[] =>
  props.availableGroups
    .filter((summary): summary is FilterResultGroupSummary & { key: FilterResultGroupKey } => summary.key != null)
    .map((summary) => {
      const value = summary.key.value;
      const palette = props.palette;
      return {
        id: resultGroupKeyId(summary.key),
        key: summary.key,
        label: value == null ? 'Ungrouped' : formatFilterGroupLabel(value, props.filterInfo),
        count: Number(summary.count ?? 0),
        color:
          value != null && palette && !palette.isEmptyColorPalette()
            ? colorForFilterGroup(palette, value, props.filterInfo)
            : null,
      };
    })
);

const quickRows = computed((): QuickRow[] => rows.value.slice(0, DESKTOP_CATEGORY_LIMIT));
const hasCategories = computed((): boolean => rows.value.length > 0);
const allCategories = computed((): boolean => props.selection == null);
const selectedCount = computed(
  (): number => availableKeys.value.filter((key) => isResultGroupSelected(key, props.selection)).length
);
const selectionSummary = computed((): string =>
  allCategories.value ? `All ${availableKeys.value.length}` : `${selectedCount.value} of ${availableKeys.value.length}`
);

function isSelected(key: FilterResultGroupKey): boolean {
  return isResultGroupSelected(key, props.selection);
}

function toggleRow(key: FilterResultGroupKey): void {
  emit('update:selection', updateResultGroupSelection(props.selection, availableKeys.value, [key], !isSelected(key)));
}
</script>

<style scoped>
.category-quick {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid var(--border-subtle, var(--border-default));
  border-radius: 0.75rem;
  background: var(--surface-glass-heavy, var(--surface-elevated));
}

.category-quick__status {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  min-height: 2.35rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.category-quick__controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.category-quick__heading {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.75rem;
}

.category-quick__copy {
  min-width: 0;
}

.category-quick__summary {
  flex: 0 0 auto;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: 700;
}

.category-quick__controls {
  align-items: flex-start;
}

.category-quick__chips {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.category-chip,
.category-manage {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  min-height: 2.35rem;
  border: 1px solid var(--border-default);
  border-radius: 999px;
  padding: 0.42rem 0.7rem;
  background: var(--surface-glass-heavy, var(--surface-elevated));
  color: var(--text-secondary);
  font: inherit;
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s,
    color 0.15s;
}

.category-chip:hover,
.category-manage:hover {
  border-color: var(--accent);
  background: var(--surface-hover);
}

.category-chip--included {
  border-color: color-mix(in srgb, var(--accent) 45%, var(--border-default));
  background: var(--accent-subtle);
  color: var(--accent-text);
}

.category-chip--excluded {
  color: var(--text-muted);
  background: transparent;
}

.category-chip__swatch {
  width: 0.72rem;
  height: 0.72rem;
  flex: 0 0 auto;
  border-radius: 50%;
  border: 2px solid var(--category-color, var(--text-muted));
  background: var(--category-color, var(--text-muted));
}

.category-chip__swatch--hollow {
  background: transparent;
}

.category-chip__label {
  max-width: 8.5rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-chip__count {
  opacity: 0.75;
}

.category-chip__state {
  font-size: 0.72rem;
}

.category-manage {
  flex: 0 0 auto;
  border-radius: 0.7rem;
  color: var(--accent-text);
}

@media (max-width: 48rem) {
  .category-quick__controls {
    align-items: stretch;
  }

  .category-chip--phone-overflow {
    display: none;
  }

  .category-manage {
    min-width: 2.75rem;
    padding-inline: 0.65rem;
  }

  .category-manage span {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
    white-space: nowrap;
  }
}
</style>
