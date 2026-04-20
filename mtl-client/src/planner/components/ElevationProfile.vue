<template>
  <div class="elev-profile" v-if="hasData">
    <div class="elev-profile__header">
      <span class="elev-profile__title"><i class="bi bi-graph-up"></i> Elevation</span>
      <div class="elev-profile__totals">
        <span><i class="bi bi-arrow-up-right"></i> {{ totals.ascent }} m</span>
        <span><i class="bi bi-arrow-down-right"></i> {{ totals.descent }} m</span>
        <span class="elev-profile__totals-range">{{ totals.minEle }}–{{ totals.maxEle }} m</span>
      </div>
    </div>
    <svg
      ref="svgEl"
      class="elev-profile__svg"
      :viewBox="`0 0 ${vbWidth} ${vbHeight}`"
      preserveAspectRatio="none"
      @pointermove="onPointerMove"
      @pointerleave="onPointerLeave"
      @pointerdown="onPointerMove"
    >
      <!-- Gradient fill below the line -->
      <defs>
        <linearGradient :id="gradientId" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#ff5722" stop-opacity="0.45"/>
          <stop offset="100%" stop-color="#ff5722" stop-opacity="0.02"/>
        </linearGradient>
      </defs>

      <!-- Filled area -->
      <path :d="areaPath" :fill="`url(#${gradientId})`" />
      <!-- Line on top -->
      <path :d="linePath" fill="none" stroke="#ff5722" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round"/>

      <!-- Hover indicator -->
      <g v-if="hover">
        <line :x1="hover.x" :y1="0" :x2="hover.x" :y2="vbHeight" stroke="rgba(0,0,0,0.35)" stroke-width="0.5" stroke-dasharray="2 2"/>
        <circle :cx="hover.x" :cy="hover.y" r="3" fill="#ff5722" stroke="#fff" stroke-width="1"/>
      </g>
    </svg>
    <div v-if="hover" class="elev-profile__readout">
      <span><strong>{{ (hover.distanceM / 1000).toFixed(2) }} km</strong></span>
      <span>{{ Math.round(hover.elevationM) }} m</span>
      <span :class="{ pos: hover.gradePct > 0, neg: hover.gradePct < 0 }">
        {{ hover.gradePct > 0 ? '+' : '' }}{{ hover.gradePct.toFixed(1) }}%
      </span>
    </div>
  </div>
  <div v-else class="elev-profile elev-profile--empty">
    <span class="elev-profile__placeholder"><i class="bi bi-graph-up"></i> Elevation profile appears once a route is computed.</span>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

interface HoverInfo {
  x: number;
  y: number;
  distanceM: number;
  elevationM: number;
  gradePct: number;
  /** Index into the input coordinates array. */
  index: number;
  /** [lng, lat] for the map cursor sync. */
  lng: number;
  lat: number;
}

const props = defineProps<{
  /** Route polyline as [lng, lat, elevationM] triples. */
  coordinates: [number, number, number][];
}>();

const emit = defineEmits<{
  (e: 'hover', point: { lng: number; lat: number; elevationM: number; distanceM: number } | null): void;
}>();

// SVG viewBox dimensions — kept in arbitrary units; preserveAspectRatio=none stretches to container.
const vbWidth = 600;
const vbHeight = 110;
const padX = 4;
const padY = 6;
const gradientId = `elev-grad-${Math.random().toString(36).slice(2, 8)}`;

const svgEl = ref<SVGSVGElement | null>(null);
const hover = ref<HoverInfo | null>(null);

interface Sample {
  distanceM: number;
  elevationM: number;
  lng: number;
  lat: number;
}

/** Densified samples (one per coordinate) with cumulative distance. */
const samples = computed<Sample[]>(() => {
  const out: Sample[] = [];
  const coords = props.coordinates;
  if (!coords || coords.length < 2) return out;
  let cum = 0;
  out.push({ distanceM: 0, elevationM: coords[0][2] || 0, lng: coords[0][0], lat: coords[0][1] });
  for (let i = 1; i < coords.length; i++) {
    cum += haversine(coords[i - 1][1], coords[i - 1][0], coords[i][1], coords[i][0]);
    out.push({ distanceM: cum, elevationM: coords[i][2] || 0, lng: coords[i][0], lat: coords[i][1] });
  }
  return out;
});

const hasData = computed(() => samples.value.length >= 2);

const bounds = computed(() => {
  if (!hasData.value) return { minEle: 0, maxEle: 0, totalM: 0 };
  let minEle = Infinity, maxEle = -Infinity;
  for (const s of samples.value) {
    if (s.elevationM < minEle) minEle = s.elevationM;
    if (s.elevationM > maxEle) maxEle = s.elevationM;
  }
  // Avoid a flat line at 0-height by giving min/max at least 10m apart.
  if (maxEle - minEle < 10) {
    const mid = (minEle + maxEle) / 2;
    minEle = mid - 5;
    maxEle = mid + 5;
  }
  const totalM = samples.value[samples.value.length - 1].distanceM;
  return { minEle, maxEle, totalM };
});

const totals = computed(() => {
  let ascent = 0, descent = 0;
  for (let i = 1; i < samples.value.length; i++) {
    const dz = samples.value[i].elevationM - samples.value[i - 1].elevationM;
    if (dz > 0) ascent += dz;
    else descent += -dz;
  }
  return {
    ascent: Math.round(ascent),
    descent: Math.round(descent),
    minEle: Math.round(bounds.value.minEle),
    maxEle: Math.round(bounds.value.maxEle),
  };
});

function project(s: Sample): { x: number; y: number } {
  const { minEle, maxEle, totalM } = bounds.value;
  const usableW = vbWidth - padX * 2;
  const usableH = vbHeight - padY * 2;
  const x = padX + (totalM > 0 ? (s.distanceM / totalM) * usableW : 0);
  const y = padY + (1 - (s.elevationM - minEle) / Math.max(1, maxEle - minEle)) * usableH;
  return { x, y };
}

const linePath = computed(() => {
  if (!hasData.value) return '';
  return samples.value
    .map((s, i) => {
      const { x, y } = project(s);
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(' ');
});

const areaPath = computed(() => {
  if (!hasData.value) return '';
  const first = project(samples.value[0]);
  const last = project(samples.value[samples.value.length - 1]);
  return `M${first.x.toFixed(2)},${vbHeight - padY} ${samples.value
    .map((s) => {
      const { x, y } = project(s);
      return `L${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(' ')} L${last.x.toFixed(2)},${vbHeight - padY} Z`;
});

function onPointerMove(ev: PointerEvent) {
  const svg = svgEl.value;
  if (!svg || !hasData.value) return;
  const rect = svg.getBoundingClientRect();
  const xInVb = ((ev.clientX - rect.left) / rect.width) * vbWidth;
  // Find sample whose projected x is closest
  let bestIdx = 0;
  let bestDx = Infinity;
  for (let i = 0; i < samples.value.length; i++) {
    const { x } = project(samples.value[i]);
    const dx = Math.abs(x - xInVb);
    if (dx < bestDx) {
      bestDx = dx;
      bestIdx = i;
    }
  }
  const s = samples.value[bestIdx];
  const { x, y } = project(s);
  // Local grade: (ele[i+1] - ele[i-1]) / (dist[i+1] - dist[i-1])
  const prev = samples.value[Math.max(0, bestIdx - 1)];
  const next = samples.value[Math.min(samples.value.length - 1, bestIdx + 1)];
  const dDist = next.distanceM - prev.distanceM;
  const dEle = next.elevationM - prev.elevationM;
  const gradePct = dDist > 0 ? (dEle / dDist) * 100 : 0;
  hover.value = {
    x,
    y,
    distanceM: s.distanceM,
    elevationM: s.elevationM,
    gradePct,
    index: bestIdx,
    lng: s.lng,
    lat: s.lat,
  };
  emit('hover', { lng: s.lng, lat: s.lat, elevationM: s.elevationM, distanceM: s.distanceM });
}

function onPointerLeave() {
  hover.value = null;
  emit('hover', null);
}

watch(() => props.coordinates, () => {
  hover.value = null;
});

function haversine(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6_371_000;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLng = (lng2 - lng1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) ** 2
    + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}
</script>

<style scoped>
.elev-profile {
  background: var(--p-surface-100, #ffffff);
  border-radius: 10px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  padding: 0.5rem 0.6rem 0.4rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.05);
}
.elev-profile--empty {
  padding: 0.55rem 0.7rem;
  font-size: 0.85rem;
  color: var(--p-text-muted-color, #6b7280);
}
.elev-profile__placeholder { display: inline-flex; align-items: center; gap: 0.4rem; }
.elev-profile__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.78rem;
  color: var(--p-text-muted-color, #6b7280);
}
.elev-profile__title {
  font-weight: 600;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  font-size: 0.7rem;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}
.elev-profile__totals {
  display: flex;
  gap: 0.7rem;
  font-variant-numeric: tabular-nums;
}
.elev-profile__totals-range { opacity: 0.8; }
.elev-profile__svg {
  width: 100%;
  height: 100px;
  display: block;
  touch-action: none;
  cursor: crosshair;
}
.elev-profile__readout {
  display: flex;
  gap: 0.9rem;
  font-size: 0.78rem;
  font-variant-numeric: tabular-nums;
  color: var(--p-text-color, #374151);
}
.elev-profile__readout .pos { color: #c2410c; }
.elev-profile__readout .neg { color: #1d4ed8; }

@media (max-width: 640px) {
  .elev-profile__svg { height: 88px; }
  .elev-profile__totals { gap: 0.5rem; font-size: 0.72rem; }
}
</style>
