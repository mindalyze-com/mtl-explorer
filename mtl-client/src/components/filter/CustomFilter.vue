<template>
  <!-- filter-root must be flex: 1 1 auto + min-height: 0 to fill the BottomSheet body -->
  <div class="filter-root">

    <!-- ── Scrollable content area ── -->
    <div class="filter-scroll">
      <section class="filter-hero" :class="{ 'filter-hero--active': filterEnabled }">
        <div class="filter-hero__header">
          <div class="filter-hero__copy">
            <span class="filter-hero__eyebrow">Track filter</span>
            <div class="filter-hero__title-row">
              <i :class="['bi', filterEnabled ? 'bi-funnel-fill' : 'bi-funnel', 'filter-hero__icon']"></i>
              <h2 class="filter-hero__title">{{ filterEnabled ? 'Filter active' : 'Show all tracks' }}</h2>
            </div>
          </div>
          <label class="toggle-switch" :aria-label="filterEnabled ? 'Disable filter' : 'Enable filter'">
            <input type="checkbox" :checked="filterEnabled" @change="filterEnabled ? disableFilter() : enableFilter()" />
            <span :class="['toggle-switch__track', filterEnabled && 'toggle-switch__track--on']"><span class="toggle-switch__thumb"></span></span>
            <span :class="['toggle-switch__text', filterEnabled && 'toggle-switch__text--on']">{{ filterEnabled ? 'On' : 'Off' }}</span>
          </label>
        </div>
        <p class="filter-hero__summary">{{ filterHeroSummary }}</p>
        <div class="filter-hero__stats">
          <div class="filter-stat-pill">
            <span class="filter-stat-pill__label">Visible</span>
            <strong class="filter-stat-pill__value">{{ activeTrackCountDisplay }}</strong>
          </div>
          <div class="filter-stat-pill">
            <span class="filter-stat-pill__label">Total</span>
            <strong class="filter-stat-pill__value">{{ totalTrackCountDisplay }}</strong>
          </div>
          <div v-if="filterEnabled" class="filter-stat-pill" :class="{ 'filter-stat-pill--accent': previewGroupCount > 0 }">
            <span class="filter-stat-pill__label">Groups</span>
            <strong class="filter-stat-pill__value">{{ previewGroupCountDisplay }}</strong>
          </div>
          <div v-if="filterEnabled" class="filter-stat-pill" :class="{ 'filter-stat-pill--accent': palettePreviewColors.length > 0 }">
            <span class="filter-stat-pill__label">Colors</span>
            <span v-if="palettePreviewColors.length > 0" class="filter-stat-pill__swatches">
              <span v-for="(c, i) in palettePreviewColors.slice(0, 3)" :key="i" class="filter-stat-pill__dot" :style="{ backgroundColor: c }"></span>
            </span>
            <strong v-else class="filter-stat-pill__value">—</strong>
          </div>
        </div>
      </section>

      <div v-if="!filterEnabled" class="filter-empty-state">
        <div class="filter-empty-state__icon-wrap">
          <i class="bi bi-sliders filter-empty-state__icon"></i>
        </div>
        <div class="filter-empty-state__body">
          <h3 class="filter-empty-state__title">Filtering is currently off</h3>
          <p class="filter-empty-state__text">
            Enable the filter to narrow tracks, add geo conditions, group results, and apply a color palette to the map.
          </p>
        </div>
      </div>

      <template v-else>
        <div class="filter-view-switch" role="tablist" aria-label="Filter views">
          <button
            class="filter-view-switch__btn"
            :class="{ 'filter-view-switch__btn--active': activeView === 'configure' }"
            @click="activeView = 'configure'"
            type="button"
          >
            <i class="bi bi-sliders2"></i>
            Configure
          </button>
          <button
            class="filter-view-switch__btn"
            :class="{ 'filter-view-switch__btn--active': activeView === 'results' }"
            :disabled="!canOpenResultsView && !isPreviewLoading"
            @click="activeView = 'results'"
            type="button"
          >
            <i class="bi bi-palette"></i>
            Coloring &amp; review
            <span v-if="resultsBadge" class="filter-view-switch__meta">{{ resultsBadge }}</span>
          </button>
        </div>

        <div v-if="activeView === 'configure'" class="filter-view-stack">
          <section class="filter-card">
            <div class="filter-card__header">
              <div>
                <span class="filter-section__label">Filter</span>
                <h3 class="filter-card__title">{{ selectedFilterLabel }}</h3>
              </div>
              <span class="filter-card__badge">Core</span>
            </div>
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
            <p v-if="selectedFilter?.filterInfo?.filterConfig?.description" class="filter-desc filter-desc--spacious">
              {{ selectedFilter.filterInfo.filterConfig.description }}
            </p>
          </section>

          <section v-if="hasParams" class="filter-card">
            <div class="filter-card__header">
              <div>
                <span class="filter-section__label">Parameters</span>
                <h3 class="filter-card__title">Tune the filter</h3>
              </div>
              <span class="filter-card__badge">{{ selectedFilter?.filterInfo?.paramDefinitions?.length ?? 0 }}</span>
            </div>
            <div class="filter-fields-grid">
              <div v-for="pd in (selectedFilter?.filterInfo?.paramDefinitions || [])" :key="pd.name" class="filter-field filter-field--card">
                <template v-if="pd.type === 'DATE_TIME' && pd.name && selectedFilter?.filterParams?.dateTimeParams">
                  <label class="filter-field__label" :for="pd.name">{{ pd.label }}</label>
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
                  <label class="filter-field__label" :for="pd.name">{{ pd.label }}</label>
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

          <div class="filter-cta-bar">
            <button
              class="filter-primary-btn"
              :disabled="!canOpenResultsView && !isPreviewLoading"
              @click="activeView = 'results'"
              type="button"
            >
              Open coloring &amp; review
            </button>
          </div>

          <section v-if="rawSQL" class="filter-card filter-card--advanced">
            <div class="filter-card__header">
              <div>
                <span class="filter-section__label">Advanced</span>
                <h3 class="filter-card__title">Inspect generated SQL</h3>
              </div>
              <span class="filter-card__badge">Expert</span>
            </div>
            <button class="filter-advanced-toggle" @click="showSqlDetails = !showSqlDetails" type="button">
              <span class="filter-advanced-toggle__copy">
                <i class="bi bi-code-slash filter-advanced-toggle__icon"></i>
                <span>{{ showSqlDetails ? 'Hide SQL details' : 'Show SQL details' }}</span>
              </span>
              <i :class="['bi', showSqlDetails ? 'bi-chevron-up' : 'bi-chevron-down', 'filter-advanced-toggle__chevron']"></i>
            </button>

            <template v-if="showSqlDetails">
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
              <p class="sql-mode-hint" v-if="sqlViewMode === 'template'">
                The raw SQL expression stored in the database. Sub-queries are referenced as Thymeleaf fragments
                (<code>[[~{...}]]</code>) and <code>:PARAM_NAME</code> placeholders are not yet substituted.
              </p>
              <p class="sql-mode-hint" v-else>
                The fully expanded SQL as produced by the server. Thymeleaf fragments are already inlined, while
                <code>:PARAM_NAME</code> placeholders are still bound at execution time.
              </p>
              <div class="sql-block__code">
                <highlightjs language="sql" :code="sqlViewMode === 'template' ? rawSQL : resolvedSQL" />
              </div>
            </template>
          </section>
        </div>

        <div v-else class="filter-view-stack">
          <section class="filter-card">
            <div class="filter-card__header">
              <div>
                <span class="filter-section__label">Color palette</span>
                <h3 class="filter-card__title">{{ paletteLabel }}</h3>
              </div>
              <span class="filter-card__badge">Map</span>
            </div>
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
            <p class="filter-supporting-text">{{ paletteReviewHelpText }}</p>
          </section>

          <section class="filter-card filter-preview">
            <div class="filter-card__header">
              <div>
                <span class="filter-section__label">Preview</span>
                <h3 class="filter-card__title">{{ previewSectionTitle }}</h3>
              </div>
              <span class="filter-card__badge" :class="{ 'filter-card__badge--busy': isPreviewLoading }">{{ isPreviewLoading ? 'Updating' : 'Live' }}</span>
            </div>
            <p v-if="previewSectionNote" class="preview-section-note">{{ previewSectionNote }}</p>

            <div v-if="isPreviewLoading" class="preview-skeleton">
              <div class="skeleton-line skeleton-line--wide"></div>
              <div class="skeleton-line skeleton-line--medium"></div>
              <div class="skeleton-line skeleton-line--narrow"></div>
            </div>

            <template v-else-if="previewResult">
              <div class="preview-summary">
                <span class="preview-summary__count">{{ previewResult.resultEntries?.length ?? 0 }} tracks</span>
                <span v-if="previewGroupCount > 0" class="preview-summary__groups">
                  · {{ previewGroupCount }} groups
                </span>
              </div>

              <template v-if="previewHasColors">
                <ul class="legend-list">
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
              </template>

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
              <span>Adjust the filter in Configure to generate results here.</span>
            </div>
          </section>
        </div>
      </template>
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

  </div>
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
import {trackStore} from "@/utils/trackStore";
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
};

export default defineComponent({
  name: 'CustomFilter',
  components: {
    highlightjs: hljsVuePlugin.component,
    BottomSheet,
    GeoShapeParam,
    TrackShapePreview,
  },
  props: ['tileLayer', 'palette', 'totalTrackCount', 'visibleTrackCount'],

  data(): {
    filters: FilterInfo[];
    selectedFilter: ClientFilterConfig;
    colorPaletteList: ColorPalette[];
    filterEnabled: boolean;
    activeView: 'configure' | 'results';
    previewResult: QueryResult | null;
    isPreviewLoading: boolean;
    previewError: string | null;
    previewDebounceTimer: ReturnType<typeof setTimeout> | null;
    previewAbortController: AbortController | null;
    showSqlDetails: boolean;
    sqlViewMode: 'template' | 'resolved';
    showDrillDown: boolean;
    drillDownGroup: string | null;
    drillDownFullResult: QueryResult | null;
    isDrillDownLoading: boolean;
  } {
    return {
      filters: [] as FilterInfo[],
      selectedFilter: new ClientFilterConfig(),
      colorPaletteList: [] as ColorPalette[],
      // Toggle state
      filterEnabled: false,
      activeView: 'configure' as 'configure' | 'results',
      // Live preview
      previewResult: null as QueryResult | null,
      isPreviewLoading: false,
      previewError: null as string | null,
      previewDebounceTimer: null as ReturnType<typeof setTimeout> | null,
      previewAbortController: null as AbortController | null,
      // SQL details panel
      showSqlDetails: false,
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
      this.activeView = 'configure';
      // Guarantee sub-maps exist before the params form renders
      this.selectedFilter = this.normalizeClientFilterConfig(this.selectedFilter as any);
      this.scheduleLivePreview();
    },
    disableFilter() {
      this.filterEnabled = false;
      this.activeView = 'configure';
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
      this.activeView = 'configure';
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
        // Pass the resolved IDs+versions to trackStore so the map can skip get-simplified?mode=ids.
        // ResolveFilterResult extends FilterResult so it can be passed directly.
        if (result.trackVersions.size > 0) {
          trackStore.setPendingFilterResult(result);
        }
        this.drillDownFullResult = null;
        this.$emit(EVENTS.filterChangedEvent);
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
        this.activeView = 'configure';
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

/* ── Root: fills sheet-body (flex: 1 auto + min-height: 0 is the BottomSheet contract) ── */
.filter-root {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  width: 100%;
  color: var(--text-secondary);
}

/* ── Scrollable content area ── */
.filter-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
  padding: 0.75rem 1rem 1rem;
}

/* ── Hero / top status ── */
.filter-hero {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 1rem;
  margin-bottom: 1rem;
  border-radius: 1rem;
  background:
    linear-gradient(135deg, var(--surface-glass-heavy), var(--surface-glass-subtle));
  border: 1px solid var(--border-medium);
  box-shadow: var(--shadow-sm);
}
.filter-hero--active {
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent-bg) 68%, var(--surface-glass-heavy)), var(--surface-glass-subtle));
  border-color: color-mix(in srgb, var(--accent-muted) 55%, var(--border-medium));
}
.filter-hero__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
.filter-hero__copy {
  min-width: 0;
}
.filter-hero__eyebrow {
  display: inline-block;
  margin-bottom: 0.3rem;
  font-size: 0.72rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--text-muted);
}
.filter-hero__title-row {
  display: flex;
  align-items: center;
  gap: 0.55rem;
}
.filter-hero__icon {
  font-size: 1rem;
  color: var(--accent-text);
}
.filter-hero__title {
  margin: 0;
  font-size: 1.1rem;
  line-height: 1.2;
  color: var(--text-primary);
}
.filter-hero__summary {
  margin: 0;
  font-size: 0.86rem;
  color: var(--text-muted);
  line-height: 1.5;
}
.filter-hero__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
}
.filter-stat-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.45rem 0.7rem;
  border-radius: 9999px;
  background: var(--surface-glass);
  border: 1px solid var(--border-default);
}
.filter-stat-pill--accent {
  border-color: color-mix(in srgb, var(--accent-text) 45%, var(--border-default));
  background: color-mix(in srgb, var(--accent-bg) 55%, var(--surface-glass));
}
.filter-stat-pill__label {
  font-size: 0.72rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-muted);
}
.filter-stat-pill__value {
  font-size: 0.82rem;
  color: var(--text-primary);
}
.filter-stat-pill__swatches {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}
.filter-stat-pill__dot {
  display: inline-block;
  width: 0.65rem;
  height: 0.65rem;
  border-radius: 9999px;
  border: 1px solid rgba(255,255,255,0.3);
  flex-shrink: 0;
}

/* ── Empty state ── */
.filter-empty-state {
  display: flex;
  gap: 0.9rem;
  align-items: flex-start;
  padding: 1rem;
  border-radius: 0.9rem;
  background: var(--surface-glass-subtle);
  border: 1px solid var(--border-default);
}
.filter-empty-state__icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.4rem;
  height: 2.4rem;
  border-radius: 0.75rem;
  background: var(--surface-glass);
  border: 1px solid var(--border-default);
  flex-shrink: 0;
}
.filter-empty-state__icon {
  font-size: 1rem;
  color: var(--accent-text);
}
.filter-empty-state__body {
  min-width: 0;
}
.filter-empty-state__title {
  margin: 0 0 0.25rem;
  font-size: 0.95rem;
  color: var(--text-primary);
}
.filter-empty-state__text {
  margin: 0;
  font-size: 0.82rem;
  color: var(--text-muted);
  line-height: 1.5;
}

/* ── Toggle switch ── */
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
  width: 0;
  height: 0;
  pointer-events: none;
}
.toggle-switch__track {
  position: relative;
  display: inline-block;
  width: 2.75rem;
  height: 1.5rem;
  border-radius: 9999px;
  background: var(--border-medium, #94a3b8);
  transition: background 0.2s;
  flex-shrink: 0;
}
.toggle-switch__track--on {
  background: var(--accent, #6366f1);
}
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
.toggle-switch__track--on .toggle-switch__thumb {
  transform: translateX(1.25rem);
}
.toggle-switch__text {
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--text-secondary);
  min-width: 1.5rem;
}
.toggle-switch__text--on {
  color: var(--accent-text);
}

/* ── View switch ── */
.filter-view-switch {
  display: flex;
  gap: 0.3rem;
  margin-bottom: 1rem;
  padding: 0.35rem;
  border-radius: 0.95rem;
  background: var(--surface-glass-subtle);
  border: 1px solid var(--border-default);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.45);
}
.filter-view-switch__btn {
  flex: 1 1 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  min-width: 0;
  padding: 0.55rem 0.8rem;
  border: none;
  border-radius: 0.75rem;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s, transform 0.15s;
  white-space: nowrap;
}
.filter-view-switch__btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.filter-view-switch__btn:hover:not(:disabled) {
  color: var(--text-secondary);
  background: var(--surface-hover);
}
.filter-view-switch__btn--active {
  background: var(--surface-glass-heavy);
  color: var(--accent-text);
  box-shadow: var(--shadow-sm);
}
.filter-view-switch__meta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.25rem;
  height: 1.25rem;
  padding: 0 0.35rem;
  border-radius: 9999px;
  background: var(--accent-bg);
  color: var(--accent-text);
  font-size: 0.7rem;
  font-weight: 700;
}

.filter-cta-bar {
  display: flex;
  justify-content: flex-start;
  margin-top: -0.2rem;
}

.filter-view-stack {
  display: flex;
  flex-direction: column;
  gap: 0.95rem;
}

/* ── Cards / sections ── */
.filter-card {
  padding: 0.95rem;
  border-radius: 0.95rem;
  background: var(--surface-glass-subtle);
  border: 1px solid var(--border-default);
  box-shadow: var(--shadow-sm);
}
.filter-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.85rem;
}
.filter-card__title {
  margin: 0;
  font-size: 0.98rem;
  line-height: 1.25;
  color: var(--text-primary);
}
.filter-card__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.2rem 0.55rem;
  border-radius: 9999px;
  background: var(--surface-glass);
  border: 1px solid var(--border-default);
  color: var(--text-muted);
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  white-space: nowrap;
}
.filter-card__badge--busy {
  color: var(--accent-text);
  border-color: color-mix(in srgb, var(--accent-text) 45%, var(--border-default));
  background: color-mix(in srgb, var(--accent-bg) 55%, var(--surface-glass));
}
.filter-section__label {
  font-size: 0.72rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: var(--text-muted);
  margin-bottom: 0.4rem;
  display: block;
}
.filter-desc {
  font-size: 0.82rem;
  color: var(--text-muted);
  margin-top: 0.4rem;
  margin-bottom: 0;
  line-height: 1.5;
}
.filter-desc--spacious {
  margin-top: 0.65rem;
}
.filter-supporting-text {
  margin: 0.65rem 0 0;
  font-size: 0.8rem;
  color: var(--text-muted);
  line-height: 1.5;
}
.palette-preview {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-top: 0.75rem;
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
  font-size: 0.68rem;
  font-weight: 700;
}

/* ── Full-width inputs & dropdowns ── */
.filter-full-width {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

/* ── Parameter fields ── */
.filter-fields-grid {
  display: grid;
  gap: 0.75rem;
}
.filter-field {
  min-width: 0;
}
.filter-field--card {
  padding: 0.8rem;
  border-radius: 0.8rem;
  background: var(--surface-glass);
  border: 1px solid var(--border-default);
}
.filter-field__label {
  display: block;
  font-size: 0.82rem;
  color: var(--text-secondary);
  margin-bottom: 0.3rem;
  font-weight: 500;
}

.filter-primary-btn,
.filter-advanced-toggle {
  border: none;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.filter-primary-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 2.5rem;
  padding: 0.65rem 1rem;
  border-radius: 0.8rem;
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent-text-light) 60%, var(--accent)));
  color: #fff;
  font-size: 0.84rem;
  font-weight: 700;
  box-shadow: 0 10px 24px var(--accent-glow);
}
.filter-primary-btn:disabled {
  opacity: 0.5;
  box-shadow: none;
  cursor: not-allowed;
}

.filter-card--advanced {
  border-style: dashed;
}
.filter-advanced-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0.75rem 0.85rem;
  border-radius: 0.8rem;
  background: var(--surface-glass);
  color: var(--text-secondary);
}
.filter-advanced-toggle__copy {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
}
.filter-advanced-toggle__icon,
.filter-advanced-toggle__chevron {
  color: var(--text-muted);
}

/* ── Live preview section ── */
.filter-preview {
  margin-bottom: 0;
}

/* Skeleton shimmer */
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
.skeleton-line--wide  { width: 70%; }
.skeleton-line--medium { width: 50%; }
.skeleton-line--narrow { width: 35%; }

@keyframes shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.preview-summary {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 0.5rem;
}
.preview-summary__count  { color: var(--accent-text); }
.preview-summary__groups { font-weight: 400; color: var(--text-secondary); }
.preview-section-note {
  margin: 0 0 0.75rem;
  font-size: 0.8rem;
  color: var(--text-muted);
  line-height: 1.5;
}

/* Legend */
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
  padding: 0.4rem 0.6rem;
  border-radius: 0.5rem;
  background: var(--surface-glass-subtle);
  border: 1px solid var(--border-default);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.legend-row:hover {
  background: var(--surface-glass);
  border-color: var(--accent-text);
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
  font-size: 0.85rem;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.legend-subtitle {
  font-size: 0.72rem;
  color: var(--text-muted);
}
.legend-count {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--surface-glass);
  border: 1px solid var(--border-default);
  border-radius: 9999px;
  padding: 0.05rem 0.5rem;
  white-space: nowrap;
}
.legend-chevron {
  font-size: 0.65rem;
  color: var(--text-muted);
  flex-shrink: 0;
}

.preview-hint {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.65rem 0.75rem;
  border-radius: 0.5rem;
  background: var(--surface-glass-subtle);
  border: 1px solid var(--border-default);
  font-size: 0.82rem;
  color: var(--text-secondary);
  line-height: 1.5;
}
.preview-hint__icon {
  font-size: 1.1rem;
  color: var(--accent-text);
  opacity: 0.7;
  flex-shrink: 0;
  margin-top: 0.1rem;
}
.preview-error {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  font-size: 0.82rem;
  color: var(--danger, #ef4444);
  padding: 0.5rem 0;
}
.preview-error--card,
.preview-plain-state {
  padding: 0.75rem 0.8rem;
  border-radius: 0.8rem;
  background: var(--surface-glass);
  border: 1px solid var(--border-default);
}
.preview-plain-state {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  font-size: 0.82rem;
  color: var(--text-secondary);
  line-height: 1.5;
}

/* ── SQL details panel ── */
.filter-sql-details {
  border-top: 1px solid var(--border-subtle);
  padding-top: 0.75rem;
}

.sql-details-toggle {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  width: 100%;
  background: none;
  border: none;
  padding: 0.3rem 0.5rem;
  margin: -0.3rem -0.5rem;
  border-radius: 0.4rem;
  cursor: pointer;
  color: var(--text-secondary);
  transition: background 0.15s;
  -webkit-tap-highlight-color: transparent;
}
.sql-details-toggle:hover {
  background: var(--surface-glass-subtle);
}
.sql-details-toggle__icon {
  font-size: 0.95rem;
  color: var(--text-muted);
}
.sql-details-toggle__label {
  flex: 1;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-muted);
  text-align: left;
}
.sql-details-toggle__chevron {
  font-size: 0.65rem;
  color: var(--text-muted);
}

.sql-mode-toggle {
  display: inline-flex;
  margin-top: 0.65rem;
  border: 1px solid var(--border-default);
  border-radius: 0.4rem;
  overflow: hidden;
}
.sql-mode-btn {
  flex: 1;
  padding: 0.3rem 0.85rem;
  font-size: 0.75rem;
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
  background: var(--accent, #6366f1);
  color: #fff;
}
.sql-mode-hint {
  font-size: 0.75rem;
  color: var(--text-muted);
  line-height: 1.45;
  margin: 0.45rem 0 0.5rem;
}
.sql-mode-hint code {
  font-size: 0.72rem;
  background: var(--surface-glass);
  border: 1px solid var(--border-default);
  border-radius: 0.2rem;
  padding: 0.05rem 0.25rem;
  color: var(--accent-text);
  font-family: monospace;
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
  font-size: 0.72rem;
  line-height: 1.55;
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
  font-size: 0.875rem;
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
.my-datatable { font-size: 0.85rem; }
.color-indicator {
  display: inline-block;
  width: 16px;
  height: 16px;
  margin-left: 8px;
  border-radius: 50%;
}

/* ── Mobile tweaks ── */
@media screen and (max-width: 768px) {
  .filter-scroll {
    padding: 0.5rem 0.75rem 0.75rem;
  }
  .filter-hero,
  .filter-card,
  .filter-empty-state {
    padding: 0.85rem;
  }
  .filter-hero__header {
    align-items: flex-start;
  }
  .filter-view-switch {
    gap: 0.25rem;
  }
  .filter-view-switch__btn {
    padding-inline: 0.55rem;
    font-size: 0.76rem;
  }
  .results-tiles {
    grid-template-columns: 1fr;
  }
  .my-datatable { font-size: 0.875rem; }
  .my-datatable :deep(th),
  .my-datatable :deep(td) {
    padding: 0.5rem 0.25rem;
    white-space: nowrap;
  }
}

@media screen and (min-width: 900px) {
  .filter-fields-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

</style>
