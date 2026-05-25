<template>
  <div class="dialog-flex-root results-container">
    <Tabs v-model:value="activeTab" class="measure-tabview">
      <!-- Sticky context strip: single source of truth for tracks / visits; launches overlays -->
      <div class="measure-topbar">
        <div class="measure-context-strip">
          <!-- Tracks pill: jumps to Table tab -->
          <button
            class="measure-context-pill"
            :title="activeTab === '0' ? 'Track selection \u2014 already on Table' : 'Go to Table to change selection'"
            @click="activeTab = '0'"
          >
            <i class="bi bi-people-fill"></i>
            <span class="measure-context-pill-value"
              >{{ selectedTrackIds.size }}<span class="measure-context-pill-sep"> / </span>{{ trackCount }}</span
            >
            <span class="measure-context-pill-label">tracks</span>
          </button>

          <!-- Consolidated toggle -->
          <label
            class="measure-context-toggle"
            :title="consolidateVisits ? 'Consolidated — averages repeated visits' : 'Each visit shown individually'"
          >
            <ToggleSwitch v-model="consolidateVisits" />
            <span class="measure-context-toggle-label">{{ consolidateVisits ? 'Consolidated' : 'Each visit' }}</span>
          </label>

          <span class="measure-context-spacer"></span>

          <!-- Compare action: opens overlay -->
          <button
            class="measure-action-btn measure-action-btn--compare"
            :disabled="selectedTrackIds.size === 0 || availableSegments.length === 0"
            :title="
              selectedTrackIds.size === 0
                ? 'Pick tracks in the table first'
                : 'Compare per-point metrics across selected tracks'
            "
            @click="compareOverlayVisible = true"
          >
            <i class="bi bi-graph-up"></i>
            <span>Compare</span>
            <span v-if="selectedTrackIds.size > 0" class="measure-action-badge">{{ selectedTrackIds.size }}</span>
          </button>

          <!-- Race action: opens overlay -->
          <button
            class="measure-action-btn measure-action-btn--race"
            :disabled="selectedTrackIds.size === 0 || availableSegments.length === 0"
            :title="
              selectedTrackIds.size === 0 ? 'Pick tracks in the table first' : 'Race selected tracks on this segment'
            "
            @click="raceOverlayVisible = true"
          >
            <i class="bi bi-play-fill"></i>
            <span>Race</span>
            <span v-if="selectedTrackIds.size > 0" class="measure-action-badge">{{ selectedTrackIds.size }}</span>
          </button>

          <!-- Help -->
          <button
            class="measure-help-btn"
            :title="helpVisible ? 'Hide help' : 'How to read results'"
            @click="helpVisible = !helpVisible"
          >
            <i :class="helpVisible ? 'bi bi-question-circle-fill' : 'bi bi-question-circle'"></i>
          </button>
        </div>

        <!-- Collapsible help -->
        <Transition name="help-slide">
          <div v-if="helpVisible" class="measure-help-inline">
            <p>Each column is a <strong>segment</strong> — passage between two trigger zones.</p>
            <table class="measure-help-table">
              <tbody>
                <tr>
                  <td><strong>A &rarr; B</strong></td>
                  <td>Zone A to zone B</td>
                </tr>
                <tr>
                  <td><strong>B &rarr; A</strong></td>
                  <td>Return (reverse direction)</td>
                </tr>
                <tr>
                  <td><strong>B &rarr; B</strong></td>
                  <td>Loop — left and returned to B</td>
                </tr>
              </tbody>
            </table>
            <p class="measure-help-sub">
              Turn on <strong>Consolidated</strong> to average repeated crossings; off shows each visit numbered (A1→B1,
              B1→A2…).
            </p>
          </div>
        </Transition>

        <!-- Two-tab toggle: Table | Trends -->
        <div class="measure-topbar-row measure-topbar-row--tabs">
          <div class="measure-view-toggle">
            <button
              :class="['measure-toggle-btn', { 'measure-toggle-btn--active': activeTab === '0' }]"
              @click="activeTab = '0'"
            >
              <i class="bi bi-table"></i> Table
            </button>
            <button
              :class="['measure-toggle-btn', { 'measure-toggle-btn--active': activeTab === '1' }]"
              @click="activeTab = '1'"
            >
              <i class="bi bi-activity"></i> Trends
            </button>
          </div>
        </div>
      </div>
      <TabPanels>
        <TabPanel value="0">
          <div class="panel-outer panel-stack">
            <!-- Table-only metric selector (affects segment columns) -->
            <div class="measure-table-subbar">
              <span class="measure-control-label">Column metric</span>
              <div class="measure-metric-chips">
                <button
                  v-for="u in crossingUnits"
                  :key="u"
                  class="measure-metric-chip"
                  :class="{ 'measure-metric-chip--active': crossingUnitSelected === u }"
                  @click="crossingUnitSelected = u"
                >
                  {{ u }}
                </button>
              </div>
            </div>
            <div class="table-scroll-wrapper measure-results-table-wrap">
              <DataTable
                :value="resultsCalculated"
                column-resize-mode="fit"
                responsive-layout="scroll"
                sort-field="trackStartDate"
                :sort-order="-1"
                class="p-datatable-sm measure-results-table"
              >
                <template #empty>
                  <div class="measure-empty-state">
                    <div class="measure-empty-icon">
                      <i class="bi bi-signpost-split"></i>
                    </div>
                    <h3 class="measure-empty-headline">No tracks cross all selected zones</h3>
                    <p class="measure-empty-body">
                      The analysis requires tracks to pass through <strong>every</strong> zone. Some tracks may pass
                      through individual zones but not all of them.
                    </p>
                    <div v-if="perZoneCounts && perZoneCounts.length" class="measure-empty-zones">
                      <span class="measure-empty-zones-label">Tracks per zone:</span>
                      <span
                        v-for="zc in perZoneCounts"
                        :key="zc.name"
                        class="measure-empty-zone-chip"
                        :class="zc.count === 0 ? 'measure-empty-zone-chip--zero' : 'measure-empty-zone-chip--ok'"
                      >
                        {{ zc.name }}: {{ zc.count }}
                      </span>
                    </div>
                    <ul class="measure-empty-tips">
                      <li><i class="bi bi-geo-alt"></i> Move zones closer to your recorded routes</li>
                      <li><i class="bi bi-arrows-angle-expand"></i> Increase the detection radius</li>
                      <li><i class="bi bi-dash-circle"></i> Try using fewer zones</li>
                    </ul>
                  </div>
                </template>
                <Column
                  :style="isMobile ? 'min-width: 2.25rem; width: 2.25rem' : 'min-width: 2.75rem; width: 2.75rem'"
                  body-class="measure-select-cell"
                  header-class="measure-select-cell"
                >
                  <template #header>
                    <input
                      type="checkbox"
                      class="measure-select-checkbox"
                      :checked="allSelected"
                      :indeterminate.prop="someSelected"
                      :title="allSelected ? 'Clear selection' : 'Select all tracks for comparison'"
                      @change="toggleSelectAll"
                    />
                  </template>
                  <template #body="slotProps">
                    <input
                      type="checkbox"
                      class="measure-select-checkbox"
                      :checked="selectedTrackIds.has(slotProps.data.trackId)"
                      title="Include in Compare"
                      @change="toggleTrackSelection(slotProps.data.trackId)"
                    />
                  </template>
                </Column>
                <Column field="fileName" header="Name" :sortable="true" style="min-width: 8rem">
                  <template #body="slotProps">
                    <a
                      class="link-style measure-name-link"
                      :title="slotProps.data.fileName || slotProps.data.trackId"
                      @click="showTrackDetails(slotProps.data.trackId)"
                    >
                      {{ slotProps.data.fileName || slotProps.data.trackId }}
                    </a>
                  </template>
                </Column>

                <Column
                  field="trackStartDate"
                  header="Start"
                  :sortable="true"
                  :style="isMobile ? 'min-width: 7rem' : 'min-width: 9rem'"
                >
                  <template #body="slotProps">
                    {{ formatDate(slotProps.data.trackStartDate, isMobile) }}
                  </template>
                </Column>
                <Column
                  v-if="!isMobile"
                  field="trackDurationInMillis"
                  header="Duration"
                  :sortable="true"
                  style="min-width: 7rem"
                >
                  <template #body="slotProps">
                    <span
                      v-tooltip.top="{
                        value: formatDurationTooltip(slotProps.data.trackDurationInMillis),
                        showDelay: 400,
                      }"
                    >
                      {{ formatDurationSmart(slotProps.data.trackDurationInMillis) }}
                    </span>
                  </template>
                </Column>

                <Column field="statusSort" header="" :sortable="true" style="min-width: 2.5rem; width: 2.5rem">
                  <template #body="slotProps">
                    <span
                      v-if="slotProps.data.status && slotProps.data.status.ok"
                      class="measure-status-chip measure-status-chip--ok"
                      title="No stops detected in any segment"
                    >
                      <i class="bi bi-check-circle-fill"></i>
                    </span>
                    <button
                      v-else-if="slotProps.data.status"
                      type="button"
                      class="measure-status-chip measure-status-chip--notes"
                      aria-label="Stop details"
                      @click.stop="showStopsInfo($event, slotProps.data.status)"
                    >
                      <i class="bi bi-exclamation-circle-fill"></i>
                    </button>
                  </template>
                </Column>

                <Column
                  v-for="col of resultTableColumns"
                  :key="col.field"
                  :field="col.field"
                  :header="col.header"
                  sortable
                  :style="isMobile ? 'min-width: 5.75rem' : 'min-width: 8rem'"
                >
                  <template #body="slotProps">
                    {{ slotProps.data[col.field] }}
                  </template>
                </Column>
              </DataTable>
            </div>
          </div>
        </TabPanel>

        <TabPanel value="1">
          <div class="panel-outer chart-panel">
            <div class="measure-graph-card">
              <div class="measure-graph-header">
                <span class="measure-graph-title"><i class="bi bi-activity"></i> Segment trends over time</span>
                <span class="measure-graph-sub"
                  >Average speed per segment vs. track start date — one line per segment.</span
                >
              </div>
              <MeasureGraph :graph-series-data="graphSeriesData"></MeasureGraph>
            </div>
          </div>
        </TabPanel>
      </TabPanels>
    </Tabs>

    <!-- Compare bottom sheet (stacked on top) -->
    <BottomSheet
      v-model="compareOverlayVisible"
      title="Segment Analyzer (Compare)"
      icon="bi bi-stopwatch"
      :detents="[{ height: '92vh' }]"
      :z-index="5200"
      :no-backdrop="true"
    >
      <SegmentCompare
        :measure-service-result="measureServiceResult"
        :consolidate-visits="consolidateVisits"
        :selected-track-ids="selectedTrackIds"
        :selected-segment="selectedSegment"
        :available-segments="availableSegments"
        @show-track-details="showTrackDetails"
        @goto-table="
          compareOverlayVisible = false;
          activeTab = '0';
        "
      />
    </BottomSheet>

    <!-- Race bottom sheet (stacked on top) -->
    <BottomSheet
      v-model="raceOverlayVisible"
      title="Segment Analyzer (Race)"
      icon="bi bi-stopwatch"
      :detents="[{ height: '92vh' }]"
      :z-index="5200"
      :no-backdrop="true"
    >
      <VirtualRace
        :measure-service-result="measureServiceResult"
        :consolidate-visits="consolidateVisits"
        :initial-segment="selectedSegment"
        :selected-track-ids="selectedTrackIds"
        @show-track-details="showTrackDetails"
      />
    </BottomSheet>

    <Popover ref="stopsInfoPopover" append-to="body">
      <p class="measure-status-info-text">{{ currentStopsInfo }}</p>
    </Popover>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { CrossingPointsResponse } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import type Highcharts from 'highcharts';
import ToggleSwitch from 'primevue/toggleswitch';
import {
  formatDuration,
  formatNumber,
  formatDurationSmart,
  formatDurationTooltip,
  formatDateAndTime,
  formatDateCompact,
} from '@/utils/Utils';
import VirtualRace from '@/components/virtual-race/VirtualRace.vue';
import SegmentCompare from '@/components/measure/SegmentCompare.vue';
import MeasureGraph from '@/components/measure/MeasureGraph.vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';

defineOptions({ name: 'DisplayMeasureResults' });

const COLUM_ID = {
  trackId: 'trackId',
  fileName: 'fileName',
  crossingDurationMillis: 'crossingDurationMillis',
  trackStartDate: 'trackStartDate',
  trackEndDate: 'trackEndDate',
  trackDurationInMillis: 'trackDurationInMillis',
  avgSpeedSinceLastTriggerPoint: 'avgSpeedSinceLastTriggerPoint',
  distanceInMeterSinceLastTriggerPoint: 'distanceInMeterSinceLastTriggerPoint',
  status: 'status',
  statusSort: 'statusSort',
};

const CROSSING_UNITS = {
  distance: 'distance',
  speed: 'speed',
  time: 'time',
};

type SegmentCode = { point1?: string; point2?: string; consolidated?: boolean; p1Visit?: number; p2Visit?: number };
type SegmentOption = { name?: string; count?: number; code: SegmentCode };
type TableRow = Record<string, unknown>;
type GraphSeries = Highcharts.SeriesLineOptions;
type MeasureGpsTrack = {
  endDate?: Date;
  id: number;
  indexedFile: { name: string };
  startDate?: Date;
};
type MeasureCrossing = {
  avgSpeedSinceLastTriggerPoint: number;
  distanceInMeterSinceLastTriggerPoint: number;
  segmentNotesSinceLastTriggerPoint?: { longestStopSec?: number; stopCount?: number; totalStoppedSec?: number };
  timeInSecSinceLastTriggerPoint: number;
  triggerPoint: { name: string };
};
type MeasureCrossingsPerTrack = {
  crossings: MeasureCrossing[];
  gpsTrack: MeasureGpsTrack;
};
type StopStatus = {
  ok?: boolean;
  totalStopCount: number;
  totalStoppedSec: number;
  longestStopSec: number;
  segments?: Array<{ label: string; stopCount: number; totalStoppedSec: number }>;
};

function asMeasureCrossingsPerTrack(value: unknown): MeasureCrossingsPerTrack {
  return value as MeasureCrossingsPerTrack;
}

const props = defineProps<{
  measureServiceResult: CrossingPointsResponse;
}>();

const emit = defineEmits<{
  'show-track-details': [trackId: number | string];
}>();

const helpVisible = ref(false);
const activeTab = ref('0');
const crossingUnits = [CROSSING_UNITS.speed, CROSSING_UNITS.time, CROSSING_UNITS.distance];
const crossingUnitSelected = ref(CROSSING_UNITS.speed);
const consolidateVisits = ref(true);
const selectedTrackIds = ref(new Set<number>());
const selectedSegment = ref<SegmentCode | null>(null);
const currentStopsInfo = ref('');
const raceOverlayVisible = ref(false);
const compareOverlayVisible = ref(false);
const autoSelectedFor = ref<string | null>(null);
const stopsInfoPopover = ref<{ toggle?: (event: Event) => void } | null>(null);

const allResults = computed(() => {
  if (!props.measureServiceResult) {
    return {
      tableData: [] as TableRow[],
      columns: [] as Array<{ field: string; header: string }>,
      graphSeries: [] as GraphSeries[],
    };
  }

  const durationMillisPostfix = '-DurationMillis';
  const speedPostfix = '-speed';
  const allColumns = new Map<string, string>();
  const tableData: TableRow[] = [];

  for (const crossingRaw of Object.values(props.measureServiceResult.crossings || {})) {
    const crossing = asMeasureCrossingsPerTrack(crossingRaw);
    const tableRow: TableRow = {};

    const gpsTrack = crossing.gpsTrack;
    tableRow[COLUM_ID.trackId] = gpsTrack.id;
    tableRow[COLUM_ID.fileName] = gpsTrack.indexedFile.name;
    tableRow[COLUM_ID.trackStartDate] = gpsTrack.startDate;
    tableRow[COLUM_ID.trackEndDate] = gpsTrack.endDate;
    let duration = 0;
    if (gpsTrack.startDate && gpsTrack.endDate) {
      duration = gpsTrack.endDate.getTime() - gpsTrack.startDate.getTime();
    }
    tableRow[COLUM_ID.trackDurationInMillis] = duration;

    const countPerTriggerPoint = new Map<string, number>();
    let lastTrackCrossing: MeasureCrossing | null = null;
    const consolidatedSegments = new Map<
      string,
      { count: number; totalTimeSec: number; totalSpeed: number; totalDistance: number }
    >();
    // Per-row stop notes: aggregate across all segments of this track.
    // Each entry: { label, stopCount, totalStoppedSec, longestStopSec }
    const segmentNotesList: Array<{
      label: string;
      longestStopSec: number;
      stopCount: number;
      totalStoppedSec: number;
    }> = [];

    crossing.crossings.forEach((trackCrossing) => {
      const triggerPoint = trackCrossing.triggerPoint;
      if (countPerTriggerPoint.has(triggerPoint.name)) {
        countPerTriggerPoint.set(triggerPoint.name, (countPerTriggerPoint.get(triggerPoint.name) || 0) + 1);
      } else {
        countPerTriggerPoint.set(triggerPoint.name, 1);
      }

      if (lastTrackCrossing != null) {
        let segmentP1, segmentP2;
        if (consolidateVisits.value) {
          segmentP1 = lastTrackCrossing.triggerPoint.name;
          segmentP2 = triggerPoint.name;
        } else {
          segmentP1 =
            lastTrackCrossing.triggerPoint.name + countPerTriggerPoint.get(lastTrackCrossing.triggerPoint.name);
          segmentP2 = triggerPoint.name + countPerTriggerPoint.get(triggerPoint.name);
        }
        const segmentLabel = segmentP1 + ' - ' + segmentP2;
        const segmentKey = segmentP1 + '-' + segmentP2;
        allColumns.set(segmentKey, segmentLabel);

        // Collect per-segment notes (stops) for this visit. Notes are on
        // the CURRENT crossing (they describe the segment ending at it).
        const notes = trackCrossing.segmentNotesSinceLastTriggerPoint;
        const stopCount = notes?.stopCount ?? 0;
        if (stopCount > 0) {
          segmentNotesList.push({
            label: segmentLabel,
            stopCount,
            totalStoppedSec: notes?.totalStoppedSec || 0,
            longestStopSec: notes?.longestStopSec || 0,
          });
        }

        if (consolidateVisits.value) {
          if (!consolidatedSegments.has(segmentKey)) {
            consolidatedSegments.set(segmentKey, { count: 0, totalTimeSec: 0, totalSpeed: 0, totalDistance: 0 });
          }
          const seg = consolidatedSegments.get(segmentKey)!;
          seg.count++;
          seg.totalTimeSec += trackCrossing.timeInSecSinceLastTriggerPoint || 0;
          seg.totalSpeed += trackCrossing.avgSpeedSinceLastTriggerPoint || 0;
          seg.totalDistance += trackCrossing.distanceInMeterSinceLastTriggerPoint || 0;
        } else {
          switch (crossingUnitSelected.value) {
            case CROSSING_UNITS.time: {
              tableRow[segmentKey + durationMillisPostfix] = trackCrossing.timeInSecSinceLastTriggerPoint * 1000;
              tableRow[segmentKey] = formatDuration(trackCrossing.timeInSecSinceLastTriggerPoint * 1000);
              break;
            }
            case CROSSING_UNITS.speed: {
              tableRow[segmentKey + durationMillisPostfix] = trackCrossing.avgSpeedSinceLastTriggerPoint;
              tableRow[segmentKey] = formatNumber(trackCrossing.avgSpeedSinceLastTriggerPoint, 2);
              break;
            }
            case CROSSING_UNITS.distance: {
              tableRow[segmentKey + durationMillisPostfix] = trackCrossing.distanceInMeterSinceLastTriggerPoint;
              tableRow[segmentKey] = formatNumber(trackCrossing.distanceInMeterSinceLastTriggerPoint, 0);
              break;
            }
            default: {
              console.error('no crossing unit given');
            }
          }

          tableRow[segmentKey + COLUM_ID.avgSpeedSinceLastTriggerPoint] = trackCrossing.avgSpeedSinceLastTriggerPoint;
          tableRow[segmentKey + COLUM_ID.distanceInMeterSinceLastTriggerPoint] =
            trackCrossing.distanceInMeterSinceLastTriggerPoint;
          tableRow[segmentKey + speedPostfix] = trackCrossing.avgSpeedSinceLastTriggerPoint;
        }
      }

      lastTrackCrossing = trackCrossing;
    });

    if (consolidateVisits.value) {
      for (const [segmentKey, seg] of consolidatedSegments) {
        const avgTimeSec = seg.totalTimeSec / seg.count;
        const avgSpeed = seg.totalSpeed / seg.count;
        const avgDistance = seg.totalDistance / seg.count;

        switch (crossingUnitSelected.value) {
          case CROSSING_UNITS.time: {
            tableRow[segmentKey + durationMillisPostfix] = avgTimeSec * 1000;
            tableRow[segmentKey] = formatDuration(avgTimeSec * 1000);
            break;
          }
          case CROSSING_UNITS.speed: {
            tableRow[segmentKey + durationMillisPostfix] = avgSpeed;
            tableRow[segmentKey] = formatNumber(avgSpeed, 2);
            break;
          }
          case CROSSING_UNITS.distance: {
            tableRow[segmentKey + durationMillisPostfix] = avgDistance;
            tableRow[segmentKey] = formatNumber(avgDistance, 0);
            break;
          }
        }
        tableRow[segmentKey + speedPostfix] = avgSpeed;
      }
    }

    // Per-row status summary (stop notes aggregated across segments).
    let totalStopCount = 0;
    let totalStoppedSec = 0;
    let longestStopSec = 0;
    for (const sn of segmentNotesList) {
      totalStopCount += sn.stopCount;
      totalStoppedSec += sn.totalStoppedSec;
      if (sn.longestStopSec > longestStopSec) longestStopSec = sn.longestStopSec;
    }
    tableRow[COLUM_ID.status] = {
      ok: totalStopCount === 0,
      totalStopCount,
      totalStoppedSec,
      longestStopSec,
      segments: segmentNotesList,
    };
    // Sortable scalar — higher = more problematic.
    tableRow[COLUM_ID.statusSort] = totalStopCount * 1_000_000 + Math.round(totalStoppedSec);

    tableData.push(tableRow);
  }

  const columns: Array<{ field: string; header: string }> = [];
  allColumns.forEach((v, k) => {
    columns.push({ field: k, header: v });
  });

  const graphSeries: GraphSeries[] = [];
  allColumns.forEach((v1, k1) => {
    const data: Array<[number, number]> = [];
    tableData.forEach((v2) => {
      const speed = v2[k1 + speedPostfix];
      const trackStartDate = v2[COLUM_ID.trackStartDate];
      if (typeof speed === 'number' && trackStartDate instanceof Date) {
        data.push([trackStartDate.getTime(), speed]);
      }
    });
    data.sort((a, b) => a[0] - b[0]);
    graphSeries.push({ type: 'line', name: v1, data });
  });

  return { tableData, columns, graphSeries };
});

const resultsCalculated = computed(() => allResults.value.tableData);
const resultTableColumns = computed(() => allResults.value.columns);
const graphSeriesData = computed(() => allResults.value.graphSeries);
const trackCount = computed(() => resultsCalculated.value.length);

const availableSegments = computed<SegmentOption[]>(() => {
  if (!props.measureServiceResult) return [];
  if (consolidateVisits.value !== false) {
    return (props.measureServiceResult.segmentsStats || []).map((s) => ({
      name: s.label,
      count: s.count,
      code: { point1: s.point1, point2: s.point2, consolidated: true },
    }));
  }
  const segMap = new Map<string, SegmentOption>();
  for (const [, trackCrossingsRaw] of Object.entries(props.measureServiceResult.crossings || {})) {
    const trackCrossings = asMeasureCrossingsPerTrack(trackCrossingsRaw);
    const countPerTP = new Map<string, number>();
    let last: MeasureCrossing | null = null;
    for (const c of trackCrossings.crossings) {
      const name = c.triggerPoint.name;
      countPerTP.set(name, (countPerTP.get(name) || 0) + 1);
      if (last != null) {
        const p1 = last.triggerPoint.name;
        const p1v = countPerTP.get(p1);
        const p2 = name;
        const p2v = countPerTP.get(p2);
        const key = p1 + p1v + '-' + p2 + p2v;
        if (!segMap.has(key)) {
          segMap.set(key, {
            name: p1 + p1v + ' - ' + p2 + p2v,
            count: 0,
            code: { point1: p1, p1Visit: p1v, point2: p2, p2Visit: p2v, consolidated: false },
          });
        }
        const segment = segMap.get(key)!;
        segment.count = (segment.count || 0) + 1;
      }
      last = c;
    }
  }
  return Array.from(segMap.values());
});

const allSelected = computed(() => {
  const rows = resultsCalculated.value;
  return rows.length > 0 && rows.every((r) => selectedTrackIds.value.has(rowTrackId(r)));
});

const someSelected = computed(() => {
  return selectedTrackIds.value.size > 0 && !allSelected.value;
});

const perZoneCounts = computed(() => {
  if (!props.measureServiceResult || !props.measureServiceResult.tracksPerZone) return null;
  const counts = props.measureServiceResult.tracksPerZone;
  return Object.entries(counts).map(([name, count]) => ({ name, count }));
});

const isMobile = computed(() => {
  if (typeof window === 'undefined') {
    return false;
  }
  return window.innerWidth <= 768;
});

function segmentsEqual(a: SegmentCode | null | undefined, b: SegmentCode | null | undefined) {
  if (!a || !b) return false;
  if (a.consolidated !== b.consolidated) return false;
  if (a.point1 !== b.point1 || a.point2 !== b.point2) return false;
  if (a.consolidated === false) {
    if (a.p1Visit !== b.p1Visit || a.p2Visit !== b.p2Visit) return false;
  }
  return true;
}

function toggleTrackSelection(trackId: number) {
  const next = new Set(selectedTrackIds.value);
  if (next.has(trackId)) next.delete(trackId);
  else next.add(trackId);
  selectedTrackIds.value = next;
}

function toggleSelectAll() {
  if (allSelected.value) {
    selectedTrackIds.value = new Set();
  } else {
    selectedTrackIds.value = new Set(
      resultsCalculated.value.map(rowTrackId).filter((trackId): trackId is number => trackId != null)
    );
  }
}

function rowTrackId(row: TableRow): number {
  return Number(row[COLUM_ID.trackId]);
}

function formatDate(date: Date | string | number | null | undefined, compact = false) {
  return compact ? formatDateCompact(date) : formatDateAndTime(date);
}

function formatStoppedDuration(sec: number | null | undefined) {
  if (sec == null || sec <= 0) return '';
  const total = Math.round(sec);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

function formatStopsTooltip(status: StopStatus | null | undefined) {
  if (!status || status.ok) return 'No stops detected';
  const lines = [];
  lines.push(
    `${status.totalStopCount} stop${status.totalStopCount !== 1 ? 's' : ''} · total ${formatStoppedDuration(status.totalStoppedSec)}`
  );
  if (status.longestStopSec > 0 && status.totalStopCount > 1) {
    lines.push(`Longest: ${formatStoppedDuration(status.longestStopSec)}`);
  }
  if (status.segments && status.segments.length) {
    lines.push('—');
    for (const sn of status.segments) {
      lines.push(`${sn.label}: ${sn.stopCount}× · ${formatStoppedDuration(sn.totalStoppedSec)}`);
    }
  }
  return lines.join('\n');
}

function showStopsInfo(event: Event, status: StopStatus) {
  currentStopsInfo.value = formatStopsTooltip(status);
  stopsInfoPopover.value?.toggle?.(event);
}

function showTrackDetails(id: number | string) {
  emit('show-track-details', id);
}

watch(
  () => props.measureServiceResult,
  () => {
    // Auto-select the first N tracks when a new result set arrives.
    // Uses a key derived from the tracks to avoid re-triggering on unrelated re-renders.
    const ids =
      props.measureServiceResult && props.measureServiceResult.crossings
        ? Object.values(props.measureServiceResult.crossings)
            .map((c) => c.gpsTrack?.id)
            .filter((id): id is number => id != null)
        : [];
    const key = ids.join(',');
    if (autoSelectedFor.value === key) return;
    autoSelectedFor.value = key;
    const AUTO_SELECT_LIMIT = 5;
    selectedTrackIds.value = new Set(ids.slice(0, AUTO_SELECT_LIMIT));
  },
  { immediate: true }
);

watch(
  availableSegments,
  (next) => {
    // Keep the current segment if still present; otherwise default to the first.
    if (!next || next.length === 0) {
      selectedSegment.value = null;
      return;
    }
    const stillValid = selectedSegment.value && next.some((s) => segmentsEqual(s.code, selectedSegment.value));
    if (!stillValid) selectedSegment.value = next[0].code;
  },
  { immediate: true }
);

watch(consolidateVisits, () => {
  // Segment codes change shape between consolidated / unconsolidated; reset.
  selectedSegment.value = null;
});
</script>

<style scoped>
/* ── Base ────────────────────────────────────────────────── */
.link-style {
  color: var(--accent-text);
  text-decoration: none;
  cursor: pointer;
  transition: color 0.3s ease;
}

.link-style:hover {
  color: var(--accent-text-light);
  text-decoration: underline;
}

.measure-name-link {
  display: block;
  max-width: 14rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.results-container {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  gap: 0;
  padding: 0;
}

/* ── Unified single panel ────────────────────────────────── */
.measure-topbar {
  display: flex;
  flex-direction: column;
  gap: 0;
  flex: 0 0 auto;
  padding: 0.75rem 0.9rem 0;
  background: transparent;
}

/* ── Context strip ───────────────────────────────────────── */
.measure-context-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.55rem;
  padding-bottom: 0.55rem;
  margin-bottom: 0.4rem;
  border-bottom: 1px solid var(--border-subtle);
}

.measure-context-field {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}

.measure-context-field--segment {
  flex: 1 1 14rem;
  min-width: 12rem;
}

.measure-context-label {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  white-space: nowrap;
}

.measure-context-label i {
  color: var(--accent-text);
}

.measure-context-select {
  flex: 1 1 auto;
  min-width: 0;
  --p-select-padding-y: 0.3rem;
  font-size: var(--text-sm-size);
}

.measure-context-select :deep(.p-select-label) {
  padding: 0.3rem 0.5rem !important;
  font-size: var(--text-sm-size);
}

.measure-context-select-count {
  color: var(--text-muted);
  font-weight: 500;
  font-size: var(--text-xs-size);
}

.measure-context-select-empty {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-style: italic;
}

.measure-context-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.3rem 0.6rem;
  border-radius: 999px;
  background: var(--surface-glass);
  border: 1px solid var(--border-default);
  color: var(--text-secondary);
  cursor: pointer;
  font-family: inherit;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  white-space: nowrap;
  transition:
    background 0.15s,
    color 0.15s,
    border-color 0.15s;
}

.measure-context-pill:hover {
  background: var(--surface-glass-heavy);
  color: var(--accent-text);
  border-color: var(--accent-muted);
}

.measure-context-pill i {
  color: var(--accent-text);
  font-size: var(--text-base-size);
}

.measure-context-pill-value {
  font-weight: 700;
}

.measure-context-pill-sep {
  color: var(--text-muted);
  font-weight: 400;
}

.measure-context-pill-label {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.measure-context-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: var(--text-xs-size);
  color: var(--text-secondary);
  white-space: nowrap;
  cursor: pointer;
  --p-toggleswitch-width: 2rem;
  --p-toggleswitch-height: 1.1rem;
  --p-toggleswitch-handle-size: 0.8rem;
}

.measure-context-toggle-label {
  font-weight: 600;
}

.measure-context-spacer {
  flex: 1 1 auto;
  min-width: 0;
}

.measure-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.35rem 0.7rem;
  border-radius: 999px;
  background: var(--surface-glass);
  border: 1px solid var(--border-default);
  color: var(--text-secondary);
  cursor: pointer;
  font-family: inherit;
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: var(--text-xs-lh);
  white-space: nowrap;
  transition:
    background 0.15s,
    color 0.15s,
    transform 0.12s,
    border-color 0.15s;
}

.measure-action-btn:hover:not(:disabled) {
  background: var(--surface-glass-heavy);
  color: var(--accent-text);
  border-color: var(--accent-muted);
  transform: translateY(-1px);
}

.measure-action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.measure-action-btn i {
  font-size: var(--text-base-size);
}

.measure-action-btn--compare {
  background: var(--accent-bg);
  border-color: var(--accent-muted);
  color: var(--accent-text);
}

.measure-action-btn--compare:hover:not(:disabled) {
  background: var(--accent-text);
  color: var(--text-inverse);
  border-color: var(--accent-text);
}

.measure-action-btn--race {
  background: var(--accent-bg);
  border-color: var(--accent-muted);
  color: var(--accent-text);
}

.measure-action-btn--race:hover:not(:disabled) {
  background: var(--accent-text);
  color: var(--text-inverse);
  border-color: var(--accent-text);
}

.measure-action-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.15rem;
  height: 1.15rem;
  padding: 0 0.35rem;
  border-radius: 999px;
  background: var(--accent-text);
  color: var(--text-inverse);
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: var(--text-xs-lh);
}

/* ── Race / Compare overlay (full-sheet takeover) ────────── */
/* Rows */
.measure-topbar-row {
  display: flex;
  align-items: center;
  min-width: 0;
}

.measure-topbar-row--head {
  gap: 0.6rem;
  margin-bottom: 0.4rem;
  padding-bottom: 0.55rem;
  border-bottom: 1px solid var(--border-subtle);
}

.measure-topbar-row--tabs {
  /* tabs row flush with panel edges */
}

.measure-results-count {
  flex: 1 1 auto;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.measure-results-count i {
  color: var(--accent-text);
  font-size: var(--text-base-size);
}

.measure-table-subbar {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.2rem 0 0.5rem;
  flex-wrap: wrap;
}

/* Control groups (Metric / Visits) */
.measure-control-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.measure-control-label {
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  white-space: nowrap;
}

/* Metric chips */
.measure-metric-chips {
  display: flex;
  align-items: center;
  gap: 0.2rem;
}

.measure-metric-chip {
  background: var(--surface-glass-subtle);
  border: 1px solid var(--border-default);
  border-radius: 999px;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: var(--text-xs-size);
  font-weight: 600;
  letter-spacing: 0.03em;
  text-transform: capitalize;
  padding: 0.25rem 0.6rem;
  transition:
    background 0.15s,
    color 0.15s,
    border-color 0.15s;
  line-height: var(--text-xs-lh);
  white-space: nowrap;
}

.measure-metric-chip:hover {
  background: var(--surface-glass);
  color: var(--text-primary);
}

.measure-metric-chip--active {
  background: var(--accent-bg);
  border-color: var(--accent-muted);
  color: var(--accent-text);
}

/* Consolidate */
.measure-consolidate-pill {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  cursor: pointer;
  font-size: var(--text-xs-size);
  color: var(--text-secondary);
  white-space: nowrap;
  /* Scale toggle to match chip height via PrimeVue design tokens */
  --p-toggleswitch-width: 2rem;
  --p-toggleswitch-height: 1.1rem;
  --p-toggleswitch-handle-size: 0.8rem;
}

/* Help btn */
.measure-help-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 0.2rem;
  border-radius: 50%;
  font-size: var(--text-base-size);
  line-height: var(--text-base-lh);
  transition: color 0.15s;
  flex-shrink: 0;
}

.measure-help-btn:hover {
  color: var(--accent-text);
}

/* Inline help (inside panel) */
.measure-help-inline {
  padding: 0.6rem 0;
  border-bottom: 1px solid var(--border-subtle);
  font-size: var(--text-xs-size);
  color: var(--text-secondary);
  line-height: var(--text-xs-lh);
}

.measure-help-inline p {
  margin: 0 0 0.3rem;
}

.measure-help-sub {
  margin-top: 0.35rem !important;
}

.help-slide-enter-active,
.help-slide-leave-active {
  transition:
    opacity 0.15s ease,
    max-height 0.2s ease;
  max-height: 16rem;
  overflow: hidden;
}

.help-slide-enter-from,
.help-slide-leave-to {
  opacity: 0;
  max-height: 0;
}

/* ── View toggle (mirrors stats style) ──────────────────── */
.measure-topbar-row--tabs {
  width: 100%;
  padding: 0.55rem 0 0.6rem;
}

.measure-view-toggle {
  display: flex;
  width: 100%;
  align-items: center;
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 8px;
  padding: 3px;
  gap: 2px;
}

.measure-toggle-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.3rem;
  padding: 0.28rem 0.65rem;
  border-radius: 5px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s;
  white-space: nowrap;
  font-family: inherit;
}

.measure-toggle-btn:hover {
  color: var(--text-secondary);
  background: var(--surface-hover);
}

.measure-toggle-btn--active {
  background: var(--surface-glass-heavy);
  color: var(--accent-text);
  box-shadow: var(--shadow-sm);
}

.measure-toggle-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.15rem;
  height: 1.15rem;
  padding: 0 0.3rem;
  border-radius: 999px;
  background: var(--accent-text);
  color: var(--text-inverse);
  font-size: var(--text-2xs-size);
  font-weight: 700;
  line-height: var(--text-2xs-lh);
  margin-left: 0.1rem;
}

.measure-select-cell {
  text-align: center;
  padding: 0.15rem 0.25rem !important;
}

.measure-select-checkbox {
  cursor: pointer;
  width: 1rem;
  height: 1rem;
  accent-color: var(--accent-text);
  margin: 0;
}

/* ── Tabs ────────────────────────────────────────────────── */
.measure-tabview {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
}

.measure-tabview :deep(.p-tablist) {
  display: none;
}

.measure-tabview :deep(.p-tabpanels) {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
  background: transparent;
  border: none;
}

.measure-tabview :deep(.p-tabpanel:not([data-p-active='true'])) {
  display: none !important;
}

.measure-tabview :deep(.p-tabpanel[data-p-active='true']) {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
  padding: 0.35rem 0.5rem calc(0.55rem + var(--safe-bottom));
}

/* ── Panels ──────────────────────────────────────────────── */
.panel-outer {
  flex: 0 0 auto;
  display: flex;
  min-width: 0;
}

.panel-stack {
  flex-direction: column;
  max-width: 100%;
}

.chart-panel {
  flex: 1 1 auto;
  min-height: 0;
  flex-direction: column;
  min-width: 0;
}

.race-panel {
  min-width: 0;
}

.results-container :deep(.p-button) {
  padding: 0.5rem;
}

.measure-results-table-wrap {
  width: 100%;
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  -webkit-overflow-scrolling: touch;
}

.measure-results-table {
  width: max-content;
  min-width: 100%;
}

.measure-graph-card {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  gap: 0.5rem;
  padding: 0.25rem 0;
}

.measure-graph-header {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  flex: 0 0 auto;
  padding: 0 0.2rem;
}

.measure-graph-title {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 700;
}

.measure-graph-title i {
  color: var(--accent-text);
}

.measure-graph-sub {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.measure-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 12rem;
  padding: 1.5rem 1.25rem;
  color: var(--text-secondary);
  text-align: center;
  gap: 0.5rem;
}

.measure-empty-icon {
  font-size: var(--text-3xl-size);
  color: var(--text-muted);
  opacity: 0.6;
  margin-bottom: 0.2rem;
}

.measure-empty-headline {
  margin: 0;
  font-size: var(--text-base-size);
  font-weight: 700;
  color: var(--text-primary);
}

.measure-empty-body {
  margin: 0;
  font-size: var(--text-sm-size);
  color: var(--text-muted);
  line-height: var(--text-sm-lh);
  max-width: 28rem;
}

.measure-empty-zones {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  margin-top: 0.25rem;
}

.measure-empty-zones-label {
  font-size: var(--text-xs-size);
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.measure-empty-zone-chip {
  display: inline-flex;
  align-items: center;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: var(--text-xs-size);
  font-weight: 700;
  border: 1px solid transparent;
}

.measure-empty-zone-chip--ok {
  background: var(--success-bg);
  border-color: color-mix(in srgb, var(--success) 32%, transparent);
  color: var(--success);
}

.measure-empty-zone-chip--zero {
  background: var(--warning-bg);
  border-color: color-mix(in srgb, var(--warning) 32%, transparent);
  color: var(--warning-text);
}

.measure-empty-tips {
  list-style: none;
  margin: 0.5rem 0 0;
  padding: 0;
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  text-align: left;
}

.measure-empty-tips li {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.2rem 0;
}

.measure-empty-tips li i {
  font-size: var(--text-sm-size);
  color: var(--text-muted);
  opacity: 0.7;
  flex-shrink: 0;
}

/* ── Help table ──────────────────────────────────────────── */
.measure-help-table {
  border-collapse: collapse;
  font-size: var(--text-xs-size);
  margin: 0.3rem 0;
}

.measure-help-table td {
  padding: 0.1rem 0.5rem 0.1rem 0;
  vertical-align: top;
}

.measure-help-table td:first-child {
  white-space: nowrap;
  min-width: 5rem;
}

/* ── Mobile ──────────────────────────────────────────────── */
@media screen and (max-width: 768px) {
  .results-container {
    gap: 0.4rem;
    padding: 0.1rem 0.1rem calc(0.45rem + var(--safe-bottom));
  }

  .measure-topbar {
    padding: 0.65rem 0.75rem 0;
  }

  .measure-metric-chip {
    font-size: var(--text-2xs-size);
    padding: 0.22rem 0.45rem;
  }

  .measure-results-table {
    font-size: var(--text-xs-size);
  }

  .measure-results-table :deep(th),
  .measure-results-table :deep(td) {
    padding: 0.3rem 0.28rem;
    white-space: nowrap;
  }

  .measure-results-table-wrap {
    border-radius: 0.85rem;
  }

  .measure-results-table :deep(.p-datatable-table) {
    width: auto;
    min-width: 100%;
  }
}

/* ── Status chip (stops) ─────────────────────────────────── */
.measure-status-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.32rem;
  padding: 0.18rem 0.55rem;
  border: none;
  border-radius: 999px;
  font-family: inherit;
  font-size: var(--text-xs-size);
  font-weight: 600;
  white-space: nowrap;
  line-height: var(--text-xs-lh);
}

.measure-status-chip i {
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.measure-status-chip--ok {
  background: var(--success-bg);
  color: var(--success);
}

.measure-status-chip--notes {
  background: var(--warning-bg);
  color: var(--warning-text);
  cursor: pointer;
}

.measure-status-chip--notes:hover,
.measure-status-chip--notes:focus-visible {
  color: var(--warning-text);
  outline: 1px solid var(--warning);
  outline-offset: 2px;
}

.measure-status-info-text {
  max-width: min(280px, calc(100vw - 2rem));
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-secondary);
  margin: 0;
  padding: 0.1rem 0;
  white-space: pre-line;
}

.measure-status-dur {
  opacity: 0.78;
  font-weight: 500;
}
</style>
