<template>
  <BottomSheet
    :model-value="modelValue"
    title="Route overlays"
    icon="bi bi-signpost-2"
    :detents="[{ height: '82vh' }, { height: '95vh' }]"
    :no-backdrop="true"
    :z-index="5100"
    sheet-class="sheet--solid-over-map sheet--map-settings-detail"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="map-layer-sheet map-route-layers-sheet">
      <div class="map-layer-sheet__intro">
        <p>Add public route networks over the map.</p>
        <span>{{ enabledCount }} of {{ routeCount }} layers shown</span>
      </div>

      <section
        v-for="group in routeGroups"
        :key="group.title"
        class="map-route-layers-sheet__group"
        :aria-labelledby="group.id"
      >
        <div class="map-route-layers-sheet__heading settings-section-heading">
          <h3 :id="group.id">{{ group.title }}</h3>
          <p>{{ group.description }}</p>
        </div>

        <div class="map-layer-list">
          <LayerControl
            v-for="layer in group.layers"
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
      </section>
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

defineOptions({ name: 'MapRouteLayersSheet' });

const props = defineProps<MapLayerSheetProps>();
const emit = defineEmits<MapLayerSheetEmit>();

const routeGroups = [
  {
    id: 'worldwide-routes-title',
    title: 'Worldwide routes',
    description: 'Waymarked Trails data from OpenStreetMap.',
    layers: [
      {
        id: 'wmt-hiking',
        label: 'Hiking',
        info: 'International, national, regional, and local routes',
        icon: 'bi bi-person-walking',
        color: 'var(--viz-red)',
      },
      {
        id: 'wmt-cycling',
        label: 'Cycling',
        info: 'EuroVelo, national, and regional networks',
        icon: 'bi bi-bicycle',
        color: 'var(--viz-blue)',
      },
      {
        id: 'wmt-mtb',
        label: 'Mountain biking',
        info: 'Mountain bike trails and routes',
        icon: 'bi bi-bicycle',
        color: 'var(--viz-brown)',
      },
    ],
  },
  {
    id: 'swiss-routes-title',
    title: 'Swiss routes',
    description: 'Regional overlays available in Switzerland.',
    layers: [
      {
        id: 'wanderland',
        label: 'Hiking routes',
        info: 'National and regional hiking routes',
        icon: 'bi bi-person-walking',
        color: 'var(--viz-red)',
      },
      {
        id: 'veloland',
        label: 'Cycling routes',
        info: 'National and regional cycling routes',
        icon: 'bi bi-bicycle',
        color: 'var(--viz-blue)',
      },
      {
        id: 'mountainbikeland',
        label: 'Mountain bike routes',
        info: 'National and regional mountain bike routes',
        icon: 'bi bi-bicycle',
        color: 'var(--viz-brown)',
      },
      {
        id: 'wanderwege',
        label: 'Hiking trails',
        info: 'Signposted yellow, red-white, and blue-white trails',
        icon: 'bi bi-signpost-split',
        color: 'var(--warning)',
      },
    ],
  },
] as const;

const routeCount = routeGroups.reduce((count, group) => count + group.layers.length, 0);
const enabledCount = computed((): number =>
  routeGroups.reduce(
    (count, group) => count + group.layers.filter((layer) => props.layerStates[layer.id].enabled).length,
    0
  )
);
</script>

<style scoped>
.map-route-layers-sheet {
  --map-layer-sheet-gap: 1.4rem;
}

.map-route-layers-sheet__group {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}
</style>
