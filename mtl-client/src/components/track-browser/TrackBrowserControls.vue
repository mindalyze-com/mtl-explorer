<template>
  <div class="track-browser-controls">
    <span class="track-browser-controls__search">
      <i class="pi pi-search track-browser-controls__search-icon" />
      <InputText
        ref="searchInput"
        aria-label="Search tracks by name, activity, date, file or creator"
        :model-value="query"
        placeholder="Search tracks…"
        title="Search by name, activity, date, file or creator"
        class="track-browser-controls__input"
        @update:model-value="(value) => emit('update:query', String(value || ''))"
      />
      <button
        v-if="query"
        type="button"
        class="track-browser-controls__clear"
        aria-label="Clear search"
        @click="clearSearch"
      >
        <i class="pi pi-times" aria-hidden="true" />
      </button>
    </span>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

defineProps<{
  query: string;
}>();

const searchInput = ref<{ $el: HTMLInputElement } | null>(null);

function clearSearch() {
  emit('update:query', '');
  searchInput.value?.$el.focus();
}

const emit = defineEmits<{
  (event: 'update:query', value: string): void;
}>();
</script>

<style scoped>
.track-browser-controls {
  flex: 1 1 18rem;
  min-width: 0;
  max-width: 28rem;
}

.track-browser-controls__search {
  display: flex;
  align-items: center;
  position: relative;
  width: 100%;
}

.track-browser-controls__search-icon {
  position: absolute;
  left: 0.8rem;
  color: var(--text-muted);
  pointer-events: none;
  z-index: 1;
}

.track-browser-controls__input {
  width: 100%;
  min-height: var(--control-field-height);
  padding-inline: 2.4rem 2.75rem !important;
}

.track-browser-controls__clear {
  position: absolute;
  right: 0;
  display: grid;
  place-items: center;
  width: var(--control-field-height);
  height: var(--control-field-height);
  border: 0;
  border-radius: var(--p-inputtext-border-radius);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.track-browser-controls__clear:hover {
  color: var(--text-primary);
}

.track-browser-controls__clear:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -3px;
}
</style>
