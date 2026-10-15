<script setup lang="ts">
import { computed } from 'vue';
import type { TrackDiagnosis, TrackFinding } from 'x8ing-mtl-api-typescript-fetch';
import { reasonLabel } from './api';
import FindingMarker from './FindingMarker.vue';
import { findingPointRange } from './findingLocation';
const props = defineProps<{ diagnosis?: TrackDiagnosis; inspectable?: boolean }>();
defineEmits<{ select: [finding: TrackFinding]; center: [finding: TrackFinding, pointIndex: number] }>();
function centerPoints(finding: TrackFinding) {
  const range = findingPointRange(finding);
  return range ? [...new Set([range.before, range.after])] : [];
}
const groups = computed(() => [
  {
    information: false,
    findings: (props.diagnosis?.findings ?? [])
      .filter((f) => f.severity !== 'INFORMATION')
      .sort((a, b) => Number(b.severity === 'REVIEW') - Number(a.severity === 'REVIEW')),
  },
  { information: true, findings: (props.diagnosis?.findings ?? []).filter((f) => f.severity === 'INFORMATION') },
]);
const stages: Record<string, string> = {
  READ: 'Reading',
  CONVERSION: 'Conversion',
  PARSING: 'Parsing',
  TIMING: 'Timestamps',
  POSITION: 'GPS positions',
  ELEVATION: 'Elevation',
  PROCESSING: 'Processing',
};
</script>
<template>
  <template v-if="diagnosis?.findings?.length">
    <component
      :is="group.information ? 'details' : 'section'"
      v-for="group in groups.filter((g) => g.findings.length)"
      :key="String(group.information)"
      :class="{ 'source-information': group.information }"
    >
      <summary v-if="group.information">Information ({{ group.findings.length }})</summary>
      <h3 v-else>Review findings</h3>
      <p v-if="group.information">
        These findings do not require review.
        <template v-if="group.findings.some((finding) => finding.reason === 'ELEVATION_MISSING')"
          >Missing elevation is allowed; elevation statistics are unavailable where altitude is missing.</template
        >
      </p>
      <ul class="source-findings">
        <li
          v-for="(finding, index) in group.findings"
          :key="index"
          :class="{ 'source-findings__serious': finding.severity === 'REVIEW' }"
        >
          <div class="source-findings__heading">
            <strong>
              <FindingMarker
                v-if="finding.severity !== 'INFORMATION'"
                :label="finding.reason ? reasonLabel(finding.reason) : 'Selected finding'"
                :description="finding.message"
              />{{
                finding.reason ? reasonLabel(finding.reason) : (stages[finding.stage ?? ''] ?? finding.stage)
              }}</strong
            ><span>{{
              finding.severity === 'REVIEW'
                ? 'Serious finding'
                : finding.severity === 'WARNING'
                  ? 'Check'
                  : 'Information'
            }}</span>
          </div>
          <small v-if="finding.sectionIndex != null">{{
            finding.sectionIndex < 0 ? 'Whole source' : `Section ${finding.sectionIndex + 1}`
          }}</small>
          <p>{{ finding.message }}</p>
          <small v-if="finding.firstPointIndex != null"
            >Source point {{ finding.firstPointIndex + 1
            }}<template v-if="finding.lastPointIndex != null && finding.lastPointIndex !== finding.firstPointIndex"
              >–{{ finding.lastPointIndex + 1 }}</template
            >.</small
          >
          <p v-if="finding.affectedCount || finding.magnitude != null" class="source-findings__evidence">
            <span v-if="finding.affectedCount">Affected: {{ finding.affectedCount.toLocaleString() }}.</span>
            <span v-if="finding.magnitude != null">
              Measured value: {{ finding.magnitude.toLocaleString(undefined, { maximumFractionDigits: 2 }) }}
              {{ finding.unit }}.</span
            >
          </p>
          <small v-if="finding.affectedCapabilities?.length"
            >Affects: {{ finding.affectedCapabilities.join(', ') }}.</small
          >
          <div v-if="inspectable && finding.firstPointIndex != null" class="source-findings__actions">
            <button type="button" @click="$emit('select', finding)">
              <template
                v-if="
                  (finding.reason === 'TIME_BACKWARD' || finding.reason === 'TIME_DUPLICATE') &&
                  finding.firstPointIndex > 0
                "
                >Inspect transition {{ finding.firstPointIndex }} → {{ finding.firstPointIndex + 1 }}</template
              >
              <template v-else-if="finding.reason === 'RECORDING_GAP'"
                >Inspect transition {{ finding.firstPointIndex + 1 }} → {{ finding.firstPointIndex + 2 }}</template
              >
              <template
                v-else-if="
                  finding.reason === 'POSITION_JUMP' &&
                  (finding.affectedCount ?? 1) === 1 &&
                  finding.lastPointIndex != null &&
                  finding.lastPointIndex !== finding.firstPointIndex
                "
                >Inspect transition {{ finding.firstPointIndex + 1 }} → {{ finding.lastPointIndex + 1 }}</template
              >
              <template v-else>Inspect point {{ finding.firstPointIndex + 1 }}</template>
            </button>
            <button
              v-for="pointIndex in centerPoints(finding)"
              :key="pointIndex"
              type="button"
              :aria-label="`Center on map at point ${pointIndex + 1}: ${finding.reason ? reasonLabel(finding.reason) : 'Selected finding'}`"
              :title="`Center on point ${pointIndex + 1}`"
              @click="$emit('center', finding, pointIndex)"
            >
              <i class="bi bi-crosshair" aria-hidden="true"></i>
              {{ centerPoints(finding).length > 1 ? `Center point ${pointIndex + 1}` : 'Center on map' }}
            </button>
          </div>
        </li>
      </ul>
    </component>
    <p v-if="!groups[0]?.findings.length">No findings require review.</p>
  </template>
  <p v-else-if="diagnosis">No findings require review.</p>
  <p v-else>Assess this source to see its diagnosis.</p>
</template>
<style scoped>
h3 {
  font-size: 1rem;
  margin: 1.25rem 0 0.5rem;
}
.source-information {
  margin: 1rem 0;
  color: var(--text-secondary);
}
summary {
  cursor: pointer;
}
.source-findings {
  list-style: none;
  padding: 0;
  display: grid;
  gap: 0.65rem;
}
.source-findings li {
  padding: 0.75rem;
  border: 1px solid var(--border-light);
  border-radius: 8px;
}
.source-findings .source-findings__serious {
  border-left: 3px solid #b45309;
}
.source-findings__heading {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}
.source-findings__heading > span,
small,
.source-findings__evidence {
  color: var(--text-secondary);
  font-size: 0.8rem;
}
p {
  margin: 0.4rem 0;
}
button {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-height: 2.75rem;
  border: 0;
  padding: 0.3rem 0;
  background: none;
  color: var(--primary-color);
  cursor: pointer;
}
.source-findings__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0 1rem;
}
button:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}
</style>
