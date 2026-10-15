<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import {
  TrackQualityControllerApi,
  type TrackObservation,
  type TrackQualityReport,
  type ReviewedTrack,
  type TrackFinding,
} from 'x8ing-mtl-api-typescript-fetch';
import { getApiConfiguration } from '@/utils/openApiClient';
import ReviewMap from './ReviewMap.vue';
import { ORIGINAL_TRACK_COLOR } from './colors';
import { reviewApi, reviewError } from './api';
import {
  FINDING_CONTEXT_POINTS,
  belongsToFindingSection,
  findingPointRange,
  hasMapPosition,
  type FindingMapFocus,
} from './findingLocation';

const props = defineProps<{
  points: TrackObservation[];
  tracks: ReviewedTrack[];
  sourceHash?: string;
  sourceId?: number;
}>();
const map = ref<InstanceType<typeof ReviewMap> | null>(null);
const focus = shallowRef<FindingMapFocus | null>(null);
const focusLoading = ref(false),
  focusError = ref(''),
  focusNotice = ref('');
let focusRequest: AbortController | undefined;

function resetFocus() {
  focusRequest?.abort();
  focus.value = null;
  focusLoading.value = false;
  focusError.value = '';
  focusNotice.value = '';
}
watch(() => [props.sourceId, props.sourceHash], resetFocus);

async function centerFinding(finding: TrackFinding, pointIndex = finding.firstPointIndex, reveal = true) {
  const range = findingPointRange(finding);
  if (!range || pointIndex == null) return;
  focusRequest?.abort();
  const current = new AbortController();
  focusRequest = current;
  focusLoading.value = true;
  focusError.value = '';
  focusNotice.value = '';
  if (reveal) map.value?.reveal();
  try {
    let points = focus.value?.finding === finding ? focus.value.points : props.points;
    if (focus.value?.finding !== finding && props.sourceId != null && props.sourceHash) {
      const windows: { from: number; limit: number }[] = [];
      for (const index of [range.before, range.after].sort((a, b) => a - b)) {
        const from = Math.max(0, index - FINDING_CONTEXT_POINTS);
        const limit = FINDING_CONTEXT_POINTS * 2 + 1;
        const previous = windows.at(-1);
        if (previous && from <= previous.from + previous.limit)
          previous.limit = Math.max(previous.limit, from + limit - previous.from);
        else windows.push({ from, limit });
      }
      const pages = await Promise.all(
        windows.map(({ from, limit }) =>
          reviewApi().getSourceReviewPoints(
            { sourceId: props.sourceId!, sourceHash: props.sourceHash!, from, limit },
            { signal: current.signal }
          )
        )
      );
      if (current.signal.aborted) return;
      const unique = new Map(pages.flatMap((page) => page.points ?? []).map((point) => [point.pointIndex, point]));
      points = [...unique.values()].sort((a, b) => (a.pointIndex ?? 0) - (b.pointIndex ?? 0));
    }
    const candidates = points.filter((point) => hasMapPosition(point) && belongsToFindingSection(point, finding));
    let point = candidates.find((point) => point.pointIndex === pointIndex);
    if (!point) {
      point = candidates
        .filter(
          (point) => point.pointIndex != null && Math.abs(point.pointIndex - pointIndex) <= FINDING_CONTEXT_POINTS
        )
        .sort((a, b) => Math.abs(a.pointIndex! - pointIndex) - Math.abs(b.pointIndex! - pointIndex))[0];
      if (point)
        focusNotice.value = `Point ${pointIndex + 1} has no usable GPS position. Centered on nearby point ${point.pointIndex! + 1}.`;
    }
    if (!point) {
      focus.value = null;
      focusError.value = 'No usable GPS position near this finding.';
      return;
    }
    focus.value = { finding, points, pointIndex: point.pointIndex! };
    await nextTick();
    if (!current.signal.aborted) {
      if (reveal) map.value?.reveal();
      await map.value?.refit();
    }
  } catch (failure) {
    if (!current.signal.aborted) {
      const text = await reviewError(failure);
      if (!current.signal.aborted) focusError.value = text;
    }
  } finally {
    if (focusRequest === current) focusLoading.value = false;
  }
}
defineExpose({ centerFinding });
const reports = ref<TrackQualityReport[]>([]),
  loading = ref(false),
  error = ref('');
let request: AbortController | undefined;
async function load() {
  request?.abort();
  const current = new AbortController();
  request = current;
  reports.value = [];
  error.value = '';
  loading.value = false;
  if (!props.sourceHash || !props.tracks.length) return;
  loading.value = true;
  const hash = props.sourceHash;
  const api = new TrackQualityControllerApi(getApiConfiguration());
  const results = await Promise.allSettled(
    props.tracks.map((track) => api.getTrackQuality({ trackId: track.trackId! }, { signal: current.signal }))
  );
  if (current.signal.aborted) return;
  const loaded = results.flatMap((result) => (result.status === 'fulfilled' ? [result.value] : []));
  reports.value = loaded.filter((report) => report.sourceHash === hash);
  if (loaded.some((report) => report.sourceHash !== hash))
    error.value = 'Some tracks come from an older version of this file. Re-import it to compare them.';
  else if (results.some((result) => result.status === 'rejected'))
    error.value = 'Some processed paths could not be loaded. Try again.';
  loading.value = false;
}
watch(
  () => [props.sourceHash, props.tracks] as const,
  () => void load(),
  { immediate: true }
);
onBeforeUnmount(() => {
  request?.abort();
  focusRequest?.abort();
});
</script>
<template>
  <ReviewMap
    ref="map"
    :points="points"
    :color="ORIGINAL_TRACK_COLOR"
    label="Original source · red"
    :processed="reports"
    :focus="focus"
    @reset="resetFocus"
    @center="focus && centerFinding(focus.finding, $event)"
  />
  <p v-if="focusLoading" role="status">Loading issue location…</p>
  <p v-if="focusError" role="alert">{{ focusError }}</p>
  <p v-if="focusNotice" role="status">{{ focusNotice }}</p>
  <p v-if="loading" role="status">Loading processed paths…</p>
  <p v-if="error" role="alert">{{ error }} <button type="button" @click="load">Retry comparison</button></p>
  <p v-if="reports.length">
    Compare the original recording with the processed track. Gaps are not joined in the processed track.
  </p>
</template>
