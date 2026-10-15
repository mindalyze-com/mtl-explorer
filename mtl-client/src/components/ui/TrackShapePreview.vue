<template>
  <div ref="containerRef" class="track-shape-preview" :style="{ width: width + 'px', height: height + 'px' }">
    <svg v-if="svgPath" :viewBox="viewBox" class="track-shape-preview__svg" preserveAspectRatio="xMidYMid meet">
      <path
        :d="svgPath"
        fill="none"
        :stroke="lineColor"
        :stroke-width="lineWidth"
        stroke-linecap="round"
        stroke-linejoin="round"
        vector-effect="non-scaling-stroke"
      />
    </svg>
  </div>
</template>

<script setup lang="ts">
import { warnSanitized } from '@/utils/safeLogging';

import { ref, onMounted, onBeforeUnmount, watch } from 'vue';
import { geoMercator, geoPath } from 'd3-geo';
import { fetchDetailTrackAtPrecision, readBestCachedTrackShape } from '@/utils/tracks/trackCollectionLoader';
import { DETAIL_TRACK_PRECISION } from '@/utils/tracks/trackConstants';
import { isAbortLikeError } from '@/utils/errors';
import { buildTrackGeometry } from '@/utils/tracks/trackFeatureAdapter';
import type { TrackPrecisionResult } from '@/utils/tracks/trackTypes';

const props = withDefaults(
  defineProps<{
    trackId: number;
    width?: number;
    height?: number;
    lineColor?: string;
    lineWidth?: number;
    /** Padding in SVG user units around the fitted shape */
    padding?: number;
  }>(),
  {
    width: 80,
    height: 60,
    lineColor: '#4c4fcd',
    lineWidth: 2,
    padding: 6,
  }
);

const containerRef = ref<HTMLElement | null>(null);
const svgPath = ref<string | null>(null);
const viewBox = ref('0 0 80 60');

let observer: IntersectionObserver | null = null;
let loaded = false;
let abortController: AbortController | null = null;

function buildSvgPath(result: TrackPrecisionResult) {
  const { coordinates, gpsTrack, recordingBreakBeforePointIndexes } = result;
  if (coordinates.length < 2) {
    svgPath.value = null;
    return false;
  }

  const geojson: GeoJSON.Feature = {
    type: 'Feature',
    geometry: buildTrackGeometry(gpsTrack, coordinates, {
      collapseShortTracks: false,
      recordingBreakBeforePointIndexes,
    }),
    properties: {},
  };
  if (geojson.geometry.type === 'MultiLineString' && !geojson.geometry.coordinates.length) {
    svgPath.value = null;
    return false;
  }

  // Compute bounding box of coordinates
  let minLng = Infinity,
    maxLng = -Infinity,
    minLat = Infinity,
    maxLat = -Infinity;
  for (const c of coordinates) {
    if (c[0] < minLng) minLng = c[0];
    if (c[0] > maxLng) maxLng = c[0];
    if (c[1] < minLat) minLat = c[1];
    if (c[1] > maxLat) maxLat = c[1];
  }

  // Coarse simplification can collapse a closed loop to identical endpoints.
  // Fitting that zero-size extent produces an infinite scale and NaN SVG points.
  if (!Number.isFinite(minLng + maxLng + minLat + maxLat) || (minLng === maxLng && minLat === maxLat)) {
    svgPath.value = null;
    return false;
  }

  const pad = props.padding;
  const drawW = props.width - pad * 2;
  const drawH = props.height - pad * 2;

  // Use d3 geoMercator projection fitted to our SVG dimensions
  const projection = geoMercator().fitExtent(
    [
      [pad, pad],
      [pad + drawW, pad + drawH],
    ],
    geojson
  );

  const pathGenerator = geoPath(projection);
  svgPath.value = pathGenerator(geojson) || null;
  viewBox.value = `0 0 ${props.width} ${props.height}`;
  return !!svgPath.value;
}

async function loadAndRender() {
  if (loaded || abortController) return;

  const request = new AbortController();
  abortController = request;
  const trackId = props.trackId;
  try {
    const result = await readBestCachedTrackShape(trackId, {
      signal: request.signal,
    });
    if (request.signal.aborted) return;

    if (!result || !buildSvgPath(result)) {
      const detail = await fetchDetailTrackAtPrecision(trackId, DETAIL_TRACK_PRECISION, request.signal);
      if (request.signal.aborted) return;
      buildSvgPath(detail);
    }
    loaded = true;
    observer?.disconnect();
    observer = null;
  } catch (e: unknown) {
    if (isAbortLikeError(e, request.signal)) return;
    warnSanitized('Could not load track shape preview:', e, {
      topic1: 'tracks',
      topic2: 'TrackShapePreview',
      topic3: 'loadAndRender',
    });
  } finally {
    if (abortController === request) abortController = null;
  }
}

onMounted(() => {
  if (!containerRef.value) return;

  // Use IntersectionObserver for lazy loading — only load when visible
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) {
        loadAndRender();
      }
    },
    { rootMargin: '100px' } // Start loading slightly before visible
  );
  observer.observe(containerRef.value);
});

onBeforeUnmount(() => {
  observer?.disconnect();
  abortController?.abort();
});

// Re-render if trackId changes (e.g., reuse in v-for)
watch(
  () => props.trackId,
  () => {
    abortController?.abort();
    abortController = null;
    loaded = false;
    svgPath.value = null;
    loadAndRender();
  }
);
</script>

<style scoped>
.track-shape-preview {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.track-shape-preview__svg {
  width: 100%;
  height: 100%;
  opacity: 0;
  animation: track-shape-fadein 0.3s ease forwards;
}

@keyframes track-shape-fadein {
  to {
    opacity: 1;
  }
}
</style>
