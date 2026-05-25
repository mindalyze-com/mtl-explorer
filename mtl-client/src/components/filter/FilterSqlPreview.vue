<template>
  <section class="filter-sql-preview" aria-label="SQL template preview">
    <header class="filter-sql-preview__header">
      <div class="filter-sql-preview__heading">
        <h3 class="filter-sql-preview__title">SQL template preview</h3>
        <p class="filter-sql-preview__subtitle">{{ subtitle }}</p>
      </div>
      <button type="button" class="filter-sql-preview__copy" :disabled="!visibleSQL" @click="copyCurrentSql">
        <i class="bi bi-copy"></i>
        <span>{{ copyLabel }}</span>
      </button>
    </header>

    <div class="filter-sql-preview__body">
      <div class="filter-sql-preview__code-panel">
        <div class="filter-sql-preview__toolbar">
          <div class="sql-mode-toggle" role="tablist" aria-label="SQL view">
            <button
              :class="['sql-mode-btn', viewMode === 'template' && 'sql-mode-btn--active']"
              type="button"
              role="tab"
              :aria-selected="viewMode === 'template'"
              @click="emit('update:view-mode', 'template')"
            >
              Template
            </button>
            <button
              :class="['sql-mode-btn', viewMode === 'resolved' && 'sql-mode-btn--active']"
              type="button"
              role="tab"
              :aria-selected="viewMode === 'resolved'"
              @click="emit('update:view-mode', 'resolved')"
            >
              Resolved
            </button>
          </div>
          <p class="filter-sql-preview__hint">{{ modeHelp }}</p>
        </div>

        <div class="sql-block__code">
          <highlightjs language="mtl-pgsql" :autodetect="false" :code="visibleSQL" />
        </div>
      </div>

      <aside class="filter-sql-preview__params" aria-label="SQL parameters">
        <div class="filter-sql-preview__params-head">
          <h4 class="filter-sql-preview__params-title">Parameters</h4>
          <span class="filter-sql-preview__params-count">{{ paramReferences.length }}</span>
        </div>

        <div v-if="paramReferences.length > 0" class="filter-sql-preview__param-list">
          <article v-for="param in paramReferences" :key="param.name" class="filter-sql-param">
            <div class="filter-sql-param__topline">
              <code class="filter-sql-param__placeholder" :title="param.placeholders.join(', ')">
                {{ displayPlaceholder(param) }}
              </code>
              <span v-if="param.placeholders.length > 1" class="filter-sql-param__placeholder-count">
                {{ param.placeholders.length }}
              </span>
              <span class="filter-sql-param__name">{{ param.label }}</span>
            </div>

            <div class="filter-sql-param__meta" :title="param.detail">{{ param.detail }}</div>
          </article>
        </div>

        <div v-else class="filter-sql-preview__empty">
          <i class="bi bi-braces"></i>
          <span>No SQL parameters</span>
        </div>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import {
  buildSqlParamReferences,
  sqlForViewMode,
  sqlViewModeHelp,
  type SqlParamReference,
} from '@/utils/filterSqlPreview';

type SqlViewMode = 'template' | 'resolved';

defineOptions({ name: 'FilterSqlPreview' });

const props = defineProps<{
  filterInfo?: FilterInfo | null;
  viewMode: SqlViewMode;
}>();

const emit = defineEmits<{
  (event: 'update:view-mode', value: SqlViewMode): void;
}>();

const copyState = ref<'idle' | 'copied' | 'error'>('idle');
let copyStateTimer: ReturnType<typeof setTimeout> | null = null;

const rawSQL = computed((): string => props.filterInfo?.filterConfig?.expression ?? '');
const resolvedSQL = computed((): string => props.filterInfo?.resolvedSQL ?? '');
const visibleSQL = computed((): string => sqlForViewMode(props.viewMode, rawSQL.value, resolvedSQL.value));
const modeHelp = computed((): string => sqlViewModeHelp(props.viewMode));
const subtitle = computed((): string =>
  props.viewMode === 'resolved'
    ? 'Advanced - expanded from the selected filter'
    : 'Advanced - generated from the selected filter and parameters'
);
const paramReferences = computed((): SqlParamReference[] => buildSqlParamReferences(props.filterInfo));
const copyLabel = computed((): string => {
  if (copyState.value === 'copied') return 'Copied';
  if (copyState.value === 'error') return 'Copy failed';
  return 'Copy SQL';
});

async function copyCurrentSql(): Promise<void> {
  if (!visibleSQL.value) return;
  try {
    await navigator.clipboard.writeText(visibleSQL.value);
    setCopyState('copied');
  } catch (error) {
    console.warn('[filter-sql-preview] failed to copy SQL', error);
    setCopyState('error');
  }
}

function setCopyState(state: 'copied' | 'error'): void {
  copyState.value = state;
  if (copyStateTimer) clearTimeout(copyStateTimer);
  copyStateTimer = setTimeout(() => {
    copyState.value = 'idle';
    copyStateTimer = null;
  }, 1800);
}

function displayPlaceholder(param: SqlParamReference): string {
  return param.placeholders.length > 1 ? `:${param.name}` : (param.placeholders[0] ?? `:${param.name}`);
}

onBeforeUnmount(() => {
  if (copyStateTimer) clearTimeout(copyStateTimer);
});
</script>

<style scoped>
.filter-sql-preview {
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-default);
  border-radius: 0.75rem;
  overflow: hidden;
  background: var(--surface-glass-heavy);
}

.filter-sql-preview__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.95rem 1rem;
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
}

.filter-sql-preview__heading {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.18rem;
}

.filter-sql-preview__title,
.filter-sql-preview__params-title {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-base-size, 1rem);
  font-weight: 800;
  line-height: var(--text-base-lh, 1.4);
}

.filter-sql-preview__subtitle,
.filter-sql-preview__hint {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 600;
  line-height: var(--text-xs-lh);
}

.filter-sql-preview__copy {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  min-height: 2.25rem;
  border: 1px solid var(--border-default);
  border-radius: 0.55rem;
  padding: 0.35rem 0.75rem;
  color: var(--text-secondary);
  background: var(--surface-glass-subtle);
  font-size: var(--text-sm-size);
  font-weight: 700;
  line-height: var(--text-sm-lh);
  cursor: pointer;
  white-space: nowrap;
}

.filter-sql-preview__copy:hover:not(:disabled),
.filter-sql-preview__copy:focus-visible:not(:disabled) {
  color: var(--accent-text);
  border-color: color-mix(in srgb, var(--accent, #6366f1) 45%, var(--border-default));
  background: var(--accent-subtle);
}

.filter-sql-preview__copy:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.filter-sql-preview__body {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(15.5rem, 0.3fr);
}

.filter-sql-preview__code-panel {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-subtle, var(--border-default));
}

.filter-sql-preview__toolbar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
}

.sql-mode-toggle {
  flex: 0 0 auto;
  display: inline-flex;
  border: 1px solid var(--border-default);
  border-radius: 0.5rem;
  overflow: hidden;
  align-self: flex-start;
}

.sql-mode-btn {
  min-width: 5.7rem;
  padding: 0.32rem 0.85rem;
  font-size: var(--text-xs-size);
  font-weight: 700;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--text-secondary);
  transition:
    background 0.15s,
    color 0.15s;
  -webkit-tap-highlight-color: transparent;
}

.sql-mode-btn + .sql-mode-btn {
  border-left: 1px solid var(--border-default);
}

.sql-mode-btn--active {
  background: var(--accent);
  color: var(--text-inverse);
}

.sql-block__code {
  min-height: 16rem;
  min-width: 0;
  flex: 1 1 auto;
  overflow: hidden;
  background: var(--surface-glass-subtle);
}

.sql-block__code :deep(pre) {
  height: 100%;
  min-height: 16rem;
  margin: 0;
  padding: 1rem 1.1rem;
  font-size: var(--text-xs-size);
  line-height: 1.65;
  overflow: auto;
  white-space: pre;
  background: transparent !important;
}

.sql-block__code :deep(code) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  background: transparent !important;
}

.sql-block__code :deep(.hljs-mtl-param) {
  border-radius: 0.28rem;
  padding: 0.03rem 0.16rem;
  color: var(--accent-text);
  background: var(--accent-subtle);
  font-weight: 800;
}

.filter-sql-preview__params {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface-glass-subtle);
}

.filter-sql-preview__params-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.7rem 0.8rem 0.55rem;
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
}

.filter-sql-preview__params-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.35rem;
  min-height: 1.35rem;
  border-radius: 999px;
  padding: 0 0.4rem;
  color: var(--accent-text);
  background: var(--accent-subtle);
  font-size: var(--text-xs-size);
  font-weight: 800;
}

.filter-sql-preview__param-list {
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  padding: 0.25rem 0;
}

.filter-sql-param {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.12rem;
  padding: 0.32rem 0.8rem;
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
  background: transparent;
}

.filter-sql-param__topline {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 0.45rem;
}

.filter-sql-param__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
  font-size: var(--text-xs-size);
  font-weight: 800;
  line-height: 1.25;
}

.filter-sql-param__placeholder {
  flex: 0 0 auto;
  border-radius: 0.28rem;
  padding: 0.04rem 0.22rem;
  color: var(--accent-text);
  background: var(--accent-subtle);
  font-size: var(--text-xs-size);
  font-weight: 800;
  line-height: 1.25;
}

.filter-sql-param__placeholder-count {
  flex: 0 0 auto;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 800;
  line-height: 1.25;
}

.filter-sql-param__meta {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 650;
  line-height: 1.25;
}

.filter-sql-preview__empty {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  font-weight: 700;
}

@media (max-width: 54rem) {
  .filter-sql-preview__body {
    grid-template-columns: minmax(0, 1fr);
  }

  .filter-sql-preview__code-panel {
    border-right: 0;
    border-bottom: 1px solid var(--border-subtle, var(--border-default));
  }

  .filter-sql-preview__params {
    max-height: 18rem;
  }
}

@media (max-width: 36rem) {
  .filter-sql-preview__header,
  .filter-sql-preview__toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-sql-preview__copy,
  .sql-mode-toggle {
    width: 100%;
  }

  .sql-mode-btn {
    min-width: 0;
    flex: 1;
  }
}
</style>
