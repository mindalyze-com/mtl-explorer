<template>
  <aside
    class="filter-catalog"
    :class="{
      'filter-catalog--can-scroll': listCanScroll,
      'filter-catalog--at-end': listAtEnd,
    }"
    aria-label="Filters"
  >
    <div class="filter-catalog__heading">
      <h2 class="filter-catalog__title">Choose a filter</h2>
      <span class="filter-catalog__total">{{ totalCount }} filters</span>
    </div>

    <div class="filter-catalog__search">
      <i class="bi bi-search filter-catalog__search-icon"></i>
      <input
        v-model="query"
        class="filter-catalog__search-input"
        type="search"
        placeholder="Search filters, groups, or keywords"
        autocomplete="off"
      />
    </div>

    <div class="filter-catalog__chips" aria-label="Filter groups">
      <button
        type="button"
        class="filter-catalog__chip"
        :class="{ 'filter-catalog__chip--active': activeGroupLabel === null }"
        @click="activeGroupLabel = null"
      >
        All
        <span class="filter-catalog__chip-count">{{ totalCount }}</span>
      </button>
      <button
        v-for="group in groups"
        :key="group.label"
        type="button"
        class="filter-catalog__chip"
        :class="{ 'filter-catalog__chip--active': activeGroupLabel === group.label }"
        @click="activeGroupLabel = group.label"
      >
        {{ group.label }}
        <span class="filter-catalog__chip-count">{{ group.items.length }}</span>
      </button>
    </div>

    <div ref="listScrollEl" class="filter-catalog__scroll" @scroll="updateScrollState">
      <section v-for="group in visibleGroups" :key="group.label" class="filter-catalog__group">
        <h3 class="filter-catalog__group-title">{{ group.label }}</h3>
        <button
          v-for="filterInfo in group.items"
          :key="filterKey(filterInfo)"
          type="button"
          class="filter-catalog-row"
          :class="{ 'filter-catalog-row--active': isSelected(filterInfo) }"
          @click="emit('select-filter', filterInfo)"
        >
          <span class="filter-catalog-row__icon" :class="groupIconClass(group.label)">
            <i :class="groupIcon(group.label)"></i>
          </span>
          <span class="filter-catalog-row__body">
            <span class="filter-catalog-row__title">{{ filterInfo.filterConfig?.displayName }}</span>
            <span v-if="filterInfo.filterConfig?.description" class="filter-catalog-row__description">
              {{ filterInfo.filterConfig.description }}
            </span>
          </span>
          <i
            class="filter-catalog-row__state bi"
            :class="isSelected(filterInfo) ? 'bi-check-circle-fill' : 'bi-chevron-right'"
          ></i>
        </button>
      </section>

      <div v-if="visibleGroups.length === 0" class="filter-catalog__empty">
        <i class="bi bi-search"></i>
        <span>No filters found</span>
      </div>
    </div>
    <button
      type="button"
      class="filter-catalog__scroll-cue"
      :disabled="!listCanScroll || listAtEnd"
      aria-label="Scroll filter list down"
      @pointerdown.stop
      @click.stop="scrollListDown"
    >
      <i class="bi bi-chevron-down"></i>
    </button>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, onUpdated, ref, watch } from 'vue';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import {
  filterOptionGroupItemCount,
  filterOptionGroupsByCatalogSearch,
  isSameFilterInfo,
  type FilterOptionGroup,
} from '@/utils/filterMetadata';

const CATALOG_SCROLL_PAGE_FRACTION = 0.82;

defineOptions({ name: 'FilterCatalog' });

const props = defineProps<{
  groups: FilterOptionGroup[];
  selectedFilterInfo?: FilterInfo | null;
}>();

const emit = defineEmits<{
  (event: 'select-filter', filterInfo: FilterInfo): void;
}>();

const query = ref('');
const activeGroupLabel = ref<string | null>(null);
const listCanScroll = ref(false);
const listAtEnd = ref(true);
const listScrollEl = ref<HTMLElement | null>(null);

let resizeHandler: (() => void) | null = null;
let resizeObserver: ResizeObserver | null = null;

const totalCount = computed((): number => filterOptionGroupItemCount(props.groups));
const visibleGroups = computed((): FilterOptionGroup[] =>
  filterOptionGroupsByCatalogSearch(props.groups, query.value, activeGroupLabel.value)
);

watch([query, activeGroupLabel], () => {
  updateScrollStateSoon();
});

watch(
  () => props.groups,
  () => {
    if (activeGroupLabel.value && !props.groups.some((group) => group.label === activeGroupLabel.value)) {
      activeGroupLabel.value = null;
    }
    updateScrollStateSoon();
  }
);

onMounted(() => {
  updateScrollStateSoon();
  resizeHandler = () => updateScrollStateSoon();
  window.addEventListener('resize', resizeHandler);
  const el = listScrollEl.value;
  if (el && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => updateScrollStateSoon());
    resizeObserver.observe(el);
  }
});

onUpdated(() => {
  updateScrollStateSoon();
});

onBeforeUnmount(() => {
  if (resizeHandler) window.removeEventListener('resize', resizeHandler);
  resizeObserver?.disconnect();
});

function updateScrollStateSoon(): void {
  void nextTick(() => updateScrollState());
}

function updateScrollState(): void {
  const el = listScrollEl.value;
  if (!el) return;
  const threshold = 3;
  listCanScroll.value = el.scrollHeight > el.clientHeight + threshold;
  listAtEnd.value = el.scrollTop + el.clientHeight >= el.scrollHeight - threshold;
}

function scrollListDown(): void {
  const el = listScrollEl.value;
  if (!el) return;
  el.scrollBy({
    top: Math.max(el.clientHeight * CATALOG_SCROLL_PAGE_FRACTION, 1),
    behavior: 'smooth',
  });
}

function filterKey(filterInfo: FilterInfo): string {
  const filterConfig = filterInfo.filterConfig;
  return String(
    filterConfig?.id ??
      `${filterConfig?.filterDomain ?? ''}:${filterConfig?.filterName ?? filterConfig?.displayName ?? ''}`
  );
}

function isSelected(filterInfo: FilterInfo): boolean {
  return isSameFilterInfo(props.selectedFilterInfo, filterInfo);
}

function groupIcon(groupLabel: string): string {
  const normalized = groupLabel.toLowerCase();
  if (normalized.includes('activity')) return 'bi bi-bicycle';
  if (normalized.includes('date') || normalized.includes('time')) return 'bi bi-calendar3';
  if (normalized.includes('quality')) return 'bi bi-shield-exclamation';
  if (normalized.includes('performance')) return 'bi bi-speedometer2';
  if (normalized.includes('people')) return 'bi bi-people';
  if (normalized.includes('core')) return 'bi bi-funnel';
  if (normalized.includes('user')) return 'bi bi-person';
  return 'bi bi-sliders';
}

function groupIconClass(groupLabel: string): string {
  const normalized = groupLabel
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
  return `filter-catalog-row__icon--${normalized || 'default'}`;
}
</script>

<style scoped>
.filter-catalog {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  color: var(--text-secondary);
}

.filter-catalog__heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  padding-bottom: 0.75rem;
}

.filter-catalog__title {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-base-size, 1rem);
  font-weight: 800;
  line-height: var(--text-base-lh, 1.4);
}

.filter-catalog__total {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: var(--text-xs-lh);
}

.filter-catalog__search {
  position: relative;
  flex: 0 0 auto;
}

.filter-catalog__search-icon {
  position: absolute;
  left: 0.85rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  pointer-events: none;
}

.filter-catalog__search-input {
  width: 100%;
  min-height: 2.65rem;
  padding: 0.55rem 0.85rem 0.55rem 2.35rem;
  border: 1px solid var(--border-default);
  border-radius: 0.6rem;
  background: var(--surface-glass-heavy);
  color: var(--text-primary);
  font: inherit;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  outline: none;
  transition:
    border-color 0.15s,
    background 0.15s;
}

.filter-catalog__search-input:focus {
  border-color: var(--accent);
  background: var(--surface-glass-light);
}

.filter-catalog__search-input::placeholder {
  color: var(--text-muted);
}

.filter-catalog__chips {
  display: flex;
  flex-wrap: wrap;
  flex: 0 0 auto;
  gap: 0.4rem;
  padding: 0.75rem 0 0.65rem;
  overflow: visible;
}

.filter-catalog__chips::-webkit-scrollbar {
  display: none;
}

.filter-catalog__chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  min-height: 1.9rem;
  padding: 0.3rem 0.7rem;
  border: 1px solid var(--border-default);
  border-radius: 9999px;
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: 600;
  line-height: var(--text-xs-lh);
  white-space: nowrap;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s,
    color 0.15s;
}

.filter-catalog__chip:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}

.filter-catalog__chip--active {
  border-color: transparent;
  background: var(--accent-subtle);
  color: var(--accent-text);
}

.filter-catalog__chip-count {
  opacity: 0.65;
}

.filter-catalog__scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding-right: 0;
  padding-bottom: 1.2rem;
  scrollbar-width: none;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
}

.filter-catalog__scroll::-webkit-scrollbar {
  display: none;
}

.filter-catalog__scroll-cue {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: none;
  align-items: end;
  justify-content: center;
  height: 3rem;
  padding: 0 0 0.15rem;
  border: 0;
  appearance: none;
  box-sizing: border-box;
  font: inherit;
  pointer-events: auto;
  cursor: pointer;
  color: var(--text-muted);
  background: linear-gradient(
    to bottom,
    transparent,
    color-mix(in srgb, var(--surface-sheet-solid) 82%, transparent) 54%,
    var(--surface-sheet-solid)
  );
}

.filter-catalog__scroll-cue:disabled {
  cursor: default;
}

.filter-catalog__scroll-cue i {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.7rem;
  height: 1.7rem;
  border: 1px solid var(--border-default);
  border-radius: 9999px;
  background: var(--surface-glass-heavy);
  color: var(--accent-text);
  font-size: var(--text-xs-size);
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.08);
}

.filter-catalog__scroll-cue:hover i,
.filter-catalog__scroll-cue:focus-visible i {
  border-color: var(--accent);
  background: var(--accent-subtle);
}

.filter-catalog--can-scroll:not(.filter-catalog--at-end) .filter-catalog__scroll-cue {
  display: flex;
}

.filter-catalog__group + .filter-catalog__group {
  margin-top: 0.85rem;
}

.filter-catalog__group-title {
  margin: 0 0 0.4rem;
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: var(--text-xs-lh);
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.filter-catalog-row {
  width: 100%;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-height: 4.6rem;
  padding: 0.75rem;
  border: 1px solid var(--border-default);
  border-radius: 0.55rem;
  background: var(--surface-glass-subtle);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s,
    box-shadow 0.15s;
}

.filter-catalog-row + .filter-catalog-row {
  margin-top: 0.45rem;
}

.filter-catalog-row:hover {
  border-color: var(--border-hover);
  background: var(--surface-hover);
}

.filter-catalog-row--active {
  border-color: var(--accent);
  background: var(--accent-subtle);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent) 30%, transparent);
}

.filter-catalog-row__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 2.45rem;
  height: 2.45rem;
  border-radius: 0.6rem;
  background: var(--surface-glass-light);
  color: var(--accent-text);
  font-size: var(--text-lg-size);
}

.filter-catalog-row__icon--quality {
  color: var(--warning, #d97706);
}

.filter-catalog-row__icon--performance {
  color: var(--success, #15803d);
}

.filter-catalog-row__body {
  min-width: 0;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.filter-catalog-row__title {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 600;
  line-height: var(--text-sm-lh);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.filter-catalog-row__description {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.filter-catalog-row__state {
  flex: 0 0 auto;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}

.filter-catalog-row--active .filter-catalog-row__state {
  color: var(--accent-text);
}

.filter-catalog__empty {
  min-height: 8rem;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}

@media screen and (max-width: 768px) {
  .filter-catalog__chips {
    flex-wrap: nowrap;
    overflow-x: auto;
    scrollbar-width: none;
  }
}
</style>
