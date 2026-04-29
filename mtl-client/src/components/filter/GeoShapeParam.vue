<template>
  <div class="geo-param">
    <label class="filter-field__label">{{ paramDef.label }}</label>
    <div class="geo-param__row">
      <template v-if="hasValue">
        <span class="geo-param__summary">{{ shapeSummary }}</span>
        <button class="geo-param__btn geo-param__btn--clear" @click="clearShape" title="Clear shape">
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

<script lang="ts">
import { defineComponent, type PropType } from "vue";
import type { ParamDefinition } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';
import type { GeoCircle } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/GeoCircle';
import type { GeoRectangle } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/GeoRectangle';
import type { GeoPolygon } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/GeoPolygon';

export default defineComponent({
  name: 'GeoShapeParam',
  props: {
    paramDef: {
      type: Object as PropType<ParamDefinition>,
      required: true,
    },
    circle: {
      type: Object as PropType<GeoCircle>,
      default: undefined,
    },
    rectangle: {
      type: Object as PropType<GeoRectangle>,
      default: undefined,
    },
    polygon: {
      type: Object as PropType<GeoPolygon>,
      default: undefined,
    },
  },
  emits: ['start-geo-drawing', 'clear-geo-shape'],
  computed: {
    hasValue(): boolean {
      switch (this.paramDef.type) {
        case 'GEO_CIRCLE': return this.circle != null;
        case 'GEO_RECTANGLE': return this.rectangle != null;
        case 'GEO_POLYGON': return this.polygon != null && (this.polygon.coordinates?.length ?? 0) >= 3;
        default: return false;
      }
    },
    shapeSummary(): string {
      switch (this.paramDef.type) {
        case 'GEO_CIRCLE':
          if (!this.circle) return '';
          return `Circle at ${this.circle.lat?.toFixed(4)}, ${this.circle.lng?.toFixed(4)} — r=${formatRadius(this.circle.radiusM ?? 0)}`;
        case 'GEO_RECTANGLE':
          if (!this.rectangle) return '';
          return `Rectangle (${this.rectangle.minLat?.toFixed(3)}…${this.rectangle.maxLat?.toFixed(3)}, ${this.rectangle.minLng?.toFixed(3)}…${this.rectangle.maxLng?.toFixed(3)})`;
        case 'GEO_POLYGON':
          if (!this.polygon) return '';
          return `Polygon with ${this.polygon.coordinates?.length ?? 0} points`;
        default:
          return '';
      }
    },
    drawIcon(): string {
      switch (this.paramDef.type) {
        case 'GEO_CIRCLE': return 'bi bi-circle';
        case 'GEO_RECTANGLE': return 'bi bi-bounding-box';
        case 'GEO_POLYGON': return 'bi bi-pentagon';
        default: return 'bi bi-geo-alt';
      }
    },
  },
  methods: {
    startDrawing() {
      this.$emit('start-geo-drawing', this.paramDef);
    },
    clearShape() {
      this.$emit('clear-geo-shape', this.paramDef);
    },
  },
});

function formatRadius(meters: number): string {
  if (meters >= 1000) return `${(meters / 1000).toFixed(1)} km`;
  return `${Math.round(meters)} m`;
}
</script>

<style scoped>
.geo-param {
  margin-bottom: 0.5rem;
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
  transition: background 0.15s, border-color 0.15s;
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
