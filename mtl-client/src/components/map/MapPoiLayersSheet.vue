<template>
  <BottomSheet
    :model-value="modelValue"
    title="Points of interest"
    icon="bi bi-geo-alt"
    :detents="[{ height: '82vh' }, { height: '95vh' }]"
    :no-backdrop="true"
    :z-index="5100"
    sheet-class="sheet--solid-over-map sheet--map-settings-detail"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="map-layer-sheet map-poi-sheet">
      <div class="map-layer-sheet__intro">
        <p>Show useful places on the map. More detail appears as you zoom in.</p>
        <span>{{ selectedCount }} of {{ POI_CATEGORIES.length }} categories enabled</span>
      </div>
      <LayerControl
        label="Points of interest"
        :info="
          store.poiEnabled ? 'Shown where vector map data is available' : 'Hidden; your category choices are saved'
        "
        icon="bi bi-geo-alt"
        :enabled="store.poiEnabled"
        :opacity="store.poiOpacity"
        @update:enabled="store.setPoiEnabled"
        @update:opacity="store.setPoiOpacity"
      />
      <div class="map-poi-sheet__actions">
        <button type="button" @click="store.setAllPoiCategories(true)">Enable all</button>
        <button type="button" @click="store.setAllPoiCategories(false)">Disable all</button>
      </div>
      <div class="map-layer-list">
        <LayerControl
          v-for="category in POI_CATEGORIES"
          :key="category.id"
          :label="category.label"
          :info="category.description"
          :icon="`bi bi-${category.icon}`"
          :color="category.color"
          :enabled="store.poiCategories[category.id]"
          :opacity="store.poiOpacity"
          :show-opacity="false"
          @update:enabled="store.setPoiCategory(category.id, $event)"
        />
      </div>
    </div>
    <template #footer><MapSettingsDetailFooter @done="emit('update:modelValue', false)" /></template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import LayerControl from './LayerControl.vue';
import MapSettingsDetailFooter from './MapSettingsDetailFooter.vue';
import { POI_CATEGORIES } from './poiCategories';
import { useMapSettingsStore } from '@/stores/mapSettingsStore';

defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();
const store = useMapSettingsStore();
const selectedCount = computed(() => POI_CATEGORIES.filter(({ id }) => store.poiCategories[id]).length);
</script>

<style scoped>
.map-poi-sheet__actions {
  display: flex;
  gap: 0.6rem;
}
.map-poi-sheet__actions button {
  min-height: 2.75rem;
  padding: 0.5rem 0.85rem;
  border: 1px solid var(--border-default);
  border-radius: 0.6rem;
  background: var(--surface-ground);
  color: var(--text-secondary);
  font: inherit;
  font-size: var(--text-sm-size);
  cursor: pointer;
}
</style>
