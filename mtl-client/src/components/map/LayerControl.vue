<template>
  <div class="lc">
    <div class="lc-row" @click="$emit('update:enabled', !enabled)">
      <i
        class="bi lc-check"
        :class="enabled ? 'bi-check-circle-fill' : 'bi-circle'"
        :style="enabled ? { color: color } : {}"
      />
      <div class="lc-label-wrap">
        <span class="lc-label">{{ label }}</span>
        <span v-if="info" class="lc-info">{{ info }}</span>
      </div>
    </div>
    <div v-if="enabled && showOpacity" class="lc-slider-area">
      <MtlSlider
        class="lc-opacity-slider"
        :model-value="opacity"
        :min="0"
        :max="100"
        :step="1"
        variant="opacity"
        :aria-label="`${label} opacity`"
        :style="{ '--mtl-slider-accent': color }"
        @update:model-value="onOpacityChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import MtlSlider from '@/components/ui/MtlSlider.vue';

defineProps({
  label: { type: String, required: true },
  color: { type: String, default: '#6366f1' },
  info: { type: String, default: null },
  enabled: { type: Boolean, required: true },
  opacity: { type: Number, required: true },
  showOpacity: { type: Boolean, default: true },
});

const emit = defineEmits<{
  'update:enabled': [value: boolean];
  'update:opacity': [value: number];
}>();

function onOpacityChange(value: number | number[]) {
  emit('update:opacity', Array.isArray(value) ? value[0] : value);
}
</script>

<style scoped>
.lc-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.45rem 0.3rem;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.12s;
  user-select: none;
  -webkit-user-select: none;
}
.lc-row:hover {
  background: var(--surface-hover);
}

.lc-check {
  font-size: var(--text-lg-size);
  min-width: 1.3rem;
  text-align: center;
  color: var(--text-faint);
  transition: color 0.15s;
}

.lc-label-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.05rem;
  min-width: 0;
}

.lc-label {
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
}

.lc-info {
  font-size: var(--text-xs-size);
  color: var(--text-faint);
  line-height: var(--text-xs-lh);
  white-space: normal;
}

/* ── Slider ── */
.lc-slider-area {
  padding: 0 0.75rem 0.35rem 2.1rem;
}

.lc-opacity-slider {
  --mtl-slider-hit-padding-x: 11px;
  --mtl-slider-hit-padding-y: 10px;
  --mtl-slider-handle-size-default: 22px;
  --mtl-slider-handle-size-coarse: 28px;
  --mtl-slider-track-height-default: 10px;
  --mtl-slider-track-height-coarse: 12px;
  margin: 0 -11px;
}

/* ── Touch-friendly sizing ── */
@media (pointer: coarse) {
  .lc-opacity-slider {
    --mtl-slider-hit-padding-x: 14px;
    margin: 0 -14px;
  }
}
</style>
