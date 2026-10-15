<script setup lang="ts">
defineProps<{ label: string; description?: string }>();
</script>
<template>
  <span class="finding-marker" role="img" :aria-label="label" :title="description || label">!</span>
</template>
<style scoped>
.finding-marker {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1rem;
  height: 1rem;
  margin-inline-end: 0.35rem;
  border: 1px solid currentColor;
  border-radius: 50%;
  color: var(--error-text);
  font-size: 0.75rem;
  font-weight: 700;
  line-height: 1;
  vertical-align: text-bottom;
}
</style>
