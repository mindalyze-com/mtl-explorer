<template>
  <BottomSheet
    :model-value="modelValue"
    title="Filter view"
    icon="bi bi-funnel"
    :detents="[{ height: '78vh' }, { height: '95vh' }]"
    :no-backdrop="true"
    :z-index="5100"
    scroll-hint-label="More views"
    sheet-class="sheet--filter-detail"
    @update:model-value="onVisibilityChange"
  >
    <div class="filter-view-sheet">
      <p class="filter-view-sheet__intro">Choose how tracks are grouped and compared.</p>
      <FilterCatalog :groups="groups" :selected-filter-info="draftSelection" @select-filter="draftSelection = $event" />
    </div>

    <template #footer>
      <FilterSheetActions :apply-disabled="!draftSelection" @cancel="cancel" @apply="apply" />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterOptionGroup } from '@/utils/filterMetadata';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import FilterCatalog from '@/components/filter/FilterCatalog.vue';
import FilterSheetActions from '@/components/filter/FilterSheetActions.vue';

defineOptions({ name: 'FilterViewSheet' });

const props = defineProps<{
  modelValue: boolean;
  groups: FilterOptionGroup[];
  selectedFilterInfo?: FilterInfo | null;
}>();

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void;
  (event: 'apply', value: FilterInfo): void;
}>();

const draftSelection = ref<FilterInfo | null>(null);

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
