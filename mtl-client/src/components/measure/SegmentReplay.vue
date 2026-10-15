<template>
  <section class="segment-replay" aria-label="Replay selected attempts">
    <div class="replay-stage">
      <MiniMap :tracks-geo-json="geoJson" :map-bounds="bounds" fit-bounds-on-resize class="replay-map" />
      <div class="replay-controls">
        <Button size="small" :disabled="!ready" @click="playing = !playing">{{ playing ? 'Pause' : 'Play' }}</Button>
        <Button size="small" outlined @click="reset">Reset</Button>
        <label
          >Playback speed<select v-model.number="speed">
            <option v-for="option in SPEEDS" :key="option" :value="option">{{ option }}×</option>
          </select></label
        >
        <label><input v-model="showTrails" type="checkbox" />Trails</label>
      </div>
      <label class="replay-scrubber"
        >Replay position · {{ formatDuration(elapsed * 1000) }} / {{ formatDuration(duration * 1000)
        }}<input
          v-model.number="elapsed"
          type="range"
          min="0"
          :max="duration"
          step="any"
          :disabled="!ready"
          @input="playing = false"
      /></label>
      <p v-if="!ready" role="status">{{ SEGMENT_REPLAY_UNAVAILABLE_HINT }}</p>
    </div>
    <ol aria-label="Replay rankings" class="replay-rankings">
      <li v-for="racer in ranked" :key="racer.entry.attempt.identity.reference">
        <span :style="{ color: colors[racer.index] }" aria-hidden="true">●</span>
        <ActivityTypeBadge :type="racer.entry.track?.activityType" size="xs" />
        <span
          >{{ segmentTrackName(racer.entry.track, racer.entry.attempt.identity.trackId) }} ·
          {{ visitLabel(racer.entry.attempt)
          }}<small v-if="racer.entry.attempt.identity.start">{{
            formatDateCompact(racer.entry.attempt.identity.start)
          }}</small
          ><small
            >{{
              racer.finished ? 'Finished' : racer.position && racer.distance != null ? formatDistanceSmart(racer.distance) : 'Recording gap'
            }}
            · {{ formatDuration(Math.min(elapsed, racer.entry.attempt.elapsedSeconds ?? 0) * 1000) }}</small
          ></span
        >
      </li>
    </ol>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import Button from 'primevue/button';
import type { SegmentGeometryPoint } from 'x8ing-mtl-api-typescript-fetch';
import MiniMap from '@/components/map/MiniMap.vue';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';
import type { MiniMapGeoJson, MiniMapBounds } from '@/components/map/useMiniMap';
import type { AttemptData } from './useSegmentAttempts';
import { segmentHoverPosition, segmentTrackName, SEGMENT_REPLAY_UNAVAILABLE_HINT } from './segmentDisplay';
import { visitLabel } from './useAnalysisSession';
import { formatDateCompact, formatDuration, formatDistanceSmart } from '@/utils/Utils';
const props = withDefaults(
  defineProps<{ entries: AttemptData[]; colors: string[]; initialElapsedSeconds?: number }>(),
  {
    initialElapsedSeconds: 0,
  }
);
const SPEEDS = [1, 2, 5, 10, 20, 50, 100];
const FRAME_MS = 40;
const elapsed = ref(0),
  speed = ref(10),
  playing = ref(false),
  showTrails = ref(true);
let timer: ReturnType<typeof setInterval> | undefined;
let previousTime = 0;
const duration = computed(() => Math.max(0, ...props.entries.map((d) => d.attempt.elapsedSeconds ?? 0)));
const ready = computed(
  () =>
    props.entries.length > 0 &&
    props.entries.every((d) => d.core?.geometry?.length && d.attempt.identity.timingStatus === 'AVAILABLE' && d.attempt.distanceMeters != null) &&
    duration.value > 0
);
function reset() {
  playing.value = false;
  elapsed.value = 0;
}
watch(() => props.entries.map((d) => d.attempt.identity.reference).join('|'), reset);
watch(
  () => props.initialElapsedSeconds,
  (seconds) => {
    playing.value = false;
    elapsed.value = Number.isFinite(seconds) ? Math.max(0, Math.min(seconds, duration.value)) : 0;
  },
  { immediate: true }
);
watch(playing, (value) => {
  clearInterval(timer);
  if (!value) return;
  if (elapsed.value >= duration.value) elapsed.value = 0;
  previousTime = performance.now();
  timer = setInterval(() => {
    const now = performance.now();
    elapsed.value = Math.min(duration.value, elapsed.value + ((now - previousTime) / 1000) * speed.value);
    previousTime = now;
    if (elapsed.value >= duration.value) playing.value = false;
  }, FRAME_MS);
});
onBeforeUnmount(() => clearInterval(timer));
/** Interpolates only server-defined playback coordinates; breaks never become motion. */
function progressDistance(points: SegmentGeometryPoint[], seconds: number): number | null {
  let previous: SegmentGeometryPoint | undefined;
  for (const point of points) {
    if (point.elapsedSeconds == null) {
      previous = point;
      continue;
    }
    if (point.elapsedSeconds === seconds) return point.distanceMeters ?? null;
    if (point.elapsedSeconds > seconds) {
      if (!previous || point.breakBefore || previous.elapsedSeconds == null || previous.distanceMeters == null || point.distanceMeters == null) return null;
      const fraction = (seconds - previous.elapsedSeconds!) / (point.elapsedSeconds - previous.elapsedSeconds!);
      return previous.distanceMeters + (point.distanceMeters - previous.distanceMeters) * fraction;
    }
    previous = point;
  }
  return previous?.distanceMeters ?? null;
}
const racers = computed(() =>
  props.entries.map((entry, index) => {
    const end = entry.attempt.elapsedSeconds ?? 0;
    const seconds = Math.min(elapsed.value, end);
    const geometry = entry.core?.geometry ?? [];
    const distance = progressDistance(geometry, seconds);
    return {
      entry,
      index,
      distance,
      finished: elapsed.value >= end,
      position: segmentHoverPosition(geometry, { axis: 'time', x: seconds * 1000 }),
      ratio: distance == null ? null : distance / (entry.attempt.distanceMeters || 1),
    };
  })
);
const ranked = computed(() =>
  [...racers.value].sort((a, b) =>
    a.finished && b.finished
      ? (a.entry.attempt.elapsedSeconds ?? 0) - (b.entry.attempt.elapsedSeconds ?? 0)
      : a.finished
        ? -1
        : b.finished
          ? 1
          : (b.ratio ?? -1) - (a.ratio ?? -1) || a.index - b.index
  )
);
const geoJson = computed<MiniMapGeoJson>(() => {
  const features: MiniMapGeoJson['features'] = [];
  const ranks = new Map(ranked.value.map((racer, index) => [racer.index, index + 1]));
  for (const racer of racers.value) {
    const color = props.colors[racer.index];
    let path: number[][] = [],
      trail: number[][] = [];
    function flush() {
      if (path.length > 1)
        features.push({
          type: 'Feature',
          geometry: { type: 'LineString', coordinates: path },
          properties: { color, type: 'track', opacity: 0.3 },
        });
      if (showTrails.value && trail.length > 1)
        features.push({
          type: 'Feature',
          geometry: { type: 'LineString', coordinates: trail },
          properties: { color, type: 'trail' },
        });
      path = [];
      trail = [];
    }
    for (const point of racer.entry.core?.geometry ?? []) {
      if (point.breakBefore) flush();
      if (point.longitude == null || point.latitude == null) {
        flush();
        continue;
      }
      const coordinate = [point.longitude, point.latitude];
      path.push(coordinate);
      if (point.elapsedSeconds != null && point.elapsedSeconds <= elapsed.value) trail.push(coordinate);
    }
    flush();
    if (racer.position)
      features.push({
        type: 'Feature',
        id: racer.entry.attempt.identity.reference,
        geometry: { type: 'Point', coordinates: racer.position },
        properties: {
          color,
          type: 'racer',
          trackIndex: racer.index,
          trackName: segmentTrackName(racer.entry.track, racer.entry.attempt.identity.trackId),
          name: visitLabel(racer.entry.attempt),
          label: String(ranks.get(racer.index)),
          popupTitle: `#${ranks.get(racer.index)} · ${segmentTrackName(racer.entry.track, racer.entry.attempt.identity.trackId)}`,
          popupDetails: [
            [visitLabel(racer.entry.attempt), formatDateCompact(racer.entry.attempt.identity.start)]
              .filter(Boolean)
              .join(' · '),
            `Elapsed time: ${formatDuration(Math.min(elapsed.value, racer.entry.attempt.elapsedSeconds ?? 0) * 1000)} / ${formatDuration((racer.entry.attempt.elapsedSeconds ?? 0) * 1000)}`,
            racer.distance == null ? 'Distance unavailable' : `Distance traveled: ${formatDistanceSmart(racer.distance)}${racer.entry.attempt.distanceMeters == null ? '' : ` / ${formatDistanceSmart(racer.entry.attempt.distanceMeters)}`}`,
            racer.finished ? 'Finished' : '',
          ]
            .filter(Boolean)
            .join('\n'),
        },
      });
  }
  return { type: 'FeatureCollection', features };
});
const bounds = computed<MiniMapBounds | null>(() => {
  const points = props.entries
    .flatMap((d) => d.core?.geometry ?? [])
    .filter((p) => p.longitude != null && p.latitude != null);
  if (!points.length) return null;
  return [
    [Math.min(...points.map((p) => p.longitude!)), Math.min(...points.map((p) => p.latitude!))],
    [Math.max(...points.map((p) => p.longitude!)), Math.max(...points.map((p) => p.latitude!))],
  ];
});
</script>
<style scoped>
.segment-replay {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
  min-width: 0;
}
.replay-stage {
  min-width: 0;
}
.replay-map {
  height: 320px;
  width: 100%;
}
.replay-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin: 0.6rem 0;
}
.replay-controls select {
  font: inherit;
  border: 1px solid var(--border-default);
  border-radius: 0.4rem;
  color: var(--text-primary);
  background: var(--surface-card);
  padding: 0.4rem;
}
.replay-controls select:focus-visible,
.replay-scrubber input:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.replay-controls label {
  display: flex;
  align-items: center;
  gap: 0.3rem;
}
.replay-scrubber {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
.replay-scrubber input {
  width: 100%;
  min-height: 32px;
}
.replay-rankings {
  margin: 0;
  padding-left: 1.5rem;
}
.replay-rankings li {
  padding: 0.4rem;
  overflow-wrap: anywhere;
}
.replay-rankings small {
  display: block;
  color: var(--text-secondary);
}
@container segment-workspace (min-width: 840px) {
  .segment-replay {
    grid-template-columns: minmax(0, 1fr) minmax(19rem, 32%);
  }
}
@media (max-width: 600px) {
  .replay-map {
    height: 260px;
  }
  .replay-controls,
  .replay-scrubber,
  .replay-rankings {
    font-size: var(--text-sm-size);
  }
}
@media (pointer: coarse) {
  .replay-controls button,
  .replay-controls select {
    min-height: 44px;
  }
}
</style>
