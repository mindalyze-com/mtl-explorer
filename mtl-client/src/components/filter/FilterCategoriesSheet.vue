<template>
  <BottomSheet
    :model-value="modelValue"
    back-label="Back to Filter"
    title="Included categories"
    icon="bi bi-list-check"
    :detents="FILTER_STANDARD_DETENTS"
    :no-backdrop="true"
    :z-index="5100"
    sheet-class="sheet--filter-detail"
    @back="emit('update:modelValue', false)"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="!enabled" #header-actions>
      <FilterOffBadge />
    </template>

    <div class="filter-categories-sheet filter-detail-sheet-content">
      <p class="filter-categories-sheet__intro">Select categories, then apply to update your tracks.</p>
      <div v-if="loading" class="filter-categories-sheet__state" role="status">
        <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
        Updating categories…
      </div>
      <FilterResultGroupSelector
        v-else-if="hasCategoryRows"
        :available-groups="availableGroups"
        :selection="draftSelection"
        :filter-info="filterInfo"
        :palette="palette"
        :effective-count="draftTrackCount"
        :pre-selection-count="preSelectionCount"
        :loading="false"
        @update:selection="draftSelection = $event"
      />
      <div v-else class="filter-categories-sheet__state" role="status">
        <i class="bi bi-info-circle" aria-hidden="true"></i>
        Included categories are not available for these results.
      </div>
    </div>

    <template #footer>
      <FilterSheetActions
        :reset-visible="draftSelection != null"
        reset-aria-label="Reset categories"
        :apply-disabled="loading"
        :apply-label="enabled ? 'Apply' : 'Apply and enable'"
        @reset="resetCategories"
        @cancel="cancel"
        @apply="apply"
      >
        <template v-if="hasCategoryRows && !loading" #status>
          <p class="filter-categories-sheet__selection">
            {{ draftTrackCount }} {{ draftTrackCount === 1 ? 'track' : 'tracks' }} selected
            <span v-if="draftTrackCount === 0"> · Choose a category or reset to include all.</span>
          </p>
        </template>
      </FilterSheetActions>
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { FILTER_STANDARD_DETENTS } from '@/components/filter/filterSheetLayout';
import { computed, ref, watch } from 'vue';
import { isResultGroupSelected } from '@/utils/resultGroupSelection';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterResultGroupSelection } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupSelection';
import type { FilterResultGroupSummary } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupSummary';
import type { ColorPalette } from '@/components/filter/ColorPalette';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import FilterOffBadge from '@/components/filter/FilterOffBadge.vue';
import FilterResultGroupSelector from '@/components/filter/FilterResultGroupSelector.vue';
import FilterSheetActions from '@/components/filter/FilterSheetActions.vue';

defineOptions({ name: 'FilterCategoriesSheet' });

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    enabled?: boolean;
    availableGroups?: FilterResultGroupSummary[];
    selection?: FilterResultGroupSelection;
    filterInfo?: FilterInfo;
    palette?: ColorPalette;
    effectiveCount?: number;
    preSelectionCount?: number;
    loading?: boolean;
  }>(),
  {
    enabled: true,
    availableGroups: () => [],
    selection: undefined,
    filterInfo: undefined,
    palette: undefined,
    effectiveCount: 0,
    preSelectionCount: 0,
    loading: false,
  }
);

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void;
  (event: 'apply', value: FilterResultGroupSelection | undefined): void;
}>();

const draftSelection = ref<FilterResultGroupSelection | undefined>(undefined);
// Sum the server-provided category counts for the staged selection.
const draftTrackCount = computed(() =>
  props.availableGroups.reduce(
    (count, group) =>
      count + (group.key && isResultGroupSelected(group.key, draftSelection.value) ? (group.count ?? 0) : 0),
    0
  )
);
const hasCategoryRows = computed(
  (): boolean => props.availableGroups.length > 0 || (draftSelection.value?.includedGroups?.length ?? 0) > 0
);

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return;
    draftSelection.value = cloneSelection(props.selection);
  },
  { immediate: true }
);

function cloneSelection(selection?: FilterResultGroupSelection): FilterResultGroupSelection | undefined {
  return selection
    ? { includedGroups: (selection.includedGroups ?? []).map((key) => ({ value: key.value })) }
    : undefined;
}

function cancel(): void {
  emit('update:modelValue', false);
}

function resetCategories(): void {
  draftSelection.value = undefined;
}

function apply(): void {
  if (props.loading) return;
  emit('apply', cloneSelection(draftSelection.value));
  emit('update:modelValue', false);
}
</script>

<style scoped>
.filter-categories-sheet {
  gap: 0.8rem;
}

.filter-categories-sheet__intro {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.filter-categories-sheet__state {
  display: flex;
  min-height: 8rem;
  align-items: center;
  justify-content: center;
  gap: 0.55rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}

@media screen and (max-width: 600px) {
  .filter-categories-sheet {
    padding-inline: 0.75rem;
  }
}
</style>
