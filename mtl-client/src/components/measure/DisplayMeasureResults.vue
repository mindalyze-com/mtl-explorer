<template>
  <div class="dialog-flex-root results-container">
    <Tabs v-model:value="activeTab" class="measure-tabview">
      <!-- Single unified panel -->
      <div class="measure-topbar">

        <!-- Row 1: title + help toggle -->
        <div class="measure-topbar-row measure-topbar-row--head">
          <span class="measure-results-eyebrow">
            Sector Analyzer<template v-if="trackCount"> · {{ trackCount }} tracks</template>
          </span>
          <button class="measure-help-btn"
                  @click="helpVisible = !helpVisible"
                  :title="helpVisible ? 'Hide help' : 'How to read results'">
            <i :class="helpVisible ? 'bi bi-question-circle-fill' : 'bi bi-question-circle'"></i>
          </button>
        </div>

        <!-- Row 2: metric + visits -->
        <div class="measure-topbar-row measure-topbar-row--controls">
          <div class="measure-control-group">
            <span class="measure-control-label">Metric</span>
            <div class="measure-metric-chips">
              <button
                v-for="u in crossingUnits" :key="u"
                class="measure-metric-chip"
                :class="{ 'measure-metric-chip--active': crossingUnitSelected === u }"
                @click="crossingUnitSelected = u"
              >{{ u }}</button>
            </div>
          </div>
          <div class="measure-control-group">
            <span class="measure-control-label">Visits</span>
            <label class="measure-consolidate-pill"
                   :title="consolidateVisits ? 'Consolidated — click to show each visit' : 'Each visit shown — click to consolidate'">
              <ToggleSwitch v-model="consolidateVisits" />
              <span>{{ consolidateVisits ? 'Consolidated' : 'Each visit' }}</span>
            </label>
          </div>
        </div>

        <!-- Collapsible help -->
        <Transition name="help-slide">
          <div v-if="helpVisible" class="measure-help-inline">
            <p>Each column is a <strong>segment</strong> — passage between two trigger zones.</p>
            <table class="measure-help-table">
              <tbody>
                <tr><td><strong>A &rarr; B</strong></td><td>Zone A to zone B</td></tr>
                <tr><td><strong>B &rarr; A</strong></td><td>Return (reverse direction)</td></tr>
                <tr><td><strong>B &rarr; B</strong></td><td>Loop — left and returned to B</td></tr>
              </tbody>
            </table>
            <p class="measure-help-sub">Turn on <strong>Consolidated</strong> to average repeated crossings; off shows each visit numbered (A1→B1, B1→A2…).</p>
          </div>
        </Transition>

        <!-- Row 3: view toggle -->
        <div class="measure-topbar-row measure-topbar-row--tabs">
          <div class="measure-view-toggle">
            <button :class="['measure-toggle-btn', { 'measure-toggle-btn--active': activeTab === '0' }]" @click="activeTab = '0'">
              <i class="bi bi-table"></i> Table
            </button>
            <button :class="['measure-toggle-btn', { 'measure-toggle-btn--active': activeTab === '1' }]" @click="activeTab = '1'">
              <i class="bi bi-activity"></i> Graph
            </button>
            <button :class="['measure-toggle-btn', { 'measure-toggle-btn--active': activeTab === '2' }]" @click="activeTab = '2'">
              <i class="bi bi-trophy"></i> Race
            </button>
          </div>
        </div>

      </div>
      <TabPanels>
        <TabPanel value="0">
          <div class="panel-outer panel-stack">
            <div class="table-scroll-wrapper measure-results-table-wrap">
              <DataTable :value="resultsCalculated" columnResizeMode="fit"
                         responsiveLayout="scroll"
                         sortField="trackStartDate" :sortOrder="-1"
                         class="p-datatable-sm measure-results-table">
                <template #empty>
                  <div class="measure-empty-state">
                    <div class="measure-empty-icon">
                      <i class="bi bi-signpost-split"></i>
                    </div>
                    <h3 class="measure-empty-headline">No tracks cross all selected zones</h3>
                    <p class="measure-empty-body">
                      The analysis requires tracks to pass through <strong>every</strong> zone.
                      Some tracks may pass through individual zones but not all of them.
                    </p>
                    <div v-if="perZoneCounts && perZoneCounts.length" class="measure-empty-zones">
                      <span class="measure-empty-zones-label">Tracks per zone:</span>
                      <span v-for="zc in perZoneCounts" :key="zc.name"
                            class="measure-empty-zone-chip"
                            :class="zc.count === 0 ? 'measure-empty-zone-chip--zero' : 'measure-empty-zone-chip--ok'">
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
                <Column field="trackId" header="Id" :sortable="true" style="min-width: 4rem">
                  <template #body="slotProps">
                    <a class="link-style" @click="showTrackDetails(slotProps.data.trackId)" v-text="slotProps.data.trackId"/>
                  </template>
                </Column>
                <Column v-if="!isMobile" field="fileName" header="File Name" :sortable="true" style="min-width: 12rem"></Column>

                <Column field="trackStartDate" :header="isMobile ? 'Start' : 'Track Start Date'" :sortable="true" :style="isMobile ? 'min-width: 7rem' : 'min-width: 10rem'">
                  <template #body="slotProps">
                    {{ formatDate(slotProps.data.trackStartDate, isMobile) }}
                  </template>
                </Column>
                <Column v-if="!isMobile" field="trackDurationInMillis" header="Track Duration" :sortable="true" style="min-width: 8rem">
                  <template #body="slotProps">
                    <span v-tooltip.top="{ value: formatDurationTooltip(slotProps.data.trackDurationInMillis), showDelay: 400 }">
                      {{ formatDurationSmart(slotProps.data.trackDurationInMillis) }}
                    </span>
                  </template>
                </Column>

                <Column field="statusSort" header="Status" :sortable="true" :style="isMobile ? 'min-width: 6rem' : 'min-width: 9rem'">
                  <template #body="slotProps">
                    <span v-if="slotProps.data.status && slotProps.data.status.ok"
                          class="measure-status-chip measure-status-chip--ok"
                          :title="'No stops detected in any segment'">
                      <i class="bi bi-check-circle-fill"></i>
                      <span v-if="!isMobile">all good</span>
                    </span>
                    <span v-else-if="slotProps.data.status"
                          class="measure-status-chip measure-status-chip--notes"
                          v-tooltip.top="{ value: formatStopsTooltip(slotProps.data.status), showDelay: 200, escape: false }">
                      <i class="bi bi-exclamation-circle-fill"></i>
                      {{ slotProps.data.status.totalStopCount }} stop<span v-if="slotProps.data.status.totalStopCount !== 1">s</span>
                      <span v-if="!isMobile && slotProps.data.status.totalStoppedSec > 0" class="measure-status-dur">
                        · {{ formatStoppedDuration(slotProps.data.status.totalStoppedSec) }}
                      </span>
                    </span>
                  </template>
                </Column>

                <Column v-for="col of resultTableColumns" :key="col.field" :field="col.field" :header="col.header"
                        sortable :style="isMobile ? 'min-width: 5.75rem' : 'min-width: 8rem'">
                  <template #body="slotProps">
                    {{ slotProps.data[slotProps.field] }}
                  </template>
                </Column>
              </DataTable>
            </div>
          </div>
        </TabPanel>

        <TabPanel value="1">
          <div class="panel-outer chart-panel">
            <div class="measure-graph-card">
              <MeasureGraph :graphSeriesData="graphSeriesData"></MeasureGraph>
            </div>
          </div>
        </TabPanel>

        <TabPanel value="2">
          <div class="panel-outer race-panel">
            <VirtualRace :measureServiceResult="measureServiceResult" :consolidateVisits="consolidateVisits" @show-track-details="showTrackDetails"></VirtualRace>
          </div>
        </TabPanel>
      </TabPanels>
    </Tabs>

    <Dialog v-model:visible="gpsTrackDetailsVisible" maximizable modal header="Track Details"
      appendTo="body"
      class="tool-dialog measure-results">
      <TrackDetails :gps-track-id="gpsTrackDetailsId"></TrackDetails>
    </Dialog>

  </div>

</template>

<script>
import {defineComponent, inject} from "vue";
import MeasureGraph from "@/components/measure/MeasureGraph.vue";
import ToggleSwitch from 'primevue/toggleswitch';
import { formatDuration, formatNumber, formatDurationSmart, formatDurationTooltip, formatDateAndTime, formatDateCompact } from "@/utils/Utils";
import trackDetails from "@/components/trackdetails/TrackDetails.vue";
import TrackDetails from "@/components/trackdetails/TrackDetails.vue";
import VirtualRace from "@/components/virtual-race/VirtualRace.vue";

const COLUM_ID = {
  trackId: "trackId",
  fileName: "fileName",
  crossingDurationMillis: "crossingDurationMillis",
  trackStartDate: "trackStartDate",
  trackEndDate: "trackEndDate",
  trackDurationInMillis: "trackDurationInMillis",
  avgSpeedSinceLastTriggerPoint: "avgSpeedSinceLastTriggerPoint",
  distanceInMeterSinceLastTriggerPoint: "distanceInMeterSinceLastTriggerPoint",
  status: "status",
  statusSort: "statusSort",
}

const CROSSING_UNITS = {
  distance: "distance",
  speed: "speed",
  time: "time",
}

export default defineComponent({
  name: 'DisplayMeasureResults',
  components: {VirtualRace, TrackDetails, MeasureGraph, ToggleSwitch},
  directives: { },
  props: ["measureServiceResult"],
  data() {
    return {
      gpsTrackDetailsId: null,
      gpsTrackDetailsVisible: false,
      helpVisible: false,
      activeTab: '0',
      crossingUnits: [CROSSING_UNITS.speed, CROSSING_UNITS.time, CROSSING_UNITS.distance],
      crossingUnitSelected: CROSSING_UNITS.speed,
      consolidateVisits: true,
    }
  },
  computed: {
    trackDetails() {
      return trackDetails
    },
    crossingUnitDescription() {
      let msg = "";
      switch (this.crossingUnitSelected) {
        case CROSSING_UNITS.distance:
          msg = 'Segment stats display the segment distance (meter)';
          break;
        case CROSSING_UNITS.time:
          msg = 'Segment stats display time (duration hh:mm:ss)';
          break;
        case CROSSING_UNITS.speed:
          msg = 'Segment stats display average speed (km/h)';
          break;
      }
      return msg;
    },
    _allResults() {
      if (!this.measureServiceResult) {
        return { tableData: [], columns: [], graphSeries: [] };
      }

      const durationMillisPostfix = "-DurationMillis";
      const speedPostfix = "-speed";
      let allColumns = new Map();
      let tableData = [];

      for (const crossing of Object.values(this.measureServiceResult.crossings || {})) {
        let tableRow = {};

        let gpsTrack = crossing.gpsTrack;
        tableRow[COLUM_ID.trackId] = gpsTrack.id;
        tableRow[COLUM_ID.fileName] = gpsTrack.indexedFile.name;
        tableRow[COLUM_ID.trackStartDate] = gpsTrack.startDate;
        tableRow[COLUM_ID.trackEndDate] = gpsTrack.endDate;
        let duration = 0;
        if (gpsTrack.startDate && gpsTrack.endDate) {
          duration = (gpsTrack.endDate.getTime() - gpsTrack.startDate.getTime());
        }
        tableRow[COLUM_ID.trackDurationInMillis] = duration;

        let countPerTriggerPoint = new Map();
        let lastTrackCrossing = null;
        let consolidatedSegments = new Map();
        // Per-row stop notes: aggregate across all segments of this track.
        // Each entry: { label, stopCount, totalStoppedSec, longestStopSec }
        let segmentNotesList = [];

        crossing.crossings.map(trackCrossing => {
          let triggerPoint = trackCrossing.triggerPoint;
          if (countPerTriggerPoint.has(triggerPoint.name)) {
            countPerTriggerPoint.set(triggerPoint.name, countPerTriggerPoint.get(triggerPoint.name) + 1);
          } else {
            countPerTriggerPoint.set(triggerPoint.name, 1);
          }

          if (lastTrackCrossing != null) {
            let segmentP1, segmentP2;
            if (this.consolidateVisits) {
              segmentP1 = lastTrackCrossing.triggerPoint.name;
              segmentP2 = triggerPoint.name;
            } else {
              segmentP1 = lastTrackCrossing.triggerPoint.name + countPerTriggerPoint.get(lastTrackCrossing.triggerPoint.name);
              segmentP2 = triggerPoint.name + countPerTriggerPoint.get(triggerPoint.name);
            }
            let segmentLabel = segmentP1 + " - " + segmentP2;
            let segmentKey = segmentP1 + "-" + segmentP2;
            allColumns.set(segmentKey, segmentLabel);

            // Collect per-segment notes (stops) for this visit. Notes are on
            // the CURRENT crossing (they describe the segment ending at it).
            const notes = trackCrossing.segmentNotesSinceLastTriggerPoint;
            if (notes && notes.stopCount > 0) {
              segmentNotesList.push({
                label: segmentLabel,
                stopCount: notes.stopCount,
                totalStoppedSec: notes.totalStoppedSec || 0,
                longestStopSec: notes.longestStopSec || 0,
              });
            }

            if (this.consolidateVisits) {
              if (!consolidatedSegments.has(segmentKey)) {
                consolidatedSegments.set(segmentKey, { count: 0, totalTimeSec: 0, totalSpeed: 0, totalDistance: 0 });
              }
              let seg = consolidatedSegments.get(segmentKey);
              seg.count++;
              seg.totalTimeSec += (trackCrossing.timeInSecSinceLastTriggerPoint || 0);
              seg.totalSpeed += (trackCrossing.avgSpeedSinceLastTriggerPoint || 0);
              seg.totalDistance += (trackCrossing.distanceInMeterSinceLastTriggerPoint || 0);
            } else {
              switch (this.crossingUnitSelected) {
                case CROSSING_UNITS.time: {
                  tableRow[segmentKey + durationMillisPostfix] = trackCrossing.timeInSecSinceLastTriggerPoint * 1000;
                  tableRow[segmentKey] = this.formatDuration(trackCrossing.timeInSecSinceLastTriggerPoint * 1000);
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
                  console.error("no crossing unit given");
                }
              }

              tableRow[segmentKey + COLUM_ID.avgSpeedSinceLastTriggerPoint] = trackCrossing.avgSpeedSinceLastTriggerPoint;
              tableRow[segmentKey + COLUM_ID.distanceInMeterSinceLastTriggerPoint] = trackCrossing.distanceInMeterSinceLastTriggerPoint;
              tableRow[segmentKey + speedPostfix] = trackCrossing.avgSpeedSinceLastTriggerPoint;
            }
          }

          lastTrackCrossing = trackCrossing;
        });

        if (this.consolidateVisits) {
          for (const [segmentKey, seg] of consolidatedSegments) {
            let avgTimeSec = seg.totalTimeSec / seg.count;
            let avgSpeed = seg.totalSpeed / seg.count;
            let avgDistance = seg.totalDistance / seg.count;

            switch (this.crossingUnitSelected) {
              case CROSSING_UNITS.time: {
                tableRow[segmentKey + durationMillisPostfix] = avgTimeSec * 1000;
                tableRow[segmentKey] = this.formatDuration(avgTimeSec * 1000);
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

      let columns = [];
      allColumns.forEach((v, k) => {
        columns.push({field: k, header: v});
      });

      let graphSeries = [];
      allColumns.forEach((v1, k1) => {
        let data = [];
        tableData.forEach((v2) => {
          let speed = v2[k1 + speedPostfix];
          if (speed != null && v2[COLUM_ID.trackStartDate]) {
            data.push([v2[COLUM_ID.trackStartDate].getTime(), speed]);
          }
        });
        data.sort((a, b) => a[0] - b[0]);
        graphSeries.push({ name: v1, data });
      });

      return { tableData, columns, graphSeries };
    },
    resultsCalculated() {
      return this._allResults.tableData;
    },
    resultTableColumns() {
      return this._allResults.columns;
    },
    graphSeriesData() {
      return this._allResults.graphSeries;
    },
    trackCount() {
      return this.resultsCalculated.length;
    },
    perZoneCounts() {
      if (!this.measureServiceResult || !this.measureServiceResult.tracksPerZone) return null;
      const counts = this.measureServiceResult.tracksPerZone;
      return Object.entries(counts).map(([name, count]) => ({ name, count }));
    },
    isMobile() {
      if (typeof window === 'undefined') {
        return false;
      }
      return window.innerWidth <= 768;
    },
  },
  setup() {
    return {
      toast: inject("toast"),
    };
  },
  methods: {
    formatDate(date, compact = false) {
      return compact ? formatDateCompact(date) : formatDateAndTime(date);
    },
    formatDuration(durationInMillis) {
      return formatDuration(durationInMillis);
    },
    formatDurationSmart(durationInMillis) {
      return formatDurationSmart(durationInMillis);
    },
    formatDurationTooltip(durationInMillis) {
      return formatDurationTooltip(durationInMillis);
    },
    formatStoppedDuration(sec) {
      if (sec == null || sec <= 0) return '';
      const total = Math.round(sec);
      const h = Math.floor(total / 3600);
      const m = Math.floor((total % 3600) / 60);
      const s = total % 60;
      if (h > 0) return `${h}h ${m}m`;
      if (m > 0) return `${m}m ${s}s`;
      return `${s}s`;
    },
    formatStopsTooltip(status) {
      if (!status || status.ok) return 'No stops detected';
      const lines = [];
      lines.push(`${status.totalStopCount} stop${status.totalStopCount !== 1 ? 's' : ''} · total ${this.formatStoppedDuration(status.totalStoppedSec)}`);
      if (status.longestStopSec > 0 && status.totalStopCount > 1) {
        lines.push(`Longest: ${this.formatStoppedDuration(status.longestStopSec)}`);
      }
      if (status.segments && status.segments.length) {
        lines.push('—');
        for (const sn of status.segments) {
          lines.push(`${sn.label}: ${sn.stopCount}× · ${this.formatStoppedDuration(sn.totalStoppedSec)}`);
        }
      }
      return lines.join('\n');
    },
    showTrackDetails(e) {
      console.log("trackdetails", e);
      this.gpsTrackDetailsId = e;
      this.gpsTrackDetailsVisible = true;
    },
  },
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

.results-container {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  gap: 0.5rem;
  padding: 0.35rem 0.5rem calc(0.55rem + var(--safe-bottom));
}

/* ── Unified single panel ────────────────────────────────── */
.measure-topbar {
  display: flex;
  flex-direction: column;
  gap: 0;
  flex-shrink: 0;
  padding: 0.75rem 0.9rem 0;
}

/* Rows */
.measure-topbar-row {
  display: flex;
  align-items: center;
  min-width: 0;
}

.measure-topbar-row--head {
  gap: 0.5rem;
  margin-bottom: 0.65rem;
}

.measure-topbar-row--controls {
  gap: 1rem;
  flex-wrap: wrap;
  padding-bottom: 0.7rem;
  border-bottom: 1px solid var(--border-subtle);
}

.measure-topbar-row--tabs {
  /* tabs row flush with panel edges */
}

.measure-results-eyebrow {
  flex: 1 1 auto;
  display: inline-flex;
  align-items: center;
  padding: 0.22rem 0.6rem;
  border-radius: 999px;
  background: var(--accent-bg);
  color: var(--accent-text);
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  white-space: nowrap;
  width: fit-content;
  flex: 0 0 auto;
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
  font-size: 0.68rem;
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
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.03em;
  text-transform: capitalize;
  padding: 0.25rem 0.6rem;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
  line-height: 1;
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
  font-size: 0.72rem;
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
  font-size: 0.9rem;
  line-height: 1;
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
  font-size: 0.78rem;
  color: var(--text-secondary);
  line-height: 1.55;
}

.measure-help-inline p {
  margin: 0 0 0.3rem;
}

.measure-help-sub {
  margin-top: 0.35rem !important;
}

.help-slide-enter-active,
.help-slide-leave-active {
  transition: opacity 0.15s ease, max-height 0.2s ease;
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
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
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

/* ── Tabs ────────────────────────────────────────────────── */
.measure-tabview {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}

.measure-tabview :deep(.p-tablist) {
  display: none;
}

.measure-tabview :deep(.p-tabpanels) {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding: 0.25rem 0 0;
  background: transparent;
  border: none;
}

.measure-tabview :deep(.p-tabpanel:not([data-p-active="true"])) {
  display: none !important;
}

.measure-tabview :deep(.p-tabpanel[data-p-active="true"]) {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
  overflow: auto;
  -webkit-overflow-scrolling: touch;
}

/* ── Panels ──────────────────────────────────────────────── */
.panel-outer {
  flex: 1 1 auto;
  display: flex;
  min-height: 0;
  min-width: 0;
}

.panel-stack {
  flex-direction: column;
  max-width: 100%;
  overflow: hidden;
}

.chart-panel,
.race-panel {
  min-width: 0;
}

.results-container :deep(.p-button) {
  padding: 0.5rem;
}

.measure-results-table-wrap {
  width: 100%;
  max-width: 100%;
  border-radius: 1rem;
  border: 1px solid var(--border-default);
  background: var(--surface-glass);
  box-shadow: inset 0 1px 0 var(--border-subtle);
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
  padding: 0.6rem;
  background: linear-gradient(180deg, var(--surface-glass-heavy), var(--surface-glass));
  border: 1px solid var(--border-medium);
  border-radius: 1.15rem;
  box-shadow: var(--shadow-md);
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
  font-size: 2rem;
  color: var(--text-muted);
  opacity: 0.6;
  margin-bottom: 0.2rem;
}

.measure-empty-headline {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: var(--text-primary);
}

.measure-empty-body {
  margin: 0;
  font-size: 0.82rem;
  color: var(--text-muted);
  line-height: 1.55;
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
  font-size: 0.72rem;
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
  font-size: 0.75rem;
  font-weight: 700;
  border: 1px solid transparent;
}

.measure-empty-zone-chip--ok {
  background: rgba(34, 197, 94, 0.12);
  border-color: rgba(34, 197, 94, 0.3);
  color: #16a34a;
}

.measure-empty-zone-chip--zero {
  background: rgba(245, 158, 11, 0.12);
  border-color: rgba(245, 158, 11, 0.3);
  color: #d97706;
}

.measure-empty-tips {
  list-style: none;
  margin: 0.5rem 0 0;
  padding: 0;
  font-size: 0.78rem;
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
  font-size: 0.85rem;
  color: var(--text-muted);
  opacity: 0.7;
  flex-shrink: 0;
}

/* ── Help table ──────────────────────────────────────────── */
.measure-help-table {
  border-collapse: collapse;
  font-size: 0.73rem;
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

  .measure-topbar-row--controls {
    gap: 0.65rem;
  }

  .measure-metric-chip {
    font-size: 0.68rem;
    padding: 0.22rem 0.45rem;
  }

  .measure-results-table {
    font-size: 0.75rem;
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
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 600;
  white-space: nowrap;
  line-height: 1.1;
}

.measure-status-chip i {
  font-size: 0.82rem;
  line-height: 1;
}

.measure-status-chip--ok {
  background: color-mix(in srgb, var(--success, #16a34a) 12%, transparent);
  color: var(--success, #16a34a);
}

.measure-status-chip--notes {
  background: color-mix(in srgb, var(--warning, #d97706) 14%, transparent);
  color: var(--warning, #d97706);
  cursor: help;
}

.measure-status-dur {
  opacity: 0.78;
  font-weight: 500;
}

</style>
