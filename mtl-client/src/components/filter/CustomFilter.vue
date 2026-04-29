<template>
  <BottomSheet
    :model-value="show"
    @update:model-value="$emit('update:show', $event)"
    :detents="[{ height: '72vh' }, { height: '95vh' }]"
    @closed="$emit('closed')"
  >
    <!-- ── Header: 3 pill tabs, Stats-style ── -->
    <template #title>
      <div class="cf-header-nav">
        <i class="bi bi-funnel cf-sheet-icon"></i>
        <div class="cf-header-tabs" role="tablist" aria-label="Filter views">
          <button
            class="cf-tab"
            :class="{ 'cf-tab--active': activeView === 'filter' }"
            @pointerdown.stop
            @click="activeView = 'filter'"
            role="tab"
          >Filter</button>
          <button
            class="cf-tab"
            :class="{ 'cf-tab--active': activeView === 'colors', 'cf-tab--disabled': !colorsTabEnabled }"
            :disabled="!colorsTabEnabled"
            @pointerdown.stop
            @click="colorsTabEnabled && (activeView = 'colors')"
            role="tab"
          >
            Colors
            <span v-if="colorsBadge" class="cf-tab__badge">{{ colorsBadge }}</span>
          </button>
          <button
            class="cf-tab cf-tab--compact"
            :class="{ 'cf-tab--active': activeView === 'sql', 'cf-tab--disabled': !sqlTabEnabled }"
            :disabled="!sqlTabEnabled"
            @pointerdown.stop
            @click="sqlTabEnabled && (activeView = 'sql')"
            role="tab"
            aria-label="SQL"
            title="SQL expression"
          >
            <i class="bi bi-code-slash"></i>
            <span class="cf-tab__label-sql">SQL</span>
          </button>
        </div>
      </div>
    </template>

    <!-- ── Body ── -->
    <div class="filter-root">
      <div class="filter-scroll">

        <!-- Compact status strip (always visible, borderless) -->
        <header class="cf-status" :class="{ 'cf-status--on': filterEnabled }">
          <div class="cf-status__main">
            <div class="cf-status__title-row">
              <span class="cf-status__dot-state" :class="{ 'cf-status__dot-state--on': filterEnabled }"></span>
              <h2 class="cf-status__title">{{ filterEnabled ? selectedFilterLabel : 'All tracks visible' }}</h2>
            </div>
            <p class="cf-status__meta" v-if="filterEnabled">
              <span><strong>{{ activeTrackCountDisplay }}</strong> of {{ totalTrackCountDisplay }} tracks</span>
              <span v-if="previewGroupCount > 0" class="cf-status__meta-sep"> · <strong>{{ previewGroupCount }}</strong> group{{ previewGroupCount === 1 ? '' : 's' }}</span>
              <span v-if="palettePreviewColors.length > 0" class="cf-status__swatches">
                <span v-for="(c, i) in palettePreviewColors.slice(0, 5)" :key="i" class="cf-status__dot" :style="{ backgroundColor: c }"></span>
              </span>
              <span v-if="isPreviewLoading" class="cf-status__meta-sep"> · <i class="bi bi-arrow-repeat cf-spin"></i> updating</span>
            </p>
            <p class="cf-status__meta cf-status__meta--muted" v-else>No filter applied — showing all {{ totalTrackCountDisplay }} tracks.</p>
          </div>
          <label class="toggle-switch" :aria-label="filterEnabled ? 'Disable filter' : 'Enable filter'">
            <input type="checkbox" :checked="filterEnabled" @change="filterEnabled ? disableFilter() : enableFilter()" />
            <span :class="['toggle-switch__track', filterEnabled && 'toggle-switch__track--on']"><span class="toggle-switch__thumb"></span></span>
            <span :class="['toggle-switch__text', filterEnabled && 'toggle-switch__text--on']">{{ filterEnabled ? 'On' : 'Off' }}</span>
          </label>
        </header>

        <!-- Off: explain how to enable; fills the body across all tabs -->
        <div v-if="!filterEnabled" class="cf-off-card">
          <div class="cf-off-card__icon"><i class="bi bi-funnel"></i></div>
          <h3 class="cf-off-card__title">Filtering is off</h3>
          <p class="cf-off-card__text">
            Turn filtering on to narrow tracks by type, date or geo area, group them, and color the map.
            The <strong>Colors</strong> and <strong>SQL</strong> tabs unlock once a filter is selected.
          </p>
          <button class="cf-primary-btn" type="button" @click="enableFilter">
            <i class="bi bi-power"></i> Enable filter
          </button>
        </div>

        <!-- ── FILTER tab ── -->
        <div v-else-if="activeView === 'filter'" class="cf-tab-body">
          <div class="cf-field">
            <label class="cf-field__label">Filter type</label>
            <Select
              v-if="selectedFilter"
              v-model="selectedFilter.filterInfo"
              :options="filters"
              optionLabel="filterConfig.displayName"
              placeholder="Select a filter"
              class="filter-full-width"
              @change="onFilterInfoChanged"
              appendTo="body"
            />
            <p v-if="selectedFilter?.filterInfo?.filterConfig?.description" class="cf-field__hint">
              {{ selectedFilter.filterInfo.filterConfig.description }}
            </p>
          </div>

          <section v-if="hasParams" class="cf-section">
            <div class="cf-section__head">
              <h3 class="cf-section__title">Parameters</h3>
              <span class="cf-section__count">{{ selectedFilter?.filterInfo?.paramDefinitions?.length ?? 0 }}</span>
            </div>
            <div class="cf-params-grid">
              <div v-for="pd in (selectedFilter?.filterInfo?.paramDefinitions || [])" :key="pd.name" class="cf-field">
                <template v-if="pd.type === 'DATE_TIME' && pd.name && selectedFilter?.filterParams?.dateTimeParams">
                  <label class="cf-field__label" :for="pd.name">{{ pd.label }}</label>
                  <DatePicker
                    :model-value="getDateTimeParam(pd.name)"
                    @update:model-value="setDateTimeParam(pd.name, $event)"
                    :id="pd.name"
                    :showTime="true"
                    dateFormat="dd.mm.yy"
                    hourFormat="24"
                    show-seconds
                    placeholder="select a date"
                    @date-select="onParamChanged"
                    class="filter-full-width"
                  />
                </template>
                <template v-else-if="(pd.type === 'GEO_CIRCLE' || pd.type === 'GEO_RECTANGLE' || pd.type === 'GEO_POLYGON') && pd.name">
                  <GeoShapeParam
                    :paramDef="pd"
                    :circle="pd.type === 'GEO_CIRCLE' ? selectedFilter?.filterParams?.geoCircles?.[pd.name] : undefined"
                    :rectangle="pd.type === 'GEO_RECTANGLE' ? selectedFilter?.filterParams?.geoRectangles?.[pd.name] : undefined"
                    :polygon="pd.type === 'GEO_POLYGON' ? selectedFilter?.filterParams?.geoPolygons?.[pd.name] : undefined"
                    @start-geo-drawing="onStartGeoDrawing"
                    @clear-geo-shape="onClearGeoShape"
                  />
                </template>
                <template v-else-if="pd.name && selectedFilter?.filterParams?.stringParams">
                  <label class="cf-field__label" :for="pd.name">{{ pd.label }}</label>
                  <InputText
                    v-model="selectedFilter.filterParams.stringParams[pd.name]"
                    :id="pd.name"
                    placeholder="enter a value"
                    class="filter-full-width"
                    @input="onParamChanged"
                  />
                </template>
              </div>
            </div>
          </section>

          <!-- Inline preview summary + CTA jump to Colors -->
          <div v-if="previewResult || isPreviewLoading || previewError" class="cf-preview-mini">
            <div v-if="isPreviewLoading" class="cf-preview-mini__text">
              <i class="bi bi-arrow-repeat cf-spin"></i> Updating preview…
            </div>
            <div v-else-if="previewError" class="cf-preview-mini__text cf-preview-mini__text--err">
              <i class="bi bi-exclamation-triangle"></i> {{ previewError }}
            </div>
            <div v-else class="cf-preview-mini__text">
              <strong>{{ previewResult?.resultEntries?.length ?? 0 }}</strong> matching tracks<span v-if="previewGroupCount > 0"> across <strong>{{ previewGroupCount }}</strong> groups</span>.
            </div>
            <button
              v-if="canOpenResultsView"
              class="cf-link-btn"
              type="button"
              @click="activeView = 'colors'"
            >Open Colors <i class="bi bi-arrow-right"></i></button>
          </div>
        </div>

        <!-- ── COLORS tab ── -->
        <div v-else-if="activeView === 'colors'" class="cf-tab-body">
          <div class="cf-field">
            <label class="cf-field__label">Color palette</label>
            <Select
              v-if="selectedFilter"
              v-model="selectedFilter.palette"
              :options="colorPaletteList"
              optionLabel="pLabel"
              placeholder="No coloring"
              :show-clear="true"
              class="filter-full-width"
              appendTo="body"
              @change="onPaletteChanged"
            />
            <div v-if="palettePreviewColors.length > 0" class="palette-preview">
              <span
                v-for="(color, index) in palettePreviewColors"
                :key="`${paletteLabel}-${index}-${color}`"
                class="palette-preview__swatch"
                :style="{ backgroundColor: color }"
              ></span>
              <span v-if="paletteOverflowCount > 0" class="palette-preview__more">+{{ paletteOverflowCount }}</span>
            </div>
            <p class="cf-field__hint">{{ paletteReviewHelpText }}</p>
          </div>

          <section class="cf-section">
            <div class="cf-section__head">
              <h3 class="cf-section__title">{{ previewSectionTitle }}</h3>
              <span v-if="previewResult" class="cf-section__count">{{ previewResult.resultEntries?.length ?? 0 }}</span>
            </div>
            <p v-if="previewSectionNote" class="cf-section__note">{{ previewSectionNote }}</p>

            <div v-if="isPreviewLoading" class="preview-skeleton">
              <div class="skeleton-line skeleton-line--wide"></div>
              <div class="skeleton-line skeleton-line--medium"></div>
              <div class="skeleton-line skeleton-line--narrow"></div>
            </div>

            <template v-else-if="previewResult">
              <ul v-if="previewHasColors" class="legend-list">
                <li v-for="row in previewLegend" :key="row.group" class="legend-row"
                    @click="openGroupDrillDown(row.group)">
                  <span class="legend-swatch" :style="{ backgroundColor: row.color }"></span>
                  <span class="legend-group-wrap">
                    <span class="legend-group">{{ row.group }}</span>
                    <span class="legend-subtitle">Tap to review matching tracks</span>
                  </span>
                  <span class="legend-count">{{ row.count }}</span>
                  <i class="bi bi-chevron-right legend-chevron"></i>
                </li>
              </ul>

              <div v-else-if="previewGroupCount > 0" class="preview-hint">
                <i class="bi bi-palette preview-hint__icon"></i>
                <span>This filter returns groups. Pick a palette above to reflect those groups on the map.</span>
              </div>

              <div v-else class="preview-plain-state">
                <i class="bi bi-list-check"></i>
                <span>This filter narrows the track list directly and does not return grouped results.</span>
              </div>
            </template>

            <div v-else-if="previewError" class="preview-error preview-error--card">
              <i class="bi bi-exclamation-triangle"></i>
              <span>{{ previewError }}</span>
            </div>

            <div v-else class="preview-plain-state">
              <i class="bi bi-arrow-left-right"></i>
              <span>Adjust the filter to generate results here.</span>
            </div>
          </section>
        </div>

        <!-- ── SQL tab ── -->
        <div v-else-if="activeView === 'sql'" class="cf-tab-body">
          <div class="cf-field">
            <label class="cf-field__label">SQL expression</label>
            <div class="sql-mode-toggle">
              <button
                :class="['sql-mode-btn', sqlViewMode === 'template' && 'sql-mode-btn--active']"
                @click="sqlViewMode = 'template'"
                type="button"
              >Template</button>
              <button
                :class="['sql-mode-btn', sqlViewMode === 'resolved' && 'sql-mode-btn--active']"
                @click="sqlViewMode = 'resolved'"
                type="button"
              >Resolved</button>
            </div>
            <p class="cf-field__hint" v-if="sqlViewMode === 'template'">
              The raw SQL expression stored in the database. Sub-queries are referenced as Thymeleaf fragments
              (<code>[[~{...}]]</code>) and <code>:PARAM_NAME</code> placeholders are not yet substituted.
            </p>
            <p class="cf-field__hint" v-else>
              The fully expanded SQL as produced by the server. Thymeleaf fragments are already inlined, while
              <code>:PARAM_NAME</code> placeholders are still bound at execution time.
            </p>
          </div>
          <div class="sql-block__code">
            <highlightjs language="sql" :code="sqlViewMode === 'template' ? rawSQL : resolvedSQL" />
          </div>
        </div>

      </div>
    </div>

    <!-- ══ Drill-down sheet (stacks above main sheet) ══ -->
    <BottomSheet v-model="showDrillDown"
                 :title="drillDownTitle"
                 icon="bi bi-table"
                 :detents="[{ height: '75vh' }, { height: '95vh' }]"
                 :no-backdrop="true"
                 :zIndex="5100">
      <div class="review-root">
        <div class="review-navigation">
          <Button label="Back" icon="pi pi-arrow-left" @click="showDrillDown = false" class="p-button-secondary p-button-sm"/>
        </div>
        <div v-if="isDrillDownLoading" class="review-intro">Loading track details…</div>
        <div class="review-scroll">
          <DataTable
            :value="drillDownEntries"
            class="p-datatable-gridlines my-datatable"
            responsiveLayout="scroll"
            paginator
            :rows="10"
            :rowsPerPageOptions="[10, 50, 100, 500, 1000]"
            removable-sort
          >
            <Column field="id" header="ID" sortable/>
            <Column header="" style="width: 3.5rem; min-width: 3.5rem; max-width: 3.5rem">
              <template #body="slotProps">
                <TrackShapePreview :trackId="slotProps.data.id" :width="48" :height="32" :padding="3" />
              </template>
            </Column>
            <Column field="group" header="Group" sortable>
              <template #body="slotProps">
                <div style="white-space: nowrap;">
                  <span>{{ slotProps.data.group }}</span>
                  <span class="color-indicator"
                    :style="{ backgroundColor: selectedFilter?.palette?.getColorForGroup?.(slotProps.data.group) }"></span>
                </div>
              </template>
            </Column>
            <Column field="gpsTrack.startDate" header="Start Date" sortable>
              <template #body="slotProps">
                {{ formatDateAndTime(slotProps.data.gpsTrack?.startDate) }}
              </template>
            </Column>
            <Column field="gpsTrack.trackName" header="Track Name" sortable/>
            <Column field="gpsTrack.trackDescription" header="Description" sortable/>
          </DataTable>
        </div>
      </div>
    </BottomSheet>

  </BottomSheet>
</template>

<script lang="ts">
import {defineComponent, inject, markRaw} from "vue";
import {formatDateAndTime} from "@/utils/Utils";
import {ClientFilterConfig, type FilterParamsRequest, FilterService} from "@/components/filter/FilterService";
import {useFilterStore} from "@/stores/filterStore";
import type {FilterInfo} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type {ParamDefinition} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';
import type {QueryResult} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/QueryResult';
import type {QueryResultEntry} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/QueryResultEntry';
import {fetchFilters, fetchResolveFilter} from "@/utils/ServiceHelper";
import {format} from "date-fns";
import {ColorPalette} from "@/components/filter/ColorPalette";

import 'highlight.js/lib/common';
import hljsVuePlugin from "@highlightjs/vue-plugin";
import BottomSheet from "@/components/ui/BottomSheet.vue";
import GeoShapeParam from "@/components/filter/GeoShapeParam.vue";
import TrackShapePreview from "@/components/ui/TrackShapePreview.vue";

const EVENTS = {
  filterAppliedEvent: "filterAppliedEvent",
  filterChangedEvent: "filterChangedEvent",
  startGeoDrawing: "start-geo-drawing",
  clearGeoShape: "clear-geo-shape",
} as const;

export default defineComponent({
  name: 'CustomFilter',
  components: {
    highlightjs: hljsVuePlugin.component,
    BottomSheet,
    GeoShapeParam,
    TrackShapePreview,
  },
  props: {
    tileLayer: { type: Object, default: null },
    palette: { type: Object, default: null },
    totalTrackCount: { type: Number, default: null },
    visibleTrackCount: { type: Number, default: null },
    show: { type: Boolean, default: false },
  },

  emits: [
    'update:show',
    'closed',
    'filterAppliedEvent',
    'filterChangedEvent',
    'start-geo-drawing',
    'clear-geo-shape',
  ],

  data(): {
    filters: FilterInfo[];
    selectedFilter: ClientFilterConfig;
    colorPaletteList: ColorPalette[];
    filterEnabled: boolean,
    activeView: 'filter' | 'colors' | 'sql',
    previewResult: QueryResult | null,
    isPreviewLoading: boolean,
    previewError: string | null,
    previewDebounceTimer: ReturnType<typeof setTimeout> | null,
    previewAbortController: AbortController | null,
    sqlViewMode: 'template' | 'resolved',
    showDrillDown: boolean,
    drillDownGroup: string | null,
    drillDownFullResult: QueryResult | null,
    isDrillDownLoading: boolean,
  } {
    return {
      filters: [] as FilterInfo[],
      selectedFilter: new ClientFilterConfig(),
      colorPaletteList: [] as ColorPalette[],
      // Toggle state
      filterEnabled: false,
      activeView: 'filter' as 'filter' | 'colors' | 'sql',
      // Live preview
      previewResult: null as QueryResult | null,
      isPreviewLoading: false,
      previewError: null as string | null,
      previewDebounceTimer: null as ReturnType<typeof setTimeout> | null,
      previewAbortController: null as AbortController | null,
      // SQL display mode
      sqlViewMode: 'template' as 'template' | 'resolved',
      // Drill-down
      showDrillDown: false,
      drillDownGroup: null as string | null,
      drillDownFullResult: null as QueryResult | null,
      isDrillDownLoading: false,
    };
  },

  setup() {
    return {
      toast: inject("toast") as { add: (options: { severity: string; summary: string; detail: string; life: number }) => void },
      formatDateAndTime,
      filterStore: useFilterStore(),
    };
  },

  async mounted() {
    try {
      this.colorPaletteList = await ColorPalette.fetch();
      const emptyPalette = new ColorPalette();
      emptyPalette.pLabel = "No coloring";
      emptyPalette.pDescription = "No coloring at all";
      this.colorPaletteList.unshift(emptyPalette);

      const filters = await fetchFilters();
      this.filters = filters.sort((a, b) => (a.filterConfig?.displayOrder ?? 0) - (b.filterConfig?.displayOrder ?? 0));

      const clientFilterConfig = await FilterService.loadClientFilterConfig();
      const filterConfig = clientFilterConfig?.filterInfo?.filterConfig;

      if (filterConfig?.id) {
        const selectedFilterInfo = filters.find(f => f.filterConfig?.id === filterConfig.id);
        if (selectedFilterInfo) {
          clientFilterConfig.filterInfo = selectedFilterInfo;
          // Ensure sub-maps exist
          if (!clientFilterConfig.filterParams.stringParams) clientFilterConfig.filterParams.stringParams = {};
          if (!clientFilterConfig.filterParams.dateTimeParams) clientFilterConfig.filterParams.dateTimeParams = {};
          // Restore DATE_TIME string params to Date objects (localStorage serialises them as strings)
          for (const key in clientFilterConfig.filterParams.dateTimeParams) {
            const val = clientFilterConfig.filterParams.dateTimeParams[key];
            if (val && typeof val === 'string') {
              (clientFilterConfig.filterParams.dateTimeParams as Record<string, unknown>)[key] = new Date(val.replace(' ', 'T'));
            }
          }
          this.selectedFilter = this.normalizeClientFilterConfig(clientFilterConfig);
        }
      }

      // Determine initial toggle state: ON if a non-default filter is persisted
      this.filterEnabled = !this.isStandardFilterState(this.selectedFilter);

      // If filter is active, trigger an initial live preview
      if (this.filterEnabled) {
        this.scheduleLivePreview();
      }
    } catch (error) {
      console.error("Error in mounted lifecycle hook:", error);
    }
  },

  watch: {
    // If the currently-active tab becomes disabled, fall back to the Filter tab.
    colorsTabEnabled(enabled: boolean) {
      if (!enabled && this.activeView === 'colors') this.activeView = 'filter';
    },
    sqlTabEnabled(enabled: boolean) {
      if (!enabled && this.activeView === 'sql') this.activeView = 'filter';
    },
  },

  computed: {
    hasParams(): boolean {
      return (this.selectedFilter?.filterInfo?.paramDefinitions?.length ?? 0) > 0;
    },
    selectedFilterLabel(): string {
      return this.selectedFilter?.filterInfo?.filterConfig?.displayName ?? 'Select a filter';
    },
    paletteLabel(): string {
      return this.selectedFilter?.palette?.pLabel || 'No coloring';
    },
    palettePreviewColors(): string[] {
      return this.selectedFilter?.palette?.pColors?.slice(0, 6) || [];
    },
    paletteOverflowCount(): number {
      const total = this.selectedFilter?.palette?.pColors?.length || 0;
      return Math.max(total - this.palettePreviewColors.length, 0);
    },
    paletteReviewHelpText(): string {
      if (!this.previewResult) {
        return 'Choose colors here so grouped results are ready to read as soon as the preview updates.';
      }
      if (this.previewGroupCount === 0) {
        return 'This filter returns a direct list of tracks, so the palette is currently optional.';
      }
      if (this.previewHasColors) {
        return 'The legend below uses this palette and the same group-to-color mapping is applied on the map.';
      }
      return 'Choose colors where the grouping is visible. The legend below updates with the same mapping used on the map.';
    },
    filterHeroSummary(): string {
      if (!this.filterEnabled) {
        return `All ${this.totalTrackCountDisplay} tracks are currently visible. Enable filtering to narrow, group, or color the map output.`;
      }
      if (this.previewError) {
        return `${this.selectedFilterLabel} is active, but the latest preview needs attention. Review your parameters or inspect the result state.`;
      }
      if (this.isPreviewLoading) {
        return `${this.selectedFilterLabel} is active. The preview and map are updating with your latest changes.`;
      }
      if (this.previewResult) {
        return `${this.selectedFilterLabel} is active with ${this.activeTrackCountDisplay} matching tracks${this.previewGroupCount > 0 ? ` across ${this.previewGroupCount} groups` : ''}.`;
      }
      return `${this.selectedFilterLabel} is active. Configure parameters, then inspect the live result breakdown.`;
    },
    activeTrackCountDisplay(): string {
      if (this.previewResult) return String(this.previewResult.resultEntries?.length ?? 0);
      if (this.visibleTrackCount != null) return String(this.visibleTrackCount);
      return '—';
    },
    totalTrackCountDisplay(): string {
      if (this.totalTrackCount != null) return String(this.totalTrackCount);
      return '—';
    },
    previewGroupCountDisplay(): string {
      if (!this.previewResult) return '—';
      return String(this.previewGroupCount);
    },
    previewGroupCount(): number {
      if (!this.previewResult?.resultEntries) return 0;
      const groups = new Set(this.previewResult.resultEntries.map((e: QueryResultEntry) => e.group).filter(Boolean));
      return groups.size;
    },
    previewHasColors(): boolean {
      const palette = this.selectedFilter?.palette;
      return !!(palette && !palette.isEmptyColorPalette() && this.previewGroupCount > 0);
    },
    previewLegend(): Array<{ group: string; color: string; count: number }> {
      if (!this.previewHasColors || !this.previewResult?.resultEntries) return [];
      const counts = new Map<string, number>();
      for (const entry of this.previewResult.resultEntries) {
        const g = entry.group;
        if (g) counts.set(g, (counts.get(g) || 0) + 1);
      }
      const palette = this.selectedFilter.palette;
      return Array.from(counts.entries()).map(([group, count]) => ({
        group,
        color: palette.getColorForGroup(group),
        count,
      }));
    },
    canOpenResultsView(): boolean {
      return !!this.previewResult || !!this.previewError;
    },
    colorsTabEnabled(): boolean {
      return this.filterEnabled && (this.canOpenResultsView || this.isPreviewLoading);
    },
    sqlTabEnabled(): boolean {
      return this.filterEnabled && !!this.rawSQL;
    },
    colorsBadge(): string {
      if (this.isPreviewLoading) return '…';
      if (this.previewError) return '!';
      if (this.previewResult) return String(this.previewResult.resultEntries?.length ?? 0);
      return '';
    },
    resultsBadge(): string {
      if (this.isPreviewLoading) return '…';
      if (this.previewError) return '!';
      if (this.previewResult) return String(this.previewResult.resultEntries?.length ?? 0);
      return '';
    },
    previewSectionTitle(): string {
      if (this.previewGroupCount > 0) return 'Grouped breakdown';
      if (this.previewResult) return 'Filtered track list';
      if (this.previewError) return 'Preview issue';
      if (this.isPreviewLoading) return 'Refreshing preview';
      return 'Preview';
    },
    previewSectionNote(): string {
      if (this.previewHasColors) return 'Each group below uses the same color that appears on the map. Select a group to review its tracks.';
      if (this.previewGroupCount > 0) return 'The filter already groups the result. Add a palette above if you want those groups to stand out on the map.';
      if (this.previewResult) return 'This filter does not create groups, so the map simply reflects the narrowed set of tracks.';
      if (this.previewError) return 'The preview could not be rendered with the current parameters.';
      return '';
    },
    drillDownTitle(): string {
      if (!this.drillDownGroup) return 'All tracks';
      const count = this.drillDownEntries.length;
      return `${this.drillDownGroup} — ${count} tracks`;
    },
    rawSQL(): string {
      return this.selectedFilter?.filterInfo?.filterConfig?.expression ?? '';
    },
    resolvedSQL(): string {
      return this.selectedFilter?.filterInfo?.resolvedSQL ?? '';
    },
    drillDownEntries(): QueryResultEntry[] {
      if (!this.drillDownFullResult?.resultEntries) return [];
      if (!this.drillDownGroup) return this.drillDownFullResult.resultEntries;
      return this.drillDownFullResult.resultEntries.filter((e: QueryResultEntry) => e.group === this.drillDownGroup);
    },
  },

  methods: {
    getDateTimeParam(name: string | undefined): Date | null {
      if (!name) return null;
      const map = (this.selectedFilter as ClientFilterConfig | undefined)?.filterParams?.dateTimeParams as Record<string, unknown> | undefined;
      const v = map?.[name];
      if (v instanceof Date) return v;
      if (typeof v === 'string' && v) return new Date(v.replace(' ', 'T'));
      return null;
    },
    setDateTimeParam(name: string | undefined, value: Date | Date[] | (Date | null)[] | null | undefined): void {
      if (!name) return;
      const map = (this.selectedFilter as ClientFilterConfig).filterParams.dateTimeParams as Record<string, unknown> | undefined;
      if (!map) return;
      map[name] = value as unknown as string;
      this.onParamChanged();
    },
    normalizeClientFilterConfig(config: ClientFilterConfig | null | undefined): ClientFilterConfig {
      const normalized = new ClientFilterConfig();
      normalized.filterInfo = config?.filterInfo ?? ({} as FilterInfo);
      const fp = config?.filterParams || {};
      normalized.filterParams = {
        ...fp,
        stringParams: fp.stringParams || {},
        dateTimeParams: fp.dateTimeParams || {},
        geoCircles: fp.geoCircles || {},
        geoRectangles: fp.geoRectangles || {},
        geoPolygons: fp.geoPolygons || {},
      };
      normalized.palette = ColorPalette.of((config?.palette || {}) as any);
      return normalized;
    },
    isEmptyParamGroup(group?: Record<string, unknown>): boolean {
      return !group || Object.keys(group).length === 0;
    },
    isStandardFilterState(config: { filterInfo?: FilterInfo; filterParams?: FilterParamsRequest } | null | undefined): boolean {
      return FilterService.isStandardFilterWithStandardParams(config as ClientFilterConfig);
    },
    // ── Toggle ──
    enableFilter() {
      this.filterEnabled = true;
      this.activeView = 'filter';
      // Guarantee sub-maps exist before the params form renders
      this.selectedFilter = this.normalizeClientFilterConfig(this.selectedFilter as any);
      this.scheduleLivePreview();
    },
    disableFilter() {
      this.filterEnabled = false;
      this.activeView = 'filter';
      this.previewResult = null;
      this.previewError = null;
      this.showAllTracks();
    },

    // ── Filter changes → schedule preview ──
    onFilterInfoChanged() {
      if (!this.selectedFilter.filterParams) {
        this.selectedFilter.filterParams = {};
      }
      // Ensure sub-maps exist
      if (!this.selectedFilter.filterParams.stringParams) this.selectedFilter.filterParams.stringParams = {};
      if (!this.selectedFilter.filterParams.dateTimeParams) this.selectedFilter.filterParams.dateTimeParams = {};
      if (!this.selectedFilter.filterParams.geoCircles) this.selectedFilter.filterParams.geoCircles = {};
      if (!this.selectedFilter.filterParams.geoRectangles) this.selectedFilter.filterParams.geoRectangles = {};
      if (!this.selectedFilter.filterParams.geoPolygons) this.selectedFilter.filterParams.geoPolygons = {};

      // Clean out params that don't belong to the new filter
      const validParams = new Set((this.selectedFilter?.filterInfo?.paramDefinitions || []).map((pd: ParamDefinition) => pd.name));
      for (const key of Object.keys(this.selectedFilter.filterParams.stringParams)) {
        if (!validParams.has(key)) delete this.selectedFilter.filterParams.stringParams[key];
      }
      for (const key of Object.keys(this.selectedFilter.filterParams.dateTimeParams)) {
        if (!validParams.has(key)) delete this.selectedFilter.filterParams.dateTimeParams[key];
      }
      for (const key of Object.keys(this.selectedFilter.filterParams.geoCircles)) {
        if (!validParams.has(key)) delete this.selectedFilter.filterParams.geoCircles[key];
      }
      for (const key of Object.keys(this.selectedFilter.filterParams.geoRectangles)) {
        if (!validParams.has(key)) delete this.selectedFilter.filterParams.geoRectangles[key];
      }
      for (const key of Object.keys(this.selectedFilter.filterParams.geoPolygons)) {
        if (!validParams.has(key)) delete this.selectedFilter.filterParams.geoPolygons[key];
      }
      if (!this.selectedFilter.palette) {
        this.selectedFilter.palette = markRaw(new ColorPalette());
      }
      this.activeView = 'filter';
      this.scheduleLivePreview();
    },
    onParamChanged() {
      this.scheduleLivePreview();
    },
    onPaletteChanged() {
      // Palette change doesn't need a server call — just recompute colors and auto-apply
      if (this.previewResult) {
        this.rebuildPreviewPalette();
      }
      this.filterStore.save(
        ClientFilterConfig.of(this.selectedFilter.filterInfo, this.getProcessedParams(), this.selectedFilter.palette)
      );
      this.$emit(EVENTS.filterChangedEvent);
    },

    // ── Live preview engine ──
    scheduleLivePreview() {
      if (this.previewDebounceTimer) clearTimeout(this.previewDebounceTimer);
      this.previewDebounceTimer = setTimeout(() => this.executeLivePreview(), 400);
    },

    async executeLivePreview() {
      const filterId = this.selectedFilter?.filterInfo?.filterConfig?.id;
      if (!filterId) return;

      // Abort any in-flight request
      if (this.previewAbortController) this.previewAbortController.abort();
      this.previewAbortController = new AbortController();

      this.isPreviewLoading = true;
      this.previewError = null;

      try {
        const result = await fetchResolveFilter(filterId, this.getProcessedParams(), false);
        this.previewResult = result.queryResult;
        this.rebuildPreviewPalette();
        // Auto-apply: persist and notify map immediately after every successful preview
        this.filterStore.save(
          ClientFilterConfig.of(this.selectedFilter.filterInfo, this.getProcessedParams(), this.selectedFilter.palette)
        );
        this.drillDownFullResult = null;
        this.$emit(EVENTS.filterChangedEvent, result);
      } catch (error: unknown) {
        if (error instanceof Error && (error.name === 'AbortError' || error.message?.includes('abort'))) return;
        this.previewError = 'Preview failed. Check parameter values or switch to a different filter.';
        console.error('Live preview error:', error);
      } finally {
        this.isPreviewLoading = false;
      }
    },

    rebuildPreviewPalette() {
      const palette = this.selectedFilter?.palette;
      if (palette && !palette.isEmptyColorPalette() && this.previewResult?.resultEntries) {
        palette.reset();
        for (const entry of this.previewResult.resultEntries) {
          if (entry.group) palette.getColorForGroup(entry.group, true);
        }
      }
    },

    // ── Drill-down ──
    async openGroupDrillDown(group: string) {
      this.drillDownGroup = group;
      this.showDrillDown = true;

      // Fetch full details (with GPS tracks) if not already loaded
      if (!this.drillDownFullResult) {
        this.isDrillDownLoading = true;
        try {
          const filterId = this.selectedFilter?.filterInfo?.filterConfig?.id;
          if (filterId === undefined) return;
          const drillResult = await fetchResolveFilter(filterId, this.getProcessedParams(), true);
          this.drillDownFullResult = drillResult.queryResult;
        } catch (error) {
          console.error('Drill-down fetch error:', error);
        } finally {
          this.isDrillDownLoading = false;
        }
      }
    },

    showAllTracks() {
      const defaultFilter = this.filters.find(f =>
        FilterService.isStandardFilterWithStandardParams(ClientFilterConfig.of(f))
      );
      if (defaultFilter) {
        const cfg = ClientFilterConfig.of(defaultFilter, {}, undefined);
        this.selectedFilter = cfg;
        this.activeView = 'filter';
        this.filterStore.save(cfg);
        this.previewResult = null;
        this.drillDownFullResult = null;
        this.$emit(EVENTS.filterChangedEvent);

        this.toast.add({
          severity: 'info',
          summary: 'Showing all tracks',
          detail: '',
          life: 3000,
        });
      }
    },

    // ── Param helpers ──
    formatDateParamImpl(date: Date) {
      return format(date, 'yyyy-MM-dd HH:mm:ss');
    },

    getProcessedParams(): FilterParamsRequest {
      const params = this.selectedFilter.filterParams;
      if (!params) return {};
      const result: FilterParamsRequest = {
        stringParams: { ...(params.stringParams || {}) },
        dateTimeParams: {},
        geoCircles: params.geoCircles ? { ...params.geoCircles } : undefined,
        geoRectangles: params.geoRectangles ? { ...params.geoRectangles } : undefined,
        geoPolygons: params.geoPolygons ? { ...params.geoPolygons } : undefined,
      };
      // Format Date objects in dateTimeParams
      for (const key in (params.dateTimeParams || {})) {
        const value = params.dateTimeParams![key] as unknown;
        if (value && value instanceof Date) {
          result.dateTimeParams![key] = this.formatDateParamImpl(value);
        } else if (value) {
          result.dateTimeParams![key] = value as string;
        }
      }
      return result;
    },

    // ── Geo drawing ──
    onStartGeoDrawing(paramDef: ParamDefinition) {
      this.$emit('start-geo-drawing', paramDef);
    },
    onClearGeoShape(paramDef: ParamDefinition) {
      const params = this.selectedFilter.filterParams;
      if (!params || !paramDef.name) return;
      switch (paramDef.type) {
        case 'GEO_CIRCLE':
          if (params.geoCircles) delete params.geoCircles[paramDef.name];
          break;
        case 'GEO_RECTANGLE':
          if (params.geoRectangles) delete params.geoRectangles[paramDef.name];
          break;
        case 'GEO_POLYGON':
          if (params.geoPolygons) delete params.geoPolygons[paramDef.name];
          break;
      }
      this.$emit('clear-geo-shape', paramDef);
      this.scheduleLivePreview();
    },
    /**
     * Called by parent (Filter.vue) when the user finishes drawing a shape on the map.
     */
    onGeoDrawingComplete(paramDef: ParamDefinition, shape: any) {
      const params = this.selectedFilter.filterParams;
      if (!params || !paramDef.name) return;
      switch (paramDef.type) {
        case 'GEO_CIRCLE':
          if (!params.geoCircles) params.geoCircles = {};
          params.geoCircles[paramDef.name] = shape;
          break;
        case 'GEO_RECTANGLE':
          if (!params.geoRectangles) params.geoRectangles = {};
          params.geoRectangles[paramDef.name] = shape;
          break;
        case 'GEO_POLYGON':
          if (!params.geoPolygons) params.geoPolygons = {};
          params.geoPolygons[paramDef.name] = shape;
          break;
      }
      this.scheduleLivePreview();
    },
    /** Returns all currently configured geo shapes for rendering on the map. */
    getGeoShapes(): { circles: Record<string, any>, rectangles: Record<string, any>, polygons: Record<string, any>, labels: Record<string, string> } {
      const params = this.selectedFilter?.filterParams;
      const labels: Record<string, string> = {};
      for (const pd of (this.selectedFilter?.filterInfo?.paramDefinitions ?? [])) {
        if (pd.name && pd.label) labels[pd.name] = pd.label;
      }
      return {
        circles: params?.geoCircles ?? {},
        rectangles: params?.geoRectangles ?? {},
        polygons: params?.geoPolygons ?? {},
        labels,
      };
    },
  },
});
</script>

<style scoped>

/* ════════════════════════════════════════════════════════════════
   CustomFilter — flat, borderless layout.
   Structure-level chrome (cards, panels) is removed. Visual grouping
   comes from whitespace + small section headings with a thin rule.
   ════════════════════════════════════════════════════════════════ */

.filter-root {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  width: 100%;
  color: var(--text-secondary);
}

.filter-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
  padding: 0.5rem 1rem 1.25rem;
}

/* ── Header tabs (stats-style pills) ── */
.cf-header-nav {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}
.cf-sheet-icon {
  font-size: var(--text-base-size);
  color: var(--text-muted);
  flex-shrink: 0;
}
.cf-header-tabs {
  display: flex;
  gap: 0.15rem;
  min-width: 0;
}
.cf-tab {
  padding: 0.3rem 0.85rem;
  border-radius: 1rem;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  white-space: nowrap;
  line-height: var(--text-sm-lh);
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}
.cf-tab:not(.cf-tab--active):not(.cf-tab--disabled):hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}
.cf-tab--active {
  background: var(--accent-subtle);
  color: var(--accent-text);
}
.cf-tab--disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.cf-tab__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.1rem;
  height: 1.1rem;
  padding: 0 0.3rem;
  border-radius: 9999px;
  background: var(--accent-bg);
  color: var(--accent-text);
  font-size: var(--text-2xs-size, 0.65rem);
  font-weight: 700;
}
.cf-tab--compact {
  padding-inline: 0.7rem;
}
.cf-tab__label-sql {
  font-size: var(--text-xs-size);
  letter-spacing: 0.04em;
}

/* ── Status strip (borderless) ── */
.cf-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.85rem;
  padding: 0.6rem 0.1rem 0.85rem;
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
  margin-bottom: 1rem;
}
.cf-status__main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.cf-status__title-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}
.cf-status__dot-state {
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 9999px;
  background: var(--border-medium);
  flex-shrink: 0;
}
.cf-status__dot-state--on {
  background: var(--accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 22%, transparent);
}
.cf-status__title {
  margin: 0;
  font-size: var(--text-base-size);
  line-height: var(--text-base-lh);
  color: var(--text-primary);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cf-status__meta {
  margin: 0;
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
  line-height: var(--text-sm-lh);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.2rem;
}
.cf-status__meta strong { color: var(--text-primary); font-weight: 600; }
.cf-status__meta-sep { color: var(--text-muted); }
.cf-status__meta--muted { color: var(--text-muted); }
.cf-status__swatches {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  margin-left: 0.4rem;
}
.cf-status__dot {
  display: inline-block;
  width: 0.6rem;
  height: 0.6rem;
  border-radius: 9999px;
  border: 1px solid rgba(255,255,255,0.25);
  flex-shrink: 0;
}

/* ── Big "filter is off" CTA card ── */
.cf-off-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 0.75rem;
  padding: 2rem 1rem;
  color: var(--text-secondary);
}
.cf-off-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  border-radius: 9999px;
  background: var(--accent-subtle);
  color: var(--accent-text);
  font-size: 1.4rem;
}
.cf-off-card__title {
  margin: 0;
  font-size: var(--text-lg-size);
  line-height: var(--text-lg-lh);
  color: var(--text-primary);
}
.cf-off-card__text {
  margin: 0;
  max-width: 32rem;
  font-size: var(--text-sm-size);
  color: var(--text-muted);
  line-height: var(--text-sm-lh);
}

/* ── Tab body (flat stack) ── */
.cf-tab-body {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

/* ── Field: label + input + optional hint (no border) ── */
.cf-field { min-width: 0; display: flex; flex-direction: column; gap: 0.35rem; }
.cf-field__label {
  display: block;
  font-size: var(--text-xs-size);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-muted);
}
.cf-field__hint {
  margin: 0;
  font-size: var(--text-sm-size);
  color: var(--text-muted);
  line-height: var(--text-sm-lh);
}
.cf-field__hint code {
  font-size: var(--text-xs-size);
  background: var(--surface-glass);
  border: 1px solid var(--border-default);
  border-radius: 0.2rem;
  padding: 0.05rem 0.25rem;
  color: var(--accent-text);
  font-family: monospace;
}

/* ── Section: slim heading + thin rule underneath ── */
.cf-section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.cf-section__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
  padding-bottom: 0.35rem;
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
}
.cf-section__title {
  margin: 0;
  font-size: var(--text-xs-size);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-muted);
}
.cf-section__count {
  font-size: var(--text-xs-size);
  font-weight: 600;
  color: var(--text-muted);
  opacity: 0.65;
}
.cf-section__note {
  margin: 0;
  font-size: var(--text-sm-size);
  color: var(--text-muted);
  line-height: var(--text-sm-lh);
}

/* Parameter fields: flat grid, no individual card chrome */
.cf-params-grid {
  display: grid;
  gap: 0.9rem 1rem;
}

/* Inline preview summary under the filter form (no heavy card) */
.cf-preview-mini {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.6rem 0.1rem 0;
  border-top: 1px dashed var(--border-subtle, var(--border-default));
}
.cf-preview-mini__text {
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}
.cf-preview-mini__text strong { color: var(--text-primary); font-weight: 600; }
.cf-preview-mini__text--err { color: var(--error); }
.cf-link-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.35rem 0.6rem;
  border: none;
  background: transparent;
  color: var(--accent-text);
  font-size: var(--text-sm-size);
  font-weight: 600;
  cursor: pointer;
  border-radius: 0.5rem;
  transition: background 0.15s;
  -webkit-tap-highlight-color: transparent;
}
.cf-link-btn:hover { background: var(--accent-subtle); }
.cf-spin {
  animation: cf-spin 1s linear infinite;
  display: inline-block;
}
@keyframes cf-spin { to { transform: rotate(360deg); } }

/* ── Primary CTA ── */
.cf-primary-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  min-height: 2.5rem;
  padding: 0.65rem 1.25rem;
  border: none;
  border-radius: 0.8rem;
  background: var(--accent);
  color: var(--text-inverse);
  font-size: var(--text-sm-size);
  font-weight: 700;
  cursor: pointer;
  transition: filter 0.15s;
  -webkit-tap-highlight-color: transparent;
}
.cf-primary-btn:hover { filter: brightness(1.06); }

/* ── Toggle switch (unchanged) ── */
.toggle-switch {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  cursor: pointer;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  flex-shrink: 0;
}
.toggle-switch input {
  position: absolute;
  opacity: 0;
  width: 0; height: 0;
  pointer-events: none;
}
.toggle-switch__track {
  position: relative;
  display: inline-block;
  width: 2.75rem;
  height: 1.5rem;
  border-radius: 9999px;
  background: var(--border-medium);
  transition: background 0.2s;
  flex-shrink: 0;
}
.toggle-switch__track--on { background: var(--accent); }
.toggle-switch__thumb {
  position: absolute;
  top: 0.175rem;
  left: 0.175rem;
  width: 1.15rem;
  height: 1.15rem;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.25);
  transition: transform 0.2s;
}
.toggle-switch__track--on .toggle-switch__thumb { transform: translateX(1.25rem); }
.toggle-switch__text {
  font-size: var(--text-sm-size);
  font-weight: 500;
  color: var(--text-secondary);
  min-width: 1.5rem;
}
.toggle-switch__text--on { color: var(--accent-text); }

/* ── Full-width inputs & dropdowns ── */
.filter-full-width { width: 100%; min-width: 0; max-width: 100%; }

/* ── Palette preview (unchanged semantics, cleaner placement) ── */
.palette-preview {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.2rem;
}
.palette-preview__swatch,
.palette-preview__more {
  width: 1.1rem;
  height: 1.1rem;
  border-radius: 9999px;
  border: 1px solid var(--border-default);
  box-shadow: inset 0 0 0 1px rgba(255,255,255,0.22);
}
.palette-preview__more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: auto;
  min-width: 1.8rem;
  padding: 0 0.4rem;
  background: var(--surface-glass);
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
  font-weight: 700;
}

/* ── Skeleton shimmer ── */
.preview-skeleton {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
.skeleton-line {
  height: 0.85rem;
  border-radius: 0.25rem;
  background: linear-gradient(90deg,
    var(--surface-glass-subtle) 25%,
    var(--surface-glass-light) 50%,
    var(--surface-glass-subtle) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
}
.skeleton-line--wide   { width: 70%; }
.skeleton-line--medium { width: 50%; }
.skeleton-line--narrow { width: 35%; }
@keyframes shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ── Legend (kept, subtle borders) ── */
.legend-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.legend-row {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.45rem 0.6rem;
  border-radius: 0.5rem;
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.legend-row:hover {
  background: var(--surface-hover);
  border-color: var(--border-subtle, var(--border-default));
}
.legend-swatch {
  width: 0.9rem;
  height: 0.9rem;
  border-radius: 0.25rem;
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px rgba(0,0,0,0.15);
}
.legend-group-wrap {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
  flex-direction: column;
  gap: 0.12rem;
}
.legend-group {
  font-size: var(--text-sm-size);
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.legend-subtitle {
  font-size: var(--text-xs-size);
  color: var(--text-muted);
}
.legend-count {
  font-size: var(--text-xs-size);
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--surface-glass);
  border: 1px solid var(--border-default);
  border-radius: 9999px;
  padding: 0.05rem 0.5rem;
  white-space: nowrap;
}
.legend-chevron {
  font-size: var(--text-2xs-size);
  color: var(--text-muted);
  flex-shrink: 0;
}

/* ── Hints / empty preview states (still framed, but flatter) ── */
.preview-hint,
.preview-plain-state,
.preview-error--card {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 0.65rem 0.75rem;
  border-radius: 0.6rem;
  background: var(--surface-glass-subtle);
  border: 1px dashed var(--border-default);
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
  line-height: var(--text-sm-lh);
}
.preview-hint__icon {
  font-size: var(--text-lg-size);
  color: var(--accent-text);
  opacity: 0.7;
  flex-shrink: 0;
  margin-top: 0.1rem;
}
.preview-error {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  font-size: var(--text-sm-size);
  color: var(--error);
  padding: 0.5rem 0;
}
.preview-error--card {
  color: var(--error);
  border-color: color-mix(in srgb, var(--error) 40%, var(--border-default));
  background: color-mix(in srgb, var(--error) 10%, var(--surface-glass-subtle));
}

/* ── SQL: mode toggle + code block ── */
.sql-mode-toggle {
  display: inline-flex;
  border: 1px solid var(--border-default);
  border-radius: 0.5rem;
  overflow: hidden;
  align-self: flex-start;
}
.sql-mode-btn {
  flex: 1;
  padding: 0.3rem 0.85rem;
  font-size: var(--text-xs-size);
  font-weight: 600;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--text-secondary);
  transition: background 0.15s, color 0.15s;
  -webkit-tap-highlight-color: transparent;
}
.sql-mode-btn + .sql-mode-btn {
  border-left: 1px solid var(--border-default);
}
.sql-mode-btn--active {
  background: var(--accent);
  color: var(--text-inverse);
}
.sql-block__code {
  border-radius: 0.5rem;
  overflow: hidden;
  border: 1px solid var(--border-default);
  background: var(--surface-glass-subtle);
}
.sql-block__code :deep(pre) {
  margin: 0;
  padding: 0.65rem 0.75rem;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
  background: transparent !important;
}
.sql-block__code :deep(code) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  background: transparent !important;
}

/* ── Drill-down sheet content ── */
.review-root {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  padding: 0 1rem;
  box-sizing: border-box;
}
.review-intro {
  flex: 0 0 auto;
  font-size: var(--text-sm-size);
  color: var(--text-muted);
  margin-bottom: 0.75rem;
  padding-top: 0.25rem;
}
.review-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
}
.review-navigation {
  flex: 0 0 auto;
  display: flex;
  justify-content: flex-start;
  padding: 0 0 0.5rem;
  gap: 0.5rem;
  border-bottom: 1px solid var(--border-subtle);
  margin-bottom: 0.5rem;
}
.my-datatable { font-size: var(--text-sm-size); }
.color-indicator {
  display: inline-block;
  width: 16px;
  height: 16px;
  margin-left: 8px;
  border-radius: 50%;
}

/* ── Responsive ── */
@media screen and (max-width: 768px) {
  .filter-scroll { padding: 0.4rem 0.75rem 1rem; }
  .cf-tab { padding: 0.3rem 0.6rem; font-size: var(--text-xs-size); }
  .cf-status { flex-wrap: wrap; }
  .cf-status__main { flex: 1 1 10rem; }
  .my-datatable :deep(th),
  .my-datatable :deep(td) {
    padding: 0.5rem 0.25rem;
    white-space: nowrap;
  }
}

@media screen and (min-width: 900px) {
  .cf-params-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

</style>
