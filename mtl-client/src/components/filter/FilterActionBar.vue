<template>
  <footer
    class="filter-action-bar"
    :class="{
      'filter-action-bar--loading': isPreviewLoading,
      'filter-action-bar--error': Boolean(previewError),
      'filter-action-bar--waiting': !hasPreviewResult && !isPreviewLoading && !previewError,
    }"
    role="status"
    aria-live="polite"
  >
    <div class="filter-action-bar__summary">
      <div class="filter-action-bar__live" :title="statusTitle">
        <span class="filter-action-bar__live-dot"></span>
        <span class="filter-action-bar__live-label">{{ statusLabel }}</span>
      </div>

      <div class="filter-action-bar__metrics">
        <div class="filter-action-bar__metric filter-action-bar__metric--primary">
          <span class="filter-action-bar__metric-value">{{ trackMetricValue }}</span>
          <span class="filter-action-bar__metric-label">matching tracks</span>
        </div>
        <div class="filter-action-bar__metric">
          <span class="filter-action-bar__metric-value">{{ groupMetricValue }}</span>
          <span class="filter-action-bar__metric-label-row">
            <span class="filter-action-bar__metric-label">{{ categoryMetricLabel }}</span>
            <span
              v-if="categoryColorSwatches.length > 0"
              class="filter-action-bar__category-swatches"
              :title="categoryColorsTitle"
              aria-label="Category colors"
            >
              <span
                v-for="(color, index) in categoryColorSwatches"
                :key="`${color}-${index}`"
                class="filter-action-bar__category-swatch"
                :style="{ backgroundColor: color }"
              ></span>
              <span v-if="hiddenCategoryColorCount > 0" class="filter-action-bar__category-more">
                +{{ hiddenCategoryColorCount }}
              </span>
            </span>
          </span>
        </div>
      </div>

      <p v-if="previewError" class="filter-action-bar__message">{{ previewError }}</p>
    </div>

    <button type="button" class="filter-action-bar__button" :disabled="!canOpenResultsView" @click="openColors">
      Colors
      <i class="bi bi-arrow-right"></i>
    </button>
  </footer>
</template>

<script setup lang="ts">
import { computed } from 'vue';

defineOptions({ name: 'FilterActionBar' });

const CATEGORY_SWATCH_LIMIT = 8;

const props = defineProps<{
  activeTrackCountDisplay: string;
  previewGroupCount: number;
  categoryColors?: string[];
  hasPreviewResult: boolean;
  isPreviewLoading: boolean;
  previewError?: string | null;
  canOpenResultsView: boolean;
}>();

const emit = defineEmits<{
  (event: 'open-colors'): void;
}>();

const statusTitle = computed((): string => {
  if (props.previewError) return 'Preview needs attention';
  if (props.isPreviewLoading) return props.hasPreviewResult ? 'Updating live preview' : 'Calculating live preview';
  if (props.hasPreviewResult) return 'Live preview';
  return 'Waiting for parameters';
});
const statusLabel = computed((): string => (props.previewError ? 'Preview issue' : 'Live preview'));

const trackMetricValue = computed((): string => {
  if (props.previewError) return '!';
  if (props.isPreviewLoading && !props.hasPreviewResult) return '...';
  if (!props.hasPreviewResult) return '...';
  return props.activeTrackCountDisplay;
});

const groupMetricValue = computed((): string => {
  if (props.previewError) return '!';
  if (props.isPreviewLoading && !props.hasPreviewResult) return '...';
  if (!props.hasPreviewResult) return '...';
  return String(props.previewGroupCount);
});

const categoryMetricLabel = computed((): string => {
  if (props.previewGroupCount === 1) return 'category';
  return 'categories';
});
const categoryColorSwatches = computed((): string[] => {
  if (!props.hasPreviewResult || props.previewError) return [];
  return (props.categoryColors ?? []).filter(Boolean).slice(0, CATEGORY_SWATCH_LIMIT);
});
const hiddenCategoryColorCount = computed((): number => {
  if (!props.hasPreviewResult || props.previewError) return 0;
  return Math.max((props.categoryColors?.length ?? 0) - CATEGORY_SWATCH_LIMIT, 0);
});
const categoryColorsTitle = computed((): string => {
  const count = props.categoryColors?.length ?? 0;
  if (count === 1) return '1 category color';
  return `${count} category colors`;
});

function openColors(): void {
  if (!props.canOpenResultsView) return;
  emit('open-colors');
}
</script>

<style scoped>
.filter-action-bar {
  position: sticky;
  z-index: 3;
  bottom: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.85rem;
  margin-top: auto;
  padding: 0.65rem 0.75rem;
  border: 1px solid color-mix(in srgb, var(--accent, #6366f1) 18%, var(--border-default));
  border-radius: 0.5rem;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--surface-sheet-solid) 88%, var(--accent-subtle)) 0%,
    color-mix(in srgb, var(--surface-sheet-solid) 96%, var(--surface-elevated)) 100%
  );
  box-shadow:
    0 -10px 28px -24px color-mix(in srgb, var(--text-primary) 55%, transparent),
    inset 0 1px 0 color-mix(in srgb, var(--surface-elevated) 82%, transparent);
}

.filter-action-bar::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: -0.85rem;
  height: 0.85rem;
  pointer-events: none;
  background: linear-gradient(to bottom, transparent, color-mix(in srgb, var(--surface-sheet-solid) 72%, transparent));
}

.filter-action-bar--error {
  border-color: color-mix(in srgb, var(--error) 42%, var(--border-default));
  background: color-mix(in srgb, var(--error) 8%, var(--surface-sheet-solid));
}

.filter-action-bar--waiting {
  border-color: var(--border-subtle, var(--border-default));
}

.filter-action-bar__summary {
  min-width: 0;
  display: grid;
  grid-template-columns: 9.25rem minmax(0, 1fr);
  align-items: center;
  gap: 0.7rem 0.85rem;
}

.filter-action-bar__live {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: 800;
  line-height: var(--text-xs-lh);
  text-transform: uppercase;
  white-space: nowrap;
}

.filter-action-bar__live-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.filter-action-bar__live-dot {
  flex: 0 0 auto;
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  background: var(--success, var(--accent));
  box-shadow: 0 0 0 0 color-mix(in srgb, var(--success, var(--accent)) 45%, transparent);
}

.filter-action-bar--loading .filter-action-bar__live-dot {
  animation: filter-action-bar-pulse 1.15s ease-out infinite;
}

.filter-action-bar--error .filter-action-bar__live-dot {
  background: var(--error);
  box-shadow: none;
}

.filter-action-bar--waiting .filter-action-bar__live-dot {
  background: var(--text-muted);
  box-shadow: none;
}

.filter-action-bar__metrics {
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(7.25rem, 1fr));
  gap: 0.45rem;
}

.filter-action-bar__metric {
  min-width: 0;
  display: grid;
  grid-template-columns: 3.5ch minmax(0, 1fr);
  gap: 0.4rem;
  align-items: baseline;
  min-height: 2.25rem;
  padding: 0.35rem 0.55rem;
  border: 1px solid var(--border-subtle, var(--border-default));
  border-radius: 0.45rem;
  background: color-mix(in srgb, var(--surface-elevated) 72%, transparent);
}

.filter-action-bar__metric--primary {
  border-color: color-mix(in srgb, var(--accent, #6366f1) 34%, var(--border-default));
  background: color-mix(in srgb, var(--accent-subtle) 44%, var(--surface-elevated));
}

.filter-action-bar__metric-value {
  color: var(--text-primary);
  font-size: var(--text-xl-size, 1.35rem);
  font-weight: 850;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
}

.filter-action-bar__metric--primary .filter-action-bar__metric-value {
  color: var(--accent-text);
}

.filter-action-bar--error .filter-action-bar__metric-value {
  color: var(--error);
}

.filter-action-bar__metric-label {
  min-width: 0;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: var(--text-xs-lh);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-action-bar__metric-label-row {
  min-width: 0;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.filter-action-bar__category-swatches {
  display: inline-flex;
  align-items: center;
  gap: 0.12rem;
  min-width: 0;
  max-width: 6.6rem;
  overflow: hidden;
}

.filter-action-bar__category-swatch {
  flex: 0 0 auto;
  width: 0.72rem;
  height: 0.72rem;
  border: 1px solid color-mix(in srgb, var(--surface-sheet-solid) 72%, var(--border-default));
  border-radius: 0.18rem;
}

.filter-action-bar__category-more {
  flex: 0 0 auto;
  color: var(--text-muted);
  font-size: var(--text-2xs-size, 0.65rem);
  font-weight: 800;
  line-height: 1;
}

.filter-action-bar__message {
  grid-column: 2;
  margin: -0.25rem 0 0;
  color: var(--error);
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: var(--text-xs-lh);
}

.filter-action-bar__button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  min-height: 2.45rem;
  padding: 0.6rem 1rem;
  border: 1px solid var(--accent);
  border-radius: 0.6rem;
  background: var(--accent);
  color: var(--text-inverse);
  font: inherit;
  font-size: var(--text-sm-size);
  font-weight: 700;
  line-height: var(--text-sm-lh);
  min-width: 6rem;
  white-space: nowrap;
  cursor: pointer;
  box-shadow: 0 6px 18px -12px var(--accent);
  transition:
    background 0.15s,
    border-color 0.15s,
    color 0.15s;
}

.filter-action-bar__button:hover {
  border-color: var(--accent-hover);
  background: var(--accent-hover);
  color: var(--text-inverse);
}

.filter-action-bar__button:disabled {
  border-color: var(--border-default);
  background: var(--surface-glass-heavy, var(--surface-ground));
  color: var(--text-muted);
  box-shadow: none;
  cursor: not-allowed;
}

.filter-action-bar__button:disabled:hover {
  border-color: var(--border-default);
  background: var(--surface-glass-heavy, var(--surface-ground));
  color: var(--text-muted);
}

.filter-action-bar__button:focus-visible {
  outline: 2px solid var(--accent-text-light);
  outline-offset: 2px;
}

@keyframes filter-action-bar-pulse {
  70% {
    box-shadow: 0 0 0 0.42rem transparent;
  }
}

@media screen and (max-width: 768px) {
  .filter-action-bar {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 0.55rem;
    padding: 0.55rem;
  }

  .filter-action-bar__summary {
    grid-template-columns: minmax(0, 1fr);
    gap: 0.45rem;
  }

  .filter-action-bar__live {
    min-width: 0;
  }

  .filter-action-bar__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-action-bar__metric {
    grid-template-columns: minmax(0, 1fr);
    align-items: start;
    gap: 0.2rem;
    min-height: 2.6rem;
    padding: 0.4rem 0.45rem;
  }

  .filter-action-bar__metric-value {
    font-size: var(--text-lg-size, 1.15rem);
    min-width: 3ch;
    text-align: left;
  }

  .filter-action-bar__metric-label-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.25rem;
  }

  .filter-action-bar__category-swatches {
    max-width: 100%;
  }

  .filter-action-bar__message {
    grid-column: 1;
  }

  .filter-action-bar__button {
    width: auto;
    min-height: 2.35rem;
    padding: 0.5rem 0.7rem;
  }
}
</style>
