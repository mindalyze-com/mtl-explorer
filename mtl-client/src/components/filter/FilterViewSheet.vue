<template>
  <BottomSheet
    :model-value="modelValue"
    back-label="Back to Filter"
    title="Filter view"
    icon="bi bi-funnel"
    :detents="FILTER_STANDARD_DETENTS"
    :no-backdrop="true"
    :z-index="5100"
    scroll-hint-label="More views"
    sheet-class="sheet--filter-detail"
    @back="emit('update:modelValue', false)"
    @update:model-value="onVisibilityChange"
  >
    <template v-if="!enabled" #header-actions>
      <FilterOffBadge />
    </template>

    <div class="filter-view-sheet">
      <p class="filter-view-sheet__intro">
        Choose how tracks are grouped and compared. Shared criteria are kept; categories reset when the view changes.
      </p>
      <FilterCatalog
        v-if="modelValue"
        :groups="groups"
        :selected-filter-info="draftSelection"
        @select-filter="draftSelection = $event"
      />
    </div>

    <template #footer>
      <FilterSheetActions
        :apply-disabled="!draftSelection"
        :apply-label="enabled ? 'Use this view' : 'Apply and enable'"
        @cancel="cancel"
        @apply="apply"
      >
        <template v-if="removedCriteria.length || resetsCategoryOrder" #status>
          <p v-if="removedCriteria.length">Changing to this view clears: {{ removedCriteria.join(', ') }}.</p>
          <p v-if="resetsCategoryOrder">Category order resets to the new view’s default.</p>
        </template>
      </FilterSheetActions>
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { FILTER_STANDARD_DETENTS } from '@/components/filter/filterSheetLayout';
import { computed, ref, watch } from 'vue';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterParamsRequest } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterParamsRequest';
import {
  effectiveUiMetadata,
  isSameFilterInfo,
  type FilterOptionGroup,
  type LegendSortStrategy,
} from '@/utils/filterMetadata';
import { FILTER_DATE_FROM, FILTER_DATE_TO } from '@/utils/filterCriteria';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import FilterCatalog from '@/components/filter/FilterCatalog.vue';
import FilterOffBadge from '@/components/filter/FilterOffBadge.vue';
import FilterSheetActions from '@/components/filter/FilterSheetActions.vue';

defineOptions({ name: 'FilterViewSheet' });

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    enabled?: boolean;
    groups: FilterOptionGroup[];
    selectedFilterInfo?: FilterInfo | null;
    filterParams?: FilterParamsRequest;
    legendSortStrategy?: LegendSortStrategy | null;
  }>(),
  {
    enabled: true,
    selectedFilterInfo: null,
    filterParams: undefined,
    legendSortStrategy: null,
  }
);

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void;
  (event: 'apply', value: FilterInfo): void;
}>();

const draftSelection = ref<FilterInfo | null>(null);
const resetsCategoryOrder = computed(
  (): boolean =>
    !!draftSelection.value &&
    !isSameFilterInfo(props.selectedFilterInfo, draftSelection.value) &&
    props.legendSortStrategy != null
);
const removedCriteria = computed((): string[] => {
  if (!draftSelection.value || isSameFilterInfo(props.selectedFilterInfo, draftSelection.value)) return [];
  const nextNames = new Set(draftSelection.value.paramDefinitions?.map((definition) => definition.name));
  const params = props.filterParams;
  const metadata = effectiveUiMetadata(props.selectedFilterInfo).params ?? {};
  const names = [
    params?.stringParams,
    params?.dateTimeParams,
    params?.geoCircles,
    params?.geoRectangles,
    params?.geoPolygons,
  ]
    .flatMap((record) => Object.entries(record ?? {}))
    .filter(([name, value]) => value != null && String(value).trim() && !nextNames.has(name))
    .map(([name]) =>
      name === FILTER_DATE_FROM || name === FILTER_DATE_TO ? 'Date range' : metadata[name]?.label || name
    );
  return [...new Set(names)];
});

watch(
  () => props.modelValue,
  (open) => {
    if (open) draftSelection.value = props.selectedFilterInfo ?? null;
  },
  { immediate: true }
);

function onVisibilityChange(open: boolean): void {
  emit('update:modelValue', open);
}

function cancel(): void {
  emit('update:modelValue', false);
}

function apply(): void {
  if (!draftSelection.value) return;
  emit('apply', draftSelection.value);
  emit('update:modelValue', false);
}
</script>

<style scoped>
.filter-view-sheet {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  padding: 0.35rem 1rem 1rem;
  box-sizing: border-box;
  flex-direction: column;
}

.filter-view-sheet__intro {
  flex: 0 0 auto;
  margin: 0 0 0.8rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
</style>
