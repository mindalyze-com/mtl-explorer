<template>
  <span class="filter-off-badge" role="status" aria-label="Filtering is off">
    <i class="bi bi-pause-circle" aria-hidden="true"></i>
    Filtering off
  </span>
</template>

<script setup lang="ts">
defineOptions({ name: 'FilterOffBadge' });
</script>

<style scoped>
.filter-off-badge {
  display: inline-flex;
  min-height: 1.8rem;
  box-sizing: border-box;
  align-items: center;
  gap: 0.35rem;
  padding: 0.22rem 0.55rem;
  border: 1px solid color-mix(in srgb, var(--warning) 45%, var(--border-default));
  border-radius: 999px;
  background: color-mix(in srgb, var(--warning) 9%, var(--surface-elevated));
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-xs-lh);
  white-space: nowrap;
}

.filter-off-badge > i {
  color: var(--warning);
}

@media screen and (max-width: 360px) {
  .filter-off-badge {
    width: 1.8rem;
    justify-content: center;
    padding-inline: 0;
    overflow: hidden;
    color: transparent;
    font-size: 0;
  }

  .filter-off-badge > i {
    font-size: var(--text-sm-size);
  }
}
</style>
