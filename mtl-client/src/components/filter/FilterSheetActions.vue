<template>
  <div class="filter-sheet-footer">
    <div v-if="$slots.status" class="filter-sheet-feedback" role="status" aria-live="polite">
      <slot name="status" />
    </div>
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
        <button v-if="cancelVisible" type="button" class="filter-sheet-actions__cancel" @click="emit('cancel')">
          {{ cancelLabel }}
        </button>
        <button type="button" class="filter-sheet-actions__apply" :disabled="applyDisabled" @click="emit('apply')">
          {{ applyLabel }}
        </button>
      </div>
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
    applyLabel?: string;
    commitVisible?: boolean;
    cancelVisible?: boolean;
    cancelLabel?: string;
  }>(),
  {
    resetVisible: false,
    resetLabel: 'Reset',
    resetAriaLabel: '',
    applyDisabled: false,
    applyLabel: 'Apply',
    commitVisible: true,
    cancelVisible: true,
    cancelLabel: 'Cancel',
  }
);

const emit = defineEmits<{
  (event: 'reset'): void;
  (event: 'cancel'): void;
  (event: 'apply'): void;
}>();
</script>

<style scoped>
.filter-sheet-feedback {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.55rem 1rem;
  border-top: 1px solid var(--border-default);
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.filter-sheet-feedback :deep(p) {
  margin: 0;
}

.filter-sheet-feedback :deep(strong) {
  color: var(--text-primary);
  font-weight: var(--font-semibold);
}

.filter-sheet-actions {
  display: flex;
  min-width: 0;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
  /* BottomSheet owns the screen safe-area padding. */
  padding: 0.75rem 1rem 0.15rem;
  border-top: 1px solid var(--border-subtle, var(--border-default));
  background: var(--surface-ground);
}

.filter-sheet-actions button {
  display: inline-flex;
  min-height: 2.75rem;
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
  color: var(--accent-contrast) !important;
  font-weight: var(--font-semibold);
}

.filter-sheet-actions button:focus-visible {
  outline: 2px solid var(--focus-ring, var(--accent));
  outline-offset: 2px;
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
