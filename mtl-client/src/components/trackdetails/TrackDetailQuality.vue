<template>
  <div v-if="gpsTrack" class="quality-container">
    <!-- Status Badges -->
    <div class="status-row">
      <div class="status-badge" :class="loadStatusClass">
        <i :class="loadStatusIcon"></i>
        <span>{{ gpsTrack.loadStatus ?? 'UNKNOWN' }}</span>
      </div>
      <div class="status-badge" :class="duplicateStatusClass">
        <i :class="duplicateStatusIcon"></i>
        <span>{{ gpsTrack.duplicateStatus ?? '—' }}</span>
      </div>
      <div v-if="gpsTrack.didFilterOutlierByDistance" class="status-badge status-badge--neutral">
        <i class="bi bi-funnel-fill"></i>
        <span>Outliers Filtered</span>
      </div>
    </div>

    <!-- Load Messages (if any) -->
    <details v-if="gpsTrack.loadMessages" class="info-drawer">
      <summary class="info-drawer__summary">
        <i class="bi bi-chat-left-text"></i> Load Messages
        <i class="bi bi-chevron-down info-drawer__chevron"></i>
      </summary>
      <div class="load-messages">
        <div v-for="(line, idx) in gpsTrack.loadMessages.split('\n')" :key="idx" class="load-messages__line">
          {{ line }}
        </div>
      </div>
    </details>

    <!-- Point Quality Metrics -->
    <div class="section-label"><i class="bi bi-dot"></i> Point Quality</div>
    <div class="metrics-grid">
      <div class="metric-tile">
        <div class="metric-tile__value metric-tile__value--sm">{{ gpsTrack.numberOfTrackPoints ?? '—' }}</div>
        <div class="metric-tile__label">Total Points</div>
      </div>
      <div class="metric-tile">
        <div
          v-tooltip.top="{ value: formatOptionalDistanceTooltip(gpsTrack.avgDistanceBetweenPoints), showDelay: 400 }"
          class="metric-tile__value metric-tile__value--sm"
        >
          {{ formatOptionalDistance(gpsTrack.avgDistanceBetweenPoints) }}
        </div>
        <div class="metric-tile__label">Avg Pt. Distance</div>
      </div>
      <div class="metric-tile">
        <div
          v-tooltip.top="{ value: formatOptionalDistanceTooltip(gpsTrack.medianDistanceBetweenPoints), showDelay: 400 }"
          class="metric-tile__value metric-tile__value--sm"
        >
          {{ formatOptionalDistance(gpsTrack.medianDistanceBetweenPoints) }}
        </div>
        <div class="metric-tile__label">Median Pt. Distance</div>
      </div>
      <div class="metric-tile">
        <div
          v-tooltip.top="{ value: formatOptionalDistanceTooltip(gpsTrack.maxDistanceBetweenPoints), showDelay: 400 }"
          class="metric-tile__value metric-tile__value--sm"
        >
          {{ formatOptionalDistance(gpsTrack.maxDistanceBetweenPoints) }}
        </div>
        <div class="metric-tile__label">Max Pt. Distance</div>
      </div>
    </div>

    <div class="section-label"><i class="bi bi-sliders"></i> Statistics Curation</div>
    <div class="curation-panel" data-test="statistics-curation">
      <div class="curation-row">
        <span class="curation-row__label">Highlights</span>
        <Select
          :model-value="highlightReason"
          :options="exclusionReasonOptions"
          option-label="label"
          option-value="value"
          class="curation-select"
          :disabled="savingCuration"
          data-test="highlight-exclusion-select"
          @update:model-value="onHighlightReasonChange"
        />
      </div>
      <div class="curation-row">
        <span class="curation-row__label">Statistics</span>
        <Select
          :model-value="statisticsReason"
          :options="exclusionReasonOptions"
          option-label="label"
          option-value="value"
          class="curation-select"
          :disabled="savingCuration"
          data-test="statistics-exclusion-select"
          @update:model-value="onStatisticsReasonChange"
        />
      </div>
      <div v-if="hasAnyExclusion" class="curation-note" data-test="curation-note">
        <i class="bi bi-shield-exclamation"></i>
        <span>{{ curationNote }}</span>
      </div>
    </div>

    <!-- Duplicate Info (if duplicate) -->
    <div v-if="gpsTrack.duplicateOf" class="duplicate-info">
      <i class="bi bi-files info-drawer__summary-icon"></i>
      <span>Duplicate of track </span>
      <a
        class="track-link"
        @pointerdown.stop
        @mousedown.stop
        @touchstart.stop
        @click="navigateTrack(gpsTrack.duplicateOf)"
        >#{{ gpsTrack.duplicateOf }}</a
      >
      <span v-if="gpsTrack.duplicateDetails" class="duplicate-info__detail">— {{ gpsTrack.duplicateDetails }}</span>
    </div>

    <!-- Activity Classification -->
    <div class="section-label"><i class="bi bi-tag"></i> Activity Classification</div>
    <div class="info-list info-list--inline">
      <div class="info-row">
        <span class="info-key">Activity</span>
        <span class="info-val"><ActivityTypeBadge :type="gpsTrack.activityType" size="xs" /></span>
      </div>
      <div class="info-row">
        <span class="info-key">Source</span>
        <span class="info-val">
          <span class="source-badge" :class="activitySourceClass">{{ gpsTrack.activityTypeSource ?? '—' }}</span>
        </span>
      </div>
      <div v-if="gpsTrack.activityTypeSourceDetails" class="info-row">
        <span class="info-key">Source Details</span>
        <span class="info-val info-val--muted">{{ gpsTrack.activityTypeSourceDetails }}</span>
      </div>
    </div>

    <!-- Geo Coverage -->
    <div v-if="hasGeo" class="section-label"><i class="bi bi-bounding-box"></i> Geo Coverage</div>
    <div v-if="hasGeo" class="info-list info-list--inline">
      <div v-if="gpsTrack.centerLat != null" class="info-row">
        <span class="info-key">Center</span>
        <span class="info-val info-val--mono"
          >{{ formatCoord(gpsTrack.centerLat) }}, {{ formatCoord(gpsTrack.centerLng) }}</span
        >
      </div>
      <div v-if="gpsTrack.bboxMinLat != null" class="info-row">
        <span class="info-key">Bbox Lat</span>
        <span class="info-val info-val--mono"
          >{{ formatCoord(gpsTrack.bboxMinLat) }} → {{ formatCoord(gpsTrack.bboxMaxLat) }}</span
        >
      </div>
      <div v-if="gpsTrack.bboxMinLng != null" class="info-row">
        <span class="info-key">Bbox Lng</span>
        <span class="info-val info-val--mono"
          >{{ formatCoord(gpsTrack.bboxMinLng) }} → {{ formatCoord(gpsTrack.bboxMaxLng) }}</span
        >
      </div>
      <div v-if="gpsTrack.utmZone" class="info-row">
        <span class="info-key">UTM Zone</span>
        <span class="info-val">{{ gpsTrack.utmZone }}</span>
      </div>
    </div>

    <!-- GPX Metadata (collapsible) -->
    <details class="info-drawer">
      <summary class="info-drawer__summary">
        <i class="bi bi-file-earmark-code"></i> GPX Metadata
        <i class="bi bi-chevron-down info-drawer__chevron"></i>
      </summary>
      <div class="info-list">
        <div class="info-row">
          <span class="info-key">GPX Version</span><span class="info-val">{{ gpsTrack.gpxVersion ?? '—' }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">Track Type</span><span class="info-val">{{ gpsTrack.trackType ?? '—' }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">Meta Name</span><span class="info-val">{{ gpsTrack.metaName ?? '—' }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">Meta Description</span
          ><span class="info-val">{{ gpsTrack.metaDescription ?? '—' }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">Meta Author</span><span class="info-val">{{ gpsTrack.metaAuthor ?? '—' }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">Meta Time</span
          ><span class="info-val">{{ gpsTrack.metaTime ? formatDateAndTime(new Date(gpsTrack.metaTime)) : '—' }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">Meta Link</span><span class="info-val">{{ gpsTrack.metaLink ?? '—' }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">Garmin Activity ID</span
          ><span class="info-val">{{ gpsTrack.garminActivityId ?? '—' }}</span>
        </div>
      </div>
    </details>

    <!-- File & Indexer (collapsible) -->
    <details v-if="gpsTrack.indexedFile" class="info-drawer">
      <summary class="info-drawer__summary">
        <i class="bi bi-hdd"></i> File &amp; Indexer
        <i class="bi bi-chevron-down info-drawer__chevron"></i>
      </summary>
      <div class="info-list">
        <div class="info-row">
          <span class="info-key">File ID</span><span class="info-val">{{ gpsTrack.indexedFile.id ?? '—' }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">File Name</span><span class="info-val">{{ gpsTrack.indexedFile.name }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">Path</span><span class="info-val info-val--mono">{{ gpsTrack.indexedFile.path }}</span>
        </div>
        <div v-if="gpsTrack.indexedFile.basePath" class="info-row">
          <span class="info-key">Base Path</span
          ><span class="info-val info-val--mono">{{ gpsTrack.indexedFile.basePath }}</span>
        </div>
        <div v-if="gpsTrack.indexedFile.fullPath" class="info-row">
          <span class="info-key">Full Path</span
          ><span class="info-val info-val--mono">{{ gpsTrack.indexedFile.fullPath }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">Size</span
          ><span class="info-val">{{ formatOptionalBytes(gpsTrack.indexedFile.size) }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">Hash</span
          ><span class="info-val info-val--mono">{{ gpsTrack.indexedFile.hash ?? '—' }}</span>
        </div>
        <div v-if="gpsTrack.indexedFile.lastModifiedDate" class="info-row">
          <span class="info-key">Last Modified</span
          ><span class="info-val">{{ formatDateAndTime(new Date(gpsTrack.indexedFile.lastModifiedDate)) }}</span>
        </div>
        <div v-if="gpsTrack.indexedFile.indexerStatus" class="info-row">
          <span class="info-key">Indexer Status</span
          ><span class="info-val">{{ gpsTrack.indexedFile.indexerStatus }}</span>
        </div>
        <div v-if="gpsTrack.indexedFile.indexerInvocations != null" class="info-row">
          <span class="info-key">Indexer Runs</span
          ><span class="info-val">{{ gpsTrack.indexedFile.indexerInvocations }}</span>
        </div>
        <div v-if="gpsTrack.indexedFile.indexerId" class="info-row">
          <span class="info-key">Indexer ID</span
          ><span class="info-val info-val--mono">{{ gpsTrack.indexedFile.indexerId }}</span>
        </div>
        <div v-if="gpsTrack.indexedFile.lastMessage" class="info-row">
          <span class="info-key">Last Message</span><span class="info-val">{{ gpsTrack.indexedFile.lastMessage }}</span>
        </div>
        <div v-if="gpsTrack.indexedFile.indexAddedDate" class="info-row">
          <span class="info-key">Index Added</span
          ><span class="info-val">{{ formatDateAndTime(new Date(gpsTrack.indexedFile.indexAddedDate)) }}</span>
        </div>
        <div v-if="gpsTrack.indexedFile.indexUpdateDate" class="info-row">
          <span class="info-key">Index Updated</span
          ><span class="info-val">{{ formatDateAndTime(new Date(gpsTrack.indexedFile.indexUpdateDate)) }}</span>
        </div>
        <div v-if="gpsTrack.indexedFile.index" class="info-row">
          <span class="info-key">Index</span
          ><span class="info-val info-val--mono">{{ gpsTrack.indexedFile.index }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">Track Created</span
          ><span class="info-val">{{
            gpsTrack.createDate ? formatDateAndTime(new Date(gpsTrack.createDate)) : '—'
          }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">Track Updated</span
          ><span class="info-val">{{
            gpsTrack.updateDate ? formatDateAndTime(new Date(gpsTrack.updateDate)) : '—'
          }}</span>
        </div>
      </div>
    </details>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, ref, watch } from 'vue';
import { formatBytes, formatDateAndTime, formatDistance, formatDistanceTooltip } from '@/utils/Utils';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';
import { updateTrackStatisticsExclusion } from '@/utils/ServiceHelper';
import {
  StatisticsExclusionUpdateRequestHighlightExclusionReasonEnum as ExclusionReasonEnum,
  type GpsTrack,
  type StatisticsExclusionUpdateRequest,
  type StatisticsExclusionUpdateRequestHighlightExclusionReasonEnum,
  type StatisticsExclusionUpdateRequestStatisticsExclusionReasonEnum,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

type ToastService = {
  add: (message: { severity: string; summary: string; detail?: string; life?: number }) => void;
};
type ExclusionReason = StatisticsExclusionUpdateRequestHighlightExclusionReasonEnum;
type ExclusionReasonOption = {
  label: string;
  value: ExclusionReason | null;
};

const exclusionReasonOptions: ExclusionReasonOption[] = [
  { label: 'Included', value: null },
  { label: 'GPS noise', value: ExclusionReasonEnum.GpsNoise },
  { label: 'Wrong activity', value: ExclusionReasonEnum.WrongActivity },
  { label: 'Import artifact', value: ExclusionReasonEnum.ImportArtifact },
  { label: 'Other', value: ExclusionReasonEnum.Other },
];
const exclusionReasonValues = new Set(exclusionReasonOptions.map((option) => option.value).filter(Boolean));

defineOptions({
  name: 'TrackDetailQuality',
});

const props = withDefaults(
  defineProps<{
    gpsTrack?: GpsTrack | null;
  }>(),
  {
    gpsTrack: null,
  }
);

const emit = defineEmits<{
  'navigate-track': [trackId: number];
  'track-updated': [track: GpsTrack];
}>();

const gpsTrack = computed(() => props.gpsTrack);
const toast = inject<ToastService>('toast', { add: () => undefined });
const highlightReason = ref<ExclusionReason | null>(null);
const statisticsReason = ref<ExclusionReason | null>(null);
const savingCuration = ref(false);
let curationSaveSerial = 0;

const hasAnyExclusion = computed(() => highlightReason.value != null || statisticsReason.value != null);
const curationNote = computed(() => {
  if (statisticsReason.value != null) return 'Excluded from statistics and highlights.';
  if (highlightReason.value != null) return 'Excluded from highlights only.';
  return '';
});

watch(
  () => [props.gpsTrack?.id, props.gpsTrack?.highlightExclusionReason, props.gpsTrack?.statisticsExclusionReason],
  () => {
    highlightReason.value = normalizeExclusionReason(props.gpsTrack?.highlightExclusionReason);
    statisticsReason.value = normalizeExclusionReason(props.gpsTrack?.statisticsExclusionReason);
  },
  { immediate: true }
);

const loadStatusClass = computed(() => {
  switch (props.gpsTrack?.loadStatus) {
    case 'SUCCESS':
      return 'status-badge--success';
    case 'FAILED':
      return 'status-badge--error';
    case 'EMPTY_FILE':
      return 'status-badge--warning';
    default:
      return 'status-badge--neutral';
  }
});

const loadStatusIcon = computed(() => {
  switch (props.gpsTrack?.loadStatus) {
    case 'SUCCESS':
      return 'bi bi-check-circle-fill';
    case 'FAILED':
      return 'bi bi-x-circle-fill';
    case 'EMPTY_FILE':
      return 'bi bi-exclamation-circle-fill';
    default:
      return 'bi bi-question-circle';
  }
});

const duplicateStatusClass = computed(() => {
  switch (props.gpsTrack?.duplicateStatus) {
    case 'UNIQUE':
      return 'status-badge--success';
    case 'DUPLICATE':
      return 'status-badge--warning';
    case 'NOT_CHECKED_YET':
      return 'status-badge--neutral';
    case 'EXCLUDED':
      return 'status-badge--neutral';
    default:
      return 'status-badge--neutral';
  }
});

const duplicateStatusIcon = computed(() => {
  switch (props.gpsTrack?.duplicateStatus) {
    case 'UNIQUE':
      return 'bi bi-check2';
    case 'DUPLICATE':
      return 'bi bi-files';
    case 'NOT_CHECKED_YET':
      return 'bi bi-hourglass-split';
    case 'EXCLUDED':
      return 'bi bi-slash-circle';
    default:
      return 'bi bi-question-circle';
  }
});

const activitySourceClass = computed(() => {
  switch (props.gpsTrack?.activityTypeSource) {
    case 'USER_SET':
      return 'source-badge--user';
    case 'AUTO_GUESS':
      return 'source-badge--auto';
    case 'FAILED':
      return 'source-badge--failed';
    default:
      return '';
  }
});

const hasGeo = computed(
  () => props.gpsTrack?.centerLat != null || props.gpsTrack?.bboxMinLat != null || props.gpsTrack?.utmZone != null
);

function navigateTrack(trackId: number | null | undefined) {
  if (trackId != null) {
    emit('navigate-track', trackId);
  }
}

async function onHighlightReasonChange(value: ExclusionReason | null) {
  highlightReason.value = normalizeExclusionReason(value);
  await saveCuration();
}

async function onStatisticsReasonChange(value: ExclusionReason | null) {
  statisticsReason.value = normalizeExclusionReason(value);
  await saveCuration();
}

async function saveCuration() {
  const trackId = props.gpsTrack?.id;
  if (trackId == null) return;

  const saveSerial = ++curationSaveSerial;
  savingCuration.value = true;
  const request: StatisticsExclusionUpdateRequest = {
    highlightExclusionReason: highlightReason.value ?? undefined,
    statisticsExclusionReason: (statisticsReason.value ?? undefined) as
      | StatisticsExclusionUpdateRequestStatisticsExclusionReasonEnum
      | undefined,
  };

  try {
    const savedTrack = await updateTrackStatisticsExclusion(trackId, request);
    if (saveSerial !== curationSaveSerial) return;
    emit('track-updated', savedTrack);
    toast.add({ severity: 'success', summary: 'Statistics curation saved', life: 1800 });
  } catch {
    if (saveSerial !== curationSaveSerial) return;
    highlightReason.value = normalizeExclusionReason(props.gpsTrack?.highlightExclusionReason);
    statisticsReason.value = normalizeExclusionReason(props.gpsTrack?.statisticsExclusionReason);
    toast.add({
      severity: 'error',
      summary: 'Save failed',
      detail: 'Could not update statistics curation.',
      life: 4000,
    });
  } finally {
    if (saveSerial === curationSaveSerial) savingCuration.value = false;
  }
}

function normalizeExclusionReason(value: unknown): ExclusionReason | null {
  return typeof value === 'string' && exclusionReasonValues.has(value as ExclusionReason)
    ? (value as ExclusionReason)
    : null;
}

function formatCoord(v: number | null | undefined): string {
  return v != null ? v.toFixed(6) : '—';
}

function formatOptionalDistance(v: number | null | undefined): string {
  return v == null ? '—' : (formatDistance(v) ?? '—');
}

function formatOptionalDistanceTooltip(v: number | null | undefined): string {
  return v == null ? '—' : formatDistanceTooltip(v);
}

function formatOptionalBytes(v: number | null | undefined): string {
  return v == null ? '—' : formatBytes(v);
}
</script>

<style scoped>
/* ── Container ── */
.quality-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding-bottom: 0.5rem;
}

/* ── Status Row ── */
.status-row {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--border-subtle);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: var(--text-xs-size);
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  padding: 0.25rem 0.7rem;
  border-radius: 999px;
  border: 1px solid transparent;
}

.status-badge i {
  font-size: var(--text-xs-size);
}

.status-badge--success {
  background: var(--success-bg);
  color: var(--success);
  border-color: rgba(22, 163, 74, 0.2);
}
.status-badge--error {
  background: var(--error-bg);
  color: var(--error);
  border-color: rgba(220, 38, 38, 0.2);
}
.status-badge--warning {
  background: var(--warning-bg);
  color: var(--warning);
  border-color: rgba(217, 119, 6, 0.2);
}
.status-badge--neutral {
  background: var(--surface-elevated);
  color: var(--text-muted);
  border-color: var(--border-default);
}

/* ── Section Label ── */
.section-label {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: var(--text-2xs-size);
  font-weight: 600;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--text-faint);
  padding: 0.75rem 1rem 0.35rem;
}
.section-label i {
  font-size: var(--text-xs-size);
  opacity: 0.7;
}

/* ── Metrics Grid ── */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  padding: 0 0.5rem;
  margin-bottom: 0.25rem;
}

.metric-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 0.6rem 0.4rem;
  border-radius: 8px;
  background: var(--surface-elevated);
  margin: 0.2rem;
}

.metric-tile__value--sm {
  font-size: var(--text-base-size);
  font-weight: 600;
  color: var(--text-primary);
  line-height: var(--text-base-lh);
}

.metric-tile__label {
  font-size: var(--text-2xs-size);
  color: var(--text-muted);
  margin-top: 0.2rem;
  letter-spacing: 0.02em;
}

.metric-tile--energy {
  border: 1px solid var(--accent-subtle);
  background: var(--accent-bg);
}

/* ── Statistics Curation ── */
.curation-panel {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin: 0 0.5rem 0.35rem;
  padding: 0.5rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
}

.curation-row {
  display: grid;
  grid-template-columns: minmax(7rem, 0.45fr) minmax(0, 1fr);
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
}

.curation-row__label {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 650;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.curation-select {
  width: 100%;
  min-width: 0;
}

.curation-note {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.4rem 0.5rem;
  border-radius: 6px;
  background: var(--warning-bg);
  color: var(--warning-text);
  font-size: var(--text-xs-size);
}

/* ── Duplicate Info ── */
.duplicate-info {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
  margin: 0.25rem 1rem 0.5rem;
  padding: 0.55rem 0.85rem;
  border-radius: 8px;
  background: var(--warning-bg);
  border: 1px solid rgba(217, 119, 6, 0.2);
  font-size: var(--text-sm-size);
  color: var(--warning-text);
}

.duplicate-info__detail {
  opacity: 0.75;
  font-style: italic;
}

/* ── Source Badge ── */
.source-badge {
  display: inline-flex;
  font-size: var(--text-xs-size);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
}
.source-badge--user {
  background: var(--success-bg);
  color: var(--success);
}
.source-badge--auto {
  background: var(--accent-bg);
  color: var(--accent-text);
}
.source-badge--failed {
  background: var(--error-bg);
  color: var(--error);
}

/* ── Info List ── */
.info-list {
  display: flex;
  flex-direction: column;
  padding: 0.25rem 0;
}

.info-list--inline {
  padding: 0 0.5rem 0.25rem;
}

.info-list--inline .info-row {
  border-radius: 4px;
  padding: 0.3rem 0.5rem;
  border-top: 1px solid var(--border-subtle);
}

.info-row {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
  padding: 0.35rem 0.9rem;
  font-size: var(--text-sm-size);
  border-top: 1px solid var(--border-subtle);
}

.info-key {
  flex: 0 0 7rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.info-val {
  color: var(--text-secondary);
  word-break: break-word;
  min-width: 0;
}

.info-val--mono {
  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, monospace;
  font-size: var(--text-xs-size);
  opacity: 0.8;
}

.info-val--muted {
  color: var(--text-muted);
  font-style: italic;
  font-size: var(--text-xs-size);
}

/* ── Info Drawer ── */
.info-drawer {
  margin: 0.35rem 0.5rem 0;
  border-radius: 8px;
  border: 1px solid var(--border-default);
  overflow: hidden;
}

.info-drawer[open] .info-drawer__chevron {
  transform: rotate(180deg);
}

.info-drawer__chevron {
  transition: transform 0.2s ease;
}

.info-drawer__summary {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.6rem 0.9rem;
  font-size: var(--text-xs-size);
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--text-muted);
  cursor: pointer;
  user-select: none;
  list-style: none;
  background: var(--surface-elevated);
}

.info-drawer__summary::-webkit-details-marker {
  display: none;
}
.info-drawer__summary i:first-child {
  font-size: var(--text-sm-size);
}
.info-drawer__summary .info-drawer__chevron {
  margin-left: auto;
  font-size: var(--text-xs-size);
}

/* ── Load Messages ── */
.load-messages {
  padding: 0.5rem 0.9rem;
  font-size: var(--text-xs-size);
  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, monospace;
  color: var(--text-secondary);
  background: var(--surface-elevated);
  line-height: var(--text-xs-lh);
}

.load-messages__line:empty::after {
  content: '\00a0';
}

/* ── Track Link ── */
.track-link {
  color: var(--accent-text);
  cursor: pointer;
  text-decoration: none;
  font-weight: 600;
  -webkit-user-select: text;
  user-select: text;
}
.track-link:hover {
  text-decoration: underline;
}

/* ── Mobile ── */
@media (max-width: 480px) {
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .info-key {
    flex: 0 0 5.5rem;
  }
}
</style>
