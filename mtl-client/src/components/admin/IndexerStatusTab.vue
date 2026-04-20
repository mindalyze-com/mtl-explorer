<template>
  <div class="indexer-tab">

    <!-- Loading state: show only when both are empty -->
    <div v-if="summaries.length === 0 && jobSummaries.length === 0" class="indexer-empty">
      <i class="pi pi-spin pi-spinner" style="font-size: 1.1rem; color: var(--text-faint);"/>
      <span>Loading…</span>
    </div>

    <template v-else>

      <!-- ── File Indexers ── -->
      <div class="section-divider">File Indexers</div>
      <div
        v-for="s in summaries"
        :key="s.index"
        class="index-card"
        :class="{ 'index-card--active': s.pending > 0 }"
      >
        <div class="index-card__header">
          <span class="index-name">{{ s.index }}</span>
          <span v-if="s.pending > 0" class="index-badge index-badge--scanning">
            <i class="pi pi-spin pi-spinner"/> scanning
          </span>
          <span v-else class="index-badge index-badge--done">
            <i class="pi pi-check"/> done
          </span>
          <span class="index-pct">{{ s.progressPercent }}%</span>
        </div>
        <ProgressBar
          :value="s.progressPercent"
          :class="['index-progress', s.pending > 0 ? 'index-progress--active' : 'index-progress--done']"
          :show-value="false"
          style="height: 6px; border-radius: 4px;"
        />
        <div class="index-stats">
          <span class="stat stat--done" :title="`${s.completed} completed`">
            <i class="pi pi-check-circle"/> {{ s.completed }}
          </span>
          <span v-if="s.pending > 0" class="stat stat--pending" :title="`${s.pending} pending`">
            <i class="pi pi-hourglass"/> {{ s.pending }}
          </span>
          <span v-if="s.failed > 0" class="stat stat--failed" :title="`${s.failed} failed`">
            <i class="pi pi-times-circle"/> {{ s.failed }}
          </span>
          <span class="stat stat--total">{{ s.total }} total</span>
        </div>
      </div>

      <!-- ── Background Jobs ── -->
      <div class="section-divider" style="margin-top: 0.75rem;">Track Processing Jobs</div>
      <div
        v-for="j in jobSummaries"
        :key="j.job"
        class="index-card"
        :class="{ 'index-card--active': j.pending > 0 }"
      >
        <div class="index-card__header">
          <span class="index-name">{{ j.label }}</span>
          <span v-if="j.pending > 0" class="index-badge index-badge--scanning">
            <i class="pi pi-spin pi-spinner"/> running
          </span>
          <span v-else class="index-badge index-badge--done">
            <i class="pi pi-check"/> done
          </span>
          <span class="index-pct">{{ j.progressPercent }}%</span>
        </div>
        <ProgressBar
          :value="j.progressPercent"
          :class="['index-progress', j.pending > 0 ? 'index-progress--active' : 'index-progress--done']"
          :show-value="false"
          style="height: 6px; border-radius: 4px;"
        />
        <div class="index-stats">
          <span class="stat stat--done" :title="`${j.done} done`">
            <i class="pi pi-check-circle"/> {{ j.done }}
          </span>
          <span v-if="j.pending > 0" class="stat stat--pending" :title="`${j.pending} pending`">
            <i class="pi pi-hourglass"/> {{ j.pending }}
          </span>
          <span class="stat stat--total">{{ j.total }} total</span>
        </div>
      </div>

    </template>

    <!-- Refresh button -->
    <div class="indexer-actions">
      <Button
        label="Refresh"
        icon="pi pi-refresh"
        size="small"
        severity="secondary"
        :disabled="refreshing"
        @click="onRefresh"
      />
      <span v-if="lastRefreshed" class="refresh-time">Updated {{ lastRefreshed }}</span>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import ProgressBar from 'primevue/progressbar';
import Button from 'primevue/button';
import { useIndexerStatus } from '@/composables/useIndexerStatus';

const { summaries, jobSummaries, lastRefreshed, refresh } = useIndexerStatus();

const refreshing = ref(false);

async function onRefresh() {
  refreshing.value = true;
  try {
    await refresh();
  } finally {
    refreshing.value = false;
  }
}
</script>

<style scoped>
.indexer-tab {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.95rem 1rem;
  border-radius: 1.2rem;
  border: 1px solid var(--border-default);
  background: var(--surface-glass-light);
  box-shadow: var(--shadow-sm);
}

.indexer-empty {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-faint);
  font-size: 0.85rem;
  padding: 1rem 0;
}

/* ── Index card ── */
.index-card {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  padding: 0.75rem 0;
  border-bottom: 1px solid var(--border-subtle);
}

.index-card--active .index-name {
  animation: alert-pulse 2s ease-in-out infinite;
}

/* ── Header ── */
.index-card__header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.index-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary);
  font-family: 'SF Mono', 'Fira Code', monospace;
  letter-spacing: 0.03em;
}

.index-pct {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-muted);
  margin-left: auto;
}

/* ── Badges ── */
.index-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.65rem;
  font-weight: 600;
  padding: 0.1rem 0.45rem;
  border-radius: 1rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.index-badge--scanning {
  background: rgba(234, 179, 8, 0.15);
  color: #ca8a04;
}

.dark-theme .index-badge--scanning {
  background: rgba(234, 179, 8, 0.12);
  color: #fbbf24;
}

.index-badge--done {
  background: rgba(34, 197, 94, 0.12);
  color: var(--success);
}

/* ── Progress bar overrides ── */
:deep(.index-progress--active .p-progressbar-value) {
  background: linear-gradient(90deg, #f59e0b, #eab308);
  animation: progress-shimmer 1.8s ease-in-out infinite;
}

:deep(.index-progress--done .p-progressbar-value) {
  background: var(--success);
}

@keyframes progress-shimmer {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* ── Stats row ── */
.index-stats {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  flex-wrap: wrap;
}

.stat {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
}

.stat .pi {
  font-size: 0.7rem;
}

.stat--done   { color: var(--success); }
.stat--pending { color: #f59e0b; }
.stat--failed  { color: var(--error); }

.stat--total {
  color: var(--text-faint);
  margin-left: auto;
}

/* ── Refresh row ── */
.indexer-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding-top: 0.5rem;
}

.refresh-time {
  font-size: 0.72rem;
  color: var(--text-faint);
}

/* ── Reuse the same pulse keyframes defined in NavigationSheet ── */
@keyframes alert-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}
</style>
