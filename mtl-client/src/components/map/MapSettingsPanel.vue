<template>
  <BottomSheet
    :model-value="isOpen"
    :detents="[{ height: 'min(86vh, 44rem)' }, { height: '95vh' }]"
    :no-backdrop="true"
    sheet-class="sheet--solid-over-map sheet--map-settings-overview"
    @update:model-value="onMainVisibilityChange"
    @closed="onMainClosed"
  >
    <template #title>
      <div class="map-settings-header">
        <i class="bi bi-map map-settings-header__icon" aria-hidden="true"></i>
        <h2>Map</h2>
      </div>
    </template>

    <div class="map-settings-root" :inert="innerSheetOpen ? true : undefined">
      <MapSettingsOverview
        :basemap-enabled="layerStates.basemap.enabled"
        :data-summary="dataSummary"
        :routes-summary="routesSummary"
        :source-summary="sourceSummary"
        :style-summary="styleSummary"
        :terrain-summary="terrainSummary"
        :theme-name="selectedTheme.name"
        :theme-thumbnail="selectedTheme.thumbnail"
        @open-style="showStyle = true"
        @open-terrain="showTerrain = true"
        @open-data="showDataLayers = true"
        @open-routes="showRouteLayers = true"
        @reset="emit('reset-settings')"
      />
    </div>

    <MapStyleSheet
      v-model="showStyle"
      :basemap="layerStates.basemap"
      :map-source-mode="mapSourceMode"
      :selected-theme="modelValue"
      :themes="themes"
      @update:map-source-mode="emit('update:map-source-mode', $event)"
      @update:selected-theme="emit('update:modelValue', $event)"
      @toggle-basemap="emit('toggle-layer', 'basemap')"
      @change-basemap-opacity="emit('change-layer-opacity', 'basemap', $event)"
    />

    <MapTerrainSheet
      v-model="showTerrain"
      :terrain="layerStates.terrain"
      :terrain-exaggeration="terrainExaggeration"
      @set-enabled="emit('set-terrain-mode-enabled', $event)"
      @change-exaggeration="emit('change-terrain-exaggeration', $event)"
    />

    <MapDataLayersSheet
      v-model="showDataLayers"
      :layer-states="layerStates"
      @toggle-layer="emit('toggle-layer', $event)"
      @change-layer-opacity="onLayerOpacityChange"
    />

    <MapRouteLayersSheet
      v-model="showRouteLayers"
      :layer-states="layerStates"
      @toggle-layer="emit('toggle-layer', $event)"
      @change-layer-opacity="onLayerOpacityChange"
    />
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import MapSettingsOverview from '@/components/map/MapSettingsOverview.vue';
import MapStyleSheet from '@/components/map/MapStyleSheet.vue';
import MapTerrainSheet from '@/components/map/MapTerrainSheet.vue';
import MapDataLayersSheet from '@/components/map/MapDataLayersSheet.vue';
import MapRouteLayersSheet from '@/components/map/MapRouteLayersSheet.vue';
import {
  DATA_LAYER_IDS,
  ROUTE_LAYER_IDS,
  type MapSettingsLayerStates,
  type MapSourceMode,
  type MapThemeOption,
} from '@/components/map/mapSettingsPanelTypes';

defineOptions({ name: 'MapSettingsPanel' });

const props = defineProps<{
  layerStates: MapSettingsLayerStates;
  mapSourceMode: MapSourceMode;
  modelValue: string;
  terrainExaggeration: number;
  themes: MapThemeOption[];
}>();

const emit = defineEmits<{
  'change-layer-opacity': [layer: string, opacity: number];
  'change-terrain-exaggeration': [exaggeration: number];
  'reset-settings': [];
  'set-terrain-mode-enabled': [enabled: boolean];
  'toggle-layer': [layer: string];
  'tool-closed': [];
  'tool-opened': [];
  'update:map-source-mode': [value: MapSourceMode];
  'update:modelValue': [value: string];
}>();

const isOpen = ref(false);
const showStyle = ref(false);
const showTerrain = ref(false);
const showDataLayers = ref(false);
const showRouteLayers = ref(false);

const innerSheetOpen = computed(
  (): boolean => showStyle.value || showTerrain.value || showDataLayers.value || showRouteLayers.value
);
const selectedTheme = computed<MapThemeOption>(
  () =>
    props.themes.find((theme) => theme.code === props.modelValue) ??
    props.themes[0] ?? { code: props.modelValue, name: 'Map', thumbnail: '' }
);
const sourceSummary = computed((): string => (props.mapSourceMode === 'remote' ? 'Remote tiles' : 'Automatic source'));
const styleSummary = computed(
  (): string => `${selectedTheme.value.name}${props.layerStates.basemap.enabled ? '' : ' · hidden'}`
);
const terrainSummary = computed((): string =>
  props.layerStates.terrain.enabled ? `3D · ${props.terrainExaggeration.toFixed(1)}× relief` : '2D map'
);
const enabledDataLayerCount = computed(
  (): number => DATA_LAYER_IDS.filter((layerId) => props.layerStates[layerId].enabled).length
);
const enabledRouteLayerCount = computed(
  (): number => ROUTE_LAYER_IDS.filter((layerId) => props.layerStates[layerId].enabled).length
);
const dataSummary = computed((): string => `${enabledDataLayerCount.value} of ${DATA_LAYER_IDS.length} shown`);
const routesSummary = computed((): string =>
  enabledRouteLayerCount.value === 0
    ? 'None shown'
    : `${enabledRouteLayerCount.value} of ${ROUTE_LAYER_IDS.length} shown`
);

watch(isOpen, (open) => {
  if (!open) closeInnerSheets();
});

function closeInnerSheets(): void {
  showStyle.value = false;
  showTerrain.value = false;
  showDataLayers.value = false;
  showRouteLayers.value = false;
}

function onMainVisibilityChange(open: boolean): void {
  isOpen.value = open;
}

function onMainClosed(): void {
  closeInnerSheets();
  emit('tool-closed');
}

function onLayerOpacityChange(layer: string, opacity: number): void {
  emit('change-layer-opacity', layer, opacity);
}

function toggle(): void {
  isOpen.value = !isOpen.value;
  if (isOpen.value) emit('tool-opened');
}

function open(): void {
  if (isOpen.value) return;
  isOpen.value = true;
  emit('tool-opened');
}

function close(): void {
  isOpen.value = false;
  closeInnerSheets();
}

defineExpose({ open, toggle, close });
</script>

<style scoped>
.map-settings-header {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 0.45rem;
}

.map-settings-header__icon {
  flex: 0 0 auto;
  color: var(--accent-text);
  font-size: var(--text-base-size);
}

.map-settings-header h2 {
  margin: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-size: var(--text-base-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-base-lh);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.map-settings-root {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior-y: contain;
  -webkit-overflow-scrolling: touch;
}
</style>
