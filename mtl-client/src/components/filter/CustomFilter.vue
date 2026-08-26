<template>
  <BottomSheet
    :model-value="show"
    :detents="FILTER_STANDARD_DETENTS"
    :no-backdrop="false"
    sheet-class="sheet--solid-over-map sheet--filter-overview"
    @update:model-value="emit('update:show', $event)"
    @closed="onSheetClosed"
  >
    <template #title>
      <div class="filter-header">
        <button
          v-if="showScopeHelp"
          type="button"
          class="filter-header__back"
          aria-label="Back to Filter"
          @pointerdown.stop
          @click.stop="closeScopeHelp"
        >
          <i class="bi bi-arrow-left" aria-hidden="true"></i>
        </button>
        <i v-else class="bi bi-funnel filter-header__icon" aria-hidden="true"></i>
        <h2 class="filter-header__panel-title panel-title">{{ showScopeHelp ? 'How filters work' : 'Filter' }}</h2>
      </div>
    </template>

    <template v-if="sqlTabEnabled && !showScopeHelp" #header-actions>
      <div ref="filterOverflowEl" class="filter-overflow" @pointerdown.stop @click.stop>
        <button
          type="button"
          class="filter-header-action filter-action-text"
          aria-label="Filter actions"
          :aria-expanded="showOverflowMenu"
          @click="showOverflowMenu = !showOverflowMenu"
        >
          <i class="bi bi-three-dots"></i>
        </button>
        <div v-if="showOverflowMenu" class="filter-overflow__menu">
          <button type="button" class="filter-action-text" @click="openSqlView">
            <i class="bi bi-code-slash"></i>
            View SQL
          </button>
        </div>
      </div>
    </template>

    <div class="filter-root" :inert="innerScreenOpen ? true : undefined">
      <FilterScopeHelp v-if="showScopeHelp" :first-visit="scopeHelpFirstVisit" @done="closeScopeHelp" />

      <template v-else>
        <div v-if="definitionsError" class="filter-state filter-state--error" role="alert">
          <i class="bi bi-exclamation-triangle"></i>
          <div>
            <strong>Couldn’t load filter views.</strong>
            <span>{{ definitionsError }}</span>
          </div>
          <button type="button" class="filter-action-text" @click="loadFilterDefinitions">Retry</button>
        </div>

        <div v-else-if="!filtersLoaded" class="filter-state" aria-live="polite">
          <i class="pi pi-spin pi-spinner"></i>
          <span>Loading filter…</span>
        </div>

        <FilterOverview
          v-else
          :enabled="filterEnabled"
          :loading="isPreviewLoading"
          :error="Boolean(previewError)"
          :result-text="resultStatusText"
          :result-detail="resultStatusDetail"
          :result-action-label="overviewResultActionLabel"
          :result-action-icon="overviewResultActionIcon"
          :show-secondary-result-action="Boolean(previewError && lastSuccessfulConfig)"
          secondary-result-action-label="Revert"
          :show-review-action="reviewAvailable"
          :reset-undo-available="resetUndoAvailable"
          :active-identity="activeFilterIdentity"
          :view-summary="currentViewName"
          :criteria-summary="criteriaSummary"
          :categories-summary="categoriesSummary"
          :categories-available="categoriesAvailable"
          :colors-summary="mapColorsStatusText"
          :colors-available="mapColorsAvailable"
          :palette-colors="palettePreviewColors"
          @update:enabled="setFilterEnabled"
          @result-action="onOverviewResultAction"
          @secondary-result-action="revertDraft"
          @open-view="openViewPicker"
          @open-criteria="showCriteria = true"
          @open-categories="showCategoryManager = true"
          @open-colors="showColoring = true"
          @open-scope-help="openScopeHelp"
          @review="openTrackReview"
          @reset="resetFilter"
          @undo-reset="undoReset"
        />
      </template>
    </div>

    <FilterViewSheet
      v-model="showViewPicker"
      :groups="filterOptionGroups"
      :selected-filter-info="selectedFilter?.filterInfo ?? null"
      @apply="selectFilterInfo"
    />

    <FilterCriteriaSheet
      v-model="showCriteria"
      :filter-info="selectedFilter?.filterInfo"
      :filter-params="selectedFilter?.filterParams"
      :track-id-candidate-tracks="trackIdCandidateTracks"
      :track-id-candidates-loading="isTrackIdCandidatesLoading"
      @change="updateCriteria"
      @apply-and-draw="updateCriteriaAndDraw"
    />

    <FilterCategoriesSheet
      v-model="showCategoryManager"
      :available-groups="previewResult?.availableGroups ?? []"
      :selection="selectedFilter?.filterParams?.resultGroupSelection"
      :filter-info="selectedFilter?.filterInfo"
      :palette="selectedFilter?.palette"
      :effective-count="previewResult?.resultEntries?.length ?? 0"
      :pre-selection-count="previewResult?.preGroupSelectionCount ?? previewResult?.resultEntries?.length ?? 0"
      :loading="isPreviewLoading"
      @apply="setResultGroupSelection"
    />

    <FilterColoringSheet
      v-model="showColoring"
      :palettes="colorPaletteList"
      :palette="selectedFilter?.palette"
      :default-palette="defaultColoringPalette"
      :legend-sort-strategy="effectiveLegendSortStrategy"
      :order-options="legendSortStrategyOptions"
      @apply="applyColoring"
    />

    <BottomSheet
      v-model="showSql"
      title="View SQL"
      icon="bi bi-code-slash"
      :detents="[{ height: '75vh' }, { height: '95vh' }]"
      :no-backdrop="true"
      :z-index="5100"
      sheet-class="sheet--filter-detail"
    >
      <div class="filter-inner-screen">
        <FilterSqlPreview
          :filter-info="selectedFilter?.filterInfo"
          :view-mode="sqlViewMode"
          @update:view-mode="sqlViewMode = $event"
        />
      </div>
    </BottomSheet>

    <FilterTrackReviewSheet
      v-model="showDrillDown"
      :title="drillDownTitle"
      :loading="isDrillDownLoading"
      :error="drillDownError"
      :entries="drillDownEntries"
      @retry="refreshOpenDrillDown"
      @select-track="emit('select-track', $event)"
      @open-details="emit('open-details', $event)"
    />
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, markRaw, onBeforeUnmount, onMounted, ref, shallowRef, watch, type Ref } from 'vue';
import { ClientFilterConfig, type FilterParamsRequest, FilterService } from '@/components/filter/FilterService';
import { useFilterStore } from '@/stores/filterStore';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { ParamDefinition } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';
import type { QueryResult } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/QueryResult';
import type { QueryResultEntry } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/QueryResultEntry';
import type { FilterResultGroupSelection } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupSelection';
import type {
  GeoCircle,
  GeoPolygon,
  GeoRectangle,
  GpsTrack,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import type { FilterMapInteractionEvents } from '@/components/filter/filterEvents';
import { FilterConfigEntityLegendSortStrategyEnum } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterConfigEntity';
import { fetchFilters, fetchResolveFilter, type ResolveFilterResult } from '@/utils/ServiceHelper';
import { loadCachedTracks } from '@/utils/tracks/trackCollectionLoader';
import { format } from 'date-fns';
import { ColorPalette } from '@/components/filter/ColorPalette';
import {
  buildFilterOptionGroups,
  colorForFilterGroup,
  findPreferredPalette,
  effectiveParamMetadataEntries,
  formatFilterGroupLabel,
  isGradientPalette,
  isSequentialGradientFilter,
  isSameFilterInfo,
  legendSortStrategyDescription,
  legendSortStrategyLabel,
  normalizeLegendSortStrategy,
  parseFilterRef,
  sortFilterInfos,
  type FilterOptionGroup,
  type FilterParamMetadata,
  type LegendSortStrategy,
} from '@/utils/filterMetadata';
import {
  ensureFilterParamMaps,
  hasCompleteStringParamsForDefinitions,
  pruneFilterParamsForDefinitions,
} from '@/utils/filterParams';

import BottomSheet from '@/components/ui/BottomSheet.vue';
import FilterOverview from '@/components/filter/FilterOverview.vue';
import FilterScopeHelp from '@/components/filter/FilterScopeHelp.vue';
import FilterViewSheet from '@/components/filter/FilterViewSheet.vue';
import FilterCriteriaSheet from '@/components/filter/FilterCriteriaSheet.vue';
import FilterCategoriesSheet from '@/components/filter/FilterCategoriesSheet.vue';
import FilterColoringSheet from '@/components/filter/FilterColoringSheet.vue';
import FilterTrackReviewSheet from '@/components/filter/FilterTrackReviewSheet.vue';
import FilterSqlPreview from '@/components/filter/FilterSqlPreview.vue';
import { FILTER_STANDARD_DETENTS } from '@/components/filter/filterSheetLayout';
import { useAsyncState } from '@/composables/useAsyncState';
import { isAbortLikeError } from '@/utils/errors';
import {
  readJsonStorage,
  readStorage,
  removeStorage,
  STORAGE_KEYS,
  writeJsonStorage,
  writeStorage,
} from '@/utils/appStorage';
import { isResultGroupSelected } from '@/utils/resultGroupSelection';
import { formatActiveFilterIdentity } from '@/utils/activeFilterIdentity';

const EVENTS = {
  filterChangedEvent: 'filterChangedEvent',
  filterStyleChanged: 'filter-style-changed',
  startGeoDrawing: 'start-geo-drawing',
  clearGeoShape: 'clear-geo-shape',
} as const;

const PALETTE_PREVIEW_SWATCH_LIMIT = 10;
const RESET_UNDO_DURATION_MS = 8_000;
const SELECTABLE_LEGEND_SORT_STRATEGIES: LegendSortStrategy[] = [
  FilterConfigEntityLegendSortStrategyEnum.LabelAsc,
  FilterConfigEntityLegendSortStrategyEnum.NumericAsc,
  FilterConfigEntityLegendSortStrategyEnum.CountDesc,
];
const TRACK_PICKER_WIDGET = 'trackPicker';
const OPTIONS_SOURCE_ORIGIN_FILTER_RESULT = 'originFilterResult';

type SqlViewMode = 'template' | 'resolved';
type FilterNavigationState = {
  screen: 'overview' | 'review';
  reviewGroup: string | null;
};
type ResetSnapshot = {
  config: ClientFilterConfig;
  paused: boolean;
  previewResult: QueryResult | null;
  previewFilterResult: ResolveFilterResult | null;
};
type CriteriaUpdate = {
  filterParams: FilterParamsRequest;
  clearedGeoParams: ParamDefinition[];
};
type GeoShapes = {
  circles: Record<string, GeoCircle>;
  rectangles: Record<string, GeoRectangle>;
  polygons: Record<string, GeoPolygon>;
  labels: Record<string, string>;
};
type Emits = FilterMapInteractionEvents & {
  'update:show': [value: boolean];
  closed: [];
  filterChangedEvent: [];
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
const filterStore = useFilterStore();

const filters = shallowRef<FilterInfo[]>([]);
const selectedFilter: Ref<ClientFilterConfig> = ref(new ClientFilterConfig()) as Ref<ClientFilterConfig>;
const colorPaletteList = shallowRef<ColorPalette[]>([]);
const filterEnabled = ref(true);
const previewResult = ref<QueryResult | null>(null);
const previewFilterResult = ref<ResolveFilterResult | null>(null);
const { loading: isPreviewLoading, error: previewError } = useAsyncState<string | null>(null);
const sqlViewMode = ref<SqlViewMode>('template');
const showDrillDown = ref(false);
const drillDownGroup = ref<string | null>(null);
const drillDownFullResult = ref<QueryResult | null>(null);
const isDrillDownLoading = ref(false);
const drillDownError = ref('');
const trackIdCandidateTracks = shallowRef<GpsTrack[]>([]);
const isTrackIdCandidatesLoading = ref(false);
const showViewPicker = ref(false);
const showCriteria = ref(false);
const showCategoryManager = ref(false);
const showColoring = ref(false);
const showSql = ref(false);
const showScopeHelp = ref(false);
const scopeHelpFirstVisit = ref(false);
const scopeHelpSeen = ref(readStorage(STORAGE_KEYS.filterScopeHelpSeen) === 'true');
const showOverflowMenu = ref(false);
const definitionsError = ref<string | null>(null);
const lastSuccessfulConfig = shallowRef<ClientFilterConfig | null>(null);
const resetSnapshot = shallowRef<ResetSnapshot | null>(null);
const resetUndoAvailable = ref(false);
const filterOverflowEl = ref<HTMLElement | null>(null);

let previewDebounceTimer: ReturnType<typeof setTimeout> | null = null;
let previewRequestSeq = 0;
let trackIdCandidateDebounceTimer: ReturnType<typeof setTimeout> | null = null;
let trackIdCandidateLoadSeq = 0;
let lastTrackIdCandidateLoadKey = '';
let resetUndoTimer: ReturnType<typeof setTimeout> | null = null;
let drillDownRequestSeq = 0;

function normalizeSelectableLegendSortStrategy(value: unknown): LegendSortStrategy | null {
  const strategy = normalizeLegendSortStrategy(value);
  return strategy && SELECTABLE_LEGEND_SORT_STRATEGIES.includes(strategy) ? strategy : null;
}

const filterOptionGroups = computed((): FilterOptionGroup[] => buildFilterOptionGroups(filters.value));
const filtersLoaded = computed((): boolean => filters.value.length > 0 && Boolean(selectedFilter.value?.filterInfo));
const activeFilterIdentity = computed((): string =>
  filterEnabled.value
    ? formatActiveFilterIdentity(selectedFilter.value?.filterInfo, selectedFilter.value?.filterParams)
    : ''
);
const currentViewName = computed(
  (): string => selectedFilter.value?.filterInfo?.filterConfig?.displayName?.trim() || 'Filter'
);
const innerScreenOpen = computed(
  (): boolean =>
    showViewPicker.value ||
    showCriteria.value ||
    showCategoryManager.value ||
    showColoring.value ||
    showSql.value ||
    showDrillDown.value
);
const selectedOrderLabel = computed((): string =>
  effectiveLegendSortStrategy.value ? legendSortStrategyLabel(effectiveLegendSortStrategy.value) : 'Default order'
);
const hasNoMatches = computed(
  (): boolean => previewResult.value != null && (previewResult.value.resultEntries?.length ?? 0) === 0
);
const availableCategoryCount = computed((): number => previewResult.value?.availableGroups?.length ?? 0);
const selectedCategoryCount = computed(
  (): number => selectedFilter.value?.filterParams?.resultGroupSelection?.includedGroups?.length ?? 0
);
const categoriesAvailable = computed(
  (): boolean =>
    previewResult.value?.groupingAvailable === true &&
    (availableCategoryCount.value > 0 || selectedCategoryCount.value > 0)
);
const categoryStatusText = computed((): string => {
  if (categoriesAvailable.value) return '';
  if (isPreviewLoading.value && !previewResult.value) return 'Finding categories from the current results…';
  if (previewError.value && !previewResult.value) return 'Included categories are unavailable until results load.';
  if (!previewResult.value) return 'Included categories become available when results load.';
  if (!previewResult.value.groupingAvailable) return 'This view does not create categories.';
  return 'No categories are available for these results.';
});
const categoriesSummary = computed((): string => {
  if (!categoriesAvailable.value) return categoryStatusText.value;
  const availableGroups = previewResult.value?.availableGroups ?? [];
  const selection = selectedFilter.value?.filterParams?.resultGroupSelection;
  if (!selection) return `All ${availableGroups.length} categories`;
  const selectedCount = availableGroups.filter(
    (summary) => summary.key && isResultGroupSelected(summary.key, selection)
  ).length;
  const unavailableCount = Math.max(0, selectedCategoryCount.value - selectedCount);
  const currentSummary = `${selectedCount} of ${availableGroups.length} categories`;
  return unavailableCount > 0 ? `${currentSummary} · ${unavailableCount} unavailable` : currentSummary;
});
const mapColorsAvailable = computed((): boolean => categoriesAvailable.value);
const activeCriteriaCount = computed((): number => {
  const params = selectedFilter.value?.filterParams;
  return (
    Object.values(params?.stringParams ?? {}).filter((value) => String(value ?? '').trim()).length +
    Object.values(params?.dateTimeParams ?? {}).filter(Boolean).length +
    Object.values(params?.geoCircles ?? {}).filter(Boolean).length +
    Object.values(params?.geoRectangles ?? {}).filter(Boolean).length +
    Object.values(params?.geoPolygons ?? {}).filter(Boolean).length
  );
});
const criteriaSummary = computed((): string => {
  if (activeCriteriaCount.value === 0) return 'No criteria';
  return `${activeCriteriaCount.value} ${activeCriteriaCount.value === 1 ? 'criterion' : 'criteria'} active`;
});
const hasActiveCriteria = computed((): boolean => {
  const params = selectedFilter.value?.filterParams;
  return Boolean(
    Object.keys(params?.stringParams ?? {}).length ||
    Object.keys(params?.dateTimeParams ?? {}).length ||
    Object.keys(params?.geoCircles ?? {}).length ||
    Object.keys(params?.geoRectangles ?? {}).length ||
    Object.keys(params?.geoPolygons ?? {}).length
  );
});
const noMatchesRecovery = computed((): 'categories' | 'criteria' | 'view' => {
  const preSelectionCount = Number(previewResult.value?.preGroupSelectionCount ?? 0);
  const hasCategorySelection = selectedFilter.value?.filterParams?.resultGroupSelection != null;
  if (hasCategorySelection && preSelectionCount > 0) return 'categories';
  if (hasActiveCriteria.value) return 'criteria';
  return 'view';
});
const noMatchesActionLabel = computed((): string => {
  if (noMatchesRecovery.value === 'categories') return 'Show all categories';
  if (noMatchesRecovery.value === 'criteria') return 'Clear criteria';
  return 'Change view';
});
const reviewAvailable = computed(
  (): boolean =>
    filterEnabled.value &&
    !isPreviewLoading.value &&
    !previewError.value &&
    !hasNoMatches.value &&
    previewResult.value != null
);
const overviewResultActionLabel = computed((): string => {
  if (isPreviewLoading.value) return '';
  if (!filterEnabled.value) return '';
  if (previewError.value) return 'Retry';
  if (hasNoMatches.value) return noMatchesActionLabel.value;
  return '';
});
const overviewResultActionIcon = computed((): string => {
  if (previewError.value) return 'bi bi-arrow-repeat';
  if (hasNoMatches.value) return 'bi bi-arrow-counterclockwise';
  return 'bi bi-arrow-repeat';
});
const resultStatusText = computed((): string => {
  if (definitionsError.value) return 'Filter views unavailable';
  if (!filtersLoaded.value) return 'Loading filter…';
  if (!filterEnabled.value) return 'Filter paused';
  if (isPreviewLoading.value) {
    const priorCount = previewResult.value?.resultEntries?.length;
    return priorCount == null ? 'Updating…' : `Updating ${priorCount} tracks…`;
  }
  if (previewError.value) {
    return previewResult.value ? 'Update failed' : 'Results unavailable';
  }
  if (hasNoMatches.value) return 'No tracks match';
  if (previewResult.value) {
    const count = previewResult.value.resultEntries?.length ?? 0;
    return `${count} ${count === 1 ? 'matching track' : 'matching tracks'}`;
  }
  return 'Ready';
});
const resultStatusDetail = computed((): string => {
  if (definitionsError.value || !filtersLoaded.value) return '';
  if (!filterEnabled.value) return 'All tracks are shown. Your filter setup is kept.';
  if (isPreviewLoading.value) return 'Checking the current criteria and categories.';
  if (previewError.value) {
    return previewResult.value
      ? 'The previous result remains active on the map.'
      : 'Try again to load matching tracks.';
  }
  if (previewResult.value) return 'Based on the current criteria and categories.';
  return '';
});
const legendSortStrategyOptions = computed(
  (): Array<{ label: string; value: LegendSortStrategy | null; description: string }> => [
    {
      label: 'Default order',
      value: null,
      description: 'Uses the view’s default group order.',
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
const defaultColoringPalette = computed((): ColorPalette | undefined => {
  const preferred = findPreferredPalette(colorPaletteList.value, selectedFilter.value?.filterInfo?.filterConfig);
  return preferred ?? colorPaletteList.value.find((palette) => palette.isEmptyColorPalette());
});
const paletteLabel = computed((): string => selectedFilter.value?.palette?.pLabel || 'No coloring');
const mapColorsStatusText = computed((): string => {
  if (mapColorsAvailable.value) return `${paletteLabel.value} · ${selectedOrderLabel.value}`;
  if (isPreviewLoading.value && !previewResult.value) return 'Available after results are loaded.';
  if (previewError.value && !previewResult.value) return 'Unavailable until results can be loaded.';
  if (!previewResult.value) return 'Available after results are loaded.';
  if (!previewResult.value.groupingAvailable) return 'This view does not provide category colors.';
  return 'No category colors are available for these results.';
});
const allPaletteColors = computed((): string[] => selectedFilter.value?.palette?.pColors || []);
const palettePreviewColors = computed((): string[] => allPaletteColors.value.slice(0, PALETTE_PREVIEW_SWATCH_LIMIT));
const sqlTabEnabled = computed((): boolean => !!rawSQL.value);
const drillDownEntries = computed((): QueryResultEntry[] => {
  if (!drillDownFullResult.value?.resultEntries) return [];
  if (!drillDownGroup.value) return drillDownFullResult.value.resultEntries;
  return drillDownFullResult.value.resultEntries.filter((e: QueryResultEntry) => e.group === drillDownGroup.value);
});
const drillDownTitle = computed((): string => {
  if (!drillDownGroup.value) return 'Matching tracks';
  const count = drillDownEntries.value.length;
  const groupLabel = formatFilterGroupLabel(drillDownGroup.value, selectedFilter.value?.filterInfo);
  return `${groupLabel} — ${count} tracks`;
});
const rawSQL = computed((): string => selectedFilter.value?.filterInfo?.filterConfig?.expression ?? '');

onMounted(() => {
  void loadFilterDefinitions();
});

watch(
  () => props.show,
  (open) => {
    if (open === true) {
      if (!scopeHelpSeen.value) openScopeHelp(true);
      return;
    }
    if (open !== false) return;
    showViewPicker.value = false;
    showCriteria.value = false;
    showCategoryManager.value = false;
    showColoring.value = false;
    showSql.value = false;
    showScopeHelp.value = false;
    scopeHelpFirstVisit.value = false;
    showDrillDown.value = false;
  },
  { immediate: true }
);

watch(
  () => filterStore.activeResult,
  (result) => {
    if (!filterEnabled.value) return;
    if (!result || !('queryResult' in result)) {
      invalidateDrillDownResult();
      previewFilterResult.value = null;
      previewResult.value = null;
      return;
    }
    previewFilterResult.value = result;
    previewResult.value = result.queryResult;
    previewError.value = null;
    rebuildPreviewPalette();
    if (showDrillDown.value) {
      void loadDrillDownFullResult({ preserveCurrent: true });
    } else {
      invalidateDrillDownResult();
    }
  }
);

watch(
  () => filterStore.dataFreshnessRevision,
  () => {
    refreshTrackIdCandidates();
    if (!filterEnabled.value) {
      invalidateDrillDownResult();
      void loadPausedPreview();
    }
  }
);

function dismissResetUndo(): void {
  resetUndoAvailable.value = false;
  resetSnapshot.value = null;
  if (resetUndoTimer) {
    clearTimeout(resetUndoTimer);
    resetUndoTimer = null;
  }
}

function onSheetClosed(): void {
  showOverflowMenu.value = false;
  showScopeHelp.value = false;
  scopeHelpFirstVisit.value = false;
  dismissResetUndo();
  emit('closed');
}

function onDocumentPointerDown(event: PointerEvent): void {
  const target = event.target;
  if (!(target instanceof Node)) return;
  if (showOverflowMenu.value && !filterOverflowEl.value?.contains(target)) {
    showOverflowMenu.value = false;
  }
}

function onDocumentKeyDown(event: KeyboardEvent): void {
  if (event.key !== 'Escape') return;
  if (showOverflowMenu.value) {
    event.preventDefault();
    event.stopPropagation();
    showOverflowMenu.value = false;
    return;
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown, true);
  document.addEventListener('keydown', onDocumentKeyDown, true);
});

async function loadFilterDefinitions(): Promise<void> {
  definitionsError.value = null;
  try {
    if (colorPaletteList.value.length === 0) {
      const palettes = await ColorPalette.fetch();
      const emptyPalette = markRaw(new ColorPalette());
      emptyPalette.pLabel = 'No coloring';
      emptyPalette.pDescription = 'No coloring at all';
      colorPaletteList.value = [emptyPalette, ...palettes.map((palette) => markRaw(palette))];
    }

    const fetchedFilters = await fetchFilters();
    filters.value = sortFilterInfos(fetchedFilters);

    const persistedPaused = readJsonStorage(STORAGE_KEYS.filterPaused, false, (value) => value === true);
    const pausedDraft = persistedPaused
      ? readJsonStorage<ClientFilterConfig | null>(STORAGE_KEYS.filterPausedDraft, null)
      : null;
    const clientFilterConfig = normalizeClientFilterConfig(pausedDraft ?? (await filterStore.ensureLoaded()));
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

    restoreDateParams(clientFilterConfig);
    selectedFilter.value = normalizeClientFilterConfig(clientFilterConfig);
    applyPreferredPaletteForSelectedFilter(false);
    filterEnabled.value = !persistedPaused;
    lastSuccessfulConfig.value = filterEnabled.value ? cloneClientFilterConfig(selectedFilter.value) : null;
    if (filterEnabled.value) {
      scheduleLivePreview();
    } else {
      void loadPausedPreview();
    }
    scheduleTrackIdCandidateLoad();
  } catch (error) {
    definitionsError.value = error instanceof Error ? error.message : String(error);
    console.error('Error in mounted lifecycle hook:', error);
  }
}

onBeforeUnmount(() => {
  if (previewDebounceTimer) clearTimeout(previewDebounceTimer);
  if (trackIdCandidateDebounceTimer) clearTimeout(trackIdCandidateDebounceTimer);
  if (resetUndoTimer) clearTimeout(resetUndoTimer);
  drillDownRequestSeq += 1;
  document.removeEventListener('pointerdown', onDocumentPointerDown, true);
  document.removeEventListener('keydown', onDocumentKeyDown, true);
});

function normalizeClientFilterConfig(config: ClientFilterConfig | null | undefined): ClientFilterConfig {
  const normalized = new ClientFilterConfig();
  normalized.filterInfo = config?.filterInfo ?? ({} as FilterInfo);
  const fp = ensureFilterParamMaps(config?.filterParams || {});
  normalized.filterParams = {
    ...fp,
    stringParams: { ...(fp.stringParams || {}) },
    dateTimeParams: { ...(fp.dateTimeParams || {}) },
    geoCircles: { ...(fp.geoCircles || {}) },
    geoRectangles: { ...(fp.geoRectangles || {}) },
    geoPolygons: { ...(fp.geoPolygons || {}) },
    resultGroupSelection: fp.resultGroupSelection
      ? {
          includedGroups: (fp.resultGroupSelection.includedGroups ?? []).map((key) => ({
            value: key.value,
          })),
        }
      : undefined,
  };
  normalized.palette = markRaw(ColorPalette.of(config?.palette));
  normalized.legendSortStrategy = normalizeSelectableLegendSortStrategy(config?.legendSortStrategy);
  return normalized;
}

function cloneClientFilterConfig(config: ClientFilterConfig): ClientFilterConfig {
  return normalizeClientFilterConfig(config);
}

function restoreDateParams(config: ClientFilterConfig): void {
  config.filterParams = ensureFilterParamMaps(config.filterParams);
  for (const key in config.filterParams.dateTimeParams || {}) {
    const value = config.filterParams.dateTimeParams?.[key];
    if (value && typeof value === 'string') {
      (config.filterParams.dateTimeParams as Record<string, unknown>)[key] = new Date(value.replace(' ', 'T'));
    }
  }
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

function selectFilterInfo(filterInfo: FilterInfo) {
  const sameFilter = isSameFilterInfo(selectedFilter.value?.filterInfo, filterInfo);
  selectedFilter.value.filterInfo = filterInfo;
  showViewPicker.value = false;
  if (!sameFilter) {
    selectedFilter.value.filterParams = {
      ...selectedFilter.value.filterParams,
      resultGroupSelection: undefined,
    };
    onFilterInfoChanged();
  }
}

// ── Pause and resume ──
function setFilterEnabled(enabled: boolean): void {
  if (enabled) resumeFilter();
  else pauseFilter();
}

function resumeFilter(): void {
  filterEnabled.value = true;
  writeJsonStorage(STORAGE_KEYS.filterPaused, false);
  removeStorage(STORAGE_KEYS.filterPausedDraft);
  selectedFilter.value = normalizeClientFilterConfig(selectedFilter.value);
  scheduleLivePreview();
  scheduleTrackIdCandidateLoad();
}

function pauseFilter(): void {
  persistPausedDraft();
  filterEnabled.value = false;
  writeJsonStorage(STORAGE_KEYS.filterPaused, true);
  invalidatePendingPreview();
  previewError.value = null;
  applyNormalTrackSet();
}

// ── Filter changes → schedule preview ──
function onFilterInfoChanged() {
  invalidateDrillDownResult();
  selectedFilter.value.legendSortStrategy = null;
  selectedFilter.value.filterParams = pruneFilterParamsForDefinitions(
    selectedFilter.value.filterParams,
    selectedFilter.value?.filterInfo?.paramDefinitions || []
  );
  if (!selectedFilter.value.palette) {
    selectedFilter.value.palette = markRaw(new ColorPalette());
  }
  applyPreferredPaletteForSelectedFilter(true);
  if (filterEnabled.value) scheduleLivePreview();
  else persistPausedDraft();
  scheduleTrackIdCandidateLoad();
}

function onParamChanged(paramName?: string) {
  if (filterEnabled.value) scheduleLivePreview();
  else persistPausedDraft();
  if (paramName !== selectedTrackPickerParamName()) {
    scheduleTrackIdCandidateLoad();
  }
}

function setResultGroupSelection(selection: FilterResultGroupSelection | undefined): void {
  const params = ensureFilterParamMaps(selectedFilter.value.filterParams);
  params.resultGroupSelection = selection;
  selectedFilter.value.filterParams = params;
  if (filterEnabled.value) scheduleLivePreview();
  else persistPausedDraft();
}

function applyColoring(value: {
  palette: ColorPalette | undefined;
  legendSortStrategy: LegendSortStrategy | null;
}): void {
  selectedFilter.value = ClientFilterConfig.of(
    selectedFilter.value.filterInfo,
    selectedFilter.value.filterParams,
    value.palette,
    normalizeSelectableLegendSortStrategy(value.legendSortStrategy)
  );
  if (previewResult.value) {
    rebuildPreviewPalette();
  }
  if (!filterEnabled.value) {
    persistPausedDraft();
    return;
  }
  filterStore.save(
    ClientFilterConfig.of(
      selectedFilter.value.filterInfo,
      getProcessedParams(),
      selectedFilter.value.palette,
      selectedFilter.value.legendSortStrategy
    ),
    { trackSetChanged: false }
  );
  lastSuccessfulConfig.value = cloneClientFilterConfig(selectedFilter.value);
  emit(EVENTS.filterStyleChanged);
}

function persistCurrentFilterDraft(): void {
  if (!filterEnabled.value) {
    persistPausedDraft();
    return;
  }
  filterStore.save(
    ClientFilterConfig.of(
      selectedFilter.value.filterInfo,
      getProcessedParams(),
      selectedFilter.value.palette,
      selectedFilter.value.legendSortStrategy
    ),
    { trackSetChanged: false }
  );
}

function persistPausedDraft(): void {
  writeJsonStorage(
    STORAGE_KEYS.filterPausedDraft,
    ClientFilterConfig.of(
      selectedFilter.value.filterInfo,
      getProcessedParams(),
      selectedFilter.value.palette,
      selectedFilter.value.legendSortStrategy
    )
  );
}

function invalidatePendingPreview(): void {
  previewRequestSeq += 1;
  if (previewDebounceTimer) {
    clearTimeout(previewDebounceTimer);
    previewDebounceTimer = null;
  }
  isPreviewLoading.value = false;
}

// ── Live preview engine ──
function scheduleLivePreview() {
  if (!filterEnabled.value) {
    persistPausedDraft();
    isPreviewLoading.value = false;
    return;
  }
  if (previewDebounceTimer) clearTimeout(previewDebounceTimer);
  const requestSeq = ++previewRequestSeq;
  isPreviewLoading.value = true;
  previewDebounceTimer = setTimeout(() => executeLivePreview(requestSeq), 400);
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
  if (!selectedTrackPickerSourceFilterInfo()) {
    trackIdCandidateTracks.value = [];
    isTrackIdCandidatesLoading.value = false;
    return;
  }
  trackIdCandidateDebounceTimer = setTimeout(() => {
    void loadTrackIdCandidates();
  }, 350);
}

function refreshTrackIdCandidates(): void {
  lastTrackIdCandidateLoadKey = '';
  trackIdCandidateLoadSeq += 1;
  scheduleTrackIdCandidateLoad();
}

function parseCandidateTrack(rawTrack: GpsTrack): GpsTrack {
  return {
    ...rawTrack,
    startDate: rawTrack.startDate ? new Date(rawTrack.startDate) : undefined,
    endDate: rawTrack.endDate ? new Date(rawTrack.endDate) : undefined,
    createDate: rawTrack.createDate ? new Date(rawTrack.createDate) : undefined,
  };
}

function isStandardTrackPickerSource(sourceFilterInfo: FilterInfo): boolean {
  return FilterService.isStandardFilterWithStandardParams(ClientFilterConfig.of(sourceFilterInfo, {}));
}

function expectedTrackPickerCandidateCount(): number | null {
  const count = Number(props.totalTrackCount);
  return Number.isFinite(count) && count > 0 ? count : null;
}

function normalizeCandidateTracks(rawTracks: Iterable<GpsTrack>): GpsTrack[] {
  return [...rawTracks].filter((track): track is GpsTrack => Boolean(track?.id)).map(parseCandidateTrack);
}

async function loadCachedTrackIdCandidates(sourceFilterInfo: FilterInfo): Promise<GpsTrack[] | null> {
  if (!isStandardTrackPickerSource(sourceFilterInfo)) return null;

  const expectedCount = expectedTrackPickerCandidateCount();
  if (expectedCount == null) return null;

  const cachedTracks = await loadCachedTracks();
  if (cachedTracks.size !== expectedCount) return null;

  return normalizeCandidateTracks(cachedTracks.values());
}

async function loadTrackIdCandidates() {
  const sourceFilterInfo = selectedTrackPickerSourceFilterInfo();
  const filterId = sourceFilterInfo?.filterConfig?.id;
  if (!filterId) return;

  const loadKey = JSON.stringify({ filterId, expectedCount: expectedTrackPickerCandidateCount() });
  if (loadKey === lastTrackIdCandidateLoadKey && trackIdCandidateTracks.value.length > 0) return;

  const seq = ++trackIdCandidateLoadSeq;
  isTrackIdCandidatesLoading.value = true;
  try {
    const cachedCandidates = await loadCachedTrackIdCandidates(sourceFilterInfo);
    if (seq !== trackIdCandidateLoadSeq) return;
    if (cachedCandidates) {
      trackIdCandidateTracks.value = cachedCandidates;
      lastTrackIdCandidateLoadKey = loadKey;
      return;
    }

    const result = await fetchResolveFilter(filterId, {}, true);
    if (seq !== trackIdCandidateLoadSeq) return;
    trackIdCandidateTracks.value = normalizeCandidateTracks(
      (result.queryResult.resultEntries ?? []).map((entry) => entry.gpsTrack).filter(Boolean) as GpsTrack[]
    );
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

async function executeLivePreview(requestSeq: number): Promise<ResolveFilterResult | null> {
  const filterId = selectedFilter.value?.filterInfo?.filterConfig?.id;
  if (!filterId) {
    if (requestSeq === previewRequestSeq) isPreviewLoading.value = false;
    return null;
  }

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
      previewError.value = 'Complete the required fields to update this view.';
      return null;
    }

    const result = await fetchResolveFilter(filterId, processedParams, false);
    if (requestSeq !== previewRequestSeq || !filterEnabled.value) return null;
    previewFilterResult.value = result;
    previewResult.value = result.queryResult;
    rebuildPreviewPalette();
    invalidateDrillDownResult();
    applyResolvedPreview(result);
    return result;
  } catch (error: unknown) {
    if (requestSeq !== previewRequestSeq || !filterEnabled.value) return null;
    if (isAbortLikeError(error)) return null;
    previewError.value = 'Preview failed. Check parameter values or switch to a different filter.';
    previewFilterResult.value = null;
    console.error('Live preview error:', error);
    return null;
  } finally {
    if (requestSeq === previewRequestSeq) isPreviewLoading.value = false;
  }
}

async function loadPausedPreview(): Promise<void> {
  const filterId = selectedFilter.value?.filterInfo?.filterConfig?.id;
  const processedParams = getProcessedParams();
  if (
    filterId == null ||
    !hasCompleteStringParamsForDefinitions(
      processedParams,
      selectedFilter.value?.filterInfo?.paramDefinitions || [],
      selectedFilter.value?.filterInfo
    )
  ) {
    return;
  }

  const requestSeq = ++previewRequestSeq;
  try {
    const result = await fetchResolveFilter(filterId, processedParams, false);
    if (requestSeq !== previewRequestSeq || filterEnabled.value) return;
    previewFilterResult.value = result;
    previewResult.value = result.queryResult;
    rebuildPreviewPalette();
  } catch (error) {
    if (requestSeq !== previewRequestSeq || filterEnabled.value) return;
    console.warn('Paused filter preview could not be refreshed:', error);
  }
}

function applyResolvedPreview(result: ResolveFilterResult): void {
  const cfg = ClientFilterConfig.of(
    selectedFilter.value.filterInfo,
    getProcessedParams(),
    selectedFilter.value.palette,
    selectedFilter.value.legendSortStrategy
  );
  filterStore.applyResolvedFilter(cfg, result);
  selectedFilter.value = normalizeClientFilterConfig(cfg);
  lastSuccessfulConfig.value = cloneClientFilterConfig(selectedFilter.value);
  writeJsonStorage(STORAGE_KEYS.filterPaused, false);
  removeStorage(STORAGE_KEYS.filterPausedDraft);
  emit(EVENTS.filterChangedEvent);
}

function rebuildPreviewPalette() {
  const palette = selectedFilter.value?.palette;
  const result = previewResult.value;
  if (palette && !palette.isEmptyColorPalette() && result?.resultEntries) {
    const filterInfo = selectedFilter.value?.filterInfo;
    const availableGroups = (result.availableGroups ?? [])
      .map((summary) => summary.key?.value)
      .filter((group): group is string => Boolean(group))
      .sort((left, right) => left.localeCompare(right, undefined, { numeric: true, sensitivity: 'base' }));
    for (const group of availableGroups) {
      colorForFilterGroup(palette, group, filterInfo);
    }
    for (const entry of result.resultEntries) {
      if (entry.group) colorForFilterGroup(palette, entry.group, filterInfo, true);
    }
  }
}

// ── Drill-down ──
function invalidateDrillDownResult(): void {
  drillDownRequestSeq += 1;
  drillDownFullResult.value = null;
  isDrillDownLoading.value = false;
  drillDownError.value = '';
}

async function loadDrillDownFullResult(options: { preserveCurrent?: boolean } = {}): Promise<void> {
  const filterId = selectedFilter.value?.filterInfo?.filterConfig?.id;
  if (filterId === undefined) return;

  const preserveCurrent = options.preserveCurrent === true;
  const requestSeq = ++drillDownRequestSeq;
  if (!preserveCurrent) drillDownFullResult.value = null;
  drillDownError.value = '';
  isDrillDownLoading.value = true;
  try {
    const drillResult = await fetchResolveFilter(filterId, getProcessedParams(), true);
    if (requestSeq !== drillDownRequestSeq) return;
    drillDownFullResult.value = drillResult.queryResult;
  } catch (error) {
    if (requestSeq !== drillDownRequestSeq) return;
    drillDownError.value = drillDownFullResult.value
      ? 'Tracks could not be refreshed. Showing saved results.'
      : 'Tracks could not be loaded.';
    console.error('Drill-down fetch error:', error);
  } finally {
    if (requestSeq === drillDownRequestSeq) isDrillDownLoading.value = false;
  }
}

function refreshOpenDrillDown(): void {
  if (!showDrillDown.value) return;
  void loadDrillDownFullResult({ preserveCurrent: true });
}

async function openGroupDrillDown(group: string | null) {
  drillDownGroup.value = group;
  showDrillDown.value = true;
  if (!drillDownFullResult.value) await loadDrillDownFullResult();
}

function openTrackReview(): void {
  void openGroupDrillDown(null);
}

function getNavigationState(): FilterNavigationState {
  return {
    screen: showDrillDown.value ? 'review' : 'overview',
    reviewGroup: drillDownGroup.value,
  };
}

function restoreNavigationState(state: unknown): void {
  if (!state || typeof state !== 'object' || !('screen' in state) || state.screen !== 'review') return;
  drillDownGroup.value = 'reviewGroup' in state && typeof state.reviewGroup === 'string' ? state.reviewGroup : null;
  showDrillDown.value = true;
  if (!drillDownFullResult.value) void loadDrillDownFullResult();
}

function defaultFilterConfig(): ClientFilterConfig | null {
  const defaultFilter = filters.value.find((f) =>
    FilterService.isStandardFilterWithStandardParams(ClientFilterConfig.of(f))
  );
  return defaultFilter ? ClientFilterConfig.of(defaultFilter, {}, undefined, null) : null;
}

function applyNormalTrackSet(): void {
  const cfg = defaultFilterConfig();
  if (!cfg) return;
  filterStore.save(cfg);
  emit(EVENTS.filterChangedEvent);
}

function openViewPicker(): void {
  showOverflowMenu.value = false;
  showViewPicker.value = true;
}

function openScopeHelp(firstVisit = false): void {
  showOverflowMenu.value = false;
  scopeHelpFirstVisit.value = firstVisit;
  showScopeHelp.value = true;
  if (!scopeHelpSeen.value) {
    scopeHelpSeen.value = true;
    writeStorage(STORAGE_KEYS.filterScopeHelpSeen, 'true');
  }
}

function closeScopeHelp(): void {
  showScopeHelp.value = false;
  scopeHelpFirstVisit.value = false;
}

function openSqlView(): void {
  showOverflowMenu.value = false;
  showSql.value = true;
}

function updateCriteria(value: CriteriaUpdate): void {
  selectedFilter.value.filterParams = ensureFilterParamMaps(value.filterParams);
  for (const paramDefinition of value.clearedGeoParams) {
    emit(EVENTS.clearGeoShape, paramDefinition);
  }
  onParamChanged();
}

function updateCriteriaAndDraw(value: CriteriaUpdate & { paramDefinition: ParamDefinition }): void {
  updateCriteria(value);
  emit(EVENTS.startGeoDrawing, value.paramDefinition);
}

function retryPreview(): void {
  scheduleLivePreview();
}

function onOverviewResultAction(): void {
  if (previewError.value) {
    retryPreview();
    return;
  }
  if (hasNoMatches.value) {
    recoverFromNoMatches();
  }
}

function revertDraft(): void {
  if (!lastSuccessfulConfig.value) return;
  invalidatePendingPreview();
  selectedFilter.value = cloneClientFilterConfig(lastSuccessfulConfig.value);
  previewError.value = null;
  invalidateDrillDownResult();
}

function recoverFromNoMatches(): void {
  if (noMatchesRecovery.value === 'categories') {
    setResultGroupSelection(undefined);
    return;
  }
  if (noMatchesRecovery.value === 'criteria') {
    const selection = selectedFilter.value.filterParams?.resultGroupSelection;
    selectedFilter.value.filterParams = ensureFilterParamMaps({ resultGroupSelection: selection });
    onParamChanged();
    return;
  }
  openViewPicker();
}

function resetFilter(): void {
  showOverflowMenu.value = false;
  const cfg = defaultFilterConfig();
  if (!cfg) return;

  resetSnapshot.value = {
    config: cloneClientFilterConfig(selectedFilter.value),
    paused: !filterEnabled.value,
    previewResult: previewResult.value,
    previewFilterResult: previewFilterResult.value,
  };
  invalidatePendingPreview();
  filterEnabled.value = true;
  writeJsonStorage(STORAGE_KEYS.filterPaused, false);
  removeStorage(STORAGE_KEYS.filterPausedDraft);
  selectedFilter.value = normalizeClientFilterConfig(cfg);
  previewResult.value = null;
  previewFilterResult.value = null;
  previewError.value = null;
  invalidateDrillDownResult();
  filterStore.save(cfg);
  emit(EVENTS.filterChangedEvent);
  scheduleLivePreview();

  resetUndoAvailable.value = true;
  if (resetUndoTimer) clearTimeout(resetUndoTimer);
  resetUndoTimer = setTimeout(() => {
    resetUndoTimer = null;
    resetUndoAvailable.value = false;
    resetSnapshot.value = null;
  }, RESET_UNDO_DURATION_MS);
}

function undoReset(): void {
  const snapshot = resetSnapshot.value;
  if (!snapshot) return;
  if (resetUndoTimer) {
    clearTimeout(resetUndoTimer);
    resetUndoTimer = null;
  }
  resetUndoAvailable.value = false;
  resetSnapshot.value = null;
  invalidatePendingPreview();
  selectedFilter.value = cloneClientFilterConfig(snapshot.config);
  previewResult.value = snapshot.previewResult;
  previewFilterResult.value = snapshot.previewFilterResult;
  filterEnabled.value = !snapshot.paused;
  if (snapshot.paused) {
    persistPausedDraft();
    writeJsonStorage(STORAGE_KEYS.filterPaused, true);
    applyNormalTrackSet();
  } else {
    writeJsonStorage(STORAGE_KEYS.filterPaused, false);
    scheduleLivePreview();
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
    resultGroupSelection: params.resultGroupSelection
      ? {
          includedGroups: (params.resultGroupSelection.includedGroups ?? []).map((key) => ({
            value: key.value,
          })),
        }
      : undefined,
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
  selectedFilter.value.filterParams = params;
  persistCurrentFilterDraft();
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
  getNavigationState,
  restoreNavigationState,
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
  container-type: inline-size;
}
.filter-header {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  min-width: 0;
}

.filter-header__icon {
  flex: 0 0 auto;
  color: var(--accent-text);
  font-size: 1rem;
}

.filter-header__back {
  display: inline-flex;
  width: 2.25rem;
  height: 2.25rem;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  cursor: pointer;
}

.filter-header__back:hover {
  background: var(--surface-hover);
  color: var(--accent-text);
}

.filter-header__back:focus-visible {
  outline: 2px solid var(--focus-ring, var(--accent));
  outline-offset: 2px;
}

.filter-header-action,
.filter-overflow__menu button,
.filter-state button {
  border: 0;
  background: transparent;
}

.filter-header-action:hover {
  background: var(--accent-subtle);
}

.filter-header-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 50%;
  color: var(--text-secondary);
}

.filter-overflow {
  position: relative;
  z-index: 30;
}

.filter-overflow__menu {
  position: absolute;
  z-index: 20;
  top: calc(100% + 0.35rem);
  right: 0;
  display: flex;
  flex-direction: column;
  width: 12rem;
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: 0.7rem;
  background: var(--surface-elevated);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.22);
}

.filter-overflow__menu button {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-height: 2.75rem;
  padding: 0.65rem 0.85rem;
  color: var(--text-primary);
  text-align: left;
}

.filter-overflow__menu button:hover {
  background: var(--surface-hover);
}

.filter-root {
  background: var(--surface-ground);
}

.filter-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.65rem;
  min-height: 10rem;
  border-radius: 0.7rem;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.filter-state > div {
  display: flex;
  flex-direction: column;
}

.filter-state--error {
  color: var(--error);
}
.filter-inner-screen {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  padding: 0.5rem 1rem 1rem;
  box-sizing: border-box;
}
@media screen and (max-width: 768px) {
  .filter-header-action {
    width: 2.75rem;
    height: 2.75rem;
  }

  .filter-header__back {
    width: 2.75rem;
    height: 2.75rem;
  }
}

.filter-root {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior-y: contain;
  background: var(--surface-ground);
  -webkit-overflow-scrolling: touch;
}

@media screen and (max-width: 768px) {
  :global(.sheet.sheet--filter-overview .sheet-fullscreen-btn),
  :global(.sheet.sheet--filter-detail .sheet-fullscreen-btn) {
    display: none;
  }
}
</style>
