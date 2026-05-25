<template>
  <div class="indexer-tab">
    <!-- Loading state: show only when all status groups are empty -->
    <div v-if="summaries.length === 0 && jobSummaries.length === 0 && operationalTasks.length === 0" class="indexer-empty">
      <i class="pi pi-spin pi-spinner" style="font-size: var(--text-lg-size); color: var(--text-faint)" />
      <span>Loading…</span>
    </div>

    <template v-else>
      <section class="rescan-panel" aria-labelledby="rescan-panel-title">
        <div class="rescan-panel__header">
          <span class="rescan-panel__icon">
            <i class="pi pi-exclamation-triangle" />
          </span>
          <div class="rescan-panel__copy">
            <h4 id="rescan-panel-title">Manual index rescan</h4>
            <p>
              Docker Desktop on Windows with WSL2 can show copied files in the container without sending Linux file
              events. Queue a rescan after adding files from a Windows-mounted folder.
            </p>
          </div>
        </div>
        <div class="rescan-panel__actions">
          <Button
            label="Rescan GPS"
            icon="pi pi-map-marker"
            size="small"
            :loading="rescanLoadingIndex === 'GPS'"
            :disabled="rescanLoadingIndex !== null"
            @click="onTriggerRescan('GPS')"
          />
          <Button
            label="Rescan Media"
            icon="pi pi-images"
            size="small"
            severity="secondary"
            :loading="rescanLoadingIndex === 'MEDIA'"
            :disabled="rescanLoadingIndex !== null"
            @click="onTriggerRescan('MEDIA')"
          />
        </div>
        <span
          v-if="rescanMessage || rescanError"
          :class="['rescan-panel__status', rescanError ? 'rescan-panel__status--error' : 'rescan-panel__status--ok']"
        >
          {{ rescanError || rescanMessage }}
        </span>
      </section>

      <!-- ── File Indexers ── -->
      <div class="section-divider">File Indexers</div>
      <div v-for="s in summaries" :key="s.index" class="index-card" :class="{ 'index-card--active': s.pending > 0 }">
        <div class="index-card__header">
          <span class="index-name">{{ s.index }}</span>
          <span v-if="s.pending > 0" class="index-badge index-badge--scanning">
            <i class="pi pi-spin pi-spinner" /> scanning
          </span>
          <span v-else class="index-badge index-badge--done"> <i class="pi pi-check" /> done </span>
          <span class="index-pct">{{ s.progressPercent }}%</span>
        </div>
        <ProgressBar
          :value="s.progressPercent"
          :class="['index-progress', s.pending > 0 ? 'index-progress--active' : 'index-progress--done']"
          :show-value="false"
          style="height: 6px; border-radius: 4px"
        />
        <div class="index-stats">
          <span class="stat stat--done" :title="`${s.completed} completed`">
            <i class="pi pi-check-circle" /> {{ s.completed }}
          </span>
          <span v-if="s.pending > 0" class="stat stat--pending" :title="`${s.pending} pending`">
            <i class="pi pi-hourglass" /> {{ s.pending }}
          </span>
          <span v-if="s.failed > 0" class="stat stat--failed" :title="`${s.failed} failed`">
            <i class="pi pi-times-circle" /> {{ s.failed }}
          </span>
          <span class="stat stat--total">{{ s.total }} total</span>
        </div>
      </div>

      <!-- ── Background Jobs ── -->
      <div class="section-divider" style="margin-top: 0.75rem">Track Processing Jobs</div>
      <div v-for="j in jobSummaries" :key="j.job" class="index-card" :class="{ 'index-card--active': j.pending > 0 }">
        <div class="index-card__header">
          <span class="index-name">{{ j.label }}</span>
          <span v-if="j.pending > 0" class="index-badge index-badge--scanning">
            <i class="pi pi-spin pi-spinner" /> running
          </span>
          <span v-else class="index-badge index-badge--done"> <i class="pi pi-check" /> done </span>
          <span class="index-pct">{{ j.progressPercent }}%</span>
        </div>
        <ProgressBar
          :value="j.progressPercent"
          :class="['index-progress', j.pending > 0 ? 'index-progress--active' : 'index-progress--done']"
          :show-value="false"
          style="height: 6px; border-radius: 4px"
        />
        <div class="index-stats">
          <span class="stat stat--done" :title="`${j.done} done`"> <i class="pi pi-check-circle" /> {{ j.done }} </span>
          <span v-if="j.pending > 0" class="stat stat--pending" :title="`${j.pending} pending`">
            <i class="pi pi-hourglass" /> {{ j.pending }}
          </span>
          <span class="stat stat--total">{{ j.total }} total</span>
        </div>
      </div>

      <!-- ── Operational Tasks ── -->
      <template v-if="operationalTasks.length > 0">
        <div class="section-divider" style="margin-top: 0.75rem">Map &amp; Routing</div>
        <div
          v-for="task in operationalTasks"
          :key="task.id"
          class="index-card"
          :class="{
            'index-card--active': task.active,
            'index-card--warning': task.state === 'warning',
            'index-card--disabled': task.state === 'disabled',
          }"
        >
          <div class="index-card__header">
            <span class="index-name index-name--plain">{{ task.label }}</span>
            <span :class="['index-badge', operationalTaskBadgeClass(task)]">
              <i :class="operationalTaskBadgeIcon(task)" /> {{ task.statusLabel }}
            </span>
            <span v-if="task.progressPercent !== null" class="index-pct">{{ task.progressPercent }}%</span>
          </div>
          <ProgressBar
            v-if="task.indeterminate"
            mode="indeterminate"
            :class="['index-progress', operationalTaskProgressClass(task)]"
            :show-value="false"
            style="height: 6px; border-radius: 4px"
          />
          <ProgressBar
            v-else
            :value="task.progressPercent ?? 0"
            :class="['index-progress', operationalTaskProgressClass(task)]"
            :show-value="false"
            style="height: 6px; border-radius: 4px"
          />
          <div class="index-stats">
            <span v-if="task.detail" class="stat stat--detail">{{ task.detail }}</span>
            <span v-if="compactVersionInfo(task.versionInfo)" class="stat stat--version">
              <i class="pi pi-tag" /> {{ compactVersionInfo(task.versionInfo) }}
            </span>
            <span v-if="task.metric" class="stat stat--total">{{ task.metric }}</span>
          </div>
        </div>
      </template>
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
import { triggerIndexerRescan, type AdminOperationalTask } from '@/utils/serverAdminApi';
import { compactVersionInfo } from '@/utils/versionInfo';

const { summaries, jobSummaries, operationalTasks, lastRefreshed, refresh } = useIndexerStatus();

const refreshing = ref(false);
const rescanLoadingIndex = ref<'GPS' | 'MEDIA' | null>(null);
const rescanMessage = ref('');
const rescanError = ref('');

async function onRefresh() {
  refreshing.value = true;
  try {
    await refresh();
  } finally {
    refreshing.value = false;
  }
}

async function onTriggerRescan(index: 'GPS' | 'MEDIA') {
  rescanLoadingIndex.value = index;
  rescanMessage.value = '';
  rescanError.value = '';
  try {
    const response = await triggerIndexerRescan(index);
    rescanMessage.value = response.message ?? `${index} rescan request sent.`;
    await refresh();
  } catch (err) {
    console.error('[MTL] Manual index rescan failed:', err);
    rescanError.value = err instanceof Error ? err.message : 'Rescan request failed.';
  } finally {
    rescanLoadingIndex.value = null;
  }
}

function operationalTaskBadgeClass(task: AdminOperationalTask): string {
  if (task.state === 'running') return 'index-badge--scanning';
  if (task.state === 'done') return 'index-badge--done';
  if (task.state === 'disabled') return 'index-badge--disabled';
  return 'index-badge--warning';
}

function operationalTaskProgressClass(task: AdminOperationalTask): string {
  if (task.state === 'running') return 'index-progress--active';
  if (task.state === 'done') return 'index-progress--done';
  if (task.state === 'disabled') return 'index-progress--disabled';
  return 'index-progress--warning';
}

function operationalTaskBadgeIcon(task: AdminOperationalTask): string {
  if (task.state === 'running') return 'pi pi-spin pi-spinner';
  if (task.state === 'done') return 'pi pi-check';
  if (task.state === 'disabled') return 'pi pi-minus-circle';
  return 'pi pi-exclamation-triangle';
}
</script>

<style scoped>
.indexer-tab {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.95rem 1rem;
  border-radius: 0.625rem;
  border: 1px solid var(--border-default);
  background: var(--surface-glass-light);
}

.indexer-empty {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-faint);
  font-size: var(--text-sm-size);
  padding: 1rem 0;
}

/* ── Manual rescan ── */
.rescan-panel {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.9rem;
  border: 1px solid color-mix(in srgb, var(--warning) 58%, var(--border-default));
  border-left: 4px solid var(--warning);
  border-radius: 0.5rem;
  background: color-mix(in srgb, var(--warning-bg) 58%, var(--surface-glass-light));
  box-shadow: 0 0.5rem 1.25rem color-mix(in srgb, var(--warning) 12%, transparent);
}

.rescan-panel__header {
  display: flex;
  gap: 0.75rem;
  align-items: flex-start;
}

.rescan-panel__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 2rem;
  width: 2rem;
  height: 2rem;
  border-radius: 0.5rem;
  background: color-mix(in srgb, var(--warning) 18%, transparent);
  color: var(--warning-text);
}

.rescan-panel__copy {
  min-width: 0;
}

.rescan-panel__copy h4 {
  margin: 0 0 0.25rem;
  font-size: var(--text-base-size);
  color: var(--text-primary);
}

.rescan-panel__copy p {
  margin: 0;
  font-size: var(--text-sm-size);
  line-height: 1.45;
  color: var(--text-muted);
}

.rescan-panel__actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.rescan-panel__status {
  font-size: var(--text-xs-size);
  font-weight: 600;
}

.rescan-panel__status--ok {
  color: var(--success);
}

.rescan-panel__status--error {
  color: var(--error);
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

.index-card--warning .index-name {
  color: var(--warning-text);
}

.index-card--disabled .index-name {
  color: var(--text-faint);
}

/* ── Header ── */
.index-card__header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.index-name {
  font-size: var(--text-base-size);
  font-weight: 600;
  color: var(--text-primary);
  font-family: 'SF Mono', 'Fira Code', monospace;
  letter-spacing: 0.03em;
}

.index-name--plain {
  font-family: inherit;
  letter-spacing: 0;
}

.index-pct {
  font-size: var(--text-sm-size);
  font-weight: 600;
  color: var(--text-muted);
  margin-left: auto;
}

/* ── Badges ── */
.index-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: var(--text-2xs-size);
  font-weight: 600;
  padding: 0.1rem 0.45rem;
  border-radius: 1rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.index-badge--scanning {
  background: var(--warning-bg);
  color: var(--warning-text);
}

.index-badge--done {
  background: var(--success-bg);
  color: var(--success);
}

.index-badge--warning {
  background: var(--warning-bg);
  color: var(--warning-text);
}

.index-badge--disabled {
  background: var(--surface-elevated);
  color: var(--text-faint);
}

/* ── Progress bar overrides ── */
:deep(.index-progress--active .p-progressbar-value) {
  background: linear-gradient(90deg, var(--warning), var(--accent-text));
  animation: progress-shimmer 1.8s ease-in-out infinite;
}

:deep(.index-progress--done .p-progressbar-value) {
  background: var(--success);
}

:deep(.index-progress--warning .p-progressbar-value) {
  background: var(--warning);
}

:deep(.index-progress--disabled .p-progressbar-value) {
  background: var(--border-default);
}

@keyframes progress-shimmer {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
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
  font-size: var(--text-xs-size);
}

.stat .pi {
  font-size: var(--text-xs-size);
}

.stat--done {
  color: var(--success);
}
.stat--pending {
  color: var(--warning);
}
.stat--failed {
  color: var(--error);
}

.stat--detail {
  color: var(--text-muted);
  min-width: 0;
  flex: 1 1 auto;
}

.stat--version {
  color: var(--text-muted);
  min-width: 0;
}

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
  font-size: var(--text-xs-size);
  color: var(--text-faint);
}

/* ── Reuse the same pulse keyframes defined in NavigationSheet ── */
@keyframes alert-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}
</style>
