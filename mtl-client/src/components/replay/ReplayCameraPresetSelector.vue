<template>
  <div class="camera-presets">
    <button
      v-for="preset in REPLAY_CAMERA_PRESETS"
      :key="preset.id"
      type="button"
      class="camera-presets__button replay-choice"
      :class="{ 'camera-presets__button--active': modelValue === preset.id }"
      @click="emit('select', preset.id)"
    >
      <i :class="preset.icon"></i>
      <span>{{ preset.label }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { REPLAY_CAMERA_PRESETS } from '@/components/replay/trackReplayCamera';
import type { ReplayCameraPresetId } from '@/components/replay/trackReplayPath';

defineOptions({ name: 'ReplayCameraPresetSelector' });

defineProps<{ modelValue: ReplayCameraPresetId }>();
const emit = defineEmits<{ select: [preset: ReplayCameraPresetId] }>();
</script>

<style scoped>
.camera-presets {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  min-width: 0;
  border: 1px solid var(--border-medium);
  border-radius: 0.95rem;
  overflow: hidden;
}

.camera-presets__button {
  flex: 1 1 0;
  border-width: 0 1px 0 0;
  border-style: solid;
  border-color: var(--border-medium);
  border-radius: 0;
  background: rgba(255, 255, 255, 0.54);
}

.camera-presets__button:last-child {
  border-right: 0;
}

.camera-presets__button--active {
  border-color: var(--replay-accent-bright);
  background: var(--replay-accent-gradient);
  color: var(--accent-contrast);
}

@media (max-width: 768px) {
  .camera-presets__button {
    min-height: 2.65rem;
  }
}
</style>
