<template>
  <section class="metabolic-energy" aria-label="Estimated calories" data-test="metabolic-energy">
    <template v-if="energy?.activeKcal != null">
      <div class="metabolic-energy__totals">
        <div>
          <strong data-test="total-calories">{{ formatNumber(energy.totalKcal, 0) }} kcal</strong>
          <span>Including resting energy</span>
        </div>
      </div>
    </template>
    <p v-else data-test="calorie-unavailable">
      {{ pending ? 'Calorie estimate pending.' : 'Calorie estimate unavailable.' }}
    </p>
    <details v-if="energy" class="metabolic-energy__details">
      <summary>How calories are estimated</summary>
      <p>Active calories are what you burned on top of resting. Total calories include resting. Stops count as rest.</p>
      <p>{{ energy.methodDescription }}</p>
      <p>
        Body weight: {{ energy.bodyWeightKg != null ? formatMass(energy.bodyWeightKg, 1) : '—' }}. These are
        population-based estimates, not measured calorie expenditure.
      </p>
      <template v-if="energy.coverage !== 'COMPLETE'">
        <p>
          {{ formatDurationSmart((energy.coveredSeconds ?? 0) * 1000) }} covered,
          {{ formatDurationSmart((energy.uncoveredSeconds ?? 0) * 1000) }} excluded.
          <span v-if="energy.unknownDuration">Additional excluded intervals have unknown duration.</span>
        </p>
        <p v-if="(energy.recordingGapSeconds ?? 0) > 0">
          Recording gaps: {{ formatDurationSmart((energy.recordingGapSeconds ?? 0) * 1000) }}.
        </p>
        <p v-if="(energy.sparseSamplingSeconds ?? 0) > 0">
          Sparse sampling: {{ formatDurationSmart((energy.sparseSamplingSeconds ?? 0) * 1000) }}.
        </p>
        <ul v-if="energy.exclusionReasons?.length" aria-label="Reasons for excluded intervals">
          <li v-for="reason in energy.exclusionReasons" :key="reason">{{ exclusionLabels[reason] }}</li>
        </ul>
      </template>
    </details>
  </section>
</template>

<script setup lang="ts">
import type { MetabolicEnergy } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { formatDurationSmart, formatMass, formatNumber } from '@/utils/Utils';

defineProps<{ energy?: MetabolicEnergy | null; pending?: boolean }>();
const exclusionLabels: Record<NonNullable<MetabolicEnergy['exclusionReasons']>[number], string> = {
  ACTIVITY_REQUIRED: 'An activity type is required.',
  INVALID_BODY_WEIGHT: 'Body weight is outside the supported range.',
  INSUFFICIENT_DATA: 'There are too few supported observations.',
  MISSING_TIMING: 'Some timestamps are missing or invalid.',
  RECORDING_GAP: 'Intervals across recording breaks are excluded.',
  SPARSE_SAMPLING: 'Some observations are too far apart in time to determine motion.',
  UNSUPPORTED_MOTION: 'Some intervals have uncertain motion or unsupported continuity.',
  MISSING_ELEVATION: 'Some moving intervals lack usable slope or elevation data.',
  INSUFFICIENT_DISTANCE: 'Some moving intervals have too little horizontal distance to determine slope.',
  GRADE_OUTSIDE_MODEL: 'Some slopes are outside the walking or running model range of −45% to +45%.',
  INCONSISTENT_ENERGY_ESTIMATES:
    'Some water-sport parts are left out because their estimated work is higher than their calorie estimate.',
  UNSUPPORTED_MECHANICAL_POWER: 'Some intervals lack usable mechanical power or exceed its limit.',
};
</script>

<style scoped>
.metabolic-energy {
  margin-block: 1rem;
}
.metabolic-energy__totals {
  display: grid;
  gap: 1rem;
}
.metabolic-energy__totals div {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.metabolic-energy__totals strong {
  font-size: 1.2rem;
  font-variant-numeric: tabular-nums;
}
.metabolic-energy__totals span,
.metabolic-energy p {
  color: var(--text-secondary);
  font-size: 0.875rem;
}
.metabolic-energy__details {
  margin-top: 0.75rem;
  font-size: 0.875rem;
}
.metabolic-energy__details summary {
  min-height: 2.75rem;
  align-content: center;
  cursor: pointer;
}
.metabolic-energy p {
  margin-block: 0.5rem;
  line-height: 1.5;
}
</style>
