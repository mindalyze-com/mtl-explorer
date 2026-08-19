<template>
  <BottomSheet
    :model-value="modelValue"
    title="Included categories"
    icon="bi bi-list-check"
    :detents="[{ height: '82vh' }, { height: '95vh' }]"
    :no-backdrop="true"
    :z-index="5100"
    sheet-class="sheet--filter-detail"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="filter-categories-sheet filter-detail-sheet-content">
      <p class="filter-categories-sheet__intro">Choose which categories remain in the current result.</p>
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
        :effective-count="effectiveCount"
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
        @reset="resetCategories"
        @cancel="cancel"
        @apply="apply"
      />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterResultGroupSelection } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupSelection';
import type { FilterResultGroupSummary } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupSummary';
import type { ColorPalette } from '@/components/filter/ColorPalette';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import FilterResultGroupSelector from '@/components/filter/FilterResultGroupSelector.vue';
import FilterSheetActions from '@/components/filter/FilterSheetActions.vue';

defineOptions({ name: 'FilterCategoriesSheet' });

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    availableGroups?: FilterResultGroupSummary[];
    selection?: FilterResultGroupSelection;
    filterInfo?: FilterInfo;
    palette?: ColorPalette;
    effectiveCount?: number;
    preSelectionCount?: number;
    loading?: boolean;
  }>(),
  {
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
