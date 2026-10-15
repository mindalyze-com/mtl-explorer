<template>
  <div class="health-grouping-control">
    <span>Group by</span>
    <div class="health-grouping-options" role="group" aria-label="Group by">
      <button
        v-for="option in HEALTH_GROUPINGS"
        :key="option.value"
        type="button"
        :aria-pressed="modelValue === option.value"
        :title="option.value === 'AUTO' && resolved ? `Automatic grouping: ${resolved.toLowerCase()}` : undefined"
        @click="$emit('update:modelValue', option.value)"
      >
        {{ option.label }}
      </button>
    </div>
  </div>
</template>
<script setup lang="ts">
import { HEALTH_GROUPINGS, type HealthGrouping } from '../utils/healthChartPreferences';
defineProps<{ modelValue: HealthGrouping; resolved?: HealthGrouping }>();
defineEmits<{ 'update:modelValue': [value: HealthGrouping] }>();
</script>
<style scoped>
.health-grouping-control {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  white-space: nowrap;
}
.health-grouping-options {
  display: inline-flex;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  overflow: hidden;
}
button {
  min-height: 2.5rem;
  padding: 0.4rem 0.65rem;
  border: 0;
  border-right: 1px solid var(--border-default);
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  cursor: pointer;
}
button:last-child {
  border-right: 0;
}
button:hover {
  background: var(--surface-hover);
}
button[aria-pressed='true'] {
  background: var(--accent-bg);
  color: var(--accent-text);
}
button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}
@media (max-width: 700px) {
  .health-grouping-control {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.35rem;
  }
  .health-grouping-options {
    display: grid;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    width: 100%;
  }
  button {
    min-height: 2.75rem;
    padding-inline: 0.45rem;
  }
}
</style>
