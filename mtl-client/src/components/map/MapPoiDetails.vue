<template>
  <article class="map-poi-details" aria-label="Place details" :style="{ '--poi-color': presentation.color }">
    <div class="map-poi-details__identity">
      <span class="map-poi-details__icon" aria-hidden="true">
        <i :class="presentation.icon"></i>
      </span>
      <div class="map-poi-details__description">
        <p class="map-poi-details__type">{{ presentation.typeLabel }}</p>
        <p v-if="presentation.categoryLabel" class="map-poi-details__category">{{ presentation.categoryLabel }}</p>
      </div>
      <dl v-if="elevation !== null" class="map-poi-details__metric">
        <dt><i class="bi bi-triangle" aria-hidden="true"></i> Elevation</dt>
        <dd>{{ elevation }} m</dd>
      </dl>
    </div>
    <p class="map-poi-details__source">
      <i class="bi bi-map" aria-hidden="true"></i>
      <span>Source: {{ candidate.sourceLabel || 'Map places' }}</span>
    </p>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { mapPoiPresentation, type MapPoiCandidate } from './mapPoiPresentation';

defineOptions({ name: 'MapPoiDetails' });
const props = defineProps<{ candidate: MapPoiCandidate }>();
const presentation = computed(() => mapPoiPresentation(props.candidate));
const elevation = computed(() => {
  const value = props.candidate.properties.elevation;
  if (typeof value !== 'number' && typeof value !== 'string') return null;
  if (typeof value === 'string' && !value.trim()) return null;
  const meters = Number(value);
  return Number.isFinite(meters) ? meters : null;
});
</script>

<style scoped>
.map-poi-details {
  display: grid;
  gap: 0.85rem;
  padding: 1rem;
  border: 1px solid var(--border-default);
  border-radius: 0.85rem;
  background: var(--surface-elevated);
  color: var(--text-primary);
}

.map-poi-details__identity {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem;
}

.map-poi-details__icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 3rem;
  height: 3rem;
  border: 1px solid color-mix(in srgb, var(--poi-color) 25%, var(--border-default));
  border-radius: 0.75rem;
  background: color-mix(in srgb, var(--poi-color) 12%, var(--surface-sheet-solid));
  color: color-mix(in srgb, var(--poi-color) 70%, var(--text-primary));
  font-size: var(--text-xl-size);
}

.map-poi-details__description {
  flex: 1 1 8rem;
  min-width: 0;
  overflow-wrap: anywhere;
}

.map-poi-details__type,
.map-poi-details__category,
.map-poi-details__source {
  margin: 0;
}

.map-poi-details__type {
  font-size: var(--text-base-size);
  font-weight: 600;
}

.map-poi-details__category {
  margin-top: 0.15rem;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
}

.map-poi-details__metric {
  flex: 0 0 auto;
  margin: 0;
  padding-left: 0.8rem;
  border-left: 1px solid var(--border-default);
}

.map-poi-details__metric dt {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.map-poi-details__metric dd {
  margin: 0.2rem 0 0;
  font-size: var(--text-sm-size);
  font-weight: 600;
}

.map-poi-details__source {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  padding-top: 0.7rem;
  border-top: 1px solid var(--border-default);
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  overflow-wrap: anywhere;
}

.map-poi-details__source i {
  flex: 0 0 auto;
}
</style>
