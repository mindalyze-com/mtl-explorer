<template>
  <div class="geo-param">
    <div class="geo-param__label-row">
      <label class="filter-field__label">{{ paramDef.label }}</label>
      <span v-if="optional" class="geo-param__optional">Optional</span>
    </div>
    <div class="geo-param__row">
      <template v-if="hasValue">
        <span class="geo-param__summary">{{ shapeSummary }}</span>
        <button class="geo-param__btn geo-param__btn--clear" title="Clear shape" @click="clearShape">
          <i class="bi bi-x-lg"></i>
        </button>
      </template>
      <button class="geo-param__btn geo-param__btn--draw" @click="startDrawing">
        <i :class="drawIcon"></i>
        {{ hasValue ? 'Redraw' : 'Draw on map' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { ParamDefinition } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';
import type { GeoCircle } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/GeoCircle';
import type { GeoRectangle } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/GeoRectangle';
import type { GeoPolygon } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/GeoPolygon';

defineOptions({ name: 'GeoShapeParam' });

const props = defineProps<{
  paramDef: ParamDefinition;
  circle?: GeoCircle;
  rectangle?: GeoRectangle;
  polygon?: GeoPolygon;
  optional?: boolean;
}>();

const emit = defineEmits<{
  (event: 'start-geo-drawing', paramDef: ParamDefinition): void;
  (event: 'clear-geo-shape', paramDef: ParamDefinition): void;
}>();

const hasValue = computed((): boolean => {
  switch (props.paramDef.type) {
    case 'GEO_CIRCLE':
      return props.circle != null;
    case 'GEO_RECTANGLE':
      return props.rectangle != null;
    case 'GEO_POLYGON':
      return props.polygon != null && (props.polygon.coordinates?.length ?? 0) >= 3;
    default:
      return false;
  }
});

const shapeSummary = computed((): string => {
  switch (props.paramDef.type) {
    case 'GEO_CIRCLE':
      if (!props.circle) return '';
      return `Circle at ${props.circle.lat?.toFixed(4)}, ${props.circle.lng?.toFixed(4)} — r=${formatRadius(props.circle.radiusM ?? 0)}`;
    case 'GEO_RECTANGLE':
      if (!props.rectangle) return '';
      return `Rectangle (${props.rectangle.minLat?.toFixed(3)}…${props.rectangle.maxLat?.toFixed(3)}, ${props.rectangle.minLng?.toFixed(3)}…${props.rectangle.maxLng?.toFixed(3)})`;
    case 'GEO_POLYGON':
      if (!props.polygon) return '';
      return `Polygon with ${props.polygon.coordinates?.length ?? 0} points`;
    default:
      return '';
  }
});

const drawIcon = computed((): string => {
  switch (props.paramDef.type) {
    case 'GEO_CIRCLE':
      return 'bi bi-circle';
    case 'GEO_RECTANGLE':
      return 'bi bi-bounding-box';
    case 'GEO_POLYGON':
      return 'bi bi-pentagon';
    default:
      return 'bi bi-geo-alt';
  }
});

function startDrawing() {
  emit('start-geo-drawing', props.paramDef);
}

function clearShape() {
  emit('clear-geo-shape', props.paramDef);
}

function formatRadius(meters: number): string {
  if (meters >= 1000) return `${(meters / 1000).toFixed(1)} km`;
  return `${Math.round(meters)} m`;
}
</script>

<style scoped>
.geo-param {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-bottom: 0.2rem;
  min-width: 0;
}

.geo-param__label-row {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
  flex-wrap: wrap;
}

.geo-param__optional {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 1.25rem;
  border-radius: 999px;
  padding: 0.12rem 0.45rem;
  color: var(--text-muted);
  background: var(--surface-glass-heavy, var(--surface-ground));
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.geo-param__row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.geo-param__summary {
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.geo-param__btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.3rem 0.6rem;
  border-radius: 6px;
  border: 1px solid var(--border-medium);
  background: var(--surface-glass-heavy);
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  cursor: pointer;
  white-space: nowrap;
  transition:
    background 0.15s,
    border-color 0.15s;
}

.geo-param__btn:hover {
  background: var(--surface-hover);
  border-color: var(--accent);
}

.geo-param__btn--draw {
  color: var(--accent-text);
  border-color: var(--accent);
}

.geo-param__btn--clear {
  padding: 0.3rem 0.4rem;
  color: var(--text-muted);
  border-color: transparent;
  background: transparent;
}

.geo-param__btn--clear:hover {
  color: var(--error);
  background: var(--error-bg);
}
</style>
