<template>
  <div class="segment-cardiac-cost" data-test="segment-cardiac-cost" aria-live="polite">
    <div class="segment-cardiac-cost__heading">
      <span><i class="bi bi-heart-pulse" aria-hidden="true"></i> Cardiac cost</span>
      <strong v-if="summary?.available && summary.beatsPerKj != null" class="segment-cardiac-cost__values">
        <span class="segment-cardiac-cost__unit">{{ formatNumber(summary.beatsPerKj, 1) }} beats/kJ</span>
        <span v-if="summary.wattsPerBpm != null" class="segment-cardiac-cost__unit">
          <span aria-hidden="true"> · </span>
          <abbr :title="POWER_PER_HEART_RATE_DESCRIPTION" data-test="watts-per-bpm">
            {{ formatNumber(summary.wattsPerBpm, 2) }} W/bpm
          </abbr>
        </span>
      </strong>
    </div>
    <span v-if="loading">Calculating…</span>
    <span v-else-if="loadFailed">
      Could not load.
      <button class="segment-cardiac-cost__retry" @click.stop="emit('retry')">Retry</button>
    </span>
    <template v-else-if="summary">
      <span v-if="!summary.available">Unavailable: {{ unavailableReason }}</span>
      <span v-if="(summary.movingSeconds ?? 0) > 0" data-test="cardiac-cost-coverage">
        {{ formatNumber(summary.coveragePercent, 1) }}% paired coverage ·
        {{ formatDuration((summary.coveredMovingSeconds ?? 0) * 1000) }} of
        {{ formatDuration((summary.movingSeconds ?? 0) * 1000) }} moving
      </span>
      <details @click.stop>
        <summary>How it is calculated</summary>
        <p>Heartbeats per unit of estimated work while moving, including coasting. Stops and GPS gaps are left out.</p>
        <p v-if="(summary.coveredMovingSeconds ?? 0) > 0">
          {{ formatNumber(summary.estimatedHeartbeats, 0) }} estimated beats /
          {{ formatNumber(summary.mechanicalEnergyKj, 2) }} kJ.
        </p>
        <p>{{ POWER_PER_HEART_RATE_DESCRIPTION }}</p>
        <p>Lower beats/kJ means fewer heartbeats for the same work. It is not a fitness or efficiency score.</p>
      </details>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { SegmentCardiacCostSummary } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { formatDuration, formatNumber } from '@/utils/Utils';

const props = defineProps<{ summary?: SegmentCardiacCostSummary; loading?: boolean; loadFailed?: boolean }>();
const emit = defineEmits<{ retry: [] }>();
const POWER_PER_HEART_RATE_DESCRIPTION =
  'W/bpm is average estimated power divided by average heart rate while moving. Higher W/bpm is the same as lower beats/kJ.';
const unavailableReason = computed(() => {
  switch (props.summary?.unavailableReason) {
    case 'NO_MOVING_DATA':
      return 'no usable moving intervals.';
    case 'NO_MATCHED_DATA':
      return 'no overlapping heart-rate and energy coverage.';
    case 'INSUFFICIENT_ENERGY':
      return `at least ${formatNumber(props.summary.minimumEnergyKj, 1)} kJ of covered work is needed.`;
    case 'MISSING_TIMESTAMPS':
      return 'segment timestamps are missing.';
    case 'MOTORIZED_ACTIVITY':
      return 'not applicable to motorized activities.';
    default:
      return 'not enough matching data.';
  }
});
</script>

<style scoped>
.segment-cardiac-cost {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  border-top: 1px solid var(--border-subtle);
  padding-top: 0.45rem;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  cursor: default;
}
.segment-cardiac-cost__heading {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.3rem 0.6rem;
}
.segment-cardiac-cost__heading strong {
  color: var(--text-primary);
}
.segment-cardiac-cost__values {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0 0.3rem;
}
.segment-cardiac-cost__unit {
  white-space: nowrap;
}
.segment-cardiac-cost__unit abbr {
  text-decoration: underline dotted;
  text-underline-offset: 0.2em;
  cursor: help;
}
.segment-cardiac-cost summary {
  cursor: pointer;
}
.segment-cardiac-cost p {
  margin: 0.35rem 0 0;
}
.segment-cardiac-cost__retry {
  border: none;
  background: transparent;
  color: var(--accent-text);
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
</style>
