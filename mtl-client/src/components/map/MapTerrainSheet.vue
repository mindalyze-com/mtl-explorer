<template>
  <BottomSheet
    :model-value="modelValue"
    title="Terrain"
    icon="bi bi-badge-3d"
    :detents="[{ height: '58vh' }, { height: '88vh' }]"
    :no-backdrop="true"
    :z-index="5100"
    sheet-class="sheet--solid-over-map sheet--map-settings-detail"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="map-terrain-sheet">
      <p class="map-terrain-sheet__intro">Use a perspective view and adjust the height of the landscape.</p>

      <div class="map-terrain-sheet__control-list">
        <LayerControl
          label="3D terrain"
          info="Perspective terrain using the elevation mesh"
          icon="bi bi-badge-3d"
          :enabled="terrain.enabled"
          :opacity="terrain.opacity"
          :show-opacity="false"
          color="var(--viz-blue)"
          @update:enabled="emit('set-enabled', $event)"
        />
      </div>

      <section
        class="map-terrain-sheet__relief"
        :class="{ 'map-terrain-sheet__relief--disabled': !terrain.enabled }"
        aria-labelledby="terrain-relief-title"
      >
        <div class="map-terrain-sheet__relief-heading">
          <div>
            <h3 id="terrain-relief-title" class="compact-heading">Relief</h3>
            <p>Increase or reduce the visible terrain height.</p>
          </div>
          <output>{{ terrainExaggeration.toFixed(1) }}×</output>
        </div>

        <MtlSlider
          class="map-terrain-sheet__slider"
          :model-value="terrainExaggeration"
          :min="TERRAIN_EXAGGERATION_MIN"
          :max="TERRAIN_EXAGGERATION_MAX"
          :step="TERRAIN_EXAGGERATION_STEP"
          :disabled="!terrain.enabled"
          aria-label="3D terrain relief"
          @update:model-value="onTerrainExaggerationChange"
        />

        <div class="map-terrain-sheet__range" aria-hidden="true">
          <span>{{ TERRAIN_EXAGGERATION_MIN.toFixed(1) }}×</span>
          <span>{{ TERRAIN_EXAGGERATION_MAX.toFixed(1) }}×</span>
        </div>
      </section>
    </div>

    <template #footer>
      <MapSettingsDetailFooter @done="emit('update:modelValue', false)" />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import BottomSheet from '@/components/ui/BottomSheet.vue';
import LayerControl from '@/components/map/LayerControl.vue';
import MapSettingsDetailFooter from '@/components/map/MapSettingsDetailFooter.vue';
import MtlSlider from '@/components/ui/MtlSlider.vue';
import type { MapLayerState } from '@/components/map/mapSettingsPanelTypes';
import {
  TERRAIN_EXAGGERATION_MAX,
  TERRAIN_EXAGGERATION_MIN,
  TERRAIN_EXAGGERATION_STEP,
  sanitizeTerrainExaggeration,
} from '@/components/map/terrainMode';

defineOptions({ name: 'MapTerrainSheet' });

defineProps<{
  modelValue: boolean;
  terrain: MapLayerState;
  terrainExaggeration: number;
}>();

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void;
  (event: 'set-enabled', value: boolean): void;
  (event: 'change-exaggeration', value: number): void;
}>();

function onTerrainExaggerationChange(value: number | number[]): void {
  const nextValue = Array.isArray(value) ? value[0] : value;
  emit('change-exaggeration', sanitizeTerrainExaggeration(nextValue));
}
</script>

<style scoped>
.map-terrain-sheet {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  box-sizing: border-box;
  flex-direction: column;
  gap: 1.25rem;
  padding: 0.35rem 1rem 1.5rem;
}

.map-terrain-sheet__intro {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.map-terrain-sheet__control-list,
.map-terrain-sheet__relief {
  border-top: 1px solid var(--border-default);
  border-bottom: 1px solid var(--border-default);
  background: transparent;
}

.map-terrain-sheet__relief {
  padding: 1rem 0.2rem;
  transition: opacity 0.15s ease;
}

.map-terrain-sheet__relief--disabled {
  opacity: 0.48;
}

.map-terrain-sheet__relief-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.map-terrain-sheet__relief-heading p {
  margin: 0.12rem 0 0;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.map-terrain-sheet__relief-heading output {
  flex: 0 0 auto;
  color: var(--accent-text);
  font-size: var(--text-base-size);
  font-weight: var(--font-bold);
  font-variant-numeric: tabular-nums;
}

.map-terrain-sheet__slider {
  --mtl-slider-hit-padding-x: 14px;
  --mtl-slider-hit-padding-y: 12px;
  --mtl-slider-handle-size-default: 24px;
  --mtl-slider-handle-size-coarse: 30px;
  --mtl-slider-track-height-default: 10px;
  --mtl-slider-track-height-coarse: 12px;
  margin: 0.75rem -14px 0;
}

.map-terrain-sheet__range {
  display: flex;
  justify-content: space-between;
  margin-top: 0.2rem;
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
  font-variant-numeric: tabular-nums;
}

@media screen and (max-width: 600px) {
  .map-terrain-sheet {
    padding-inline: 0.75rem;
  }
}
</style>
