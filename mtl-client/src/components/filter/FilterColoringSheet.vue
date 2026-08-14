<template>
  <BottomSheet
    :model-value="modelValue"
    title="Map colors"
    icon="bi bi-palette"
    :detents="[{ height: 'min(56vh, 28rem)' }, { height: '90vh' }]"
    :no-backdrop="true"
    :z-index="5100"
    sheet-class="sheet--filter-detail sheet--filter-colors"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="filter-coloring-sheet">
      <div class="filter-coloring-sheet__intro">
        <div>
          <h3 class="filter-panel-title">Style the result</h3>
          <p>Colors change the map, not which tracks match.</p>
        </div>
        <div v-if="previewColors.length" class="filter-coloring-sheet__preview" aria-label="Palette preview">
          <span
            v-for="(color, index) in previewColors"
            :key="`${color}-${index}`"
            :style="{ backgroundColor: color }"
          ></span>
        </div>
      </div>

      <div class="filter-coloring-sheet__fields">
        <label class="filter-coloring-sheet__field">
          <span>Palette</span>
          <Select
            v-model="draftPalette"
            :options="palettes"
            option-label="pLabel"
            placeholder="No coloring"
            class="filter-coloring-sheet__select"
            append-to="body"
          />
          <small>Assigns colors to the available categories.</small>
        </label>

        <label class="filter-coloring-sheet__field">
          <span>Category order</span>
          <Select
            v-model="draftOrder"
            :options="orderOptions"
            option-label="label"
            option-value="value"
            placeholder="Default order"
            class="filter-coloring-sheet__select"
            append-to="body"
          >
            <template #option="slotProps">
              <div class="filter-coloring-sheet__option">
                <strong>{{ slotProps.option.label }}</strong>
                <small>{{ slotProps.option.description }}</small>
              </div>
            </template>
          </Select>
          <small>Controls the legend and the sequence of palette colors.</small>
        </label>
      </div>
    </div>

    <template #footer>
      <FilterSheetActions
        :reset-visible="hasColoringOverride"
        reset-aria-label="Reset map colors"
        @reset="resetColoring"
        @cancel="cancel"
        @apply="apply"
      />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue';
import type { ColorPalette } from '@/components/filter/ColorPalette';
import type { LegendSortStrategy } from '@/utils/filterMetadata';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import FilterSheetActions from '@/components/filter/FilterSheetActions.vue';

type OrderOption = {
  label: string;
  value: LegendSortStrategy | null;
  description: string;
};

defineOptions({ name: 'FilterColoringSheet' });

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    palettes?: ColorPalette[];
    palette?: ColorPalette;
    defaultPalette?: ColorPalette;
    legendSortStrategy?: LegendSortStrategy | null;
    orderOptions?: OrderOption[];
  }>(),
  {
    palettes: () => [],
    palette: undefined,
    defaultPalette: undefined,
    legendSortStrategy: null,
    orderOptions: () => [],
  }
);

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void;
  (event: 'apply', value: { palette: ColorPalette | undefined; legendSortStrategy: LegendSortStrategy | null }): void;
}>();

const draftPalette = shallowRef<ColorPalette | undefined>(undefined);
const draftOrder = ref<LegendSortStrategy | null>(null);
const previewColors = computed((): string[] => (draftPalette.value?.pColors ?? []).slice(0, 10));
const hasColoringOverride = computed(
  (): boolean =>
    paletteIdentity(draftPalette.value) !== paletteIdentity(props.defaultPalette) || draftOrder.value != null
);

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return;
    draftPalette.value = props.palette;
    draftOrder.value = props.legendSortStrategy ?? null;
  },
  { immediate: true }
);

function cancel(): void {
  emit('update:modelValue', false);
}

function paletteIdentity(palette?: ColorPalette): string {
  if (!palette || palette.isEmptyColorPalette()) return 'empty';
  return String(palette.id ?? palette.pLabel ?? palette.pColors?.join(',') ?? 'palette');
}

function resetColoring(): void {
  draftPalette.value = props.defaultPalette;
  draftOrder.value = null;
}

function apply(): void {
  emit('apply', {
    palette: draftPalette.value,
    legendSortStrategy: draftOrder.value,
  });
  emit('update:modelValue', false);
}
</script>

<style scoped>
.filter-coloring-sheet {
  display: flex;
  flex: 1 1 auto;
  width: min(100%, 38rem);
  min-height: 0;
  overflow-y: auto;
  margin: 0 auto;
  padding: 0.35rem 1rem 1.25rem;
  box-sizing: border-box;
  flex-direction: column;
  gap: 1.2rem;
}

.filter-coloring-sheet__intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem;
  border: 1px solid var(--border-subtle, var(--border-default));
  border-radius: 0.85rem;
  background: var(--surface-elevated);
}

.filter-coloring-sheet__intro p {
  margin: 0.18rem 0 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.filter-coloring-sheet__preview {
  display: flex;
  flex: 0 0 auto;
  max-width: 12rem;
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: 999px;
}

.filter-coloring-sheet__preview span {
  width: 1.2rem;
  height: 1.2rem;
}

.filter-coloring-sheet__fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.filter-coloring-sheet__field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.4rem;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 700;
}

.filter-coloring-sheet__field > small {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 400;
  line-height: var(--text-xs-lh);
}

.filter-coloring-sheet__select {
  width: 100%;
  min-width: 0;
}

.filter-coloring-sheet__option {
  display: flex;
  max-width: 24rem;
  flex-direction: column;
  gap: 0.15rem;
}

.filter-coloring-sheet__option strong {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 600;
}

.filter-coloring-sheet__option small {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  white-space: normal;
}

@media screen and (max-width: 600px) {
  .filter-coloring-sheet {
    padding-inline: 0.75rem;
  }

  .filter-coloring-sheet__intro {
    align-items: flex-start;
    flex-direction: column;
  }

  .filter-coloring-sheet__fields {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
