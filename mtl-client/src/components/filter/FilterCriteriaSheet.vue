<template>
  <BottomSheet
    :model-value="modelValue"
    title="Filter criteria"
    icon="bi bi-sliders"
    :detents="[{ height: '78vh' }, { height: '95vh' }]"
    :no-backdrop="true"
    :z-index="5100"
    sheet-class="sheet--filter-detail"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="filter-criteria-sheet filter-detail-sheet-content">
      <p class="filter-criteria-sheet__intro">Changes apply automatically.</p>
      <FilterDetailPanel
        :selected-filter="draftFilter"
        :track-id-candidate-tracks="trackIdCandidateTracks"
        :track-id-candidates-loading="trackIdCandidatesLoading"
        @set-date-time-param="setDateTimeParam"
        @set-string-param="setStringParam"
        @start-geo-drawing="applyAndDraw"
        @clear-geo-shape="clearGeoShape"
      />
    </div>

    <template v-if="hasCriteriaDraft" #footer>
      <FilterSheetActions
        :reset-visible="hasCriteriaDraft"
        :commit-visible="false"
        reset-label="Reset criteria"
        reset-aria-label="Reset criteria"
        @reset="resetCriteria"
      />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterParamsRequest } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterParamsRequest';
import type { ParamDefinition } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/GpsTrack';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import FilterDetailPanel from '@/components/filter/FilterDetailPanel.vue';
import FilterSheetActions from '@/components/filter/FilterSheetActions.vue';
import { ensureFilterParamMaps } from '@/utils/filterParams';

type CriteriaUpdate = {
  filterParams: FilterParamsRequest;
  clearedGeoParams: ParamDefinition[];
};

defineOptions({ name: 'FilterCriteriaSheet' });

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    filterInfo?: FilterInfo;
    filterParams?: FilterParamsRequest;
    trackIdCandidateTracks?: GpsTrack[];
    trackIdCandidatesLoading?: boolean;
  }>(),
  {
    filterInfo: undefined,
    filterParams: undefined,
    trackIdCandidateTracks: () => [],
    trackIdCandidatesLoading: false,
  }
);

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void;
  (event: 'change', value: CriteriaUpdate): void;
  (event: 'apply-and-draw', value: CriteriaUpdate & { paramDefinition: ParamDefinition }): void;
}>();

const draftParams = ref<FilterParamsRequest>({});
const draftFilter = computed(() => ({
  filterInfo: props.filterInfo,
  filterParams: draftParams.value,
}));
const hasCriteriaDraft = computed((): boolean => {
  const params = draftParams.value;
  return [params.stringParams, params.dateTimeParams, params.geoCircles, params.geoRectangles, params.geoPolygons].some(
    recordHasValue
  );
});

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return;
    draftParams.value = cloneFilterParams(props.filterParams);
  },
  { immediate: true }
);

function cloneFilterParams(source?: FilterParamsRequest): FilterParamsRequest {
  const params = ensureFilterParamMaps(source ?? {});
  return {
    ...params,
    stringParams: { ...(params.stringParams ?? {}) },
    dateTimeParams: { ...(params.dateTimeParams ?? {}) },
    geoCircles: Object.fromEntries(
      Object.entries(params.geoCircles ?? {}).map(([key, value]) => [key, value ? { ...value } : value])
    ),
    geoRectangles: Object.fromEntries(
      Object.entries(params.geoRectangles ?? {}).map(([key, value]) => [key, value ? { ...value } : value])
    ),
    geoPolygons: Object.fromEntries(
      Object.entries(params.geoPolygons ?? {}).map(([key, value]) => [
        key,
        value
          ? {
              ...value,
              coordinates: value.coordinates?.map((coordinate) => [...coordinate]),
            }
          : value,
      ])
    ),
    resultGroupSelection: params.resultGroupSelection
      ? {
          includedGroups: (params.resultGroupSelection.includedGroups ?? []).map((key) => ({ value: key.value })),
        }
      : undefined,
  };
}

function currentUpdate(clearedGeoParams: ParamDefinition[] = []): CriteriaUpdate {
  return {
    filterParams: cloneFilterParams(draftParams.value),
    clearedGeoParams,
  };
}

function emitChange(clearedGeoParams: ParamDefinition[] = []): void {
  emit('change', currentUpdate(clearedGeoParams));
}

function recordHasValue(record?: Record<string, unknown>): boolean {
  return Object.values(record ?? {}).some(
    (value) => value != null && (typeof value !== 'string' || value.trim().length > 0)
  );
}

function setDateTimeParam(payload: { name?: string; value: Date | Date[] | (Date | null)[] | null | undefined }): void {
  if (!payload.name) return;
  const params = ensureFilterParamMaps(draftParams.value);
  const dateParams = params.dateTimeParams as Record<string, unknown>;
  const nextValue = Array.isArray(payload.value)
    ? payload.value.find((item): item is Date => item instanceof Date)
    : payload.value;
  if (nextValue instanceof Date && !Number.isNaN(nextValue.getTime())) {
    dateParams[payload.name] = nextValue;
  } else {
    delete dateParams[payload.name];
  }
  draftParams.value = cloneFilterParams(params);
  emitChange();
}

function setStringParam(payload: { name?: string; value: string }): void {
  if (!payload.name) return;
  const params = ensureFilterParamMaps(draftParams.value);
  if (payload.value.trim()) params.stringParams![payload.name] = payload.value;
  else delete params.stringParams![payload.name];
  draftParams.value = cloneFilterParams(params);
  emitChange();
}

function clearGeoShape(paramDefinition: ParamDefinition): void {
  const name = paramDefinition.name;
  if (!name) return;
  const params = ensureFilterParamMaps(draftParams.value);
  if (paramDefinition.type === 'GEO_CIRCLE') delete params.geoCircles?.[name];
  if (paramDefinition.type === 'GEO_RECTANGLE') delete params.geoRectangles?.[name];
  if (paramDefinition.type === 'GEO_POLYGON') delete params.geoPolygons?.[name];
  draftParams.value = cloneFilterParams(params);
  emitChange([paramDefinition]);
}

function resetCriteria(): void {
  const params = ensureFilterParamMaps(draftParams.value);
  const geoNames = new Set([
    ...Object.keys(params.geoCircles ?? {}),
    ...Object.keys(params.geoRectangles ?? {}),
    ...Object.keys(params.geoPolygons ?? {}),
  ]);
  const clearedGeoParams = (props.filterInfo?.paramDefinitions ?? []).filter((definition) =>
    Boolean(definition.name && geoNames.has(definition.name))
  );
  draftParams.value = cloneFilterParams({ resultGroupSelection: params.resultGroupSelection });
  emitChange(clearedGeoParams);
}

function applyAndDraw(paramDefinition: ParamDefinition): void {
  emit('apply-and-draw', { ...currentUpdate(), paramDefinition });
  emit('update:modelValue', false);
}
</script>

<style scoped>
.filter-criteria-sheet {
  gap: 0.75rem;
}

.filter-criteria-sheet__intro {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

@media screen and (max-width: 600px) {
  .filter-criteria-sheet {
    padding-inline: 0.75rem;
  }
}
</style>
