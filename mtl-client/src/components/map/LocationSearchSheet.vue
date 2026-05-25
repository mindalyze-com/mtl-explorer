<template>
  <BottomSheet
    :model-value="modelValue"
    :detents="SEARCH_DETENTS"
    initial-detent="compact"
    title="Search"
    icon="bi bi-search"
    header-mode="compact"
    :z-index="LOCATION_SEARCH_Z_INDEX"
    sheet-class="location-search-sheet"
    :no-backdrop="true"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="emit('closed')"
  >
    <div class="location-search">
      <div class="location-search__input-row">
        <i class="bi bi-search location-search__input-icon"></i>
        <input
          ref="inputEl"
          v-model="query"
          class="location-search__input"
          type="search"
          autocomplete="off"
          autocapitalize="words"
          spellcheck="false"
          placeholder="City, peak, area"
          @keydown.enter.prevent="selectFirstResult"
        />
        <button v-if="query" class="location-search__clear" type="button" aria-label="Clear search" @click="clearQuery">
          <i class="bi bi-x-lg"></i>
        </button>
      </div>

      <div class="location-search__sort" role="radiogroup" aria-label="Search result sort">
        <button
          v-for="option in SORT_OPTIONS"
          :key="option.id"
          class="location-search__sort-button"
          :class="{ 'location-search__sort-button--active': selectedSort === option.id }"
          type="button"
          role="radio"
          :aria-checked="selectedSort === option.id"
          @click="selectedSort = option.id"
        >
          <i :class="option.icon"></i>
          <span>{{ option.label }}</span>
        </button>
      </div>

      <div v-if="loading" class="location-search__state">
        <span class="location-search__spinner"></span>
        <span>Searching</span>
      </div>

      <div v-else-if="stateMessage" class="location-search__state">
        <i :class="stateIcon"></i>
        <span>{{ stateMessage }}</span>
      </div>

      <ul v-else class="location-search__results">
        <li v-for="result in results" :key="resultKey(result)" class="location-search__result-item">
          <button class="location-search__result" type="button" @click="emit('select', result)">
            <span class="location-search__result-icon">
              <i :class="iconForResult(result)"></i>
            </span>
            <span class="location-search__result-main">
              <span class="location-search__result-title">{{ result.displayName || result.name }}</span>
              <span class="location-search__result-context">{{ contextForResult(result) }}</span>
              <span class="location-search__result-meta">
                <span>{{ kindLabel(result) }}</span>
                <span v-if="zoomLabel(result)">{{ zoomLabel(result) }}</span>
                <span v-if="distanceLabel(result)">{{ distanceLabel(result) }}</span>
              </span>
            </span>
            <i class="bi bi-arrow-return-left location-search__result-action"></i>
          </button>
        </li>
      </ul>
    </div>
  </BottomSheet>
</template>

<script setup lang="ts">
import { toRef } from 'vue';
import type { LocationSearchResultDto } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import {
  contextForResult,
  distanceLabel,
  iconForResult,
  kindLabel,
  LOCATION_SEARCH_Z_INDEX,
  resultKey,
  SEARCH_DETENTS,
  SORT_OPTIONS,
  useLocationSearch,
  zoomLabel,
  type MapCenter,
} from '@/components/map/useLocationSearch';

const props = defineProps<{
  modelValue: boolean;
  mapCenter: MapCenter | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'closed'): void;
  (e: 'select', result: LocationSearchResultDto): void;
}>();

const { inputEl, query, selectedSort, loading, results, stateMessage, stateIcon, clearQuery, selectFirstResult } =
  useLocationSearch({
    modelValue: toRef(props, 'modelValue'),
    mapCenter: toRef(props, 'mapCenter'),
    onSelect: (result) => emit('select', result),
  });
</script>

<style scoped>
.location-search {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-height: 0;
  padding: 0 0.9rem 0.9rem;
}

.location-search__input-row {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  min-height: 2.75rem;
  padding: 0 0.75rem;
  border: 1px solid var(--border-medium);
  border-radius: 0.5rem;
  background: var(--surface-glass-heavy);
  color: var(--text-secondary);
}

.location-search__input-icon {
  flex: 0 0 auto;
  color: var(--text-muted);
}

.location-search__input {
  flex: 1 1 auto;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  font-size: var(--text-sm-size);
}

.location-search__input::placeholder {
  color: var(--text-muted);
}

.location-search__clear {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.85rem;
  height: 1.85rem;
  border: 0;
  border-radius: 0.4rem;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.location-search__clear:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}

.location-search__sort {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.45rem;
}

.location-search__sort-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  min-width: 0;
  min-height: 2.25rem;
  border: 1px solid var(--border-subtle);
  border-radius: 0.5rem;
  background: var(--surface-elevated);
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: 700;
  cursor: pointer;
}

.location-search__sort-button:hover {
  border-color: var(--border-medium);
  color: var(--text-primary);
}

.location-search__sort-button--active {
  border-color: color-mix(in srgb, var(--accent) 55%, var(--border-medium));
  background: color-mix(in srgb, var(--accent-bg) 72%, var(--surface-elevated));
  color: var(--accent);
}

.location-search__state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  min-height: 5.5rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}

.location-search__spinner {
  width: 1rem;
  height: 1rem;
  border: 2px solid var(--border-medium);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: location-search-spin 0.8s linear infinite;
}

.location-search__results {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 0.45rem;
  min-height: 0;
  margin: 0;
  padding: 0 0 0.35rem;
  overflow-y: auto;
  list-style: none;
  -webkit-overflow-scrolling: touch;
}

.location-search__result-item {
  min-width: 0;
}

.location-search__result {
  display: grid;
  grid-template-columns: 2.15rem minmax(0, 1fr) 1rem;
  align-items: center;
  gap: 0.7rem;
  width: 100%;
  min-width: 0;
  min-height: 4.35rem;
  padding: 0.55rem 0.65rem;
  border: 1px solid var(--border-subtle);
  border-radius: 0.5rem;
  background: var(--surface-glass-subtle);
  color: var(--text-secondary);
  cursor: pointer;
  text-align: left;
}

.location-search__result:hover {
  border-color: color-mix(in srgb, var(--accent-muted) 55%, var(--border-default));
  background: color-mix(in srgb, var(--accent-bg) 58%, var(--surface-glass-heavy));
}

.location-search__result-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.15rem;
  height: 2.15rem;
  border-radius: 0.5rem;
  background: var(--surface-elevated);
  color: var(--accent);
  font-size: var(--text-base-size);
}

.location-search__result-main {
  display: flex;
  flex-direction: column;
  gap: 0.16rem;
  min-width: 0;
}

.location-search__result-title,
.location-search__result-context,
.location-search__result-meta {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.location-search__result-title {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 700;
}

.location-search__result-context {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
}

.location-search__result-meta {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
  font-weight: 700;
  text-transform: uppercase;
}

.location-search__result-meta > span:not(:first-child)::before {
  content: '·';
  margin-right: 0.4rem;
  color: var(--text-faint);
}

.location-search__result-action {
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}

@keyframes location-search-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 420px) {
  .location-search {
    padding-inline: 0.65rem;
  }

  .location-search__result {
    grid-template-columns: 2rem minmax(0, 1fr);
  }

  .location-search__result-action {
    display: none;
  }
}
</style>
