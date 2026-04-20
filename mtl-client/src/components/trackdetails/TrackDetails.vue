<template>

  <div class="tool-container">

    <TrackDetailMiniMap :gps-track-id="gpsTrackId" />

    <Tabs value="0" @update:value="onTabChange">
      <TabList>
        <Tab value="0">Overview</Tab>
        <Tab value="1">Graphs</Tab>
        <Tab value="2">Quality</Tab>
        <Tab value="3">Related</Tab>
      </TabList>
      <TabPanels>
      <TabPanel value="0">
        <TrackDetailOverview :gps-track="gpsTrack ?? undefined" :track-details="trackDetails" />
      </TabPanel>

      <TabPanel value="1">
        <div class="graphs-panel" :style="graphHeightStyle">
          <div class="graphs-toolbar">
            <div class="graphs-toolbar-section">
              <span class="graphs-toolbar-label">X Axis</span>
              <div class="graphs-toggle">
                <button :class="['toggle-btn', { 'toggle-btn--active': xMode === 'time' }]" @click="setXModeValue('time')">
                  <i class="bi bi-clock"></i> Time
                </button>
                <button :class="['toggle-btn', { 'toggle-btn--active': xMode === 'distance' }]" @click="setXModeValue('distance')">
                  <i class="bi bi-signpost-split"></i> Distance
                </button>
              </div>
            </div>

            <div class="graphs-toolbar-section">
              <span class="graphs-toolbar-label">Height</span>
              <div class="graphs-slider-shell">
                <button
                  class="graphs-slider-icon-btn"
                  type="button"
                  :disabled="graphHeightPx <= GRAPH_HEIGHT_MIN"
                  aria-label="Make graphs smaller"
                  title="Make graphs smaller"
                  @click="nudgeGraphHeight(-GRAPH_HEIGHT_STEP)"
                >
                  <i class="bi bi-arrows-collapse-vertical"></i>
                </button>
                <Slider
                  v-model="graphHeightPx"
                  :min="GRAPH_HEIGHT_MIN"
                  :max="GRAPH_HEIGHT_MAX"
                  :step="GRAPH_HEIGHT_STEP"
                  class="graphs-height-slider"
                  aria-label="Adjust graph height"
                  @change="onGraphHeightCommit"
                />
                <button
                  class="graphs-slider-icon-btn"
                  type="button"
                  :disabled="graphHeightPx >= GRAPH_HEIGHT_MAX"
                  aria-label="Make graphs bigger"
                  title="Make graphs bigger"
                  @click="nudgeGraphHeight(GRAPH_HEIGHT_STEP)"
                >
                  <i class="bi bi-arrows-expand-vertical"></i>
                </button>
              </div>
            </div>
          </div>
          <TrackGraph :config="trackGraphConfigs.elevation" :track-details="trackDetails" :x-mode="xMode"/>
          <TrackGraph :config="trackGraphConfigs.elevationGain" :track-details="trackDetails" :x-mode="xMode"/>
          <TrackGraph :config="trackGraphConfigs.speed" :track-details="trackDetails" :x-mode="xMode"/>
          <TrackGraph v-if="xMode === 'time'" :config="trackGraphConfigs.distance" :track-details="trackDetails" :x-mode="xMode"/>
          <TrackGraph :config="trackGraphConfigs.energy" :track-details="trackDetails" :x-mode="xMode"/>
          <TrackGraph :config="trackGraphConfigs.power" :track-details="trackDetails" :x-mode="xMode"/>
        </div>
      </TabPanel>

      <TabPanel value="2">
        <TrackDetailQuality :gps-track="gpsTrack ?? undefined" @navigate-track="navigateToTrack" />
      </TabPanel>

      <TabPanel value="3">
        <TrackDetailRelated :related-tracks="relatedTracks ?? undefined" :gps-track="gpsTrack ?? undefined" :is-loading="isLoading" @navigate-track="navigateToTrack" />
      </TabPanel>
      </TabPanels>
    </Tabs>

  </div>

</template>

<script lang="ts">
import type {Ref} from 'vue';
import {defineComponent, inject, ref} from "vue";
import {fetchTrack, fetchTrackDetails, getRelatedTracks} from "@/utils/ServiceHelper";
import {useTrackMapSync, type TrackPoint} from "@/composables/useTrackMapSync";
import {useChartSync} from "@/composables/useChartSync";
import type {GpsTrack, GpsTrackDataPoint, RelatedTracks} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import TrackGraph from "@/components/trackdetails/TrackGraph.vue";
import { trackGraphConfigs } from "@/components/trackdetails/trackGraphConfigs";
import TrackDetailOverview from "@/components/trackdetails/TrackDetailOverview.vue";
import TrackDetailQuality from "@/components/trackdetails/TrackDetailQuality.vue";
import TrackDetailRelated from "@/components/trackdetails/TrackDetailRelated.vue";
import TrackDetailMiniMap from "@/components/trackdetails/TrackDetailMiniMap.vue";
import { USER_PREFS_KEYS, migrateLegacyKeys } from "@/utils/userPrefs";

const GRAPH_HEIGHT_STORAGE_KEY = USER_PREFS_KEYS.trackGraphHeight;
const GRAPH_HEIGHT_MIN = 100;
const GRAPH_HEIGHT_MAX = 640;
const GRAPH_HEIGHT_STEP = 10;
const GRAPH_HEIGHT_DEFAULT = 240;

function clampGraphHeight(value: number): number {
  return Math.min(GRAPH_HEIGHT_MAX, Math.max(GRAPH_HEIGHT_MIN, value));
}

function loadGraphHeight(): number {
  try {
    migrateLegacyKeys();
    const value = localStorage.getItem(GRAPH_HEIGHT_STORAGE_KEY);
    if (value === 'compact') {
      return GRAPH_HEIGHT_MIN;
    }
    if (value === 'normal') {
      return GRAPH_HEIGHT_DEFAULT;
    }
    if (value === 'tall') {
      return GRAPH_HEIGHT_MAX;
    }

    const parsed = Number.parseInt(value ?? '', 10);
    if (Number.isFinite(parsed)) {
      return clampGraphHeight(parsed);
    }
  } catch {
    // Ignore localStorage access failures and fall back to the default height.
  }
  return GRAPH_HEIGHT_DEFAULT;
}


export default defineComponent({
  name: 'TrackDetails',
  components: {
    TrackGraph,
    TrackDetailOverview,
    TrackDetailQuality,
    TrackDetailRelated,
    TrackDetailMiniMap,
  },
  props: {
    gpsTrackId: {
      type: Number,
      required: true
    }
  },
  emits: ['track-loaded'],
  data(): { gpsTrack: GpsTrack | null, relatedTracks: RelatedTracks | null, trackDetails: GpsTrackDataPoint[], xMode: 'time' | 'distance', graphHeightPx: number, graphHeightReflowFrame: number | null, GRAPH_HEIGHT_MIN: number, GRAPH_HEIGHT_MAX: number, GRAPH_HEIGHT_STEP: number, isLoading: boolean, _loadGeneration: number } {
    return {
      gpsTrack: null,
      relatedTracks: null,
      trackDetails: [],
      xMode: 'time',
      isLoading: false,
      _loadGeneration: 0,
      graphHeightPx: loadGraphHeight(),
      graphHeightReflowFrame: null,
      GRAPH_HEIGHT_MIN,
      GRAPH_HEIGHT_MAX,
      GRAPH_HEIGHT_STEP,
    };
  },
  setup(props): { toast: unknown, gpsTrackId: Ref<number>, setTrackPoints: (pts: TrackPoint[]) => void, clearAll: () => void, setXMode: (mode: 'time' | 'distance') => void, trackGraphConfigs: typeof trackGraphConfigs } {
    const toast = inject("toast");
    const gpsTrackId = ref(props.gpsTrackId);
    const {setTrackPoints, clearAll} = useTrackMapSync();
    const {setXMode} = useChartSync();
    return {toast, gpsTrackId, setTrackPoints, clearAll, setXMode, trackGraphConfigs};
  },
  mounted() {
    console.log("mounted");
    this.load(this.gpsTrackId);
  },
  beforeUnmount() {
    if (this.graphHeightReflowFrame !== null) {
      window.cancelAnimationFrame(this.graphHeightReflowFrame);
      this.graphHeightReflowFrame = null;
    }
    this.clearAll();
  },
  watch: {
    '$props.gpsTrackId'(newId: number) {
      this.gpsTrackId = newId;
      this.load(newId);
    },
  },
  computed: {
    graphHeightStyle(): Record<string, string> {
      return {
        '--track-detail-graph-height': `${this.graphHeightPx}px`,
      };
    },
  },
  methods: {
    triggerChartReflow() {
      if (this.graphHeightReflowFrame !== null) {
        return;
      }

      this.graphHeightReflowFrame = window.requestAnimationFrame(() => {
        this.graphHeightReflowFrame = null;
        this.$nextTick(() => {
          window.dispatchEvent(new Event('resize'));
        });
      });
    },

    onTabChange(value: string | number) {
      // Trigger chart reflow after the Graphs tab becomes visible (value "1")
      if (value === '1') {
        this.triggerChartReflow();
      }
    },

    setXModeValue(mode: 'time' | 'distance') {
      this.xMode = mode;
      this.setXMode(mode);
      this.triggerChartReflow();
    },

    // Called only on mouseup/touchend — saves and reflows without disturbing drag.
    onGraphHeightCommit(value: number | number[]) {
      const raw = Array.isArray(value) ? value[0] : value;
      const nextHeight = clampGraphHeight(raw);
      this.graphHeightPx = nextHeight;
      try {
        localStorage.setItem(GRAPH_HEIGHT_STORAGE_KEY, String(nextHeight));
      } catch {
        // Ignore localStorage access failures and keep the in-memory preference.
      }
      this.triggerChartReflow();
    },

    nudgeGraphHeight(delta: number) {
      // Button tap — no ongoing drag, so reflow immediately.
      const nextHeight = clampGraphHeight(this.graphHeightPx + delta);
      this.graphHeightPx = nextHeight;
      try {
        localStorage.setItem(GRAPH_HEIGHT_STORAGE_KEY, String(nextHeight));
      } catch {
        // Ignore localStorage access failures and keep the in-memory preference.
      }
      this.triggerChartReflow();
    },

    buildTrackPoints(details: GpsTrackDataPoint[]): TrackPoint[] {
      return details
        .filter((d: GpsTrackDataPoint) => {
          // Server's custom LineStringSerializer emits bare [lng, lat, alt] arrays,
          // so despite the generated `Coordinate[]` typing, runtime values are number[][].
          const coords = d.pointLongLat?.coordinates as unknown as number[] | undefined;
          return d.pointTimestamp && coords && coords.length >= 2 &&
            typeof coords[0] === 'number' && isFinite(coords[0]) &&
            typeof coords[1] === 'number' && isFinite(coords[1]);
        })
        .map((d: GpsTrackDataPoint) => {
          const coords = d.pointLongLat!.coordinates as unknown as number[];
          return {
            lat: coords[1],
            lng: coords[0],
            altitude: d.pointAltitude ?? null,
            timestamp: typeof d.pointTimestamp === 'string' ? new Date(d.pointTimestamp).getTime() : (d.pointTimestamp as Date).getTime(),
            distanceKm: (d.distanceInMeterSinceStart ?? 0) / 1000,
            pointIndex: d.pointIndex ?? 0,
          };
        });
    },

    /**
     * Load all data needed to render the Track Details panel.
     *
     * DESIGN NOTE — why we use SIMPLIFIED @ 10 m (not RAW_OUTLIER_CLEANED):
     *
     * The authoritative track-level totals — total energy (energyNetTotalWh),
     * avg/max power (powerWattsAvg/Max), track length, ascent/descent, etc. —
     * are pre-computed on the GpsTrack entity at import time from the
     * RAW_OUTLIER_CLEANED variant (see EnergyService.recalculateEnergyForTrack
     * on the server). Those numbers are already full-precision and stable.
     *
     * For the per-point data we use on this screen (map line, graphs,
     * computeSummary values in Overview), SIMPLIFIED @ 10 m is:
     *   • 5–10× smaller than RAW for typical 1 Hz GPS tracks
     *   • Visually indistinguishable on charts (Douglas-Peucker preserves shape)
     *   • Much faster to fetch, transfer and render — especially on long tracks
     *
     * So: lightweight render data here, authoritative aggregates from the entity.
     * If a “high precision” mode is ever needed, it should be an explicit
     * user-toggled option, not the default.
     */
    async load(gpsTrackId: number) {
      console.log("load gpsTrack details for id=" + gpsTrackId);
      // Generation guard: if the user navigates to another track while this
      // load is in flight, discard the stale result.
      const generation = ++this._loadGeneration;
      this.isLoading = true;
      this.clearAll();
      try {
        const [track, relatedTracks, details] = await Promise.all([
          fetchTrack(gpsTrackId),
          getRelatedTracks(gpsTrackId),
          fetchTrackDetails(gpsTrackId),
        ]);
        if (generation !== this._loadGeneration) return;

        this.gpsTrack = track;
        this.relatedTracks = relatedTracks;
        this.trackDetails = Array.isArray(details) ? details : [];

        this.$emit('track-loaded', {
          id: track.id,
          name: track.trackName || track.metaName || '',
          description: track.trackDescription || track.metaDescription || '',
        });

        if (Array.isArray(details)) {
          this.setTrackPoints(this.buildTrackPoints(details));
        }
      } finally {
        if (generation === this._loadGeneration) {
          this.isLoading = false;
        }
      }
    },
    navigateToTrack(trackId: number) {
      this.gpsTrackId = trackId;
      this.load(trackId);
    },
  },
});
</script>

<style scoped>
/*
 * IMPORTANT: BottomSheet + Teleport + scoped CSS scrolling pattern.
 *
 * .tool-container is the wrapper div required for :deep() selectors to work
 * inside a BottomSheet (which uses <Teleport to="body">). Without this wrapper,
 * scoped selectors like :deep(.p-tabs) have no ancestor carrying data-v-xxx
 * and won't match. See BottomSheet.vue comment about the neutral body contract.
 */

.tool-container {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  overflow: hidden;
}

/* Tabs fill the remaining space below the mini-map.
   TabList stays pinned; only TabPanels scroll. */
:deep(.p-tabs) {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}

:deep(.p-tablist) {
  flex: 0 0 auto;
  z-index: 2;
  background: var(--surface-glass);
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
}

:deep(.p-tabpanels) {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
}

:deep(.p-tabpanel) {
  min-height: 0;
}

.graphs-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.75rem;
  flex-wrap: wrap;
  padding: 0.75rem 1rem 0;
}

.graphs-toolbar-section {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  flex-wrap: wrap;
}

.graphs-toolbar-label {
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.graphs-toggle {
  display: flex;
  flex: 0 0 auto;
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 8px;
  padding: 3px;
  gap: 2px;
}

.toggle-btn {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.28rem 0.65rem;
  border: none;
  background: none;
  border-radius: 5px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.toggle-btn:hover {
  background: var(--surface-glass);
}

.toggle-btn--active {
  background: var(--surface-glass-heavy);
  color: var(--accent-text);
  box-shadow: var(--shadow-sm);
}

.graphs-slider-shell {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  min-width: min(17rem, 72vw);
  padding: 0.18rem 0.3rem;
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 999px;
}

.graphs-slider-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.9rem;
  height: 1.9rem;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.15s, color 0.15s, opacity 0.15s;
}

.graphs-slider-icon-btn:hover:not(:disabled) {
  background: var(--surface-glass);
  color: var(--accent-text);
}

.graphs-slider-icon-btn:disabled {
  opacity: 0.45;
  cursor: default;
}

.graphs-height-slider {
  flex: 1 1 11rem;
  min-width: 8rem;
}

.graphs-slider-shell :deep(.p-slider) {
  background: var(--slider-track);
  border-radius: 999px;
}

.graphs-slider-shell :deep(.p-slider-range) {
  background: var(--slider-gradient);
}

.graphs-slider-shell :deep(.p-slider-handle) {
  box-shadow: 0 0 0 0 var(--accent-bg);
  transition: box-shadow 0.15s ease;
}

.graphs-slider-shell :deep(.p-slider-handle:hover),
.graphs-slider-shell :deep(.p-slider-handle:focus-visible) {
  box-shadow: 0 0 0 6px var(--accent-bg);
}

@media (max-width: 768px) {
  .graphs-toolbar {
    padding-inline: 0.75rem;
  }

  .graphs-toolbar-section {
    width: 100%;
    justify-content: space-between;
  }

  .graphs-toolbar-label {
    min-width: 3.5rem;
  }

  .graphs-slider-shell {
    flex: 1 1 auto;
    min-width: 0;
  }
}

</style>