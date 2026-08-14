<template>
  <div class="filter-detail">
    <section class="filter-detail-section" aria-labelledby="filter-criteria-title">
      <div class="filter-detail-section__head">
        <span class="filter-detail-section__icon filter-section-icon">
          <i class="bi bi-sliders" aria-hidden="true"></i>
        </span>
        <div class="filter-detail-section__copy">
          <h3 id="filter-criteria-title" class="filter-detail-section__title filter-section-title">Filter criteria</h3>
          <p class="filter-detail-section__help filter-section-help">Controls which tracks match.</p>
        </div>
      </div>

      <div v-if="paramControlCount > 0" class="filter-detail-param-sections">
        <section
          v-for="section in paramSections"
          :key="section.key"
          class="filter-detail-param-section"
          :class="[
            `filter-detail-param-section--${section.tone}`,
            {
              'filter-detail-param-section--collapsed': !isParamSectionOpen(section),
              'filter-detail-param-section--single': paramSections.length === 1,
            },
          ]"
        >
          <button
            v-if="section.collapsible"
            type="button"
            class="filter-detail-param-section__summary filter-detail-param-section__summary--button"
            :aria-expanded="isParamSectionOpen(section)"
            @click="toggleParamSection(section)"
          >
            <span class="filter-detail-param-section__copy">
              <span class="filter-detail-param-section__title-row">
                <span class="filter-detail-param-section__title">{{ section.title }}</span>
              </span>
              <span class="filter-detail-param-section__subline">{{ section.description }}</span>
            </span>
            <span class="filter-detail-param-section__meta">
              <span v-if="section.activeCount > 0" class="filter-detail-param-section__active">
                {{ formatActiveCount(section.activeCount) }}
              </span>
              <i :class="isParamSectionOpen(section) ? 'bi bi-chevron-up' : 'bi bi-chevron-down'"></i>
            </span>
          </button>

          <div v-else class="filter-detail-param-section__summary">
            <span class="filter-detail-param-section__copy">
              <span class="filter-detail-param-section__title-row">
                <span class="filter-detail-param-section__title">{{ section.title }}</span>
              </span>
              <span class="filter-detail-param-section__subline">{{ section.description }}</span>
            </span>
          </div>

          <div v-show="isParamSectionOpen(section)" class="filter-detail-param-section__body">
            <section v-for="group in section.groups" :key="group.key" class="filter-detail-param-group">
              <div
                class="filter-detail-param-group__head"
                :class="{ 'filter-detail-param-group__head--area': isAreaGroup(group) }"
              >
                <div class="filter-detail-param-group__heading">
                  <h4 class="filter-detail-param-group__title">{{ displayGroupLabel(group) }}</h4>
                  <span v-if="isAreaGroup(group)" class="filter-detail-param-group__summary">{{
                    areaSummary(group)
                  }}</span>
                  <span v-else-if="isTrackPickerGroup(group)" class="filter-detail-param-group__summary">
                    {{ trackPickerSummary(group) }}
                  </span>
                </div>
                <button
                  v-if="isAreaGroup(group)"
                  type="button"
                  class="filter-detail-param-group__area-action filter-action-text"
                  :aria-expanded="isAreaGroupOpen(group)"
                  :aria-controls="areaGroupDomId(section, group)"
                  @click="toggleAreaGroup(group)"
                >
                  {{ group.activeCount > 0 ? 'Edit areas' : 'Draw area' }}
                  <i :class="isAreaGroupOpen(group) ? 'bi bi-chevron-up' : 'bi bi-chevron-down'"></i>
                </button>
                <span
                  v-else-if="!isTrackPickerGroup(group) && group.activeCount > 0"
                  class="filter-detail-param-group__active"
                >
                  {{ formatActiveCount(group.activeCount) }}
                </span>
              </div>

              <div
                v-show="!isAreaGroup(group) || isAreaGroupOpen(group)"
                :id="isAreaGroup(group) ? areaGroupDomId(section, group) : undefined"
                class="filter-detail__params-grid"
              >
                <div
                  v-for="control in group.controls"
                  :key="control.name"
                  class="filter-detail-field"
                  :class="{ 'filter-detail-field--wide': isTrackPickerControl(control) }"
                >
                  <template v-if="isTrackPickerControl(control)">
                    <TrackIdParam
                      :param-def="control.paramDefinition"
                      :model-value="stringParam(control.name)"
                      :tracks="trackIdCandidateTracks"
                      :loading="trackIdCandidatesLoading"
                      :optional="false"
                      :show-header="false"
                      @update:model-value="emit('set-string-param', { name: control.name, value: $event })"
                    />
                  </template>

                  <template v-else-if="isDateTimeControl(control)">
                    <div class="filter-detail-field__label-row">
                      <label class="filter-detail-field__label filter-meta-label" :for="control.name">
                        {{ control.label }}
                      </label>
                      <span v-if="!control.optional" class="filter-detail-field__required">Required</span>
                    </div>
                    <DateTimeParam
                      :id="control.name"
                      :model-value="dateTimeParam(control.name)"
                      :label="control.label"
                      class="filter-detail__full-width"
                      @update:model-value="emit('set-date-time-param', { name: control.name, value: $event })"
                    />
                  </template>

                  <template v-else-if="isGeoControl(control)">
                    <GeoShapeParam
                      :param-def="control.paramDefinition"
                      :circle="
                        control.paramDefinition.type === 'GEO_CIRCLE'
                          ? selectedFilter?.filterParams?.geoCircles?.[control.name]
                          : undefined
                      "
                      :rectangle="
                        control.paramDefinition.type === 'GEO_RECTANGLE'
                          ? selectedFilter?.filterParams?.geoRectangles?.[control.name]
                          : undefined
                      "
                      :polygon="
                        control.paramDefinition.type === 'GEO_POLYGON'
                          ? selectedFilter?.filterParams?.geoPolygons?.[control.name]
                          : undefined
                      "
                      :optional="false"
                      @start-geo-drawing="emit('start-geo-drawing', $event)"
                      @clear-geo-shape="emit('clear-geo-shape', $event)"
                    />
                  </template>

                  <template v-else>
                    <div class="filter-detail-field__label-row">
                      <label class="filter-detail-field__label filter-meta-label" :for="control.name">
                        {{ control.label }}
                      </label>
                      <span v-if="displayUnit(control)" class="filter-detail-field__unit">{{
                        displayUnit(control)
                      }}</span>
                      <span v-if="!control.optional" class="filter-detail-field__required">Required</span>
                    </div>
                    <InputText
                      :id="control.name"
                      :value="displayStringParam(control)"
                      :type="isNumberControl(control) ? 'number' : 'text'"
                      :inputmode="isNumberControl(control) ? 'decimal' : undefined"
                      placeholder="enter a value"
                      class="filter-detail__full-width"
                      @input="onStringInput(control, $event)"
                    />
                  </template>
                </div>
              </div>
            </section>
          </div>
        </section>
      </div>

      <p v-else class="filter-detail__empty">
        This view has no criteria. All tracks continue to the Included categories section.
      </p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { FilterParamsRequest } from '@/components/filter/FilterService';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { ParamDefinition } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { effectiveUiMetadata, type FilterParamGroupMetadata, type FilterParamMetadata } from '@/utils/filterMetadata';
import GeoShapeParam from '@/components/filter/GeoShapeParam.vue';
import TrackIdParam from '@/components/filter/TrackIdParam.vue';
import DateTimeParam from '@/components/filter/DateTimeParam.vue';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { filterParamCanonicalValue, filterParamDisplayUnit, filterParamDisplayValue } from '@/utils/filterParamUnits';
import { parseTrackIdText } from '@/utils/trackIdFilter';

const DEFAULT_PARAM_GROUP_KEY = '__ungrouped';
const TRACK_PICKER_WIDGET = 'trackPicker';

type FilterDetailConfig = {
  filterInfo?: FilterInfo;
  filterParams?: FilterParamsRequest;
};

type ParamControl = {
  name: string;
  label: string;
  widget: string;
  unit?: string;
  relation?: string;
  optional: boolean;
  metadata: FilterParamMetadata;
  paramDefinition: ParamDefinition;
  sourceOrder: number;
};

type ParamGroup = {
  key: string;
  label: string;
  order: number;
  controls: ParamControl[];
  activeCount: number;
};

type ParamSection = {
  key: 'specific' | 'base';
  title: string;
  description: string;
  tone: 'specific' | 'base';
  collapsible: boolean;
  groups: ParamGroup[];
  count: number;
  activeCount: number;
};

defineOptions({ name: 'FilterDetailPanel' });

const props = withDefaults(
  defineProps<{
    selectedFilter: FilterDetailConfig;
    trackIdCandidateTracks?: GpsTrack[];
    trackIdCandidatesLoading?: boolean;
  }>(),
  {
    trackIdCandidateTracks: () => [],
    trackIdCandidatesLoading: false,
  }
);

const emit = defineEmits<{
  (
    event: 'set-date-time-param',
    payload: { name?: string; value: Date | Date[] | (Date | null)[] | null | undefined }
  ): void;
  (event: 'set-string-param', payload: { name?: string; value: string }): void;
  (event: 'start-geo-drawing', paramDefinition: ParamDefinition): void;
  (event: 'clear-geo-shape', paramDefinition: ParamDefinition): void;
}>();

const baseScopeOpen = ref(false);
const expandedAreaGroups = ref<Set<string>>(new Set());
const { measurementSystem } = useMeasurementSystem();

const filterInfo = computed((): FilterInfo | undefined => props.selectedFilter?.filterInfo);
const paramDefinitions = computed((): ParamDefinition[] => filterInfo.value?.paramDefinitions ?? []);
const paramDefinitionsByName = computed((): Map<string, ParamDefinition> => {
  return new Map(
    paramDefinitions.value
      .filter((paramDefinition) => Boolean(paramDefinition.name))
      .map((paramDefinition) => [paramDefinition.name!, paramDefinition])
  );
});
const paramControls = computed((): ParamControl[] => {
  const params = effectiveUiMetadata(filterInfo.value).params ?? {};
  return Object.entries(params)
    .map(([name, metadata], sourceOrder): ParamControl | null => {
      const definition = paramDefinitionsByName.value.get(name);
      if (!definition) return null;

      const widget = String(metadata.widget || widgetForDefinition(definition));
      const label = String(metadata.label || definition.label || name);
      return {
        name,
        label,
        widget,
        unit: typeof metadata.unit === 'string' ? metadata.unit : undefined,
        relation: metadata.relation,
        optional: metadata.optional === true,
        metadata,
        paramDefinition: {
          ...definition,
          label,
        },
        sourceOrder,
      };
    })
    .filter((control): control is ParamControl => Boolean(control));
});
const selectedParamControls = computed((): ParamControl[] =>
  paramControls.value.filter((control) => control.relation !== 'inherited')
);
const inheritedParamControls = computed((): ParamControl[] =>
  paramControls.value.filter((control) => control.relation === 'inherited')
);
const paramSections = computed((): ParamSection[] => {
  const sections: ParamSection[] = [];
  const selectedControls = selectedParamControls.value;
  const inheritedControls = inheritedParamControls.value;
  const hasInheritedControls = inheritedControls.length > 0;

  if (inheritedControls.length > 0) {
    sections.push({
      key: 'base',
      title: 'Show tracks',
      description: 'Criteria that change the track set across MTL Explorer.',
      tone: 'base',
      collapsible: false,
      groups: groupControls(inheritedControls),
      count: inheritedControls.length,
      activeCount: countActiveControls(inheritedControls),
    });
  }

  if (selectedControls.length > 0) {
    sections.push({
      key: 'specific',
      title: hasInheritedControls ? 'View options' : 'Show tracks',
      description: hasInheritedControls
        ? 'Options used only by this view.'
        : 'Criteria that change the track set across MTL Explorer.',
      tone: 'specific',
      collapsible: false,
      groups: groupControls(selectedControls),
      count: selectedControls.length,
      activeCount: countActiveControls(selectedControls),
    });
  }

  return sections;
});
const selectedFilterKey = computed((): string => {
  const config = filterInfo.value?.filterConfig;
  if (!config) return '';
  return `${config.filterDomain ?? ''}/${config.filterName ?? ''}/${config.id ?? ''}`;
});
const defaultBaseScopeOpen = computed((): boolean => {
  const hasSpecificControls = selectedParamControls.value.length > 0;
  return !hasSpecificControls || countActiveControls(inheritedParamControls.value) > 0;
});
watch(
  selectedFilterKey,
  () => {
    baseScopeOpen.value = defaultBaseScopeOpen.value;
    expandedAreaGroups.value = new Set();
  },
  { immediate: true }
);
const paramControlCount = computed((): number => paramControls.value.length);

function groupControls(controls: ParamControl[]): ParamGroup[] {
  const groupMetadata = effectiveUiMetadata(filterInfo.value).paramGroups ?? {};
  const controlsByGroup = new Map<string, ParamControl[]>();
  for (const control of controls) {
    const groupKey = String(control.metadata.group || DEFAULT_PARAM_GROUP_KEY);
    if (!controlsByGroup.has(groupKey)) controlsByGroup.set(groupKey, []);
    controlsByGroup.get(groupKey)!.push(control);
  }

  return Array.from(controlsByGroup.entries())
    .map(([key, controls]) => {
      const metadata = (groupMetadata[key] ?? {}) as FilterParamGroupMetadata;
      return {
        key,
        label: String(metadata.label || (key === DEFAULT_PARAM_GROUP_KEY ? 'Parameters' : key)),
        order: typeof metadata.order === 'number' ? metadata.order : Number.MAX_SAFE_INTEGER,
        controls: [...controls].sort((left, right) => left.sourceOrder - right.sourceOrder),
        activeCount: countActiveControls(controls),
      };
    })
    .sort((left, right) => {
      if (left.order !== right.order) return left.order - right.order;
      return left.label.localeCompare(right.label, undefined, { numeric: true, sensitivity: 'base' });
    });
}

function dateTimeParam(name: string): Date | null {
  const value = (props.selectedFilter?.filterParams?.dateTimeParams as Record<string, unknown> | undefined)?.[name];
  if (value instanceof Date) return value;
  if (typeof value === 'string' && value) return new Date(value.replace(' ', 'T'));
  return null;
}

function stringParam(name: string): string {
  return props.selectedFilter?.filterParams?.stringParams?.[name] ?? '';
}

function displayStringParam(control: ParamControl): string {
  const value = stringParam(control.name);
  return isNumberControl(control) ? filterParamDisplayValue(value, control.unit, measurementSystem.value) : value;
}

function displayUnit(control: ParamControl): string | undefined {
  return isNumberControl(control) ? filterParamDisplayUnit(control.unit, measurementSystem.value) : control.unit;
}

function onStringInput(control: ParamControl, event: Event): void {
  const target = event.target as HTMLInputElement | null;
  const value = target?.value ?? '';
  emit('set-string-param', {
    name: control.name,
    value: isNumberControl(control) ? filterParamCanonicalValue(value, control.unit, measurementSystem.value) : value,
  });
}

function countActiveControls(controls: ParamControl[]): number {
  return controls.filter((control) => controlHasValue(control)).length;
}

function controlHasValue(control: ParamControl): boolean {
  if (isDateTimeControl(control)) return dateTimeParam(control.name) != null;
  if (isGeoControl(control)) return geoParamHasValue(control);
  return stringParam(control.name).trim().length > 0;
}

function geoParamHasValue(control: ParamControl): boolean {
  const params = props.selectedFilter?.filterParams;
  switch (control.paramDefinition.type) {
    case 'GEO_CIRCLE':
      return params?.geoCircles?.[control.name] != null;
    case 'GEO_RECTANGLE':
      return params?.geoRectangles?.[control.name] != null;
    case 'GEO_POLYGON':
      return (params?.geoPolygons?.[control.name]?.coordinates?.length ?? 0) >= 3;
    default:
      return false;
  }
}

function isParamSectionOpen(section: ParamSection): boolean {
  return !section.collapsible || baseScopeOpen.value;
}

function toggleParamSection(section: ParamSection): void {
  if (section.key !== 'base') return;
  baseScopeOpen.value = !baseScopeOpen.value;
}

function formatActiveCount(count: number): string {
  return `${count} active`;
}

function displayGroupLabel(group: ParamGroup): string {
  if (group.controls.length > 0 && group.controls.every(isDateTimeControl)) return 'Date range';
  if (group.controls.length > 0 && group.controls.every(isGeoControl)) return 'Area';
  if (group.controls.length > 0 && group.controls.every(isTrackPickerControl)) return 'Selected tracks';
  return group.label;
}

function isAreaGroup(group: ParamGroup): boolean {
  return group.controls.length > 0 && group.controls.every(isGeoControl);
}

function areaSummary(group: ParamGroup): string {
  if (group.activeCount === 0) return 'No area';
  return group.activeCount === 1 ? '1 area' : `${group.activeCount} areas`;
}

function isTrackPickerGroup(group: ParamGroup): boolean {
  return group.controls.length > 0 && group.controls.every(isTrackPickerControl);
}

function trackPickerSummary(group: ParamGroup): string {
  const selectedCount = group.controls.reduce(
    (count, control) => count + parseTrackIdText(stringParam(control.name)).length,
    0
  );
  return selectedCount === 0 ? 'All tracks' : `${selectedCount} selected ${selectedCount === 1 ? 'track' : 'tracks'}`;
}

function isAreaGroupOpen(group: ParamGroup): boolean {
  return expandedAreaGroups.value.has(group.key);
}

function toggleAreaGroup(group: ParamGroup): void {
  const next = new Set(expandedAreaGroups.value);
  if (next.has(group.key)) next.delete(group.key);
  else next.add(group.key);
  expandedAreaGroups.value = next;
}

function areaGroupDomId(section: ParamSection, group: ParamGroup): string {
  return `filter-area-${section.key}-${group.key.replace(/[^a-zA-Z0-9_-]/g, '-')}`;
}

function isGeoParam(paramDefinition: ParamDefinition): boolean {
  return (
    paramDefinition.type === 'GEO_CIRCLE' ||
    paramDefinition.type === 'GEO_RECTANGLE' ||
    paramDefinition.type === 'GEO_POLYGON'
  );
}

function widgetForDefinition(paramDefinition: ParamDefinition): string {
  if (paramDefinition.type === 'DATE_TIME') return 'dateTime';
  if (paramDefinition.type === 'GEO_CIRCLE') return 'geoCircle';
  if (paramDefinition.type === 'GEO_RECTANGLE') return 'geoRectangle';
  if (paramDefinition.type === 'GEO_POLYGON') return 'geoPolygon';
  return 'text';
}

function isTrackPickerControl(control: ParamControl): boolean {
  return control.widget === TRACK_PICKER_WIDGET;
}

function isDateTimeControl(control: ParamControl): boolean {
  return control.widget === 'dateTime' || control.paramDefinition.type === 'DATE_TIME';
}

function isNumberControl(control: ParamControl): boolean {
  return control.widget === 'number';
}

function isGeoControl(control: ParamControl): boolean {
  return (
    control.widget === 'geoCircle' ||
    control.widget === 'geoRectangle' ||
    control.widget === 'geoPolygon' ||
    isGeoParam(control.paramDefinition)
  );
}
</script>

<style scoped>
.filter-detail {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--border-subtle, var(--border-default));
  border-radius: 0.75rem;
  background: var(--surface-glass-heavy, var(--surface-elevated));
}

.filter-detail__empty {
  margin: 0;
  padding: 0.2rem 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.filter-detail__hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
}

.filter-detail__hero-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  border-radius: 0.7rem;
  background: var(--accent-subtle);
  color: var(--accent-text);
  font-size: var(--text-xl-size, 1.35rem);
}

.filter-detail__hero-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.filter-detail__group {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: var(--text-xs-lh);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.filter-detail__title {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-lg-size);
  line-height: var(--text-lg-lh);
  font-weight: 700;
}

.filter-detail__description {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  max-width: 50rem;
}

.filter-detail-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
}

.filter-detail-section__head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 0.75rem;
}

.filter-detail-section__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.12rem;
}

.filter-detail-section__help {
  margin: 0;
}

.filter-detail-param-sections {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  min-width: 0;
}

.filter-detail-param-section {
  min-width: 0;
  overflow: clip;
  border: 1px solid var(--border-subtle, var(--border-default));
  border-radius: 0.5rem;
  background: var(--surface-glass-subtle);
}

.filter-detail-param-section--specific {
  border-color: color-mix(in srgb, var(--accent) 24%, var(--border-subtle));
}

.filter-detail-param-section--base {
  background: var(--surface-glass-heavy);
}

.filter-detail-param-section--single {
  overflow: visible;
  border: 0;
  background: transparent;
}

.filter-detail-param-section--single > .filter-detail-param-section__summary {
  display: none;
}

.filter-detail-param-section--single > .filter-detail-param-section__body {
  padding: 0;
}

.filter-detail-param-section__summary {
  width: 100%;
  min-width: 0;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 0.75rem;
  align-items: center;
  padding: 0.85rem 0.95rem;
  color: inherit;
  text-align: left;
}

.filter-detail-param-section__summary--button {
  border: 0;
  background: transparent;
  cursor: pointer;
  font: inherit;
}

.filter-detail-param-section__summary--button:hover {
  background: var(--surface-hover);
}

.filter-detail-param-section__summary--button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.filter-detail-param-section__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border-radius: 0.45rem;
  background: var(--accent-subtle);
  color: var(--accent-text);
  font-size: var(--text-base-size, 1rem);
}

.filter-detail-param-section--base .filter-detail-param-section__icon {
  background: var(--surface-elevated);
  color: var(--text-secondary);
}

.filter-detail-param-section__copy,
.filter-detail-param-section__title-row {
  min-width: 0;
  display: flex;
}

.filter-detail-param-section__copy {
  flex-direction: column;
  gap: 0.18rem;
}

.filter-detail-param-section__title-row {
  align-items: center;
  gap: 0.45rem;
}

.filter-detail-param-section__title {
  min-width: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 800;
  line-height: var(--text-sm-lh);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-detail-param-section__badge,
.filter-detail-param-section__active,
.filter-detail-param-group__active,
.filter-detail-field__pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 1.25rem;
  border-radius: 999px;
  padding: 0.12rem 0.45rem;
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.filter-detail-param-section__badge {
  color: var(--accent-text);
  background: var(--accent-subtle);
}

.filter-detail-param-section--base .filter-detail-param-section__badge {
  color: var(--text-secondary);
  background: var(--surface-elevated);
}

.filter-detail-param-section__subline {
  min-width: 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 600;
  line-height: var(--text-xs-lh);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-detail-param-section__meta {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 700;
}

.filter-detail-param-section__active,
.filter-detail-param-group__active {
  color: var(--success-text, var(--accent-text));
  background: var(--success-bg, var(--accent-subtle));
}

.filter-detail-param-section__body {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0 0.95rem 1rem;
}

.filter-detail-param-group {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  min-width: 0;
}

.filter-detail-param-group + .filter-detail-param-group {
  padding-top: 1rem;
  border-top: 1px solid var(--border-subtle, var(--border-default));
}

.filter-detail-param-group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  min-height: 1.25rem;
}

.filter-detail-param-group__heading {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.filter-detail-param-group__summary {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 600;
}

.filter-detail-param-group__area-action {
  min-height: 2.75rem;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.65rem;
  border: 1px solid var(--border-medium);
  border-radius: 0.4rem;
  background: var(--surface-glass-heavy);
}

.filter-detail-param-group__area-action:hover {
  background: var(--surface-hover);
  border-color: var(--accent);
}

.filter-detail-param-group__title {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 700;
  line-height: var(--text-sm-lh);
}

.filter-detail__params-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0.9rem;
  min-width: 0;
}

.filter-detail-field {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.filter-detail-field--wide {
  grid-column: 1 / -1;
}

.filter-detail-field__label-row {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 0.35rem;
  flex-wrap: wrap;
}

.filter-detail-field__label {
  display: inline-block;
  min-width: 0;
  overflow-wrap: anywhere;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.filter-detail-field__unit {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 600;
  line-height: var(--text-xs-lh);
}

.filter-detail-field__required {
  color: var(--error);
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: var(--text-xs-lh);
}

.filter-detail-field__pill {
  color: var(--text-muted);
  background: var(--surface-elevated);
}

.filter-detail-field__origin {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1rem;
  height: 1rem;
  margin-left: auto;
  color: var(--text-muted);
  opacity: 0.68;
  font-size: var(--text-xs-size);
}

.filter-detail__full-width {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

.filter-detail__empty-params {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.8rem 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

@media (max-width: 36rem) {
  .filter-detail {
    gap: 0.95rem;
  }

  .filter-detail__hero {
    grid-template-columns: minmax(0, 1fr);
    gap: 0.65rem;
  }

  .filter-detail-param-section__summary {
    padding: 0.75rem;
  }

  .filter-detail-param-section__meta {
    gap: 0.35rem;
  }

  .filter-detail-param-section__body {
    padding: 0 0.75rem 0.85rem;
  }
}

@container (min-width: 44rem) {
  .filter-detail__params-grid {
    grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
  }
}
</style>
