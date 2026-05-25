<template>
  <div class="filter-detail">
    <section class="filter-detail__hero">
      <span class="filter-detail__hero-icon">
        <i :class="groupIcon(filterGroupLabel)"></i>
      </span>
      <div class="filter-detail__hero-copy">
        <span class="filter-detail__group">{{ filterGroupLabel }}</span>
        <h2 class="filter-detail__title">{{ selectedFilterLabel }}</h2>
        <p v-if="filterDescription" class="filter-detail__description">{{ filterDescription }}</p>
      </div>
    </section>

    <section v-if="paramControlCount > 0" class="filter-detail-section">
      <div class="filter-detail-section__head">
        <h3 class="filter-detail-section__title">Parameters</h3>
        <span class="filter-detail-section__count">{{ formatParamCount(paramControlCount) }}</span>
      </div>

      <div class="filter-detail-param-sections">
        <section
          v-for="section in paramSections"
          :key="section.key"
          class="filter-detail-param-section"
          :class="[
            `filter-detail-param-section--${section.tone}`,
            { 'filter-detail-param-section--collapsed': !isParamSectionOpen(section) },
          ]"
        >
          <button
            v-if="section.collapsible"
            type="button"
            class="filter-detail-param-section__summary filter-detail-param-section__summary--button"
            :aria-expanded="isParamSectionOpen(section)"
            @click="toggleParamSection(section)"
          >
            <span class="filter-detail-param-section__icon">
              <i :class="section.icon"></i>
            </span>
            <span class="filter-detail-param-section__copy">
              <span class="filter-detail-param-section__title-row">
                <span class="filter-detail-param-section__title">{{ section.title }}</span>
                <span class="filter-detail-param-section__badge">{{ section.badge }}</span>
              </span>
              <span class="filter-detail-param-section__subline">{{ sectionSummary(section) }}</span>
            </span>
            <span class="filter-detail-param-section__meta">
              <span v-if="section.activeCount > 0" class="filter-detail-param-section__active">
                {{ formatActiveCount(section.activeCount) }}
              </span>
              <i :class="isParamSectionOpen(section) ? 'bi bi-chevron-up' : 'bi bi-chevron-down'"></i>
            </span>
          </button>

          <div v-else class="filter-detail-param-section__summary">
            <span class="filter-detail-param-section__icon">
              <i :class="section.icon"></i>
            </span>
            <span class="filter-detail-param-section__copy">
              <span class="filter-detail-param-section__title-row">
                <span class="filter-detail-param-section__title">{{ section.title }}</span>
                <span class="filter-detail-param-section__badge">{{ section.badge }}</span>
              </span>
              <span class="filter-detail-param-section__subline">{{ sectionSummary(section) }}</span>
            </span>
          </div>

          <div v-show="isParamSectionOpen(section)" class="filter-detail-param-section__body">
            <section v-for="group in section.groups" :key="group.key" class="filter-detail-param-group">
              <div class="filter-detail-param-group__head">
                <h4 class="filter-detail-param-group__title">{{ group.label }}</h4>
                <span v-if="group.activeCount > 0" class="filter-detail-param-group__active">
                  {{ formatActiveCount(group.activeCount) }}
                </span>
              </div>

              <div class="filter-detail__params-grid">
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
                      :optional="control.optional"
                      :origin-title="originDebugTitle(control)"
                      @update:model-value="emit('set-string-param', { name: control.name, value: $event })"
                    />
                  </template>

                  <template v-else-if="isDateTimeControl(control)">
                    <div class="filter-detail-field__label-row">
                      <label class="filter-detail-field__label" :for="control.name">{{ control.label }}</label>
                      <span v-if="control.optional" class="filter-detail-field__pill">Optional</span>
                      <span class="filter-detail-field__origin" :title="originDebugTitle(control)">
                        <i :class="control.relation === 'inherited' ? 'bi bi-diagram-3' : 'bi bi-dot'"></i>
                      </span>
                    </div>
                    <DatePicker
                      :id="control.name"
                      :model-value="dateTimeParam(control.name)"
                      :show-time="true"
                      date-format="dd.mm.yy"
                      hour-format="24"
                      show-seconds
                      placeholder="select a date"
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
                      :optional="control.optional"
                      @start-geo-drawing="emit('start-geo-drawing', $event)"
                      @clear-geo-shape="emit('clear-geo-shape', $event)"
                    />
                  </template>

                  <template v-else>
                    <div class="filter-detail-field__label-row">
                      <label class="filter-detail-field__label" :for="control.name">{{ control.label }}</label>
                      <span v-if="control.unit" class="filter-detail-field__unit">{{ control.unit }}</span>
                      <span v-if="control.optional" class="filter-detail-field__pill">Optional</span>
                      <span class="filter-detail-field__origin" :title="originDebugTitle(control)">
                        <i :class="control.relation === 'inherited' ? 'bi bi-diagram-3' : 'bi bi-dot'"></i>
                      </span>
                    </div>
                    <InputText
                      :id="control.name"
                      :value="stringParam(control.name)"
                      :type="isNumberControl(control) ? 'number' : 'text'"
                      :inputmode="isNumberControl(control) ? 'decimal' : undefined"
                      placeholder="enter a value"
                      class="filter-detail__full-width"
                      @input="onStringInput(control.name, $event)"
                    />
                  </template>
                </div>
              </div>
            </section>
          </div>
        </section>
      </div>
    </section>

    <section v-else class="filter-detail__empty-params">
      <i class="bi bi-sliders"></i>
      <span>No parameters for this filter</span>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { FilterParamsRequest } from '@/components/filter/FilterService';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { ParamDefinition } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import {
  effectiveUiMetadata,
  resolveFilterGroupLabel,
  type FilterParamGroupMetadata,
  type FilterParamMetadata,
} from '@/utils/filterMetadata';
import GeoShapeParam from '@/components/filter/GeoShapeParam.vue';
import TrackIdParam from '@/components/filter/TrackIdParam.vue';

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
  originFilterRef?: string;
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
  badge: string;
  icon: string;
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

const filterInfo = computed((): FilterInfo | undefined => props.selectedFilter?.filterInfo);
const filterGroupLabel = computed((): string =>
  filterInfo.value ? resolveFilterGroupLabel(filterInfo.value) : 'Filter'
);
const selectedFilterLabel = computed((): string => filterInfo.value?.filterConfig?.displayName ?? 'Select a filter');
const filterDescription = computed((): string => filterInfo.value?.filterConfig?.description ?? '');
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
        originFilterRef: metadata.originFilterRef,
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
      title: 'Base scope',
      badge: 'Shared',
      icon: 'bi bi-diagram-3',
      tone: 'base',
      collapsible: selectedControls.length > 0,
      groups: groupControls(inheritedControls),
      count: inheritedControls.length,
      activeCount: countActiveControls(inheritedControls),
    });
  }

  if (selectedControls.length > 0) {
    sections.push({
      key: 'specific',
      title: hasInheritedControls ? 'Filter-specific parameters' : 'Parameters',
      badge: hasInheritedControls ? 'Specific' : 'Selected',
      icon: hasInheritedControls ? 'bi bi-sliders' : 'bi bi-funnel',
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

function onStringInput(name: string, event: Event): void {
  const target = event.target as HTMLInputElement | null;
  emit('set-string-param', { name, value: target?.value ?? '' });
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

function sectionSummary(section: ParamSection): string {
  const groupLabels = section.groups.map((group) => group.label).join(' / ');
  return groupLabels ? `${formatParamCount(section.count)} / ${groupLabels}` : formatParamCount(section.count);
}

function formatParamCount(count: number): string {
  return `${count} ${count === 1 ? 'parameter' : 'parameters'}`;
}

function formatActiveCount(count: number): string {
  return `${count} active`;
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

function originDebugTitle(control: ParamControl): string {
  const relation = control.relation || 'selected';
  const origin = control.originFilterRef || 'unknown origin';
  return `${relation}: ${origin}`;
}

function groupIcon(groupLabel: string): string {
  const normalized = groupLabel.toLowerCase();
  if (normalized.includes('activity')) return 'bi bi-bicycle';
  if (normalized.includes('date') || normalized.includes('time')) return 'bi bi-calendar3';
  if (normalized.includes('quality')) return 'bi bi-shield-exclamation';
  if (normalized.includes('performance')) return 'bi bi-speedometer2';
  if (normalized.includes('people')) return 'bi bi-people';
  if (normalized.includes('core')) return 'bi bi-funnel';
  if (normalized.includes('user')) return 'bi bi-person';
  return 'bi bi-sliders';
}
</script>

<style scoped>
.filter-detail {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
  min-width: 0;
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
  gap: 0.8rem;
}

.filter-detail-section__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
  padding-bottom: 0.35rem;
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
}

.filter-detail-section__title {
  margin: 0;
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: var(--text-xs-lh);
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.filter-detail-section__count {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 600;
  opacity: 0.65;
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
  border-color: color-mix(in srgb, var(--accent, #6366f1) 24%, var(--border-subtle, var(--border-default)));
}

.filter-detail-param-section--base {
  background: var(--surface-glass-heavy);
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
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: var(--text-xs-lh);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.filter-detail-field__unit {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 600;
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
