<template>
  <span class="track-choice-facts">
    <span
      v-for="fact in facts"
      :key="fact.key"
      class="track-choice-fact"
      :data-fact="fact.key"
      :title="`${fact.label}: ${fact.value}`"
      :aria-label="`${fact.label}: ${fact.value}`"
    >
      <i class="bi" :class="fact.icon" aria-hidden="true"></i>
      <span>{{ fact.value }}</span>
    </span>
  </span>
</template>

<script setup lang="ts">
import type { TrackChoiceFact } from './trackChoiceFactFormatting';

defineProps<{ facts: TrackChoiceFact[] }>();
</script>

<style scoped>
.track-choice-facts {
  display: contents;
}
.track-choice-fact {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--track-choice-badge-gap, 0.25rem);
  padding: var(--track-choice-badge-padding, 0.1rem 0.35rem);
  border: 1px solid var(--border-default);
  border-radius: 1rem;
  background: var(--surface-elevated);
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.track-choice-fact .bi {
  color: var(--text-muted);
  font-size: inherit;
}
</style>
