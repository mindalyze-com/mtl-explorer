<template>
  <Teleport to="body">
    <div class="map-config-notice" role="alert" data-test="map-config-fallback-notice">
      <div class="map-config-notice__content">
        <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
        <div>
          <div class="map-config-notice__title">Map settings could not be loaded</div>
          <div class="map-config-notice__detail">A basic online map is being used.</div>
        </div>
      </div>
      <div class="map-config-notice__actions">
        <button type="button" class="map-config-notice__retry" :disabled="retrying" @click="emit('retry')">
          <i class="bi bi-arrow-clockwise" aria-hidden="true"></i>
          {{ retrying ? 'Retrying…' : 'Retry' }}
        </button>
        <button type="button" :disabled="retrying" @click="emit('dismiss')">Dismiss</button>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ retrying?: boolean }>(), { retrying: false });

const emit = defineEmits<{
  (event: 'retry'): void;
  (event: 'dismiss'): void;
}>();
</script>

<style scoped>
.map-config-notice {
  position: fixed;
  top: max(0.75rem, env(safe-area-inset-top));
  left: 50%;
  z-index: 5600;
  display: flex;
  width: min(34rem, calc(100vw - 1.5rem));
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 0.9rem;
  transform: translateX(-50%);
  border: 1px solid color-mix(in srgb, var(--warning) 48%, var(--border-subtle));
  border-radius: 0.75rem;
  background: var(--surface-sheet-solid);
  box-shadow: var(--shadow-lg);
  color: var(--text-primary);
}

.map-config-notice__content,
.map-config-notice__actions {
  display: flex;
  align-items: center;
  gap: 0.65rem;
}

.map-config-notice__content > i {
  color: var(--warning);
  font-size: 1.25rem;
}

.map-config-notice__title {
  font-weight: 650;
}

.map-config-notice__detail {
  margin-top: 0.15rem;
  color: var(--text-secondary);
  font-size: 0.82rem;
}

.map-config-notice button {
  min-height: 2.25rem;
  padding: 0.4rem 0.7rem;
  border: 1px solid var(--border-subtle);
  border-radius: 0.5rem;
  background: var(--surface-sheet-solid);
  color: var(--text-primary);
  cursor: pointer;
}

.map-config-notice button:disabled {
  cursor: wait;
  opacity: 0.65;
}

.map-config-notice__retry {
  border-color: var(--accent) !important;
  background: var(--accent) !important;
  color: var(--accent-contrast) !important;
}

@media (max-width: 540px) {
  .map-config-notice {
    align-items: stretch;
    flex-direction: column;
  }

  .map-config-notice__actions {
    justify-content: flex-end;
  }
}
</style>
