<template>
  <div class="lc">
    <div class="lc-row" @click="$emit('update:enabled', !enabled)">
      <i class="bi lc-check"
         :class="enabled ? 'bi-check-circle-fill' : 'bi-circle'"
         :style="enabled ? { color: color } : {}" />
      <div class="lc-label-wrap">
        <span class="lc-label">{{ label }}</span>
        <span v-if="info" class="lc-info">{{ info }}</span>
      </div>
    </div>
    <div v-if="enabled" class="lc-slider-area">
      <div class="lc-track-wrapper" ref="trackWrapper"
           @pointerdown="onPointerDown">
        <div class="lc-track-inner">
          <div class="lc-track" ref="track">
            <div class="lc-track-checker"></div>
            <div class="lc-track-gradient"></div>
          </div>
          <div class="lc-handle" :style="handleStyle" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, useTemplateRef } from 'vue';

const props = defineProps({
  label: { type: String, required: true },
  color: { type: String, default: '#6366f1' },
  info: { type: String, default: null },
  enabled: { type: Boolean, required: true },
  opacity: { type: Number, required: true },
});

const emit = defineEmits(['update:enabled', 'update:opacity']);

const track = useTemplateRef('track');

const handleStyle = computed(() => ({ left: `${props.opacity}%` }));

function updateFromEvent(e) {
  if (!track.value) return;
  const rect = track.value.getBoundingClientRect();
  const raw = ((e.clientX - rect.left) / rect.width) * 100;
  const clamped = Math.max(0, Math.min(100, Math.round(raw)));
  emit('update:opacity', clamped);
}

function onPointerDown(e) {
  e.preventDefault();
  updateFromEvent(e);
  const onMove = (ev) => { ev.preventDefault(); updateFromEvent(ev); };
  const onUp = () => {
    window.removeEventListener('pointermove', onMove);
    window.removeEventListener('pointerup', onUp);
    window.removeEventListener('pointercancel', onUp);
  };
  window.addEventListener('pointermove', onMove);
  window.addEventListener('pointerup', onUp);
  window.addEventListener('pointercancel', onUp);
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

.lc-track-wrapper {
  position: relative;
  padding: 10px 11px;
  margin: 0 -11px;
  cursor: pointer;
  touch-action: none;
}

.lc-track-inner {
  position: relative;
}

.lc-track {
  position: relative;
  height: 10px;
  border-radius: 5px;
  overflow: hidden;
  border: 1px solid var(--border-default);
}

.lc-track-checker {
  position: absolute;
  inset: 0;
  background-color: rgba(200, 200, 200, 0.2);
  background-image:
    linear-gradient(45deg, rgba(140, 140, 140, 0.18) 25%, transparent 25%),
    linear-gradient(-45deg, rgba(140, 140, 140, 0.18) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, rgba(140, 140, 140, 0.18) 75%),
    linear-gradient(-45deg, transparent 75%, rgba(140, 140, 140, 0.18) 75%);
  background-size: 8px 8px;
  background-position: 0 0, 0 4px, 4px -4px, -4px 0;
}

.lc-track-gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(to right, transparent, var(--accent));
}

.lc-handle {
  position: absolute;
  top: 50%;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #fff;
  border: 2.5px solid var(--accent);
  box-shadow: 0 1px 5px rgba(0, 0, 0, 0.18);
  transform: translate(-50%, -50%);
  pointer-events: none;
}

/* ── Touch-friendly sizing ── */
@media (pointer: coarse) {
  .lc-track-wrapper {
    padding: 14px 14px;
    margin: 0 -14px;
  }
  .lc-track {
    height: 12px;
    border-radius: 6px;
  }
  .lc-handle {
    width: 28px;
    height: 28px;
    border-width: 3px;
  }
}
</style>
