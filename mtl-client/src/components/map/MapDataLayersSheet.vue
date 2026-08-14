<template>
  <BottomSheet
    :model-value="modelValue"
    title="Your data"
    icon="bi bi-layers"
    :detents="[{ height: '76vh' }, { height: '95vh' }]"
    :no-backdrop="true"
    :z-index="5100"
    sheet-class="sheet--solid-over-map sheet--map-settings-detail"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="map-layer-sheet map-data-layers-sheet">
      <div class="map-layer-sheet__intro map-data-layers-sheet__intro">
        <div>
          <p>Choose which of your data layers appear on the map.</p>
          <span>{{ enabledCount }} of {{ dataLayers.length }} layers shown</span>
        </div>
      </div>

      <div class="map-layer-list">
        <LayerControl
          v-for="layer in dataLayers"
          :key="layer.id"
          :label="layer.label"
          :info="layer.info"
          :icon="layer.icon"
          :color="layer.color"
          :enabled="layerStates[layer.id].enabled"
          :opacity="layerStates[layer.id].opacity"
          @update:enabled="emit('toggle-layer', layer.id)"
          @update:opacity="emit('change-layer-opacity', layer.id, $event)"
        />
      </div>
    </div>

    <template #footer>
      <MapSettingsDetailFooter @done="emit('update:modelValue', false)" />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import LayerControl from '@/components/map/LayerControl.vue';
import MapSettingsDetailFooter from '@/components/map/MapSettingsDetailFooter.vue';
import type { MapLayerSheetEmit, MapLayerSheetProps } from '@/components/map/mapSettingsPanelTypes';

defineOptions({ name: 'MapDataLayersSheet' });

const props = defineProps<MapLayerSheetProps>();
const emit = defineEmits<MapLayerSheetEmit>();

const dataLayers = [
  {
    id: 'tracks',
    label: 'GPS tracks',
    info: 'Recorded activities drawn as colored lines',
    icon: 'bi bi-bezier2',
    color: 'var(--viz-blue)',
  },
  {
    id: 'media',
    label: 'Photos and media',
    info: 'Geotagged media grouped by location',
    icon: 'bi bi-images',
    color: 'var(--viz-purple)',
  },
  {
    id: 'trackpoints',
    label: 'Track points and direction',
    info: 'GPS points and arrows shown when zoomed in',
    icon: 'bi bi-broadcast-pin',
    color: 'var(--viz-teal)',
  },
  {
    id: 'heatmap',
    label: 'Heatmap',
    info: 'Density view of the places you visit most',
    icon: 'bi bi-fire',
    color: 'var(--viz-red)',
  },
] as const;

const enabledCount = computed((): number => dataLayers.filter((layer) => props.layerStates[layer.id].enabled).length);
</script>

<style scoped>
.map-data-layers-sheet {
  --map-layer-sheet-gap: 1rem;
}

.map-data-layers-sheet__intro {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}
</style>
