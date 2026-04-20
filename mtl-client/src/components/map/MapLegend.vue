<template>
  <div class="mtl-card">
    <!-- ── Single header row: [count zone] [| Legende ▾] ── -->
    <div class="mtl-card__header">
      <!-- Left: track count — click opens filter panel -->
      <div class="mtl-card__count" @click="$emit('chip-click')">
        <i v-if="filterActive" class="bi bi-funnel-fill mtl-card__funnel"></i>
        <span v-if="filterActive">{{ visibleTrackCount }} / {{ totalTrackCount }} Tracks</span>
        <span v-else>{{ visibleTrackCount }} Tracks</span>
      </div>
      <!-- Right: legend toggle — only when entries exist -->
      <div
        v-if="entries.length > 0"
        class="mtl-card__legend-toggle"
        @click="$emit('update:collapsed', !collapsed)"
      >
        <span class="mtl-card__legend-label">Legend</span>
        <i :class="collapsed ? 'bi bi-chevron-down' : 'bi bi-chevron-up'" class="mtl-card__chevron"></i>
      </div>
    </div>

    <!-- Legend body — only expand when entries exist -->
    <div v-show="entries.length > 0 && !collapsed" class="mtl-card__body">
      <div class="mtl-card__scroll">
        <div
          v-for="entry in entries"
          :key="entry.group"
          class="mtl-card__row"
          :class="{ 'mtl-card__row--disabled': !isGroupVisible(entry.group) }"
          @click="toggleGroup(entry.group)"
        >
          <span
            class="mtl-card__swatch"
            :style="{ background: isGroupVisible(entry.group) ? entry.color : 'transparent',
                       borderColor: entry.color }"
          ></span>
          <span class="mtl-card__label" :title="entry.group">{{ entry.group }}</span>
          <span class="mtl-card__entry-count">{{ entry.count }}</span>
          <i :class="isGroupVisible(entry.group) ? 'bi bi-eye-fill' : 'bi bi-eye-slash'" class="mtl-card__eye"></i>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
export interface LegendEntry {
  group: string;
  color: string;
  count: number;
}
</script>

<script setup lang="ts">
const props = defineProps<{
  entries: LegendEntry[];
  collapsed: boolean;
  visibleTrackCount: number;
  totalTrackCount: number;
  filterActive: boolean;
  hiddenGroups: Set<string>;
}>();

const emit = defineEmits<{
  'update:collapsed': [value: boolean];
  'update:hiddenGroups': [value: Set<string>];
  'chip-click': [];
}>();

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
  font-size: 0.7rem;
  font-weight: 500;
  color: var(--chip-text);
  pointer-events: auto;
  letter-spacing: 0.01em;
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
}

.mtl-card__count:hover {
  background: var(--chip-bg-hover, var(--chip-bg));
}

.mtl-card__funnel {
  font-size: 0.65rem;
  color: var(--warning, #f59e0b);
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
  border-left: 1px solid var(--chip-border);
  transition: background 0.15s;
  border-radius: 0 4px 4px 0;
  flex-shrink: 0;
}

.mtl-card__legend-toggle:hover {
  background: var(--chip-bg-hover, var(--chip-bg));
}

.mtl-card__legend-label {
  font-size: 0.65rem;
  font-weight: 600;
  opacity: 0.75;
}

.mtl-card__chevron {
  font-size: 0.6rem;
  opacity: 0.65;
}

/* ── Legend body ── */
.mtl-card__body {
  border-top: 1px solid var(--chip-border);
}

.mtl-card__scroll {
  max-height: 135px;
  overflow-y: auto;
  padding: 0.2rem 0.45rem 0.25rem;
}

.mtl-card__scroll::-webkit-scrollbar { width: 3px; }
.mtl-card__scroll::-webkit-scrollbar-track { background: transparent; }
.mtl-card__scroll::-webkit-scrollbar-thumb {
  background: var(--chip-border);
  border-radius: 3px;
}

.mtl-card__row {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.12rem 0.2rem;
  line-height: 1.2;
  cursor: pointer;
  border-radius: 3px;
  transition: background 0.15s, opacity 0.2s;
  user-select: none;
}

.mtl-card__row:hover {
  background: var(--chip-bg-hover, rgba(0, 0, 0, 0.06));
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
  font-size: 0.68rem;
}

.mtl-card__entry-count {
  flex-shrink: 0;
  opacity: 0.55;
  font-variant-numeric: tabular-nums;
  font-size: 0.65rem;
}

.mtl-card__eye {
  flex-shrink: 0;
  font-size: 0.6rem;
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
