<template>
  <div class="legend-root">

    <!-- ── Empty state: no palette active ── -->
    <div v-if="isEmpty" class="legend-empty">
      <div class="legend-empty__icon-wrap">
        <i class="bi bi-funnel legend-empty__icon"></i>
      </div>
      <p class="legend-empty__title">No color scheme active</p>
      <p class="legend-empty__body">
        Open the <strong><i class="bi bi-funnel"></i> Filter</strong> panel and choose a filter
        that includes a <em>coloring</em> setting. Once a color-grouped filter is applied,
        the map will color tracks by group and the legend will appear here.
      </p>
    </div>

    <!-- ── Populated legend ── -->
    <template v-else>
      <div v-if="isExhausted" class="legend-warn">
        <i class="bi bi-exclamation-triangle-fill legend-warn__icon"></i>
        More groups than available colors — some colors are reused.
      </div>

      <p class="legend-label">Track colors by group</p>

      <ul class="legend-list">
        <li v-for="row in groupColorData" :key="row.group" class="legend-row">
          <span class="legend-swatch" :style="{ backgroundColor: row.color }"></span>
          <span class="legend-group">{{ row.group }}</span>
          <span class="legend-count">{{ row.count }}</span>
        </li>
      </ul>
    </template>

  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { ColorPalette } from '@/components/filter/ColorPalette';

const props = defineProps<{
  palette: ColorPalette;
}>();

const isEmpty = computed(() => props.palette.isEmptyColorPalette());

const groupColorData = computed(() => {
  const colorMap = props.palette.getColorMap();
  const counter = props.palette.getGroupColorCounter();
  return Array.from(colorMap.entries()).map(([group, color]) => ({
    group,
    color,
    count: counter.get(group) || 0,
  }));
});

const isExhausted = computed(
  () => !isEmpty.value && props.palette.isColorPaletteExhausted()
);
</script>

<style scoped>
.legend-root {
  padding: 0.75rem 0.25rem;
}

/* ── Empty state ── */
.legend-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 1.5rem 1rem 1rem;
  gap: 0.5rem;
}

.legend-empty__icon-wrap {
  width: 3rem;
  height: 3rem;
  border-radius: 50%;
  background: var(--surface-glass);
  border: 1px solid var(--border-default);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 0.25rem;
}

.legend-empty__icon {
  font-size: var(--text-2xl-size);
  color: var(--accent-text);
  opacity: 0.7;
}

.legend-empty__title {
  font-weight: 600;
  font-size: var(--text-base-size);
  margin: 0;
  color: var(--text-primary);
}

.legend-empty__body {
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
  line-height: var(--text-sm-lh);
  margin: 0;
  max-width: 28ch;
}

.legend-empty__body strong {
  color: var(--text-primary);
}

/* ── Warning banner ── */
.legend-warn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: rgba(234, 179, 8, 0.12);
  border: 1px solid rgba(234, 179, 8, 0.35);
  border-radius: 0.5rem;
  padding: 0.5rem 0.75rem;
  font-size: var(--text-sm-size);
  color: var(--text-primary);
  margin-bottom: 0.75rem;
}

.legend-warn__icon {
  color: #ca8a04;
  flex-shrink: 0;
}

/* ── Label ── */
.legend-label {
  font-size: var(--text-xs-size);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-secondary);
  margin: 0 0 0.5rem 0;
}

/* ── Row list ── */
.legend-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.4rem 0.6rem;
  border-radius: 0.5rem;
  background: var(--surface-glass-subtle);
  border: 1px solid var(--border-default);
}

.legend-swatch {
  width: 0.9rem;
  height: 0.9rem;
  border-radius: 0.25rem;
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px rgba(0,0,0,0.15);
}

.legend-group {
  flex: 1 1 auto;
  font-size: var(--text-sm-size);
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.legend-count {
  font-size: var(--text-xs-size);
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--surface-glass);
  border: 1px solid var(--border-default);
  border-radius: 9999px;
  padding: 0.05rem 0.5rem;
  white-space: nowrap;
}
</style>
