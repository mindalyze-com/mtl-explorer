<template>
  <div class="mtl-card" :class="{ 'mtl-card--identified-filter': filterActive && activeFilterIdentity }">
    <!-- ── Single header row: [count zone] [| map visibility ▾] ── -->
    <div class="mtl-card__header">
      <!-- Left: track count — click opens filter panel -->
      <button type="button" class="mtl-card__count" :aria-label="filterButtonAriaLabel" @click="$emit('chip-click')">
        <i v-if="filterActive" class="bi bi-funnel-fill mtl-card__funnel"></i>
        <span class="mtl-card__filter-summary">
          <span
            v-if="filterActive && activeFilterIdentity"
            class="mtl-card__filter-identity"
            :title="activeFilterIdentity"
            >{{ activeFilterIdentity }}</span
          >
          <span class="mtl-card__track-count">{{ trackCountLabel }}</span>
        </span>
      </button>
      <!-- Right: legend toggle — only when entries exist -->
      <button
        v-if="entries.length > 0"
        type="button"
        class="mtl-card__legend-toggle"
        :aria-expanded="!collapsed"
        @click="$emit('update:collapsed', !collapsed)"
      >
        <span class="mtl-card__legend-label">Map visibility</span>
        <i :class="collapsed ? 'bi bi-chevron-down' : 'bi bi-chevron-up'" class="mtl-card__chevron"></i>
      </button>
    </div>

    <!-- Legend body — only expand when entries exist -->
    <div v-show="entries.length > 0 && !collapsed" class="mtl-card__body">
      <div v-if="hiddenGroups.size > 0" class="mtl-card__visibility-actions">
        <span>{{ hiddenGroups.size }} hidden</span>
        <button type="button" class="mtl-card__show-all" @click="showAllGroups">Show all</button>
      </div>
      <div v-if="isGradientLegend" class="mtl-card__gradient">
        <button
          v-for="band in gradientBands"
          :key="band.key"
          type="button"
          class="mtl-card__gradient-band"
          :class="{
            'mtl-card__gradient-band--disabled': band.allHidden,
            'mtl-card__gradient-band--partial': band.partiallyHidden,
            'mtl-card__gradient-band--empty': band.groups.length === 0,
          }"
          :title="band.title"
          @click="toggleBand(band)"
        >
          <span class="mtl-card__gradient-range">{{ band.label }}</span>
          <span
            class="mtl-card__gradient-swatch"
            :style="{ background: band.gradient, opacity: band.allHidden ? 0.28 : 1 }"
          ></span>
          <span class="mtl-card__entry-count">{{ band.count }}</span>
          <i :class="band.allHidden ? 'bi bi-eye-slash' : 'bi bi-eye-fill'" class="mtl-card__eye"></i>
        </button>
      </div>
      <div v-else class="mtl-card__scroll">
        <button
          v-for="entry in entries"
          :key="entry.group"
          type="button"
          class="mtl-card__row"
          :class="{ 'mtl-card__row--disabled': !isGroupVisible(entry.group) }"
          :aria-pressed="isGroupVisible(entry.group)"
          :aria-label="`${isGroupVisible(entry.group) ? 'Hide' : 'Show'} ${entry.label ?? entry.group} on map`"
          @click="toggleGroup(entry.group)"
        >
          <span
            class="mtl-card__swatch"
            :style="{ background: isGroupVisible(entry.group) ? entry.color : 'transparent', borderColor: entry.color }"
          ></span>
          <span class="mtl-card__label" :title="entry.label ?? entry.group">{{ entry.label ?? entry.group }}</span>
          <span class="mtl-card__entry-count">{{ entry.count }}</span>
          <i :class="isGroupVisible(entry.group) ? 'bi bi-eye-fill' : 'bi bi-eye-slash'" class="mtl-card__eye"></i>
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
export interface LegendEntry {
  group: string;
  label?: string;
  color: string;
  count: number;
}
</script>

<script setup lang="ts">
import { computed } from 'vue';
import { buildNumericGradientBands, colorForNumericBucket, hasOnlyNumericBuckets } from '@/utils/numericGradientBands';

const props = defineProps<{
  entries: LegendEntry[];
  legendMode?: 'categorical' | 'gradient';
  gradientColors?: string[];
  gradientBucketCount?: number;
  collapsed: boolean;
  visibleTrackCount: number;
  totalTrackCount: number;
  filterActive: boolean;
  activeFilterIdentity?: string;
  hiddenGroups: Set<string>;
}>();

const emit = defineEmits<{
  'update:collapsed': [value: boolean];
  'update:hiddenGroups': [value: Set<string>];
  'chip-click': [];
}>();

type GradientBand = {
  key: string;
  start: number;
  end: number;
  label: string;
  title: string;
  count: number;
  groups: string[];
  gradient: string;
  allHidden: boolean;
  partiallyHidden: boolean;
};

const isGradientLegend = computed(() => props.legendMode === 'gradient' && hasOnlyNumericBuckets(props.entries));
const trackCountLabel = computed(() =>
  props.filterActive
    ? `${props.visibleTrackCount} / ${props.totalTrackCount} Tracks`
    : `${props.visibleTrackCount} Tracks`
);
const filterButtonAriaLabel = computed(() => {
  const identity = props.filterActive ? props.activeFilterIdentity?.trim() : '';
  return identity ? `Open Filter. ${identity}. ${trackCountLabel.value}` : `Open Filter. ${trackCountLabel.value}`;
});

const gradientBands = computed<GradientBand[]>(() => {
  const bucketCount = Math.max(1, props.gradientBucketCount ?? 100);
  return buildNumericGradientBands(props.entries, bucketCount).map((band) => {
    const groups = band.entries.map((entry) => entry.group);
    const hiddenCount = groups.filter((group) => props.hiddenGroups.has(group)).length;
    const allHidden = groups.length > 0 && hiddenCount === groups.length;
    const partiallyHidden = hiddenCount > 0 && hiddenCount < groups.length;
    return {
      ...band,
      groups,
      gradient: `linear-gradient(90deg, ${colorForNumericBucket(
        band.start,
        bucketCount,
        props.gradientColors ?? []
      )}, ${colorForNumericBucket(band.end, bucketCount, props.gradientColors ?? [])})`,
      allHidden,
      partiallyHidden,
    };
  });
});

function isGroupVisible(group: string): boolean {
  return !props.hiddenGroups.has(group);
}

function toggleGroup(group: string) {
  const next = new Set(props.hiddenGroups);
  if (next.has(group)) {
    next.delete(group);
  } else {
    next.add(group);
  }
  emit('update:hiddenGroups', next);
}

function toggleBand(band: GradientBand) {
  if (band.groups.length === 0) return;
  const next = new Set(props.hiddenGroups);
  if (band.allHidden) {
    for (const group of band.groups) next.delete(group);
  } else {
    for (const group of band.groups) next.add(group);
  }
  emit('update:hiddenGroups', next);
}

function showAllGroups() {
  emit('update:hiddenGroups', new Set());
}
</script>

<style scoped>
/* ── Unified glassmorphic card ── */
.mtl-card {
  background: var(--chip-bg);
  backdrop-filter: var(--blur-subtle);
  -webkit-backdrop-filter: var(--blur-subtle);
  border: 1px solid var(--chip-border);
  border-radius: 4px;
  min-width: 120px;
  max-width: 260px;
  font-size: var(--text-xs-size);
  font-weight: 500;
  color: var(--chip-text);
  pointer-events: auto;
  letter-spacing: 0.01em;
}

.mtl-card--identified-filter {
  max-width: min(360px, calc(100vw - 1.2rem - var(--safe-left, 0px) - var(--safe-right, 0px)));
}

/* ── Header: single row with two tap zones ── */
.mtl-card__header {
  display: flex;
  align-items: stretch;
}

/* Left zone: track count */
.mtl-card__count {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.2rem 0.5rem;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
  border-radius: 4px 0 0 4px;
  min-width: 0;
  white-space: nowrap;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
}

.mtl-card__filter-summary {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.2;
}

.mtl-card__filter-identity {
  display: block;
  width: 100%;
  overflow: hidden;
  color: var(--chip-text);
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mtl-card__track-count {
  display: block;
  opacity: 0.78;
  font-size: var(--text-2xs-size);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.mtl-card__count:hover {
  background: var(--chip-bg-hover, var(--chip-bg));
}

.mtl-card__funnel {
  font-size: var(--text-2xs-size);
  color: var(--warning);
  flex-shrink: 0;
}

/* Right zone: legend toggle */
.mtl-card__legend-toggle {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.2rem 0.45rem;
  cursor: pointer;
  user-select: none;
  border: 0;
  border-left: 1px solid var(--chip-border);
  transition: background 0.15s;
  border-radius: 0 4px 4px 0;
  flex-shrink: 0;
  background: transparent;
  color: inherit;
  font: inherit;
}

.mtl-card__legend-toggle:hover {
  background: var(--chip-bg-hover, var(--chip-bg));
}

.mtl-card__legend-label {
  font-size: var(--text-2xs-size);
  font-weight: 600;
  opacity: 0.75;
}

.mtl-card__chevron {
  font-size: var(--text-2xs-size);
  opacity: 0.65;
}

/* ── Legend body ── */
.mtl-card__body {
  border-top: 1px solid var(--chip-border);
}

.mtl-card__visibility-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.25rem 0.65rem 0.15rem;
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
}

.mtl-card__show-all {
  min-height: 1.5rem;
  padding: 0.1rem 0.3rem;
  border: 0;
  background: transparent;
  color: var(--accent-text);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.mtl-card__show-all:hover {
  text-decoration: underline;
}

.mtl-card__scroll {
  max-height: 135px;
  overflow-y: auto;
  padding: 0.2rem 0.45rem 0.25rem;
}

.mtl-card__scroll::-webkit-scrollbar {
  width: 3px;
}
.mtl-card__scroll::-webkit-scrollbar-track {
  background: transparent;
}
.mtl-card__scroll::-webkit-scrollbar-thumb {
  background: var(--chip-border);
  border-radius: 3px;
}

.mtl-card__gradient {
  padding: 0.2rem 0.45rem 0.25rem;
}

.mtl-card__gradient-band {
  width: 100%;
  display: grid;
  grid-template-columns: 3.2rem minmax(4.5rem, 1fr) max-content 0.8rem;
  align-items: center;
  gap: 0.35rem;
  padding: 0.12rem 0.2rem;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: inherit;
  font: inherit;
  line-height: 1.2;
  cursor: pointer;
  transition:
    background 0.15s,
    opacity 0.2s;
  user-select: none;
}

.mtl-card__gradient-band:hover {
  background: var(--chip-bg-hover);
}

.mtl-card__gradient-band--disabled {
  opacity: 0.46;
}

.mtl-card__gradient-band--partial .mtl-card__gradient-range {
  text-decoration: underline;
  text-decoration-style: dotted;
  text-underline-offset: 2px;
}

.mtl-card__gradient-band--empty {
  cursor: default;
  opacity: 0.42;
}

.mtl-card__gradient-band--empty:hover {
  background: transparent;
}

.mtl-card__gradient-range {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-2xs-size);
  font-variant-numeric: tabular-nums;
}

.mtl-card__gradient-swatch {
  height: 0.5rem;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--chip-border) 75%, transparent);
  min-width: 0;
}

.mtl-card__row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.12rem 0.2rem;
  line-height: 1.2;
  cursor: pointer;
  border-radius: 3px;
  transition:
    background 0.15s,
    opacity 0.2s;
  user-select: none;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
}

.mtl-card__row:hover {
  background: var(--chip-bg-hover);
}

.mtl-card__row--disabled {
  opacity: 0.4;
}

.mtl-card__row--disabled .mtl-card__label {
  text-decoration: line-through;
  text-decoration-thickness: 1px;
}

.mtl-card__swatch {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1.5px solid;
  transition: background 0.2s;
}

.mtl-card__label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-2xs-size);
}

.mtl-card__entry-count {
  flex-shrink: 0;
  opacity: 0.55;
  font-variant-numeric: tabular-nums;
  font-size: var(--text-2xs-size);
}

.mtl-card__eye {
  flex-shrink: 0;
  font-size: var(--text-2xs-size);
  opacity: 0.45;
  transition: opacity 0.15s;
}

.mtl-card__row:hover .mtl-card__eye {
  opacity: 0.8;
}

.mtl-card__row--disabled .mtl-card__eye {
  opacity: 0.35;
}
</style>
