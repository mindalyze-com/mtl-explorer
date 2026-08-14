<template>
  <div class="filter-sheet-actions">
    <button
      v-if="resetVisible"
      type="button"
      class="filter-sheet-actions__reset"
      :aria-label="resetAriaLabel || resetLabel"
      @click="emit('reset')"
    >
      <i class="bi bi-arrow-counterclockwise" aria-hidden="true"></i>
      {{ resetLabel }}
    </button>

    <div v-if="commitVisible" class="filter-sheet-actions__end">
      <button type="button" class="filter-sheet-actions__cancel" @click="emit('cancel')">Cancel</button>
      <button type="button" class="filter-sheet-actions__apply" :disabled="applyDisabled" @click="emit('apply')">
        Apply
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'FilterSheetActions' });

withDefaults(
  defineProps<{
    resetVisible?: boolean;
    resetLabel?: string;
    resetAriaLabel?: string;
    applyDisabled?: boolean;
    commitVisible?: boolean;
  }>(),
  {
    resetVisible: false,
    resetLabel: 'Reset',
    resetAriaLabel: '',
    applyDisabled: false,
    commitVisible: true,
  }
);

const emit = defineEmits<{
  (event: 'reset'): void;
  (event: 'cancel'): void;
  (event: 'apply'): void;
}>();
</script>

<style scoped>
.filter-sheet-actions {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem calc(0.15rem + env(safe-area-inset-bottom));
  border-top: 1px solid var(--border-subtle, var(--border-default));
  background: var(--surface-ground);
}

.filter-sheet-actions button {
  display: inline-flex;
  min-height: 2.65rem;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.5rem 0.9rem;
  border: 0;
  border-radius: 0.65rem;
  background: transparent;
  font: inherit;
  font-size: var(--text-sm-size);
  cursor: pointer;
}

.filter-sheet-actions__reset {
  color: var(--text-muted);
  font-weight: var(--font-medium);
}

.filter-sheet-actions__reset:hover {
  background: var(--surface-hover);
  color: var(--text-secondary);
}

.filter-sheet-actions__end {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.5rem;
  margin-left: auto;
}

.filter-sheet-actions__cancel {
  color: var(--accent-text);
  font-weight: var(--font-semibold);
}

.filter-sheet-actions__cancel:hover {
  background: var(--surface-hover);
}

.filter-sheet-actions__apply {
  background: var(--accent) !important;
  color: var(--text-inverse) !important;
  font-weight: var(--font-semibold);
}

.filter-sheet-actions button:disabled {
  cursor: default;
  opacity: 0.45;
}

@media screen and (max-width: 360px) {
  .filter-sheet-actions {
    padding-inline: 0.75rem;
  }

  .filter-sheet-actions button {
    padding-inline: 0.7rem;
  }
}
</style>
