<template>
  <BottomSheet
    :model-value="show"
    :detents="[{ height: '72vh' }, { height: '95vh' }]"
    :no-backdrop="false"
    no-scroll-hint
    sheet-class="sheet--solid-over-map sheet--filter-workbench"
    @update:model-value="emit('update:show', $event)"
    @closed="emit('closed')"
  >
    <!-- ── Header: 3 pill tabs, Stats-style ── -->
    <template #title>
      <div class="cf-header-nav">
        <i class="bi bi-funnel cf-sheet-icon"></i>
        <div class="cf-header-tabs" role="tablist" aria-label="Filter views">
          <button
            class="cf-tab"
            :class="{ 'cf-tab--active': activeView === 'filter' }"
            role="tab"
            @pointerdown.stop
            @click="activeView = 'filter'"
          >
            Filter
          </button>
          <button
            class="cf-tab"
            :class="{ 'cf-tab--active': activeView === 'colors', 'cf-tab--disabled': !colorsTabEnabled }"
            :disabled="!colorsTabEnabled"
            role="tab"
            @pointerdown.stop
            @click="colorsTabEnabled && (activeView = 'colors')"
          >
            Colors
            <span v-if="colorsBadge" class="cf-tab__badge">{{ colorsBadge }}</span>
          </button>
          <button
            class="cf-tab cf-tab--compact"
            :class="{ 'cf-tab--active': activeView === 'sql', 'cf-tab--disabled': !sqlTabEnabled }"
            :disabled="!sqlTabEnabled"
            role="tab"
            aria-label="SQL"
            title="SQL expression"
            @pointerdown.stop
            @click="sqlTabEnabled && (activeView = 'sql')"
          >
            <i class="bi bi-code-slash"></i>
            <span class="cf-tab__label-sql">SQL</span>
          </button>
        </div>
      </div>
    </template>

    <template #header-actions>
      <label
        class="toggle-switch cf-header-toggle"
        :aria-label="filterEnabled ? 'Disable filter' : 'Enable filter'"
        @pointerdown.stop
        @click.stop
      >
        <input type="checkbox" :checked="filterEnabled" @change="filterEnabled ? disableFilter() : enableFilter()" />
        <span :class="['toggle-switch__track', filterEnabled && 'toggle-switch__track--on']"
          ><span class="toggle-switch__thumb"></span
        ></span>
        <span :class="['toggle-switch__text', filterEnabled && 'toggle-switch__text--on']">{{
          filterEnabled ? 'On' : 'Off'
        }}</span>
      </label>
    </template>

    <!-- ── Body ── -->
    <div class="filter-root">
      <div class="filter-scroll" :class="{ 'filter-scroll--workbench': filterEnabled && activeView === 'filter' }">
        <!-- Off: explain how to enable; fills the body across all tabs -->
        <div v-if="!filterEnabled" class="cf-off-card">
          <div class="cf-off-card__icon"><i class="bi bi-funnel"></i></div>
          <h3 class="cf-off-card__title">Filtering is off</h3>
          <p class="cf-off-card__text">
            Turn filtering on to narrow tracks by type, date or geo area, group them, and color the map. The
            <strong>Colors</strong> and <strong>SQL</strong> tabs unlock once a filter is selected.
          </p>
          <button class="cf-primary-btn" type="button" @click="enableFilter">
            <i class="bi bi-power"></i> Enable filter
          </button>
        </div>

        <!-- ── FILTER tab ── -->
        <div v-else-if="activeView === 'filter'" class="cf-tab-body cf-tab-body--workbench">
          <div class="filter-mobile-switch" aria-label="Filter workbench view">
            <button
              type="button"
              class="filter-mobile-switch__button"
              :class="{ 'filter-mobile-switch__button--active': filterWorkbenchMode === 'catalog' }"
              @click="filterWorkbenchMode = 'catalog'"
            >
              Filters
            </button>
            <button
              type="button"
              class="filter-mobile-switch__button"
              :class="{ 'filter-mobile-switch__button--active': filterWorkbenchMode === 'detail' }"
              @click="filterWorkbenchMode = 'detail'"
            >
              Settings
            </button>
          </div>

          <div class="filter-workbench" :class="`filter-workbench--${filterWorkbenchMode}`">
            <FilterCatalog
              class="filter-workbench__catalog"
              :groups="filterOptionGroups"
              :selected-filter-info="selectedFilter?.filterInfo ?? null"
              @select-filter="selectFilterInfo"
            />

            <section class="filter-workbench__detail-pane" aria-label="Selected filter settings">
              <FilterDetailPanel
                :selected-filter="selectedFilter"
                :track-id-candidate-tracks="trackIdCandidateTracks"
                :track-id-candidates-loading="isTrackIdCandidatesLoading"
                @set-date-time-param="setDateTimeParamFromPayload"
                @set-string-param="setStringParamFromPayload"
                @start-geo-drawing="onStartGeoDrawing"
                @clear-geo-shape="onClearGeoShape"
              />
            </section>
          </div>

          <FilterActionBar
            :active-track-count-display="activeTrackCountDisplay"
            :preview-group-count="previewGroupCount"
            :category-colors="previewCategoryColors"
            :has-preview-result="previewResult != null"
            :is-preview-loading="isPreviewLoading"
            :preview-error="previewError"
            :can-open-results-view="canOpenResultsView"
            @open-colors="activeView = 'colors'"
          />
        </div>

        <!-- ── COLORS tab ── -->
        <div v-else-if="activeView === 'colors'" class="cf-tab-body cf-tab-body--colors">
          <div class="cf-colors-grid">
            <div class="cf-colors-controls">
              <div class="cf-field">
                <label class="cf-field__label">Color palette</label>
                <Select
                  v-if="selectedFilter"
                  v-model="selectedFilter.palette"
                  :options="colorPaletteList"
                  option-label="pLabel"
                  placeholder="No coloring"
                  :show-clear="true"
                  class="filter-full-width"
                  append-to="body"
                  @change="onPaletteChanged"
                />
                <div
                  v-if="palettePreviewColors.length > 0"
                  class="palette-preview"
                  :class="{ 'palette-preview--expanded': palettePreviewExpanded }"
                >
                  <span
                    v-for="(color, index) in palettePreviewColors"
                    :key="`${paletteLabel}-${index}-${color}`"
                    class="palette-preview__swatch"
                    :style="{ backgroundColor: color }"
                    :title="color"
                  ></span>
                  <button
                    v-if="paletteHasOverflow"
                    class="palette-preview__more"
                    type="button"
                    :aria-expanded="palettePreviewExpanded"
                    :title="
                      palettePreviewExpanded
                        ? 'Collapse palette preview'
                        : `Show ${paletteHiddenColorCount} more colors`
                    "
                    @click="togglePalettePreview"
                  >
                    {{ palettePreviewExpanded ? 'Show less' : `+${paletteHiddenColorCount} other colors` }}
                  </button>
                </div>
                <p class="cf-field__hint">{{ paletteReviewHelpText }}</p>
              </div>

              <div class="cf-field">
                <label class="cf-field__label">Legend order</label>
                <Select
                  :model-value="legendSortStrategyValue"
                  :options="legendSortStrategyOptions"
                  option-label="label"
                  option-value="value"
                  placeholder="Default order"
                  class="filter-full-width"
                  append-to="body"
                  @update:model-value="onLegendSortChanged"
                >
                  <template #option="slotProps">
                    <div class="legend-sort-option">
                      <span class="legend-sort-option__label">{{ slotProps.option.label }}</span>
                      <span class="legend-sort-option__description">{{ slotProps.option.description }}</span>
                    </div>
                  </template>
                </Select>
                <p class="cf-field__hint">{{ legendSortHelpText }}</p>
              </div>
            </div>

            <section class="cf-section cf-section--breakdown">
              <div class="cf-section__head">
                <h3 class="cf-section__title">{{ previewSectionTitle }}</h3>
                <span v-if="previewResult" class="cf-section__count">{{
                  previewResult.resultEntries?.length ?? 0
                }}</span>
              </div>
              <p v-if="previewSectionNote" class="cf-section__note">{{ previewSectionNote }}</p>

              <div v-if="isPreviewLoading" class="preview-skeleton">
                <div class="skeleton-line skeleton-line--wide"></div>
                <div class="skeleton-line skeleton-line--medium"></div>
                <div class="skeleton-line skeleton-line--narrow"></div>
              </div>

              <template v-else-if="previewResult">
                <ul v-if="previewHasColors" class="legend-list">
                  <li
                    v-for="row in previewLegend"
                    :key="row.group"
                    class="legend-row"
                    @click="openGroupDrillDown(row.group)"
                  >
                    <span class="legend-swatch" :style="{ backgroundColor: row.color }"></span>
                    <span class="legend-group-wrap">
                      <span class="legend-group">{{ row.label }}</span>
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
        </div>

        <!-- ── SQL tab ── -->
        <div v-else-if="activeView === 'sql'" class="cf-tab-body">
          <FilterSqlPreview
            :filter-info="selectedFilter?.filterInfo"
            :view-mode="sqlViewMode"
            @update:view-mode="sqlViewMode = $event"
          />
        </div>
      </div>
    </div>

    <!-- ══ Drill-down sheet (stacks above main sheet) ══ -->
    <BottomSheet
      v-model="showDrillDown"
      :title="drillDownTitle"
      icon="bi bi-table"
      :detents="[{ height: '75vh' }, { height: '95vh' }]"
      :no-backdrop="true"
      :z-index="5100"
    >
      <div class="review-root">
        <div class="review-navigation">
          <Button
            label="Back"
            icon="pi pi-arrow-left"
            class="p-button-secondary p-button-sm"
            @click="showDrillDown = false"
          />
        </div>
        <div v-if="isDrillDownLoading" class="review-intro">Loading track details…</div>
        <div class="review-scroll">
          <DataTable
            :value="drillDownEntries"
            class="p-datatable-gridlines my-datatable"
            responsive-layout="scroll"
            paginator
            :rows="10"
            :rows-per-page-options="[10, 50, 100, 500, 1000]"
            removable-sort
          >
            <Column field="id" header="ID" sortable />
            <Column header="" style="width: 3.5rem; min-width: 3.5rem; max-width: 3.5rem">
              <template #body="slotProps">
                <TrackShapePreview :track-id="slotProps.data.id" :width="48" :height="32" :padding="3" />
              </template>
            </Column>
            <Column field="group" header="Group" sortable>
              <template #body="slotProps">
                <div style="white-space: nowrap">
                  <span>{{ formatSelectedFilterGroupLabel(slotProps.data.group) }}</span>
                  <span
                    class="color-indicator"
                    :style="{ backgroundColor: colorForSelectedFilterGroup(slotProps.data.group) }"
                  ></span>
                </div>
              </template>
            </Column>
            <Column field="gpsTrack.startDate" header="Start Date" sortable>
              <template #body="slotProps">
                {{ formatDateAndTime(slotProps.data.gpsTrack?.startDate) }}
              </template>
            </Column>
            <Column field="gpsTrack.trackName" header="Track Name" sortable />
            <Column field="gpsTrack.trackDescription" header="Description" sortable />
          </DataTable>
        </div>
      </div>
    </BottomSheet>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, inject, markRaw, onBeforeUnmount, onMounted, ref, shallowRef, watch, type Ref } from 'vue';
import { formatDateAndTime } from '@/utils/Utils';
import { ClientFilterConfig, type FilterParamsRequest, FilterService } from '@/components/filter/FilterService';
import { useFilterStore } from '@/stores/filterStore';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { ParamDefinition } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';
import type { QueryResult } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/QueryResult';
import type { QueryResultEntry } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/QueryResultEntry';
import type {
  GeoCircle,
  GeoPolygon,
  GeoRectangle,
  GpsTrack,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { FilterConfigEntityLegendSortStrategyEnum } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterConfigEntity';
import { fetchFilters, fetchResolveFilter, type ResolveFilterResult } from '@/utils/ServiceHelper';
import { format } from 'date-fns';
import { ColorPalette } from '@/components/filter/ColorPalette';
import {
  buildFilterOptionGroups,
  colorForFilterGroup,
  compareLegendEntries,
  findPreferredPalette,
  effectiveParamMetadataEntries,
  formatFilterGroupLabel,
  gradientFilterHelpText,
  isGradientPalette,
  isSequentialGradientFilter,
  isSameFilterInfo,
  legendSortStrategyDescription,
  legendSortStrategyLabel,
  normalizeLegendSortStrategy,
  parseFilterRef,
  parseNumericBucket,
  sortFilterInfos,
  type FilterOptionGroup,
  type FilterParamMetadata,
  type LegendSortStrategy,
  type LabeledLegendEntry,
} from '@/utils/filterMetadata';
import {
  ensureFilterParamMaps,
  hasCompleteStringParamsForDefinitions,
  pruneFilterParamsForDefinitions,
} from '@/utils/filterParams';

import BottomSheet from '@/components/ui/BottomSheet.vue';
import FilterActionBar from '@/components/filter/FilterActionBar.vue';
import FilterCatalog from '@/components/filter/FilterCatalog.vue';
import FilterDetailPanel from '@/components/filter/FilterDetailPanel.vue';
import FilterSqlPreview from '@/components/filter/FilterSqlPreview.vue';
import TrackShapePreview from '@/components/ui/TrackShapePreview.vue';

const EVENTS = {
  filterAppliedEvent: 'filterAppliedEvent',
  filterChangedEvent: 'filterChangedEvent',
  filterStyleChanged: 'filter-style-changed',
  startGeoDrawing: 'start-geo-drawing',
  clearGeoShape: 'clear-geo-shape',
} as const;

const PALETTE_PREVIEW_SWATCH_LIMIT = 10;
const SELECTABLE_LEGEND_SORT_STRATEGIES: LegendSortStrategy[] = [
  FilterConfigEntityLegendSortStrategyEnum.LabelAsc,
  FilterConfigEntityLegendSortStrategyEnum.NumericAsc,
  FilterConfigEntityLegendSortStrategyEnum.CountDesc,
];
const TRACK_PICKER_WIDGET = 'trackPicker';
const OPTIONS_SOURCE_ORIGIN_FILTER_RESULT = 'originFilterResult';

type ActiveView = 'filter' | 'colors' | 'sql';
type FilterWorkbenchMode = 'catalog' | 'detail';
type SqlViewMode = 'template' | 'resolved';
type Toast = { add: (options: { severity: string; summary: string; detail: string; life: number }) => void };
type GeoShapes = {
  circles: Record<string, GeoCircle>;
  rectangles: Record<string, GeoRectangle>;
  polygons: Record<string, GeoPolygon>;
  labels: Record<string, string>;
};
type Emits = {
  (event: 'update:show', value: boolean): void;
  (event: 'closed'): void;
  (event: 'filterAppliedEvent'): void;
  (event: 'filterChangedEvent', result?: ResolveFilterResult): void;
  (event: 'filter-style-changed'): void;
  (event: 'start-geo-drawing', paramDef: ParamDefinition): void;
  (event: 'clear-geo-shape', paramDef: ParamDefinition): void;
};

defineOptions({ name: 'CustomFilter' });

const props = defineProps<{
  tileLayer?: unknown | null;
  palette?: unknown | null;
  totalTrackCount?: number | null;
  visibleTrackCount?: number | null;
  show?: boolean;
}>();

const emit = defineEmits<Emits>();
const toast = inject('toast') as Toast;
const filterStore = useFilterStore();

const filters = shallowRef<FilterInfo[]>([]);
const selectedFilter: Ref<ClientFilterConfig> = ref(new ClientFilterConfig()) as Ref<ClientFilterConfig>;
const colorPaletteList = shallowRef<ColorPalette[]>([]);
const filterEnabled = ref(false);
const activeView = ref<ActiveView>('filter');
const filterWorkbenchMode = ref<FilterWorkbenchMode>('detail');
const previewResult = ref<QueryResult | null>(null);
const isPreviewLoading = ref(false);
const previewError = ref<string | null>(null);
const sqlViewMode = ref<SqlViewMode>('template');
const showDrillDown = ref(false);
const drillDownGroup = ref<string | null>(null);
const drillDownFullResult = ref<QueryResult | null>(null);
const isDrillDownLoading = ref(false);
const palettePreviewExpanded = ref(false);
const trackIdCandidateTracks = shallowRef<GpsTrack[]>([]);
const isTrackIdCandidatesLoading = ref(false);

let previewDebounceTimer: ReturnType<typeof setTimeout> | null = null;
let previewAbortController: AbortController | null = null;
let trackIdCandidateDebounceTimer: ReturnType<typeof setTimeout> | null = null;
let trackIdCandidateLoadSeq = 0;
let lastTrackIdCandidateLoadKey = '';

function normalizeSelectableLegendSortStrategy(value: unknown): LegendSortStrategy | null {
  const strategy = normalizeLegendSortStrategy(value);
  return strategy && SELECTABLE_LEGEND_SORT_STRATEGIES.includes(strategy) ? strategy : null;
}

const filterOptionGroups = computed((): FilterOptionGroup[] => buildFilterOptionGroups(filters.value));
const legendSortStrategyValue = computed<LegendSortStrategy | null>({
  get: () => normalizeSelectableLegendSortStrategy(selectedFilter.value?.legendSortStrategy),
  set: (value) => {
    selectedFilter.value.legendSortStrategy = normalizeSelectableLegendSortStrategy(value);
  },
});
const legendSortStrategyOptions = computed(
  (): Array<{ label: string; value: LegendSortStrategy | null; description: string }> => [
    {
      label: 'Default (SQL result order)',
      value: null,
      description: legendSortStrategyDescription(null),
    },
    ...SELECTABLE_LEGEND_SORT_STRATEGIES.map((strategy) => ({
      label: legendSortStrategyLabel(strategy),
      value: strategy,
      description: legendSortStrategyDescription(strategy),
    })),
  ]
);
const effectiveLegendSortStrategy = computed((): LegendSortStrategy | null =>
  normalizeSelectableLegendSortStrategy(selectedFilter.value?.legendSortStrategy)
);
const previewLegendSortEntries = computed((): Array<{ group: string; count: number }> => {
  if (!previewResult.value?.resultEntries) return [];
  const counts = new Map<string, number>();
  for (const entry of previewResult.value.resultEntries) {
    const group = entry.group;
    if (group) counts.set(group, (counts.get(group) || 0) + 1);
  }
  return Array.from(counts.entries()).map(([group, count]) => ({ group, count }));
});
const legendSortHelpText = computed((): string => {
  const filterConfig = selectedFilter.value?.filterInfo?.filterConfig;
  const strategy = effectiveLegendSortStrategy.value;
  if (!strategy) {
    const contextNote =
      previewResult.value && previewLegendSortEntries.value.length < 2
        ? ' The current result has fewer than two legend groups, so changing order cannot visibly change it.'
        : '';
    return `Default: SQL result order. ${legendSortStrategyDescription(null)}${contextNote}`;
  }

  const effectiveLabel = legendSortStrategyLabel(strategy);
  const contextNote = legendSortContextNote(strategy);
  const behaviorText = contextNote ?? legendSortStrategyDescription(strategy);
  const gradientNote =
    isSequentialGradientFilter(filterConfig) && strategy !== FilterConfigEntityLegendSortStrategyEnum.NumericAsc
      ? ` Gradient filters are easiest to read with ${legendSortStrategyLabel(FilterConfigEntityLegendSortStrategyEnum.NumericAsc)}.`
      : '';

  return `Override: ${effectiveLabel}. ${behaviorText}${gradientNote}`;
});
const paletteLabel = computed((): string => selectedFilter.value?.palette?.pLabel || 'No coloring');
const allPaletteColors = computed((): string[] => selectedFilter.value?.palette?.pColors || []);
const palettePreviewColors = computed((): string[] =>
  palettePreviewExpanded.value ? allPaletteColors.value : allPaletteColors.value.slice(0, PALETTE_PREVIEW_SWATCH_LIMIT)
);
const paletteHasOverflow = computed((): boolean => allPaletteColors.value.length > PALETTE_PREVIEW_SWATCH_LIMIT);
const paletteHiddenColorCount = computed((): number =>
  Math.max(allPaletteColors.value.length - PALETTE_PREVIEW_SWATCH_LIMIT, 0)
);
const previewGroupCount = computed((): number => {
  if (!previewResult.value?.resultEntries) return 0;
  const groups = new Set(previewResult.value.resultEntries.map((e: QueryResultEntry) => e.group).filter(Boolean));
  return groups.size;
});
const previewHasColors = computed((): boolean => {
  const palette = selectedFilter.value?.palette;
  return !!(palette && !palette.isEmptyColorPalette() && previewGroupCount.value > 0);
});
const paletteReviewHelpText = computed((): string => {
  const filterInfo = selectedFilter.value?.filterInfo;
  const filterConfig = filterInfo?.filterConfig;
  if (isSequentialGradientFilter(filterConfig)) {
    const baseHelpText = gradientFilterHelpText(filterInfo);
    if (previewHasColors.value && isGradientPalette(selectedFilter.value?.palette)) {
      if (effectiveLegendSortStrategy.value === FilterConfigEntityLegendSortStrategyEnum.NumericAsc) {
        return `${baseHelpText} The legend is sorted from low to high percentile.`;
      }
      return `${baseHelpText} Current legend order: ${legendSortStrategyLabel(effectiveLegendSortStrategy.value)}.`;
    }
    if (previewHasColors.value) {
      return `${baseHelpText} The selected palette is categorical, so neighboring buckets will not read as a smooth scale.`;
    }
    return baseHelpText;
  }
  if (!previewResult.value) {
    return 'Choose colors here so grouped results are ready to read as soon as the preview updates.';
  }
  if (previewGroupCount.value === 0) {
    return 'This filter returns a direct list of tracks, so the palette is currently optional.';
  }
  if (previewHasColors.value) {
    return 'The legend below uses this palette and the same group-to-color mapping is applied on the map.';
  }
  return 'Choose colors where the grouping is visible. The legend below updates with the same mapping used on the map.';
});
const activeTrackCountDisplay = computed((): string => {
  if (previewResult.value) return String(previewResult.value.resultEntries?.length ?? 0);
  if (props.visibleTrackCount != null) return String(props.visibleTrackCount);
  return '—';
});
const totalTrackCountDisplay = computed((): string => {
  if (props.totalTrackCount != null) return String(props.totalTrackCount);
  return '—';
});
const previewLegend = computed((): LabeledLegendEntry[] => {
  if (!previewHasColors.value || !previewResult.value?.resultEntries) return [];
  const palette = selectedFilter.value.palette;
  const filterInfo = selectedFilter.value?.filterInfo;
  const filterConfig = filterInfo?.filterConfig;
  return [...previewLegendSortEntries.value]
    .sort((a, b) => compareLegendEntries(a, b, filterConfig, selectedFilter.value?.legendSortStrategy))
    .map((entry) => ({
      group: entry.group,
      label: formatFilterGroupLabel(entry.group, filterInfo),
      color: colorForFilterGroup(palette, entry.group, filterInfo),
      count: entry.count,
    }));
});
const previewCategoryColors = computed((): string[] => previewLegend.value.map((entry) => entry.color));
const canOpenResultsView = computed((): boolean => !!previewResult.value || !!previewError.value);
const colorsTabEnabled = computed(
  (): boolean => filterEnabled.value && (canOpenResultsView.value || isPreviewLoading.value)
);
const sqlTabEnabled = computed((): boolean => filterEnabled.value && !!rawSQL.value);
const colorsBadge = computed((): string => {
  if (isPreviewLoading.value) return '…';
  if (previewError.value) return '!';
  if (previewResult.value) return String(previewResult.value.resultEntries?.length ?? 0);
  return '';
});
const previewSectionTitle = computed((): string => {
  if (previewGroupCount.value > 0) return 'Grouped breakdown';
  if (previewResult.value) return 'Filtered track list';
  if (previewError.value) return 'Preview issue';
  if (isPreviewLoading.value) return 'Refreshing preview';
  return 'Preview';
});
const previewSectionNote = computed((): string => {
  if (previewHasColors.value)
    return 'Each group below uses the same color that appears on the map. Select a group to review its tracks.';
  if (previewGroupCount.value > 0)
    return 'The filter already groups the result. Add a palette above if you want those groups to stand out on the map.';
  if (previewResult.value)
    return 'This filter does not create groups, so the map simply reflects the narrowed set of tracks.';
  if (previewError.value) return 'The preview could not be rendered with the current parameters.';
  return '';
});
const drillDownEntries = computed((): QueryResultEntry[] => {
  if (!drillDownFullResult.value?.resultEntries) return [];
  if (!drillDownGroup.value) return drillDownFullResult.value.resultEntries;
  return drillDownFullResult.value.resultEntries.filter((e: QueryResultEntry) => e.group === drillDownGroup.value);
});
const drillDownTitle = computed((): string => {
  if (!drillDownGroup.value) return 'All tracks';
  const count = drillDownEntries.value.length;
  const groupLabel = formatFilterGroupLabel(drillDownGroup.value, selectedFilter.value?.filterInfo);
  return `${groupLabel} — ${count} tracks`;
});
const rawSQL = computed((): string => selectedFilter.value?.filterInfo?.filterConfig?.expression ?? '');
const resolvedSQL = computed((): string => selectedFilter.value?.filterInfo?.resolvedSQL ?? '');

watch(colorsTabEnabled, (enabled) => {
  if (!enabled && activeView.value === 'colors') activeView.value = 'filter';
});
watch(sqlTabEnabled, (enabled) => {
  if (!enabled && activeView.value === 'sql') activeView.value = 'filter';
});

onMounted(async () => {
  try {
    const palettes = await ColorPalette.fetch();
    const emptyPalette = markRaw(new ColorPalette());
    emptyPalette.pLabel = 'No coloring';
    emptyPalette.pDescription = 'No coloring at all';
    colorPaletteList.value = [emptyPalette, ...palettes.map((palette) => markRaw(palette))];

    const fetchedFilters = await fetchFilters();
    filters.value = sortFilterInfos(fetchedFilters);

    const clientFilterConfig = await FilterService.loadClientFilterConfig();
    const filterConfig = clientFilterConfig?.filterInfo?.filterConfig;

    const selectedFilterInfo = fetchedFilters.find((f) => {
      const candidate = f.filterConfig;
      if (filterConfig?.id != null && candidate?.id === filterConfig.id) return true;
      return (
        candidate?.filterName === filterConfig?.filterName && candidate?.filterDomain === filterConfig?.filterDomain
      );
    });
    if (selectedFilterInfo) {
      clientFilterConfig.filterInfo = selectedFilterInfo;
    }

    clientFilterConfig.filterParams = ensureFilterParamMaps(clientFilterConfig.filterParams);
    // Restore DATE_TIME string params to Date objects (localStorage serialises them as strings)
    for (const key in clientFilterConfig.filterParams.dateTimeParams || {}) {
      const val = clientFilterConfig.filterParams.dateTimeParams?.[key];
      if (val && typeof val === 'string') {
        (clientFilterConfig.filterParams.dateTimeParams as Record<string, unknown>)[key] = new Date(
          val.replace(' ', 'T')
        );
      }
    }
    selectedFilter.value = normalizeClientFilterConfig(clientFilterConfig);
    const savedFilterIsActive = isFilterStateActive(selectedFilter.value);
    applyPreferredPaletteForSelectedFilter(false);

    // Determine initial toggle state from the persisted config, before any UI-only preferred palette is applied.
    filterEnabled.value = savedFilterIsActive;

    // If filter is active, trigger an initial live preview
    if (filterEnabled.value) {
      scheduleLivePreview();
      scheduleTrackIdCandidateLoad();
    }
  } catch (error) {
    console.error('Error in mounted lifecycle hook:', error);
  }
});

onBeforeUnmount(() => {
  if (previewDebounceTimer) clearTimeout(previewDebounceTimer);
  if (trackIdCandidateDebounceTimer) clearTimeout(trackIdCandidateDebounceTimer);
  previewAbortController?.abort();
});

function legendSortContextNote(strategy: LegendSortStrategy): string | null {
  const entries = previewLegendSortEntries.value;
  if (!previewResult.value) {
    return 'The preview shows whether this changes the current groups once it has loaded.';
  }
  if (entries.length < 2) {
    return 'The current result has fewer than two legend groups, so changing order cannot visibly change it.';
  }

  switch (strategy) {
    case FilterConfigEntityLegendSortStrategyEnum.NumericAsc: {
      const numericGroupCount = entries.filter((entry) => parseNumericBucket(entry.group) != null).length;
      return numericGroupCount >= 2
        ? null
        : 'This only changes numeric bucket groups. The current groups are not numeric, so it falls back to Label A-Z.';
    }
    case FilterConfigEntityLegendSortStrategyEnum.CountDesc: {
      const distinctCounts = new Set(entries.map((entry) => entry.count));
      return distinctCounts.size > 1
        ? null
        : 'All current groups have the same track count, so this falls back to the group order.';
    }
    case FilterConfigEntityLegendSortStrategyEnum.LabelAsc:
    default:
      return null;
  }
}

function setDateTimeParam(name: string | undefined, value: Date | Date[] | (Date | null)[] | null | undefined): void {
  if (!name) return;
  const map = selectedFilter.value.filterParams.dateTimeParams as Record<string, unknown> | undefined;
  if (!map) return;
  map[name] = value as unknown as string;
  onParamChanged(name);
}

function normalizeClientFilterConfig(config: ClientFilterConfig | null | undefined): ClientFilterConfig {
  const normalized = new ClientFilterConfig();
  normalized.filterInfo = config?.filterInfo ?? ({} as FilterInfo);
  const fp = ensureFilterParamMaps(config?.filterParams || {});
  normalized.filterParams = {
    ...fp,
    stringParams: fp.stringParams || {},
    dateTimeParams: fp.dateTimeParams || {},
    geoCircles: fp.geoCircles || {},
    geoRectangles: fp.geoRectangles || {},
    geoPolygons: fp.geoPolygons || {},
  };
  normalized.palette = markRaw(ColorPalette.of(config?.palette));
  normalized.legendSortStrategy = normalizeSelectableLegendSortStrategy(config?.legendSortStrategy);
  return normalized;
}

function applyPreferredPaletteForSelectedFilter(replaceIncompatiblePalette: boolean): void {
  const filterConfig = selectedFilter.value?.filterInfo?.filterConfig;
  const preferredPalette = findPreferredPalette(colorPaletteList.value, filterConfig);
  if (!preferredPalette) return;

  const currentPalette = selectedFilter.value?.palette;
  const currentPaletteUsable = currentPalette && !currentPalette.isEmptyColorPalette();
  if (!currentPaletteUsable) {
    selectedFilter.value.palette = markRaw(preferredPalette);
    return;
  }
  if (!replaceIncompatiblePalette) return;

  const selectedFilterIsGradient = isSequentialGradientFilter(filterConfig);
  const currentPaletteIsGradient = isGradientPalette(currentPalette);
  if (
    (selectedFilterIsGradient && !currentPaletteIsGradient) ||
    (!selectedFilterIsGradient && currentPaletteIsGradient)
  ) {
    selectedFilter.value.palette = markRaw(preferredPalette);
  }
}

function formatSelectedFilterGroupLabel(group: string | undefined | null): string {
  return formatFilterGroupLabel(group, selectedFilter.value?.filterInfo);
}

function colorForSelectedFilterGroup(group: string | undefined | null): string {
  const palette = selectedFilter.value?.palette;
  if (!palette || palette.isEmptyColorPalette()) return 'transparent';
  return colorForFilterGroup(palette, group, selectedFilter.value?.filterInfo);
}

function isFilterStateActive(
  config: { filterInfo?: FilterInfo; filterParams?: FilterParamsRequest } | null | undefined
): boolean {
  return FilterService.hasActiveFilterConfig(config as ClientFilterConfig);
}

function selectFilterInfo(filterInfo: FilterInfo) {
  const sameFilter = isSameFilterInfo(selectedFilter.value?.filterInfo, filterInfo);
  selectedFilter.value.filterInfo = filterInfo;
  filterWorkbenchMode.value = 'detail';
  if (!sameFilter) onFilterInfoChanged();
}

// ── Toggle ──
function enableFilter() {
  filterEnabled.value = true;
  activeView.value = 'filter';
  filterWorkbenchMode.value = 'detail';
  // Guarantee sub-maps exist before the params form renders
  selectedFilter.value = normalizeClientFilterConfig(selectedFilter.value);
  scheduleLivePreview();
  scheduleTrackIdCandidateLoad();
}

function disableFilter() {
  filterEnabled.value = false;
  activeView.value = 'filter';
  previewResult.value = null;
  previewError.value = null;
  trackIdCandidateTracks.value = [];
  lastTrackIdCandidateLoadKey = '';
  showAllTracks();
}

// ── Filter changes → schedule preview ──
function onFilterInfoChanged() {
  palettePreviewExpanded.value = false;
  selectedFilter.value.legendSortStrategy = null;
  selectedFilter.value.filterParams = pruneFilterParamsForDefinitions(
    selectedFilter.value.filterParams,
    selectedFilter.value?.filterInfo?.paramDefinitions || []
  );
  if (!selectedFilter.value.palette) {
    selectedFilter.value.palette = markRaw(new ColorPalette());
  }
  applyPreferredPaletteForSelectedFilter(true);
  activeView.value = 'filter';
  scheduleLivePreview();
  scheduleTrackIdCandidateLoad();
}

function onParamChanged(paramName?: string) {
  scheduleLivePreview();
  if (paramName !== selectedTrackPickerParamName()) {
    scheduleTrackIdCandidateLoad();
  }
}

function setDateTimeParamFromPayload(payload: {
  name?: string;
  value: Date | Date[] | (Date | null)[] | null | undefined;
}) {
  setDateTimeParam(payload.name, payload.value);
}

function setStringParamFromPayload(payload: { name?: string; value: string }) {
  if (!payload.name) return;
  const params = ensureFilterParamMaps(selectedFilter.value.filterParams);
  if (payload.value.trim()) {
    params.stringParams![payload.name] = payload.value;
  } else {
    delete params.stringParams![payload.name];
  }
  selectedFilter.value.filterParams = params;
  onParamChanged(payload.name);
}

function onPaletteChanged() {
  palettePreviewExpanded.value = false;
  // Palette change doesn't need a server call — just recompute colors and auto-apply
  if (previewResult.value) {
    rebuildPreviewPalette();
  }
  filterStore.save(
    ClientFilterConfig.of(
      selectedFilter.value.filterInfo,
      getProcessedParams(),
      selectedFilter.value.palette,
      selectedFilter.value.legendSortStrategy
    )
  );
  emit(EVENTS.filterStyleChanged);
}

function onLegendSortChanged(value: unknown) {
  const nextStrategy = normalizeSelectableLegendSortStrategy(value);
  selectedFilter.value = ClientFilterConfig.of(
    selectedFilter.value.filterInfo,
    selectedFilter.value.filterParams,
    selectedFilter.value.palette,
    nextStrategy
  );
  if (previewResult.value) {
    rebuildPreviewPalette();
  }
  filterStore.save(
    ClientFilterConfig.of(
      selectedFilter.value.filterInfo,
      getProcessedParams(),
      selectedFilter.value.palette,
      selectedFilter.value.legendSortStrategy
    )
  );
  emit(EVENTS.filterStyleChanged);
}

function togglePalettePreview() {
  palettePreviewExpanded.value = !palettePreviewExpanded.value;
}

// ── Live preview engine ──
function scheduleLivePreview() {
  if (previewDebounceTimer) clearTimeout(previewDebounceTimer);
  previewDebounceTimer = setTimeout(() => executeLivePreview(), 400);
}

function selectedTrackPickerMetadata(): { name: string; metadata: FilterParamMetadata } | null {
  const entry = effectiveParamMetadataEntries(selectedFilter.value?.filterInfo).find(
    ([, metadata]) => metadata.widget === TRACK_PICKER_WIDGET
  );
  return entry ? { name: entry[0], metadata: entry[1] } : null;
}

function selectedTrackPickerParamName(): string | null {
  return selectedTrackPickerMetadata()?.name ?? null;
}

function selectedTrackPickerSourceFilterInfo(): FilterInfo | null {
  const trackPicker = selectedTrackPickerMetadata();
  if (trackPicker?.metadata.optionsSource?.type !== OPTIONS_SOURCE_ORIGIN_FILTER_RESULT) return null;

  const resolvedRef = parseFilterRef(trackPicker.metadata.optionsSource.resolvedFilterRef);
  if (!resolvedRef) return null;

  return (
    filters.value.find((filterInfo) => {
      const filterConfig = filterInfo.filterConfig;
      return (
        filterConfig?.filterDomain === resolvedRef.filterDomain && filterConfig?.filterName === resolvedRef.filterName
      );
    }) ?? null
  );
}

function scheduleTrackIdCandidateLoad() {
  if (trackIdCandidateDebounceTimer) clearTimeout(trackIdCandidateDebounceTimer);
  if (!filterEnabled.value || !selectedTrackPickerSourceFilterInfo()) {
    trackIdCandidateTracks.value = [];
    isTrackIdCandidatesLoading.value = false;
    return;
  }
  trackIdCandidateDebounceTimer = setTimeout(() => {
    void loadTrackIdCandidates();
  }, 350);
}

function parseCandidateTrack(rawTrack: GpsTrack): GpsTrack {
  return {
    ...rawTrack,
    startDate: rawTrack.startDate ? new Date(rawTrack.startDate) : undefined,
    endDate: rawTrack.endDate ? new Date(rawTrack.endDate) : undefined,
    createDate: rawTrack.createDate ? new Date(rawTrack.createDate) : undefined,
  };
}

async function loadTrackIdCandidates() {
  const sourceFilterInfo = selectedTrackPickerSourceFilterInfo();
  const filterId = sourceFilterInfo?.filterConfig?.id;
  if (!filterId || !filterEnabled.value) return;

  const loadKey = JSON.stringify({ filterId });
  if (loadKey === lastTrackIdCandidateLoadKey && trackIdCandidateTracks.value.length > 0) return;

  const seq = ++trackIdCandidateLoadSeq;
  isTrackIdCandidatesLoading.value = true;
  try {
    const result = await fetchResolveFilter(filterId, {}, true);
    if (seq !== trackIdCandidateLoadSeq) return;
    trackIdCandidateTracks.value = (result.queryResult.resultEntries ?? [])
      .map((entry) => entry.gpsTrack)
      .filter((track): track is GpsTrack => Boolean(track?.id))
      .map(parseCandidateTrack);
    lastTrackIdCandidateLoadKey = loadKey;
  } catch (error) {
    if (seq !== trackIdCandidateLoadSeq) return;
    console.error('Track picker candidate fetch error:', error);
    trackIdCandidateTracks.value = [];
  } finally {
    if (seq === trackIdCandidateLoadSeq) {
      isTrackIdCandidatesLoading.value = false;
    }
  }
}

async function executeLivePreview() {
  const filterId = selectedFilter.value?.filterInfo?.filterConfig?.id;
  if (!filterId) return;

  // Abort any in-flight request
  if (previewAbortController) previewAbortController.abort();
  previewAbortController = new AbortController();

  isPreviewLoading.value = true;
  previewError.value = null;

  try {
    const processedParams = getProcessedParams();
    if (
      !hasCompleteStringParamsForDefinitions(
        processedParams,
        selectedFilter.value?.filterInfo?.paramDefinitions || [],
        selectedFilter.value?.filterInfo
      )
    ) {
      previewResult.value = null;
      drillDownFullResult.value = null;
      return;
    }

    const result = await fetchResolveFilter(filterId, processedParams, false);
    previewResult.value = result.queryResult;
    rebuildPreviewPalette();
    // Auto-apply: persist and notify map immediately after every successful preview
    filterStore.save(
      ClientFilterConfig.of(
        selectedFilter.value.filterInfo,
        processedParams,
        selectedFilter.value.palette,
        selectedFilter.value.legendSortStrategy
      )
    );
    drillDownFullResult.value = null;
    emit(EVENTS.filterChangedEvent, result);
  } catch (error: unknown) {
    if (error instanceof Error && (error.name === 'AbortError' || error.message?.includes('abort'))) return;
    previewError.value = 'Preview failed. Check parameter values or switch to a different filter.';
    console.error('Live preview error:', error);
  } finally {
    isPreviewLoading.value = false;
  }
}

function rebuildPreviewPalette() {
  const palette = selectedFilter.value?.palette;
  const result = previewResult.value;
  if (palette && !palette.isEmptyColorPalette() && result?.resultEntries) {
    palette.reset();
    const filterInfo = selectedFilter.value?.filterInfo;
    const filterConfig = filterInfo?.filterConfig;
    const counts = new Map<string, number>();
    for (const entry of result.resultEntries) {
      if (entry.group) counts.set(entry.group, (counts.get(entry.group) || 0) + 1);
    }
    const sortedEntries = Array.from(counts.entries())
      .map(([group, count]) => ({ group, count }))
      .sort((a, b) => compareLegendEntries(a, b, filterConfig, selectedFilter.value?.legendSortStrategy));
    for (const entry of sortedEntries) {
      colorForFilterGroup(palette, entry.group, filterInfo);
    }
    for (const entry of result.resultEntries) {
      if (entry.group) colorForFilterGroup(palette, entry.group, filterInfo, true);
    }
  }
}

// ── Drill-down ──
async function openGroupDrillDown(group: string) {
  drillDownGroup.value = group;
  showDrillDown.value = true;

  // Fetch full details (with GPS tracks) if not already loaded
  if (!drillDownFullResult.value) {
    isDrillDownLoading.value = true;
    try {
      const filterId = selectedFilter.value?.filterInfo?.filterConfig?.id;
      if (filterId === undefined) return;
      const drillResult = await fetchResolveFilter(filterId, getProcessedParams(), true);
      drillDownFullResult.value = drillResult.queryResult;
    } catch (error) {
      console.error('Drill-down fetch error:', error);
    } finally {
      isDrillDownLoading.value = false;
    }
  }
}

function showAllTracks() {
  const defaultFilter = filters.value.find((f) =>
    FilterService.isStandardFilterWithStandardParams(ClientFilterConfig.of(f))
  );
  if (defaultFilter) {
    const cfg = ClientFilterConfig.of(defaultFilter, {}, undefined);
    selectedFilter.value = cfg;
    activeView.value = 'filter';
    filterStore.save(cfg);
    previewResult.value = null;
    drillDownFullResult.value = null;
    emit(EVENTS.filterChangedEvent);

    toast.add({
      severity: 'info',
      summary: 'Showing all tracks',
      detail: '',
      life: 3000,
    });
  }
}

// ── Param helpers ──
function formatDateParamImpl(date: Date) {
  return format(date, 'yyyy-MM-dd HH:mm:ss');
}

function getProcessedParams(options: { includeTrackIds?: boolean } = {}): FilterParamsRequest {
  const includeTrackIds = options.includeTrackIds !== false;
  const params = selectedFilter.value.filterParams;
  if (!params) return {};
  const result: FilterParamsRequest = {
    stringParams: { ...(params.stringParams || {}) },
    dateTimeParams: {},
    geoCircles: params.geoCircles ? { ...params.geoCircles } : undefined,
    geoRectangles: params.geoRectangles ? { ...params.geoRectangles } : undefined,
    geoPolygons: params.geoPolygons ? { ...params.geoPolygons } : undefined,
  };
  // Format Date objects in dateTimeParams
  for (const key in params.dateTimeParams || {}) {
    const value = params.dateTimeParams![key] as unknown;
    if (value && value instanceof Date) {
      result.dateTimeParams![key] = formatDateParamImpl(value);
    } else if (value) {
      result.dateTimeParams![key] = value as string;
    }
  }
  if (!includeTrackIds && result.stringParams) {
    const trackPickerParamName = selectedTrackPickerParamName();
    if (trackPickerParamName) delete result.stringParams[trackPickerParamName];
  }
  return pruneFilterParamsForDefinitions(result, selectedFilter.value?.filterInfo?.paramDefinitions || []);
}

// ── Geo drawing ──
function onStartGeoDrawing(paramDef: ParamDefinition) {
  emit(EVENTS.startGeoDrawing, paramDef);
}

function onClearGeoShape(paramDef: ParamDefinition) {
  const params = selectedFilter.value.filterParams;
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
  emit(EVENTS.clearGeoShape, paramDef);
  scheduleLivePreview();
  scheduleTrackIdCandidateLoad();
}

/**
 * Called by parent (Filter.vue) when the user finishes drawing a shape on the map.
 */
function onGeoDrawingComplete(paramDef: ParamDefinition, shape: GeoCircle | GeoRectangle | GeoPolygon) {
  const params = selectedFilter.value.filterParams;
  if (!params || !paramDef.name) return;
  switch (paramDef.type) {
    case 'GEO_CIRCLE':
      if (!params.geoCircles) params.geoCircles = {};
      params.geoCircles[paramDef.name] = shape as GeoCircle;
      break;
    case 'GEO_RECTANGLE':
      if (!params.geoRectangles) params.geoRectangles = {};
      params.geoRectangles[paramDef.name] = shape as GeoRectangle;
      break;
    case 'GEO_POLYGON':
      if (!params.geoPolygons) params.geoPolygons = {};
      params.geoPolygons[paramDef.name] = shape as GeoPolygon;
      break;
  }
  scheduleLivePreview();
  scheduleTrackIdCandidateLoad();
}

/** Returns all currently configured geo shapes for rendering on the map. */
function getGeoShapes(): GeoShapes {
  const params = selectedFilter.value?.filterParams;
  const labels: Record<string, string> = {};
  for (const pd of selectedFilter.value?.filterInfo?.paramDefinitions ?? []) {
    if (pd.name && pd.label) labels[pd.name] = pd.label;
  }
  return {
    circles: params?.geoCircles ?? {},
    rectangles: params?.geoRectangles ?? {},
    polygons: params?.geoPolygons ?? {},
    labels,
  };
}

defineExpose({
  onGeoDrawingComplete,
  getGeoShapes,
});
</script>

<style scoped>
/* ════════════════════════════════════════════════════════════════
   CustomFilter — flat, borderless layout.
   Structure-level chrome (cards, panels) is removed. Visual grouping
   comes from whitespace + small section headings with a thin rule.
   ════════════════════════════════════════════════════════════════ */

:global(.sheet.sheet--filter-workbench) {
  --sheet-desktop-max-width: 86rem;
  --sheet-desktop-wide-max-width: 86rem;
  --sheet-desktop-wide-width: 92vw;
}

.filter-root {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  width: 100%;
  color: var(--text-secondary);
  container-type: inline-size;
}

.filter-scroll {
  display: flex;
  flex-direction: column;
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
  transition:
    background 0.15s,
    color 0.15s;
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

.cf-tab-body--workbench {
  flex: 1 1 auto;
  gap: 0.85rem;
  min-height: 0;
  overflow: hidden;
}

.cf-tab-body--colors {
  gap: 0;
}

.filter-mobile-switch {
  display: none;
}

.filter-workbench {
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: minmax(18rem, 0.42fr) minmax(0, 1fr);
  gap: 1.35rem;
  align-items: stretch;
  height: auto;
  min-height: 0;
}

.filter-workbench__catalog,
.filter-workbench__detail-pane {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.filter-workbench__detail-pane {
  padding-left: 1.35rem;
  padding-right: 0.25rem;
  border-left: 1px solid var(--border-subtle, var(--border-default));
  overflow-y: auto;
  overscroll-behavior-y: contain;
}

.cf-colors-grid {
  display: grid;
  gap: 1.5rem;
  align-items: start;
}

.cf-colors-controls {
  display: flex;
  flex-direction: column;
  gap: 1.6rem;
  min-width: 0;
}

/* ── Field: label + input + optional hint (no border) ── */
.cf-field {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
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
.cf-preview-mini__text strong {
  color: var(--text-primary);
  font-weight: 600;
}
.cf-preview-mini__text--err {
  color: var(--error);
}
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
.cf-link-btn:hover {
  background: var(--accent-subtle);
}

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
.cf-primary-btn:hover {
  filter: brightness(1.06);
}

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
  background: var(--border-medium);
  transition: background 0.2s;
  flex-shrink: 0;
}
.toggle-switch__track--on {
  background: var(--accent);
}
.toggle-switch__thumb {
  position: absolute;
  top: 0.175rem;
  left: 0.175rem;
  width: 1.15rem;
  height: 1.15rem;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.25);
  transition: transform 0.2s;
}
.toggle-switch__track--on .toggle-switch__thumb {
  transform: translateX(1.25rem);
}
.toggle-switch__text {
  font-size: var(--text-sm-size);
  font-weight: 500;
  color: var(--text-secondary);
  min-width: 1.5rem;
}
.toggle-switch__text--on {
  color: var(--accent-text);
}

.cf-header-toggle {
  padding-right: 0.35rem;
  margin-right: 0.15rem;
}

/* ── Full-width inputs & dropdowns ── */
.filter-full-width {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

.filter-category-heading {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.45rem 0.85rem 0.2rem;
  border: 0;
  background: transparent;
  color: var(--text-muted);
}

.filter-category-heading__label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  font-weight: 500;
  letter-spacing: 0;
  text-transform: none;
}

.filter-option-item {
  display: flex;
  flex-direction: column;
  gap: 0.16rem;
  padding-left: 0.85rem;
  max-width: 28rem;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.filter-selected-value,
.filter-selected-placeholder,
.filter-option-item__label {
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  font-weight: 400;
  color: var(--text-primary);
}

.filter-selected-placeholder {
  color: var(--text-muted);
}

.filter-option-item__description {
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  font-weight: 400;
  color: var(--text-secondary);
  white-space: normal;
}

.legend-sort-option {
  display: flex;
  flex-direction: column;
  gap: 0.16rem;
  padding: 0.1rem 0;
  max-width: 24rem;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.legend-sort-option__label {
  font-size: inherit;
  line-height: inherit;
  font-weight: 400;
  color: var(--text-primary);
}

.legend-sort-option__description {
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-secondary);
  white-space: normal;
}

/* ── Palette preview (unchanged semantics, cleaner placement) ── */
.palette-preview {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.55rem;
  margin-top: 0.55rem;
}
.palette-preview--expanded {
  align-items: flex-start;
}
.palette-preview__swatch,
.palette-preview__more {
  width: 1.25rem;
  height: 1.25rem;
  border-radius: 9999px;
  border: 1px solid var(--border-default);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.24),
    0 1px 4px rgba(15, 23, 42, 0.16);
}
.palette-preview__more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: auto;
  min-width: 4.4rem;
  padding: 0 0.6rem;
  background: var(--accent-bg);
  color: var(--accent-text);
  font-size: var(--text-2xs-size);
  font-weight: 700;
  font-family: inherit;
  line-height: 1;
  white-space: nowrap;
  appearance: none;
  cursor: pointer;
  border-color: color-mix(in srgb, var(--accent) 48%, var(--border-default));
  transition:
    background 0.15s,
    border-color 0.15s,
    color 0.15s;
}
.palette-preview__more:hover,
.palette-preview__more:focus-visible {
  border-color: var(--accent);
  color: var(--accent-text);
  background: var(--accent-subtle);
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
  background: linear-gradient(
    90deg,
    var(--surface-glass-subtle) 25%,
    var(--surface-glass-light) 50%,
    var(--surface-glass-subtle) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
}
.skeleton-line--wide {
  width: 70%;
}
.skeleton-line--medium {
  width: 50%;
}
.skeleton-line--narrow {
  width: 35%;
}
@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* ── Legend (kept, subtle borders) ── */
.legend-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0;
  border: 1px solid var(--border-default);
  border-radius: 0.55rem;
  overflow: hidden;
  background: var(--surface-glass-heavy);
  box-shadow: 0 1px 8px rgba(15, 23, 42, 0.05);
}
.legend-row {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  min-height: 3.8rem;
  padding: 0.65rem 0.9rem;
  border-radius: 0;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s;
}
.legend-row:last-child {
  border-bottom: 0;
}
.legend-row:hover {
  background: var(--surface-hover);
}
.legend-swatch {
  width: 1.15rem;
  height: 1.15rem;
  border-radius: 0.25rem;
  flex-shrink: 0;
  box-shadow:
    inset 0 0 0 1px rgba(0, 0, 0, 0.18),
    0 1px 4px rgba(15, 23, 42, 0.12);
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
  padding: 0.08rem 0.6rem;
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
.my-datatable {
  font-size: var(--text-sm-size);
}
.color-indicator {
  display: inline-block;
  width: 16px;
  height: 16px;
  margin-left: 8px;
  border-radius: 50%;
}

@container (min-width: 60rem) {
  .cf-colors-grid {
    grid-template-columns: minmax(22rem, 0.95fr) minmax(26rem, 1.05fr);
    gap: 3rem;
  }

  .cf-section--breakdown {
    min-width: 0;
  }
}

/* ── Responsive ── */
@media screen and (max-width: 768px) {
  .filter-scroll {
    padding: 0.4rem 0.75rem 1rem;
  }
  .filter-scroll--workbench {
    overflow: hidden;
  }
  :global(.sheet.sheet--filter-workbench .sheet-fullscreen-btn) {
    display: none;
  }
  .cf-sheet-icon {
    display: none;
  }
  .cf-header-nav {
    gap: 0;
  }
  .cf-header-tabs {
    gap: 0.05rem;
  }
  .cf-tab {
    padding: 0.3rem 0.5rem;
    font-size: var(--text-xs-size);
  }
  .cf-tab--compact {
    padding-inline: 0.45rem;
  }
  .cf-tab__badge {
    min-width: 1rem;
    height: 1rem;
    padding: 0 0.22rem;
  }
  .cf-header-toggle {
    padding-right: 0;
    margin-right: 0;
  }
  .filter-mobile-switch {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0.25rem;
    padding: 0.2rem;
    border: 1px solid var(--border-default);
    border-radius: 0.65rem;
    background: var(--surface-glass-subtle);
  }
  .filter-mobile-switch__button {
    min-height: 2rem;
    border: 0;
    border-radius: 0.45rem;
    background: transparent;
    color: var(--text-secondary);
    font: inherit;
    font-size: var(--text-sm-size);
    font-weight: 700;
    line-height: var(--text-sm-lh);
    cursor: pointer;
  }
  .filter-mobile-switch__button--active {
    background: var(--accent-subtle);
    color: var(--accent-text);
  }
  .filter-workbench {
    display: flex;
    flex: 1 1 auto;
    flex-direction: column;
    height: 100%;
    min-height: 0;
  }
  .filter-workbench__catalog,
  .filter-workbench__detail-pane {
    flex: 1 1 auto;
  }
  .filter-workbench__detail-pane {
    padding-left: 0;
    padding-right: 0;
    padding-bottom: 1rem;
    border-left: 0;
  }
  .filter-workbench--catalog .filter-workbench__detail-pane,
  .filter-workbench--detail .filter-workbench__catalog {
    display: none;
  }
  .filter-workbench__catalog {
    height: auto;
  }
  .palette-preview__swatch,
  .palette-preview__more {
    width: 1.15rem;
    height: 1.15rem;
  }
  .palette-preview__more {
    min-width: 4.2rem;
  }
  .legend-row {
    min-height: 3.35rem;
    padding: 0.55rem 0.7rem;
  }
  .my-datatable :deep(th),
  .my-datatable :deep(td) {
    padding: 0.5rem 0.25rem;
    white-space: nowrap;
  }
}

@media screen and (max-width: 480px) {
  .cf-header-toggle {
    gap: 0;
  }

  .cf-header-toggle .toggle-switch__text {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
    white-space: nowrap;
  }
}

@media screen and (min-width: 900px) {
  .cf-params-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
