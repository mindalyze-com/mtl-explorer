<template>
  <div v-if="gpsTrack" class="quality-container">
    <section v-if="gpsTrack.indexedFile?.id != null" class="source-review">
      <RouterLink :to="{ name: 'review', params: { sourceId: gpsTrack.indexedFile.id } }"
        >Review source findings and map use</RouterLink
      >
      <p>
        Check the original recording, download it to fix it elsewhere, and choose whether it counts on the map and in
        statistics.
      </p>
      <p v-if="gpsTrack.reviewMapExcluded">
        This recording is hidden from the main map. Inspection maps remain available here.
      </p>
      <p v-if="gpsTrack.reviewExcluded">This track is excluded from statistics and highlights in Review.</p>
      <p v-if="gpsTrack.timingUnreliable">
        Source timing is missing, unreliable or estimated. Time-based metrics are unavailable.
      </p>
    </section>
    <!-- Status Badges -->
    <div class="status-row">
      <div class="status-badge" :class="loadStatusClass">
        <i :class="loadStatusIcon"></i>
        <span>{{ loadStatusLabel }}</span>
      </div>
      <div class="status-badge" :class="duplicateStatusClass">
        <i :class="duplicateStatusIcon"></i>
        <span>{{ duplicateStatusLabel }}</span>
      </div>
      <div v-if="gpsTrack.didFilterOutlierByDistance" class="status-badge status-badge--neutral">
        <i class="bi bi-funnel-fill"></i>
        <span>Outliers filtered</span>
      </div>
    </div>

    <p v-if="gpsTrack.duplicateStatus === 'FAILED' && gpsTrack.duplicateDetails" class="duplicate-failure-detail">
      {{ gpsTrack.duplicateDetails }}
    </p>

    <!-- Duplicate Info (if duplicate) -->
    <div v-if="gpsTrack.duplicateOf" class="duplicate-info">
      <i class="bi bi-files info-drawer__summary-icon"></i>
      <span>Duplicate of track </span>
      <button
        type="button"
        class="track-link"
        @pointerdown.stop
        @mousedown.stop
        @touchstart.stop
        @click="navigateTrack(gpsTrack.duplicateOf)"
      >
        #{{ gpsTrack.duplicateOf }}
      </button>
      <span v-if="gpsTrack.duplicateDetails" class="duplicate-info__detail">— {{ gpsTrack.duplicateDetails }}</span>
    </div>

    <h3 class="section-label"><i class="bi bi-sliders" aria-hidden="true"></i> Activity &amp; inclusion</h3>
    <div class="curation-panel" data-test="statistics-curation">
      <div class="curation-row">
        <label for="track-activity-type" class="curation-row__label">Activity type</label>
        <Select
          :model-value="activityType"
          :options="activityTypeOptions"
          option-label="label"
          option-value="value"
          placeholder="Select activity"
          class="curation-select"
          :disabled="savingActivityType"
          input-id="track-activity-type"
          aria-label="Activity type"
          data-test="activity-type-select"
          @update:model-value="onActivityTypeChange"
        />
      </div>
      <div class="curation-row">
        <label for="track-statistics-exclusion" class="curation-row__label">Statistics</label>
        <Select
          :model-value="statisticsReason"
          :options="exclusionReasonOptions"
          option-label="label"
          option-value="value"
          placeholder="Included"
          class="curation-select"
          :disabled="savingCuration"
          input-id="track-statistics-exclusion"
          aria-label="Statistics inclusion"
          data-test="statistics-exclusion-select"
          @update:model-value="onStatisticsReasonChange"
        />
      </div>
      <div class="curation-row">
        <label for="track-highlight-exclusion" class="curation-row__label">Highlights</label>
        <Select
          :model-value="highlightReason"
          :options="exclusionReasonOptions"
          option-label="label"
          option-value="value"
          placeholder="Included"
          class="curation-select"
          :disabled="savingCuration"
          input-id="track-highlight-exclusion"
          aria-label="Highlights inclusion"
          data-test="highlight-exclusion-select"
          @update:model-value="onHighlightReasonChange"
        />
      </div>
      <div v-if="hasAnyExclusion" class="curation-note" data-test="curation-note">
        <i class="bi bi-shield-exclamation"></i>
        <span>{{ curationNote }}</span>
      </div>
    </div>

    <p class="quality-help">
      These settings save automatically for this track. Excluding statistics also excludes highlights; the track stays
      available. Choices made in Review apply separately.
    </p>

    <p v-if="(gpsTrack.trackDurationUnknownSecs ?? 0) > 0" class="curation-note">
      <i class="bi bi-question-circle" aria-hidden="true"></i>
      Movement could not be determined for {{ formatCompactDurationSeconds(gpsTrack.trackDurationUnknownSecs!) }}.
    </p>
    <TrackGpsReview v-if="gpsTrack.id != null" :key="gpsTrack.id" :track-id="gpsTrack.id" />

    <h3 class="section-label"><i class="bi bi-info-circle" aria-hidden="true"></i> Recording details</h3>
    <div class="quality-diagnostics">
      <!-- Point Quality Metrics -->
      <details class="info-drawer">
        <summary class="info-drawer__summary">
          <i class="bi bi-rulers" aria-hidden="true"></i> Point spacing
          <span class="info-drawer__hint"
            >{{
              gpsTrack.numberOfTrackPoints == null ? '—' : formatNumber(gpsTrack.numberOfTrackPoints, 0)
            }}
            points</span
          >
          <i class="bi bi-chevron-down info-drawer__chevron" aria-hidden="true"></i>
        </summary>
        <div class="metrics-grid">
          <div class="metric-tile">
            <div class="metric-tile__value metric-tile__value--sm">
              {{ gpsTrack.numberOfTrackPoints == null ? '—' : formatNumber(gpsTrack.numberOfTrackPoints, 0) }}
            </div>
            <div class="metric-tile__label">Total points</div>
          </div>
          <div class="metric-tile">
            <div
              v-tooltip.top="{
                value: formatOptionalDistanceTooltip(gpsTrack.avgDistanceBetweenPoints),
                showDelay: 400,
              }"
              class="metric-tile__value metric-tile__value--sm"
            >
              {{ formatOptionalDistance(gpsTrack.avgDistanceBetweenPoints) }}
            </div>
            <div class="metric-tile__label">Average spacing</div>
          </div>
          <div class="metric-tile">
            <div
              v-tooltip.top="{
                value: formatOptionalDistanceTooltip(gpsTrack.medianDistanceBetweenPoints),
                showDelay: 400,
              }"
              class="metric-tile__value metric-tile__value--sm"
            >
              {{ formatOptionalDistance(gpsTrack.medianDistanceBetweenPoints) }}
            </div>
            <div class="metric-tile__label">Median spacing</div>
          </div>
          <div class="metric-tile">
            <div
              v-tooltip.top="{
                value: formatOptionalDistanceTooltip(gpsTrack.maxDistanceBetweenPoints),
                showDelay: 400,
              }"
              class="metric-tile__value metric-tile__value--sm"
            >
              {{ formatOptionalDistance(gpsTrack.maxDistanceBetweenPoints) }}
            </div>
            <div class="metric-tile__label">Largest spacing</div>
          </div>
        </div>
      </details>

      <!-- Activity Classification -->
      <details class="info-drawer">
        <summary class="info-drawer__summary">
          <i class="bi bi-tag" aria-hidden="true"></i> Activity source
          <span class="info-drawer__hint">{{ activitySourceLabel }}</span>
          <i class="bi bi-chevron-down info-drawer__chevron" aria-hidden="true"></i>
        </summary>
        <div class="info-list info-list--inline">
          <div class="info-row">
            <span class="info-key">Activity</span>
            <span class="info-val"><ActivityTypeBadge :type="gpsTrack.activityType" size="xs" /></span>
          </div>
          <div class="info-row">
            <span class="info-key">Source</span>
            <span class="info-val">
              <span class="source-badge" :class="activitySourceClass">{{ activitySourceLabel }}</span>
            </span>
          </div>
          <div v-if="gpsTrack.activityTypeSourceDetails" class="info-row">
            <span class="info-key">Source Details</span>
            <span class="info-val info-val--muted">{{ gpsTrack.activityTypeSourceDetails }}</span>
          </div>
        </div>
      </details>

      <!-- Geo Coverage -->
      <details v-if="hasGeo" class="info-drawer">
        <summary class="info-drawer__summary">
          <i class="bi bi-bounding-box" aria-hidden="true"></i> Geographic bounds
          <i class="bi bi-chevron-down info-drawer__chevron" aria-hidden="true"></i>
        </summary>
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
      </details>

      <!-- Import messages (if any) -->
      <details v-if="gpsTrack.loadMessages" class="info-drawer">
        <summary class="info-drawer__summary">
          <i class="bi bi-chat-left-text"></i> Import messages
          <i class="bi bi-chevron-down info-drawer__chevron"></i>
        </summary>
        <div class="load-messages">
          <div v-for="(line, idx) in gpsTrack.loadMessages.split('\n')" :key="idx" class="load-messages__line">
            {{ line }}
          </div>
        </div>
      </details>

      <!-- GPX metadata (collapsible) -->
      <details class="info-drawer">
        <summary class="info-drawer__summary">
          <i class="bi bi-file-earmark-code"></i> GPX metadata
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
            ><span class="info-val">{{
              gpsTrack.metaTime ? formatDateAndTime(new Date(gpsTrack.metaTime)) : '—'
            }}</span>
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
          <i class="bi bi-hdd"></i> File &amp; indexing
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
            <span class="info-key">Path</span
            ><span class="info-val info-val--mono">{{ gpsTrack.indexedFile.path }}</span>
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
            <span class="info-key">Last Message</span
            ><span class="info-val">{{ gpsTrack.indexedFile.lastMessage }}</span>
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
  </div>
</template>

<script setup lang="ts">
import { warnRequestFailure } from '@/utils/safeLogging';
import TrackGpsReview from './TrackGpsReview.vue';
import { RouterLink } from 'vue-router';
import { computed, inject, ref, watch } from 'vue';
import {
  formatBytes,
  formatDateAndTime,
  formatDistance,
  formatDistanceTooltip,
  formatCompactDurationSeconds,
  formatNumber,
} from '@/utils/Utils';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';
import { updateTrackActivityType, updateTrackStatisticsExclusion } from '@/utils/ServiceHelper';
import {
  GpsTrackActivityTypeEnum,
  StatisticsExclusionUpdateRequestHighlightExclusionReasonEnum as ExclusionReasonEnum,
  type GpsTrack,
  type GpsTrackActivityTypeEnum as ActivityType,
  type StatisticsExclusionUpdateRequest,
  type StatisticsExclusionUpdateRequestHighlightExclusionReasonEnum,
  type StatisticsExclusionUpdateRequestStatisticsExclusionReasonEnum,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import type { ToastService } from '@/types/ui';

type ExclusionReason = StatisticsExclusionUpdateRequestHighlightExclusionReasonEnum;
type ExclusionReasonOption = {
  label: string;
  value: ExclusionReason | null;
};
type ActivityTypeOption = {
  label: string;
  value: ActivityType;
};

const activityTypeOptions: ActivityTypeOption[] = [
  { label: 'Bicycle', value: GpsTrackActivityTypeEnum.Bicycle },
  { label: 'Walking', value: GpsTrackActivityTypeEnum.Walking },
  { label: 'Hiking', value: GpsTrackActivityTypeEnum.Hiking },
  { label: 'Running', value: GpsTrackActivityTypeEnum.Running },
  { label: 'Mountain biking', value: GpsTrackActivityTypeEnum.MountainBiking },
  { label: 'Stand-up paddle', value: GpsTrackActivityTypeEnum.StandUpPaddle },
  { label: 'Rowing', value: GpsTrackActivityTypeEnum.Rowing },
  { label: 'Kayaking', value: GpsTrackActivityTypeEnum.Kayaking },
  { label: 'Skiing', value: GpsTrackActivityTypeEnum.Skiing },
  { label: 'Motorbiking', value: GpsTrackActivityTypeEnum.Motorbiking },
  { label: 'Car', value: GpsTrackActivityTypeEnum.Car },
  { label: 'Airplane', value: GpsTrackActivityTypeEnum.Airplane },
  { label: 'Supersonic', value: GpsTrackActivityTypeEnum.SuperSonic },
];
const activityTypeValues = new Set<ActivityType>(Object.values(GpsTrackActivityTypeEnum));
const exclusionReasonOptions: ExclusionReasonOption[] = [
  { label: 'Included', value: null },
  { label: 'Excluded: GPS noise', value: ExclusionReasonEnum.GpsNoise },
  { label: 'Excluded: wrong activity', value: ExclusionReasonEnum.WrongActivity },
  { label: 'Excluded: import artifact', value: ExclusionReasonEnum.ImportArtifact },
  { label: 'Excluded: other', value: ExclusionReasonEnum.Other },
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
const activityType = ref<ActivityType | null>(null);
const highlightReason = ref<ExclusionReason | null>(null);
const statisticsReason = ref<ExclusionReason | null>(null);
const savingActivityType = ref(false);
const savingCuration = ref(false);
let activityTypeSaveSerial = 0;
let curationSaveSerial = 0;

const hasAnyExclusion = computed(() => highlightReason.value != null || statisticsReason.value != null);
const curationNote = computed(() => {
  if (statisticsReason.value != null) return 'Excluded from statistics and highlights.';
  if (highlightReason.value != null) return 'Excluded from highlights only.';
  return '';
});

watch(
  () => [
    props.gpsTrack?.id,
    props.gpsTrack?.activityType,
    props.gpsTrack?.highlightExclusionReason,
    props.gpsTrack?.statisticsExclusionReason,
  ],
  () => {
    activityType.value = normalizeActivityType(props.gpsTrack?.activityType);
    highlightReason.value = normalizeExclusionReason(props.gpsTrack?.highlightExclusionReason);
    statisticsReason.value = normalizeExclusionReason(props.gpsTrack?.statisticsExclusionReason);
  },
  { immediate: true }
);

const loadStatusLabel = computed(() => {
  switch (props.gpsTrack?.loadStatus) {
    case 'SUCCESS':
      return 'Imported';
    case 'FAILED':
      return 'Import failed';
    case 'EMPTY_FILE':
      return 'Empty file';
    default:
      return 'Import status unknown';
  }
});
const duplicateStatusLabel = computed(() => {
  switch (props.gpsTrack?.duplicateStatus) {
    case 'UNIQUE':
      return 'Unique track';
    case 'DUPLICATE':
      return 'Duplicate';
    case 'NOT_CHECKED_YET':
      return 'Duplicate check pending';
    case 'EXCLUDED':
      return 'Duplicate check excluded';
    case 'FAILED':
      return 'Duplicate check failed';
    default:
      return 'Duplicate status unknown';
  }
});
const activitySourceLabel = computed(() => {
  switch (props.gpsTrack?.activityTypeSource) {
    case 'USER_SET':
      return 'Set manually';
    case 'AUTO_GUESS':
      return 'Detected automatically';
    case 'FAILED':
      return 'Detection failed';
    default:
      return props.gpsTrack?.activityTypeSource?.replaceAll('_', ' ') ?? 'Unknown';
  }
});

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
    case 'FAILED':
      return 'status-badge--error';
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
    case 'FAILED':
      return 'bi bi-x-circle-fill';
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

async function onActivityTypeChange(value: ActivityType | null) {
  const normalizedType = normalizeActivityType(value);
  if (normalizedType == null) {
    activityType.value = normalizeActivityType(props.gpsTrack?.activityType);
    return;
  }

  activityType.value = normalizedType;
  await saveActivityType(normalizedType);
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
      StatisticsExclusionUpdateRequestStatisticsExclusionReasonEnum | undefined,
  };

  try {
    const savedTrack = await updateTrackStatisticsExclusion(trackId, request);
    if (saveSerial !== curationSaveSerial) return;
    emit('track-updated', savedTrack);
    toast.add({ severity: 'success', summary: 'Statistics curation saved', life: 1800 });
  } catch (failure) {
    if (saveSerial !== curationSaveSerial) return;
    warnRequestFailure('Track statistics curation could not be saved', failure, {
      topic1: 'tracks',
      topic2: 'TrackDetailQuality',
      topic3: 'curation',
    });
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

async function saveActivityType(selectedType: ActivityType) {
  const track = props.gpsTrack;
  if (!track?.id || selectedType === track.activityType) return;

  const saveSerial = ++activityTypeSaveSerial;
  savingActivityType.value = true;

  try {
    const savedTrack = await updateTrackActivityType(track.id, selectedType);
    if (saveSerial !== activityTypeSaveSerial) return;
    emit('track-updated', savedTrack);
    toast.add({ severity: 'success', summary: 'Activity type saved', life: 1800 });
  } catch (failure) {
    if (saveSerial !== activityTypeSaveSerial) return;
    warnRequestFailure('Track activity type could not be saved', failure, {
      topic1: 'tracks',
      topic2: 'TrackDetailQuality',
      topic3: 'activity-type',
    });
    activityType.value = normalizeActivityType(props.gpsTrack?.activityType);
    toast.add({
      severity: 'error',
      summary: 'Save failed',
      detail: 'Could not update the activity type.',
      life: 4000,
    });
  } finally {
    if (saveSerial === activityTypeSaveSerial) savingActivityType.value = false;
  }
}

function normalizeActivityType(value: unknown): ActivityType | null {
  return typeof value === 'string' && activityTypeValues.has(value as ActivityType) ? (value as ActivityType) : null;
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
.duplicate-failure-detail {
  margin: 0.25rem 1rem 0.5rem;
  font-size: var(--text-sm-size);
  color: var(--text-muted);
}
/* ── Container ── */
.quality-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 0.75rem;
  gap: 0.65rem;
}

/* ── Status Row ── */
.status-row {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  align-items: center;
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: var(--text-xs-size);
  font-weight: 600;
  letter-spacing: 0;
  padding: 0.3rem 0.75rem;
  border-radius: 999px;
  border: 1px solid transparent;
}

.status-badge i {
  font-size: var(--text-xs-size);
}

.status-badge--success {
  background: color-mix(in srgb, var(--success) var(--status-surface-tint), var(--surface-sheet-solid));
  color: var(--success-text);
  border-color: rgba(22, 163, 74, 0.2);
}
.status-badge--error {
  background: color-mix(in srgb, var(--error) var(--status-surface-tint), var(--surface-sheet-solid));
  color: var(--error-text);
  border-color: rgba(220, 38, 38, 0.2);
}
.status-badge--warning {
  background: var(--warning-bg);
  color: var(--warning-text);
  border-color: rgba(217, 119, 6, 0.2);
}
.status-badge--neutral {
  background: var(--surface-elevated);
  color: var(--text-muted);
  border-color: var(--border-default);
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
  padding: 0.65rem 0.4rem;
  border-radius: 8px;
  border: 1px solid var(--border-subtle);
  background: var(--surface-elevated);
  margin: 0.2rem;
  transition: border-color 0.15s ease;
}

.metric-tile:hover {
  border-color: var(--border-default);
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

.source-review {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.65rem;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  background: var(--surface-elevated);
}

.source-review a {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  color: var(--accent-text);
  font-weight: 600;
  text-underline-offset: 0.2em;
}

.source-review a:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 3px;
  border-radius: 0.25rem;
}

.source-review p {
  margin: 0;
  color: var(--text-secondary);
}

/* ── Statistics Curation ── */
.curation-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin: 0;
  padding: 0.25rem 0.65rem;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  background: var(--surface-elevated);
}

.curation-row {
  display: grid;
  grid-template-columns: minmax(7rem, 0.45fr) minmax(0, 1fr);
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
  padding: 0.5rem 0;
}

.curation-row + .curation-row {
  border-top: 1px solid var(--border-subtle);
}

.curation-row__label {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 600;
  letter-spacing: 0;
}

.curation-select {
  width: 100%;
  min-width: 0;
}

.curation-select :deep(.p-select-label) {
  white-space: normal;
  overflow-wrap: anywhere;
}

.curation-note {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin: 0;
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
  background: color-mix(in srgb, var(--success) var(--status-surface-tint), var(--surface-sheet-solid));
  color: var(--success-text);
}
.source-badge--auto {
  background: var(--accent-bg);
  color: var(--accent-text);
}
.source-badge--failed {
  background: color-mix(in srgb, var(--error) var(--status-surface-tint), var(--surface-sheet-solid));
  color: var(--error-text);
}

.info-list--inline {
  padding: 0 0.5rem 0.25rem;
}

.info-list--inline .info-row {
  border-radius: 4px;
  padding: 0.3rem 0.5rem;
  border-top: 1px solid var(--border-subtle);
}

.info-val--muted {
  color: var(--text-muted);
  font-style: italic;
  font-size: var(--text-xs-size);
}

.info-drawer__summary {
  transition: color 0.15s ease;
}

.info-drawer__summary:hover {
  color: var(--text-secondary);
}

/* ── Import messages ── */
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
  color: var(--warning-text);
  cursor: pointer;
  text-decoration: underline;
  font-weight: 600;
  background: transparent;
  border: 0;
  font: inherit;
  min-height: 2.75rem;
  -webkit-user-select: text;
  user-select: text;
}
.track-link:hover {
  text-decoration: underline;
}

/* ── Mobile ── */
@container track-details (max-width: 480px) {
  .curation-row {
    grid-template-columns: 5rem minmax(0, 1fr);
    gap: 0.5rem;
  }
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .info-key {
    flex: 0 0 5.5rem;
  }
}
.quality-help {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: 1.5;
}
.quality-diagnostics {
  display: grid;
  gap: 0.5rem;
}
.quality-container .section-label {
  padding: 0.35rem 0 0;
}
.quality-container .info-drawer {
  margin: 0;
}
.load-messages {
  overflow-wrap: anywhere;
}
@container track-details (min-width: 700px) {
  .curation-panel {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0 1rem;
  }
  .curation-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 0.4rem;
  }
  .curation-row + .curation-row {
    border-top: 0;
  }
  .curation-note {
    grid-column: 1 / -1;
  }
}
</style>
