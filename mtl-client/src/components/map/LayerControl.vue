<template>
  <div class="lc" :class="{ 'lc--enabled': enabled }" :style="{ '--lc-accent': color }">
    <button
      type="button"
      class="lc-row"
      :aria-pressed="enabled"
      :aria-label="`${enabled ? 'Hide' : 'Show'} ${label}`"
      @click="emit('update:enabled', !enabled)"
    >
      <span class="lc-icon settings-row__icon" aria-hidden="true"><i :class="icon"></i></span>
      <span class="lc-label-wrap">
        <strong class="lc-label">{{ label }}</strong>
        <small v-if="info" class="lc-info">{{ info }}</small>
      </span>
      <span class="lc-switch" aria-hidden="true"><span></span></span>
    </button>

    <div v-if="enabled && showOpacity" class="lc-slider-area">
      <div class="lc-slider-heading">
        <span>Opacity</span>
        <output>{{ Math.round(opacity) }}%</output>
      </div>
      <MtlSlider
        class="lc-opacity-slider"
        :model-value="opacity"
        :min="0"
        :max="100"
        :step="1"
        variant="opacity"
        :aria-label="`${label} opacity`"
        @update:model-value="onOpacityChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import MtlSlider from '@/components/ui/MtlSlider.vue';

withDefaults(
  defineProps<{
    color?: string;
    enabled: boolean;
    icon?: string;
    info?: string | null;
    label: string;
    opacity: number;
    showOpacity?: boolean;
  }>(),
  {
    color: 'var(--accent)',
    icon: 'bi bi-layers',
    info: null,
    showOpacity: true,
  }
);

const emit = defineEmits<{
  (event: 'update:enabled', value: boolean): void;
  (event: 'update:opacity', value: number): void;
}>();

function onOpacityChange(value: number | number[]): void {
  emit('update:opacity', Array.isArray(value) ? value[0] : value);
}
</script>

<style scoped>
.lc {
  --lc-accent: var(--accent);
  background: transparent;
  transition: background 0.15s ease;
}

.lc:hover,
.lc:focus-within {
  background: var(--surface-hover);
}

.lc-row {
  display: grid;
  width: 100%;
  min-height: 4.5rem;
  grid-template-columns: 1.6rem minmax(0, 1fr) 2.7rem;
  align-items: center;
  gap: 0.7rem;
  padding: 0.72rem 0.2rem;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  text-align: left;
  cursor: pointer;
  user-select: none;
  -webkit-user-select: none;
}

.lc-row:focus-visible {
  position: relative;
  z-index: 1;
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.lc--enabled .lc-icon {
  background: transparent;
  color: var(--lc-accent);
}

.lc-label-wrap {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.08rem;
}

.lc-label {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-sm-lh);
}

.lc-info {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: normal;
  line-height: var(--text-xs-lh);
  white-space: normal;
}

.lc-switch {
  position: relative;
  display: inline-flex;
  width: 2.7rem;
  height: 1.5rem;
  justify-self: end;
  border-radius: 999px;
  background: var(--border-medium);
  transition: background 0.18s ease;
}

.lc--enabled .lc-switch {
  background: var(--lc-accent);
}

.lc-switch span {
  position: absolute;
  top: 0.175rem;
  left: 0.175rem;
  width: 1.15rem;
  height: 1.15rem;
  border-radius: 50%;
  background: var(--accent-contrast);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.28);
  transition: transform 0.18s ease;
}

.lc--enabled .lc-switch span {
  transform: translateX(1.2rem);
}

.lc-slider-area {
  padding: 0 0.25rem 0.85rem 2.5rem;
}

.lc-slider-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.12rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.lc-slider-heading output {
  color: var(--text-secondary);
  font-weight: var(--font-semibold);
  font-variant-numeric: tabular-nums;
}

.lc-opacity-slider {
  --mtl-slider-accent: var(--lc-accent);
  --mtl-slider-hit-padding-x: 12px;
  --mtl-slider-hit-padding-y: 10px;
  --mtl-slider-handle-size-default: 22px;
  --mtl-slider-handle-size-coarse: 28px;
  --mtl-slider-track-height-default: 10px;
  --mtl-slider-track-height-coarse: 12px;
  margin: 0 -12px;
}

@media (pointer: coarse) {
  .lc-row {
    min-height: 4.75rem;
  }

  .lc-opacity-slider {
    --mtl-slider-hit-padding-x: 14px;
    margin: 0 -14px;
  }
}

@media screen and (max-width: 440px) {
  .lc-row {
    grid-template-columns: 1.45rem minmax(0, 1fr) 2.7rem;
    gap: 0.6rem;
    padding-inline: 0.1rem;
  }

  .lc-icon {
    width: 1.45rem;
    height: 1.45rem;
  }

  .lc-slider-area {
    padding-left: 2.15rem;
    padding-right: 0.2rem;
  }
}
</style>
