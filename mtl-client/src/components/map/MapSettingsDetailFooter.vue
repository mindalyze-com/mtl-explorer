<template>
  <div class="map-settings-detail-footer">
    <span>Changes are live on the map.</span>
    <button type="button" @click="emit('done')">Done</button>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'MapSettingsDetailFooter' });

const emit = defineEmits<{
  (event: 'done'): void;
}>();
</script>

<style scoped>
.map-settings-detail-footer {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem calc(0.15rem + env(safe-area-inset-bottom));
  border-top: 1px solid var(--border-subtle, var(--border-default));
  background: var(--surface-ground);
}

.map-settings-detail-footer span {
  min-width: 0;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.map-settings-detail-footer button {
  display: inline-flex;
  min-height: 2.65rem;
  align-items: center;
  justify-content: center;
  margin-left: auto;
  padding: 0.5rem 1rem;
  border: 0;
  border-radius: 0.65rem;
  background: var(--accent);
  color: var(--text-inverse);
  font: inherit;
  font-size: var(--text-sm-size);
  font-weight: var(--font-semibold);
  cursor: pointer;
}
</style>
