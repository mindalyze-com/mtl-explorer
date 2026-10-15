<template>
  <div
    ref="chooser"
    class="map-target-chooser"
    :data-sheet-content-key="JSON.stringify([state.id, activeCategoryKey, page])"
    @pointerdown="recordPointerInput"
  >
    <div ref="navigation" class="map-target-navigation">
      <nav class="map-category-nav" aria-label="Map result categories">
        <button
          type="button"
          class="map-category-tab"
          :aria-pressed="!activeCategory"
          :aria-label="`All categories, ${totalCount} results${allLookupIssue ? `, ${allLookupIssue}` : ''}`"
          data-test="map-category-all"
          @click="showOverview"
        >
          All
          <span
            class="map-category-count"
            :class="state.candidates.length ? 'map-category-count--available' : 'map-category-count--empty'"
            aria-hidden="true"
            >{{ totalCount }}</span
          >
          <span v-if="allLookupIssue" class="map-category-warning" :title="allLookupIssue" aria-hidden="true">!</span>
        </button>
        <button
          v-for="category in categories"
          :key="category.key"
          type="button"
          class="map-category-tab"
          :aria-pressed="category.key === activeCategoryKey"
          :aria-label="categoryLabel(category)"
          :data-category-key="category.key"
          data-test="map-category-tab"
          @click="openCategory(category.key)"
        >
          {{ category.shortLabel }}
          <span
            class="map-category-count"
            :class="
              category.candidates.length
                ? 'map-category-count--available'
                : categoryLookupIssue(category) && !mapCategoryPending(category)
                  ? 'map-category-count--failure'
                  : 'map-category-count--empty'
            "
            aria-hidden="true"
            >{{ categoryChipCount(category) }}</span
          >
          <span
            v-if="categoryLookupIssue(category) && (category.candidates.length || mapCategoryPending(category))"
            class="map-category-warning"
            :title="categoryLookupIssue(category)"
            aria-hidden="true"
            >!</span
          >
          <span
            v-if="mapCategoryPending(category)"
            class="map-category-spinner"
            aria-hidden="true"
            data-test="map-category-spinner"
          ></span>
        </button>
      </nav>
    </div>

    <div ref="results" class="map-target-results" data-test="map-target-results">
      <section
        v-for="group in visibleGroups"
        :key="group.category.key"
        class="map-target-group"
        :aria-labelledby="`${headingId}-${group.category.key}`"
        :data-category-key="group.category.key"
        data-test="map-target-group"
      >
        <h3 :id="`${headingId}-${group.category.key}`" class="map-target-group__title">
          <i :class="group.category.icon" class="bi" aria-hidden="true"></i>
          <span>{{ group.category.label }}</span>
          <span class="map-target-group__count">{{ categoryHeadingCount(group.category) }}</span>
        </h3>
        <ul class="map-target-list">
          <li v-for="candidate in group.candidates" :key="mapTargetKey(candidate)">
            <TrackChoiceRow
              v-if="candidate.kind === 'track'"
              :track-id="candidate.trackId"
              :label="candidate.label"
              :date="candidate.detail"
              :recorded-at="candidate.recordedAt"
              :length-meters="candidate.lengthMeters"
              :ascent-meters="candidate.ascentMeters"
              :activity-type="candidate.activityType"
              :description="candidate.description"
              :matched-media-count="candidate.matchedMediaCount"
              :context="rowContext(candidate)"
              :selected="isSelected(candidate)"
              class="map-target-row"
              :class="{ 'map-target-row--selected': isSelected(candidate) }"
              :data-target-key="mapTargetKey(candidate)"
              data-test="map-target-row"
              @pointerenter="previewPointer(candidate, $event)"
              @pointerleave="leavePointer(candidate)"
              @focus="previewFocus(candidate)"
              @blur="leaveFocus(candidate)"
              @click="selectCandidate(candidate)"
            />
            <button
              v-else
              type="button"
              class="map-target-row"
              :class="{ 'map-target-row--selected': isSelected(candidate) }"
              :aria-pressed="isSelected(candidate)"
              :aria-label="rowLabel(candidate)"
              :data-target-key="mapTargetKey(candidate)"
              data-test="map-target-row"
              @pointerenter="previewPointer(candidate, $event)"
              @pointerleave="leavePointer(candidate)"
              @focus="previewFocus(candidate)"
              @blur="leaveFocus(candidate)"
              @click="selectCandidate(candidate)"
            >
              <i
                :class="
                  candidate.kind === 'photo' && candidate.collectionScope === 'viewport'
                    ? 'bi bi-map'
                    : icons[candidate.kind]
                "
                class="map-target-row__icon"
                aria-hidden="true"
              ></i>
              <span class="map-target-row__content">
                <span class="map-target-row__heading">
                  <strong>{{ candidate.label }}</strong>
                </span>
                <span class="map-target-row__context">{{ rowContext(candidate) }}</span>
              </span>
              <i v-if="isSelected(candidate)" class="bi bi-check2 map-target-row__selected" aria-hidden="true"></i>
              <i class="bi bi-chevron-right map-target-row__chevron" aria-hidden="true"></i>
            </button>
          </li>
          <li v-if="!activeCategory && group.remaining">
            <button
              type="button"
              class="map-target-row map-category-more"
              :aria-label="`${mapCategoryMoreLabel(group.category, group.candidates.length)}, view all ${group.category.candidates.length} results`"
              :data-category-key="group.category.key"
              data-test="map-category-more"
              @click="openCategory(group.category.key)"
            >
              <i :class="group.category.icon" class="bi map-target-row__icon" aria-hidden="true"></i>
              <span class="map-target-row__content">
                <strong>{{ mapCategoryMoreLabel(group.category, group.candidates.length) }}</strong>
                <span class="map-category-preview">
                  {{ group.candidates.length ? 'View all' : `View all · ${mapCategoryPreview(group.category)}` }}
                </span>
              </span>
              <i class="bi bi-chevron-right map-target-row__chevron" aria-hidden="true"></i>
            </button>
          </li>
          <li v-if="mapCategoryPending(group.category) || mapCategoryHasFailure(group.category)">
            <div class="map-category-feedback" :data-category-key="group.category.key" data-test="map-category-status">
              <div
                v-if="mapCategoryPending(group.category)"
                class="map-category-skeleton"
                role="status"
                :aria-label="categoryPendingLabel(group.category)"
                data-test="map-category-loading"
              >
                <span class="map-category-spinner" aria-hidden="true"></span>
                <span class="map-category-skeleton__lines" aria-hidden="true"><span></span><span></span></span>
                <span class="visually-hidden">Searching…</span>
              </div>
              <MapInteractionStatus
                v-if="mapCategoryHasFailure(group.category)"
                :state="categoryFailureState(group.category)"
                data-test="map-category-failure"
              />
            </div>
          </li>
        </ul>
      </section>

      <p
        v-if="!activeCategory && emptyOverviewMessage"
        role="status"
        class="map-category-empty"
        data-test="map-target-empty"
      >
        {{ emptyOverviewMessage }}
      </p>
      <nav v-if="activeCategory && pageCount > 1" class="map-category-pages" aria-label="Result pages">
        <button type="button" :disabled="page === 0" aria-label="Previous page" @click="changePage(-1)">
          Previous
        </button>
        <span aria-live="polite" data-test="map-category-page-range">
          {{ pageStart + 1 }}–{{ pageEnd }} of {{ activeCategory.candidates.length.toLocaleString() }}
        </span>
        <button type="button" :disabled="page === pageCount - 1" aria-label="Next page" @click="changePage(1)">
          Next
        </button>
      </nav>
      <TrackProcessingNotice :processing="state.processing" :empty="!hasTracks" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue';
import TrackProcessingNotice from './TrackProcessingNotice.vue';
import MapInteractionStatus from './MapInteractionStatus.vue';
import TrackChoiceRow from './TrackChoiceRow.vue';
import { needsTrackProcessingNotice } from './trackProximitySelection';
import { mapTargetKey, type MapTargetCandidate } from './mapInteractionDecision';
import type { MapInteractionState } from './mapInteractionCoordinator';
import {
  allocateMapOverviewPreviews,
  buildMapTargetCategories,
  mapCategoryCount,
  mapCategoryHasFailure,
  mapCategoryMoreLabel,
  mapCategoryPending,
  mapCategoryPreview,
  rankMapCategoryCandidates,
  MAP_TARGET_CATEGORY_PAGE_SIZE,
  type MapTargetCategory,
} from './mapTargetCategories';

defineOptions({ name: 'MapTargetChooser' });
const props = defineProps<{ state: MapInteractionState }>();
const emit = defineEmits<{ select: [key: string]; 'preview-track': [trackId: number | null] }>();
const headingId = `${useId()}-category`;
const chooser = ref<HTMLElement | null>(null);
const navigation = ref<HTMLElement | null>(null);
const results = ref<HTMLElement | null>(null);
const activeCategoryKey = ref<string | null>(null);
const categoryOrder = ref<string[]>([]);
const candidateOrder = ref<Record<string, string[]>>({});
const previewKeys = ref<Record<string, string[]>>({});
const pages = ref<Record<string, number>>({});
let pointerCandidate: MapTargetCandidate | null = null;
let focusedCandidate: MapTargetCandidate | null = null;
let previewedTrackId: number | null = null;
let touchFocusBlocked = props.state.context.inputKind === 'touch';
const rawCategories = computed(() =>
  buildMapTargetCategories(props.state.candidates, props.state.sources).filter(
    (category) => category.candidates.length || mapCategoryPending(category) || mapCategoryHasFailure(category)
  )
);
function stableCategories(groups: MapTargetCategory[]): MapTargetCategory[] {
  return [...groups]
    .sort((a, b) => categoryOrder.value.indexOf(a.key) - categoryOrder.value.indexOf(b.key))
    .map((category) => {
      const order = candidateOrder.value[category.key] ?? [];
      return {
        ...category,
        candidates: [...category.candidates].sort(
          (a, b) => order.indexOf(mapTargetKey(a)) - order.indexOf(mapTargetKey(b))
        ),
      };
    });
}
watch(
  () => [props.state.id, rawCategories.value] as const,
  ([id, groups], previous) => {
    if (id !== previous?.[0]) {
      activeCategoryKey.value = null;
      categoryOrder.value = [];
      candidateOrder.value = {};
      previewKeys.value = {};
      pages.value = {};
    }
    for (const group of groups) {
      if (!categoryOrder.value.includes(group.key)) categoryOrder.value.push(group.key);
      const order = (candidateOrder.value[group.key] ??= []);
      for (const candidate of rankMapCategoryCandidates(group.candidates)) {
        const key = mapTargetKey(candidate);
        if (!order.includes(key)) order.push(key);
      }
      pages.value[group.key] = Math.min(
        pages.value[group.key] ?? 0,
        Math.max(0, Math.ceil(group.candidates.length / MAP_TARGET_CATEGORY_PAGE_SIZE) - 1)
      );
    }
    previewKeys.value = allocateMapOverviewPreviews(stableCategories(groups), previewKeys.value);
    if (activeCategoryKey.value && !groups.some((group) => group.key === activeCategoryKey.value)) void showOverview();
  },
  { immediate: true }
);
const categories = computed(() => stableCategories(rawCategories.value));
const activeCategory = computed(() => categories.value.find((category) => category.key === activeCategoryKey.value));
const totalCount = computed(() => {
  const pending = props.state.sources.some((source) => source.status === 'pending');
  if (!props.state.candidates.length && pending) return '…';
  return `${props.state.candidates.length.toLocaleString()}${pending ? '+' : ''}`;
});
const allLookupIssue = computed(() => {
  if (props.state.sources.some((source) => source.status === 'failed' || source.status === 'timed-out'))
    return 'Lookup unavailable';
  return props.state.sources.some((source) => source.status === 'cancelled') ? 'Lookup cancelled' : '';
});
const emptyOverviewMessage = computed(() => {
  if (props.state.candidates.length || props.state.sources.some((source) => source.status === 'pending')) return '';
  return props.state.sources.some((source) => source.status !== 'complete') ||
    (props.state.processing && needsTrackProcessingNotice(props.state.processing))
    ? 'Lookup incomplete'
    : 'Nothing found here';
});
const page = computed(() => (activeCategory.value ? (pages.value[activeCategory.value.key] ?? 0) : 0));
const pageCount = computed(() =>
  Math.ceil((activeCategory.value?.candidates.length ?? 0) / MAP_TARGET_CATEGORY_PAGE_SIZE)
);
const pageStart = computed(() => page.value * MAP_TARGET_CATEGORY_PAGE_SIZE);
const pageEnd = computed(() =>
  Math.min(pageStart.value + MAP_TARGET_CATEGORY_PAGE_SIZE, activeCategory.value?.candidates.length ?? 0)
);
const visibleGroups = computed(() =>
  activeCategory.value
    ? [
        {
          category: activeCategory.value,
          candidates: activeCategory.value.candidates.slice(pageStart.value, pageEnd.value),
          remaining: 0,
        },
      ]
    : categories.value.map((category) => {
        const keys = previewKeys.value[category.key] ?? [];
        const candidates = category.candidates.filter((candidate) => keys.includes(mapTargetKey(candidate)));
        return { category, candidates, remaining: category.candidates.length - candidates.length };
      })
);
const hasTracks = computed(() =>
  props.state.candidates.some((candidate) => candidate.kind === 'track' || candidate.kind === 'track-point')
);

function syncTrackPreview(): void {
  const candidate = pointerCandidate ?? focusedCandidate;
  const trackId =
    props.state.presentation === 'chooser' && (candidate?.kind === 'track' || candidate?.kind === 'track-point')
      ? candidate.trackId
      : null;
  if (trackId === previewedTrackId) return;
  previewedTrackId = trackId;
  emit('preview-track', trackId);
}

function clearTrackPreview(): void {
  pointerCandidate = null;
  focusedCandidate = null;
  syncTrackPreview();
}

function recordPointerInput(event: PointerEvent): void {
  touchFocusBlocked = event.pointerType === 'touch';
  if (touchFocusBlocked) clearTrackPreview();
}

function previewPointer(candidate: MapTargetCandidate, event: PointerEvent): void {
  recordPointerInput(event);
  if (touchFocusBlocked) return;
  pointerCandidate = candidate;
  syncTrackPreview();
}

function leavePointer(candidate: MapTargetCandidate): void {
  if (pointerCandidate && mapTargetKey(pointerCandidate) === mapTargetKey(candidate)) pointerCandidate = null;
  syncTrackPreview();
}

function previewFocus(candidate: MapTargetCandidate): void {
  if (touchFocusBlocked) return;
  focusedCandidate = candidate;
  syncTrackPreview();
}

function leaveFocus(candidate: MapTargetCandidate): void {
  if (focusedCandidate && mapTargetKey(focusedCandidate) === mapTargetKey(candidate)) focusedCandidate = null;
  syncTrackPreview();
}

function recordKeyboardInput(): void {
  touchFocusBlocked = false;
}

function selectCandidate(candidate: MapTargetCandidate): void {
  clearTrackPreview();
  emit('select', mapTargetKey(candidate));
}

watch(
  () => [props.state.id, props.state.presentation] as const,
  ([id, presentation], previous) => {
    if (id !== previous?.[0] || presentation !== 'chooser') clearTrackPreview();
    if (id !== previous?.[0]) touchFocusBlocked = props.state.context.inputKind === 'touch';
  },
  { flush: 'sync' }
);
watch(visibleGroups, (groups) => {
  const visibleKeys = new Set(groups.flatMap((group) => group.candidates.map(mapTargetKey)));
  if (pointerCandidate && !visibleKeys.has(mapTargetKey(pointerCandidate))) pointerCandidate = null;
  if (focusedCandidate && !visibleKeys.has(mapTargetKey(focusedCandidate))) focusedCandidate = null;
  syncTrackPreview();
});
onMounted(() => document.addEventListener('keydown', recordKeyboardInput, true));
onBeforeUnmount(() => {
  document.removeEventListener('keydown', recordKeyboardInput, true);
  clearTrackPreview();
});

const typeLabels: Record<MapTargetCandidate['kind'], string> = {
  track: 'Your GPS tracks',
  'track-point': 'Track point',
  photo: 'Photos',
  poi: 'Point of interest',
  overlay: 'Public route',
};
const icons: Record<MapTargetCandidate['kind'], string> = {
  track: 'bi bi-signpost-split',
  'track-point': 'bi bi-geo-alt',
  photo: 'bi bi-images',
  poi: 'bi bi-pin-map',
  overlay: 'bi bi-map',
};

function categoryLabel(category: MapTargetCategory): string {
  const known = category.candidates.length > 0;
  const pending = mapCategoryPending(category);
  return [
    category.label,
    mapCategoryCount(category),
    mapCategoryPreview(category),
    known && pending ? 'searching' : '',
    known || pending ? categoryLookupIssue(category) : '',
  ]
    .filter(Boolean)
    .join(', ');
}

function categoryLookupIssue(category: MapTargetCategory): string {
  if (category.sources.some((source) => source.status === 'failed' || source.status === 'timed-out'))
    return 'Lookup unavailable';
  return category.sources.some((source) => source.status === 'cancelled') ? 'Lookup cancelled' : '';
}

function categoryChipCount(category: MapTargetCategory): string {
  return !category.candidates.length && !mapCategoryPending(category) && categoryLookupIssue(category)
    ? '!'
    : mapCategoryCount(category);
}

function categoryHeadingCount(category: MapTargetCategory): string {
  if (category.key !== 'photo') return mapCategoryCount(category);
  if (!category.candidates.length && (mapCategoryPending(category) || categoryLookupIssue(category)))
    return categoryChipCount(category);
  return `${mapCategoryCount(category)} ${category.candidates.length === 1 ? 'collection' : 'collections'}`;
}

function categoryPendingLabel(category: MapTargetCategory): string {
  const sources = category.sources.filter((source) => source.status === 'pending').map((source) => source.label);
  return `${category.label}: searching. ${sources.join(', ')}`;
}

function categoryFailureState(category: MapTargetCategory): MapInteractionState {
  return {
    ...props.state,
    sources: category.sources.filter((source) => source.status !== 'complete' && source.status !== 'pending'),
  };
}

async function openCategory(key: string): Promise<void> {
  clearTrackPreview();
  activeCategoryKey.value = key;
  await nextTick();
  results.value?.scrollTo?.({ top: 0 });
  navigation.value
    ?.querySelector<HTMLButtonElement>('[data-test="map-category-tab"][aria-pressed="true"]')
    ?.focus({ preventScroll: true });
}

async function showOverview(): Promise<void> {
  clearTrackPreview();
  activeCategoryKey.value = null;
  await nextTick();
  results.value?.scrollTo?.({ top: 0 });
  navigation.value?.querySelector<HTMLButtonElement>('[data-test="map-category-all"]')?.focus({ preventScroll: true });
}

async function changePage(direction: number): Promise<void> {
  if (!activeCategory.value) return;
  const nextPage = Math.max(0, Math.min(page.value + direction, pageCount.value - 1));
  if (nextPage === page.value) return;
  clearTrackPreview();
  pages.value[activeCategory.value.key] = nextPage;
  await nextTick();
  results.value?.scrollTo?.({ top: 0 });
  chooser.value?.querySelector<HTMLButtonElement>('[data-test="map-target-row"]')?.focus({ preventScroll: true });
}

function isSelected(candidate: MapTargetCandidate): boolean {
  return props.state.selected != null && mapTargetKey(props.state.selected) === mapTargetKey(candidate);
}

function rowContext(candidate: MapTargetCandidate): string {
  if (candidate.kind === 'track') {
    const source = candidate.sourceLabel;
    return !source || source === 'Your tracks' || source === typeLabels.track
      ? typeLabels.track
      : `${typeLabels.track} · ${source}`;
  }
  const type = typeLabels[candidate.kind];
  const source = candidate.sourceLabel;
  if (candidate.kind === 'photo') {
    const noun = candidate.count === 1 ? 'photo' : 'photos';
    const count = `${candidate.count.toLocaleString()} ${noun}`;
    const repeatsTitle = candidate.label === count || candidate.label === `${candidate.count} ${noun}`;
    return [repeatsTitle ? '' : count, source && source !== type ? source : '', candidate.detail]
      .filter(Boolean)
      .join(' · ');
  }
  return [source || type, candidate.detail].filter(Boolean).join(' · ');
}

function rowLabel(candidate: MapTargetCandidate): string {
  return [
    candidate.label,
    rowContext(candidate),
    candidate.kind === 'track' ? candidate.detail : '',
    isSelected(candidate) ? 'Selected' : '',
  ]
    .filter(Boolean)
    .join(', ');
}
</script>

<style scoped>
.map-target-chooser {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  overflow: clip;
  gap: 0.75rem;
  padding: 0.25rem var(--dlg-padding) 0;
}
.map-target-navigation {
  display: grid;
  flex: 0 0 auto;
  gap: 0.5rem;
  background: var(--surface-sheet-solid);
}
.map-target-results {
  display: grid;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior-y: contain;
  align-content: start;
  gap: 0.75rem;
  padding-bottom: var(--dlg-padding);
}
.map-target-group__title {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin: 0 0 0.4rem;
  padding: 0.35rem 0.5rem;
  color: var(--text-primary);
  background: var(--accent-bg);
  border: 1px solid var(--border-default);
  border-radius: 0.4rem;
  font-size: var(--text-sm-size);
  font-weight: 600;
}
.map-target-group__title .bi {
  color: var(--accent);
}
.map-target-group__count {
  margin-left: auto;
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}
.map-category-spinner {
  display: inline-block;
  flex: 0 0 auto;
  width: 0.8rem;
  height: 0.8rem;
  border: 2px solid var(--border-default);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: map-category-spin 0.85s linear infinite;
}
.map-category-feedback {
  display: grid;
  gap: 0.3rem;
  padding: 0.45rem 0.65rem;
  background: var(--surface-sheet-solid);
  border: 1px dashed var(--border-default);
  border-radius: 0.5rem;
}
.map-category-feedback :deep(.map-identification-status) {
  min-height: 0;
}
.map-category-skeleton {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  min-height: 1.8rem;
}
.map-category-skeleton__lines {
  display: grid;
  flex: 1;
  gap: 0.4rem;
  animation: map-category-pulse 1.4s ease-in-out infinite alternate;
}
.map-category-skeleton__lines span {
  width: 65%;
  height: 0.35rem;
  background: var(--surface-elevated);
  border-radius: 1rem;
}
.map-category-skeleton__lines span + span {
  width: 40%;
}
@keyframes map-category-spin {
  to {
    transform: rotate(360deg);
  }
}
@keyframes map-category-pulse {
  to {
    opacity: 0.45;
  }
}
@media (prefers-reduced-motion: reduce) {
  .map-category-spinner,
  .map-category-skeleton__lines {
    animation: none;
  }
}
.map-target-list {
  display: grid;
  gap: 0.4rem;
  margin: 0;
  padding: 0;
  list-style: none;
  container: track-choice-results / inline-size;
}
.map-target-row {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  width: 100%;
  min-height: 3.25rem;
  padding: 0.55rem 0.65rem;
  color: var(--text-primary);
  text-align: left;
  background: var(--surface-sheet-solid);
  border: 1px solid var(--border-default);
  border-radius: 0.75rem;
  cursor: pointer;
}
.map-target-row:hover,
.map-target-row--selected {
  background: var(--surface-elevated);
  border-color: var(--accent);
}
.map-target-row:focus-visible,
.map-category-tab:focus-visible,
.map-category-pages button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.map-target-row__icon,
.map-target-row__chevron,
.map-target-row__selected {
  flex: 0 0 auto;
  color: var(--text-muted);
}
.map-target-row__content {
  display: grid;
  flex: 1 1 auto;
  min-width: 0;
  gap: 0.15rem;
}
.map-target-row__heading {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  min-width: 0;
}
.map-target-row__content strong {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-sm-size);
  font-weight: 600;
}
.map-target-row__heading strong {
  flex: 1 1 auto;
}
.map-target-row__context,
.map-category-preview,
.map-category-empty {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
}
.map-target-row__context,
.map-category-preview {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.map-category-empty {
  margin: 0;
}
.map-category-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  padding-bottom: 0.25rem;
}
.map-category-tab,
.map-category-pages button {
  min-height: 2.75rem;
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--border-default);
  border-radius: 0.5rem;
  color: var(--text-primary);
  background: var(--surface-card);
  font-size: var(--text-sm-size);
  cursor: pointer;
}
.map-category-tab[aria-pressed='true'] {
  background: var(--surface-elevated);
  border-color: var(--accent);
}
.map-category-tab {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  max-width: 100%;
  overflow-wrap: anywhere;
}
.map-category-tab span,
.map-category-pages span {
  font-variant-numeric: tabular-nums;
}
.map-category-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.3rem;
  padding: 0.05rem 0.35rem;
  border: 1px solid var(--border-default);
  border-radius: 0.6rem;
  font-size: var(--text-xs-size);
  font-weight: 600;
}
.map-category-count--available {
  color: var(--accent-text);
  background: var(--accent-bg);
  border-color: var(--accent-subtle);
}
.map-category-count--empty {
  color: var(--text-muted);
  background: transparent;
  border-color: transparent;
  font-weight: 400;
}
.map-category-count--failure,
.map-category-warning {
  color: var(--warning);
  font-weight: 700;
}
.map-category-warning {
  margin-left: -0.1rem;
}
.map-category-pages {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}
.map-category-pages span {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
}
.map-category-pages button:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
