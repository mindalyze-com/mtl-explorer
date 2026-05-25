import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterEffectiveUiMetadata } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterEffectiveUiMetadata';
import type { FilterGradientMetadata } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterGradientMetadata';
import type { FilterParamGroupMetadata } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterParamGroupMetadata';
import type { FilterParamMetadata } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterParamMetadata';
import {
  FilterConfigEntityColoringStrategyEnum,
  FilterConfigEntityFilterCategoryEnum,
  FilterConfigEntityLegendSortStrategyEnum,
  type FilterConfigEntity,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterConfigEntity';

export type { FilterEffectiveUiMetadata, FilterGradientMetadata, FilterParamGroupMetadata, FilterParamMetadata };

const FILTER_GROUP_FALLBACK = 'Other';
const USER_FILTER_GROUP = 'User';
const DEFAULT_GRADIENT_BUCKET_COUNT = 100;
const GRADIENT_PALETTE_PREFIX = 'GRADIENT_';
export type LegendSortStrategy = FilterConfigEntityLegendSortStrategyEnum;

const FILTER_GROUP_FALLBACK_ORDER = [
  'Core',
  'Activity',
  'Date & Time',
  'People',
  'Performance',
  'Quality',
  USER_FILTER_GROUP,
  FILTER_GROUP_FALLBACK,
];

const LEGEND_SORT_STRATEGY_LABELS: Record<LegendSortStrategy, string> = {
  [FilterConfigEntityLegendSortStrategyEnum.LabelAsc]: 'Name A-Z',
  [FilterConfigEntityLegendSortStrategyEnum.NumericAsc]: 'Lowest first',
  [FilterConfigEntityLegendSortStrategyEnum.CountDesc]: 'Most tracks first',
};

const LEGEND_SORT_STRATEGY_DESCRIPTIONS: Record<LegendSortStrategy, string> = {
  [FilterConfigEntityLegendSortStrategyEnum.LabelAsc]: 'Sort groups alphabetically by their displayed name.',
  [FilterConfigEntityLegendSortStrategyEnum.NumericAsc]: 'Sort numeric buckets from low to high.',
  [FilterConfigEntityLegendSortStrategyEnum.CountDesc]: 'Sort groups by matching track count, largest first.',
};

type FilterInfoWithEffectiveMetadata = FilterInfo & {
  effectiveUiMetadata?: FilterEffectiveUiMetadata;
};

type FilterMetadataSource = FilterInfo | FilterConfigEntity | undefined | null;

export type FilterOptionGroup = {
  label: string;
  items: FilterInfo[];
  displayOrder: number;
};

export type LabeledLegendEntry = {
  group: string;
  label: string;
  color: string;
  count: number;
};

type ColorPaletteLike = {
  domain3?: string;
  pLabel?: string;
  pColors?: string[];
  isEmptyColorPalette: () => boolean;
  getColorForGroup: (group: string, countForStatistics?: boolean) => string;
  getColorForGroupAtIndex: (group: string, colorIndex: number, countForStatistics?: boolean) => string;
};

function compareText(a: string, b: string): number {
  return a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' });
}

function groupSortIndex(label: string): number {
  const index = FILTER_GROUP_FALLBACK_ORDER.indexOf(label);
  return index >= 0 ? index : FILTER_GROUP_FALLBACK_ORDER.length;
}

function filterDisplayOrder(filterInfo: FilterInfo): number {
  return filterInfo.filterConfig?.displayOrder ?? Number.MAX_SAFE_INTEGER;
}

function isFilterInfo(value: FilterMetadataSource): value is FilterInfo {
  return Boolean(value && typeof value === 'object' && 'filterConfig' in value);
}

function sourceFilterConfig(source?: FilterMetadataSource): FilterConfigEntity | undefined {
  return isFilterInfo(source) ? source.filterConfig : (source ?? undefined);
}

export function effectiveUiMetadata(filterInfo?: FilterInfo | null): FilterEffectiveUiMetadata {
  return (filterInfo as FilterInfoWithEffectiveMetadata | null | undefined)?.effectiveUiMetadata ?? {};
}

function sourceEffectiveUiMetadata(source?: FilterMetadataSource): FilterEffectiveUiMetadata {
  return isFilterInfo(source) ? effectiveUiMetadata(source) : {};
}

export function effectiveParamMetadataEntries(filterInfo?: FilterInfo | null): Array<[string, FilterParamMetadata]> {
  return Object.entries(effectiveUiMetadata(filterInfo).params ?? {});
}

export function effectiveParamMetadata(
  filterInfo: FilterInfo | null | undefined,
  paramName: string
): FilterParamMetadata | undefined {
  return effectiveUiMetadata(filterInfo).params?.[paramName];
}

export function gradientBucketCount(source?: FilterMetadataSource): number {
  const metadataCount = sourceEffectiveUiMetadata(source).result?.gradient?.bucketCount;
  return Number.isFinite(metadataCount) && metadataCount! > 1 ? metadataCount! : DEFAULT_GRADIENT_BUCKET_COUNT;
}

export function optionalFilterParamNames(filterInfo?: FilterInfo | null): string[] {
  const optionalParams = effectiveParamMetadataEntries(filterInfo)
    .filter(([, metadata]) => metadata.optional === true)
    .map(([paramName]) => paramName);
  if (optionalParams.length === 0) return [];

  return Array.from(new Set(optionalParams.map((paramName) => String(paramName ?? '').trim()).filter(Boolean))).sort(
    compareText
  );
}

function normalizedGroup(group: unknown): string {
  return String(group ?? '').trim();
}

export function normalizeLegendSortStrategy(value: unknown): LegendSortStrategy | undefined {
  const strategies = Object.values(FilterConfigEntityLegendSortStrategyEnum);
  return strategies.includes(value as LegendSortStrategy) ? (value as LegendSortStrategy) : undefined;
}

export function legendSortStrategyLabel(strategy?: LegendSortStrategy | null): string {
  return strategy ? LEGEND_SORT_STRATEGY_LABELS[strategy] : 'SQL result order';
}

export function legendSortStrategyDescription(strategy?: LegendSortStrategy | null): string {
  return strategy
    ? LEGEND_SORT_STRATEGY_DESCRIPTIONS[strategy]
    : 'Keeps the first-seen group order returned by the filter SQL query.';
}

export function resolveFilterGroupLabel(filterInfo: FilterInfo): string {
  const filterConfig = filterInfo.filterConfig;
  const explicitGroup = filterConfig?.filterGroup?.trim();
  if (explicitGroup) return explicitGroup;
  if (filterConfig?.filterCategory === FilterConfigEntityFilterCategoryEnum.User) return USER_FILTER_GROUP;
  return FILTER_GROUP_FALLBACK;
}

export function sortFilterInfos(filters: FilterInfo[]): FilterInfo[] {
  return [...filters].sort((a, b) => {
    const orderDelta = filterDisplayOrder(a) - filterDisplayOrder(b);
    if (orderDelta !== 0) return orderDelta;
    return compareText(a.filterConfig?.displayName ?? '', b.filterConfig?.displayName ?? '');
  });
}

export function buildFilterOptionGroups(filters: FilterInfo[]): FilterOptionGroup[] {
  const grouped = new Map<string, FilterInfo[]>();

  for (const filterInfo of filters) {
    const label = resolveFilterGroupLabel(filterInfo);
    if (!grouped.has(label)) grouped.set(label, []);
    grouped.get(label)!.push(filterInfo);
  }

  return Array.from(grouped.entries())
    .map(([label, rawItems]) => {
      const items = sortFilterInfos(rawItems);
      return {
        label,
        items,
        displayOrder: Math.min(...items.map(filterDisplayOrder)),
      };
    })
    .sort((a, b) => {
      if (a.displayOrder !== b.displayOrder) {
        return a.displayOrder < b.displayOrder ? -1 : 1;
      }
      const fallbackDelta = groupSortIndex(a.label) - groupSortIndex(b.label);
      if (fallbackDelta !== 0) return fallbackDelta;
      return compareText(a.label, b.label);
    });
}

function normalizeCatalogSearchText(value: unknown): string {
  return String(value ?? '')
    .trim()
    .toLocaleLowerCase()
    .replace(/\s+/g, ' ');
}

export function filterOptionGroupItemCount(groups: FilterOptionGroup[]): number {
  return groups.reduce((count, group) => count + group.items.length, 0);
}

export function filterMatchesCatalogQuery(filterInfo: FilterInfo, groupLabel: string, query: string): boolean {
  const normalizedQuery = normalizeCatalogSearchText(query);
  if (!normalizedQuery) return true;

  const filterConfig = filterInfo.filterConfig;
  const searchableText = normalizeCatalogSearchText(
    [groupLabel, filterConfig?.displayName, filterConfig?.description, filterConfig?.filterName].join(' ')
  );

  return normalizedQuery.split(' ').every((term) => searchableText.includes(term));
}

export function filterOptionGroupsByCatalogSearch(
  groups: FilterOptionGroup[],
  query: string,
  activeGroupLabel: string | null = null
): FilterOptionGroup[] {
  return groups
    .filter((group) => activeGroupLabel == null || group.label === activeGroupLabel)
    .map((group) => ({
      ...group,
      items: group.items.filter((filterInfo) => filterMatchesCatalogQuery(filterInfo, group.label, query)),
    }))
    .filter((group) => group.items.length > 0);
}

export function isSameFilterInfo(left?: FilterInfo | null, right?: FilterInfo | null): boolean {
  const leftConfig = left?.filterConfig;
  const rightConfig = right?.filterConfig;
  if (!leftConfig || !rightConfig) return false;
  if (leftConfig.id != null || rightConfig.id != null) return leftConfig.id === rightConfig.id;
  return leftConfig.filterName === rightConfig.filterName && leftConfig.filterDomain === rightConfig.filterDomain;
}

export function isSequentialGradientFilter(filterConfig?: FilterConfigEntity): boolean {
  return filterConfig?.coloringStrategy === FilterConfigEntityColoringStrategyEnum.SequentialGradient;
}

export function isGradientPalette(palette?: ColorPaletteLike | null): boolean {
  if (!palette || palette.isEmptyColorPalette()) return false;
  const domain = palette.domain3 ?? '';
  const label = palette.pLabel ?? '';
  return domain.startsWith(GRADIENT_PALETTE_PREFIX) || label.toLowerCase().includes('gradient');
}

function hasUsablePalette(palette?: ColorPaletteLike | null): boolean {
  return Boolean(palette && !palette.isEmptyColorPalette());
}

export function shouldUseGradientColorScale(
  filterConfig?: FilterConfigEntity,
  palette?: ColorPaletteLike | null
): boolean {
  return hasUsablePalette(palette) && (isSequentialGradientFilter(filterConfig) || isGradientPalette(palette));
}

export function shouldUseCompactGradientLegend(
  filterConfig?: FilterConfigEntity,
  palette?: ColorPaletteLike | null,
  legendSortStrategyOverride?: unknown
): boolean {
  if (!shouldUseGradientColorScale(filterConfig, palette)) return false;

  const sortStrategy = normalizeLegendSortStrategy(legendSortStrategyOverride);
  if (sortStrategy === FilterConfigEntityLegendSortStrategyEnum.NumericAsc) return true;
  if (sortStrategy) return false;

  return isGradientPalette(palette);
}

export function findPreferredPalette<T extends ColorPaletteLike>(
  palettes: T[],
  filterConfig?: FilterConfigEntity
): T | undefined {
  const preferred = filterConfig?.preferredPalette;
  if (preferred) {
    const exactMatch = palettes.find((palette) => palette.domain3 === preferred && !palette.isEmptyColorPalette());
    if (exactMatch) return exactMatch;
  }

  if (isSequentialGradientFilter(filterConfig)) {
    return palettes.find((palette) => isGradientPalette(palette));
  }

  return undefined;
}

export function parseNumericBucket(group: unknown): number | null {
  const text = normalizedGroup(group);
  if (!text) return null;

  const directValue = Number(text);
  if (Number.isFinite(directValue)) return Math.round(directValue);

  const embeddedNumber = text.match(/(?:^|[_\s-])(\d{1,3})(?:[_\s-]|$)/);
  if (!embeddedNumber) return null;

  const parsed = Number(embeddedNumber[1]);
  return Number.isFinite(parsed) ? parsed : null;
}

function formatBucket(bucket: number, bucketCount: number): string {
  const maxBucket = Math.max(bucketCount - 1, 0);
  const width = String(maxBucket).length;
  return String(bucket).padStart(width, '0');
}

export function formatFilterGroupLabel(group: unknown, source?: FilterMetadataSource): string {
  const groupText = normalizedGroup(group);
  if (!groupText) return '';
  const filterConfig = sourceFilterConfig(source);

  const bucket = parseNumericBucket(groupText);
  if (bucket == null || !filterConfig?.groupLabelTemplate) return groupText;

  const bucketCount = gradientBucketCount(source);
  const formattedBucket = formatBucket(bucket, bucketCount);
  return filterConfig.groupLabelTemplate
    .replace(/\{bucket\}/g, formattedBucket)
    .replace(/\{bucketNumber\}/g, String(bucket));
}

export function compareFilterGroups(
  a: unknown,
  b: unknown,
  _filterConfig?: FilterConfigEntity,
  legendSortStrategyOverride?: unknown
): number {
  const left = normalizedGroup(a);
  const right = normalizedGroup(b);
  const sortStrategy = normalizeLegendSortStrategy(legendSortStrategyOverride);
  if (!sortStrategy) return 0;

  if (sortStrategy === FilterConfigEntityLegendSortStrategyEnum.NumericAsc) {
    const leftBucket = parseNumericBucket(left);
    const rightBucket = parseNumericBucket(right);
    if (leftBucket != null && rightBucket != null && leftBucket !== rightBucket) {
      return leftBucket - rightBucket;
    }
  }

  return compareText(left, right);
}

export function compareLegendEntries(
  a: Pick<LabeledLegendEntry, 'group' | 'count'>,
  b: Pick<LabeledLegendEntry, 'group' | 'count'>,
  filterConfig?: FilterConfigEntity,
  legendSortStrategyOverride?: unknown
): number {
  const sortStrategy = normalizeLegendSortStrategy(legendSortStrategyOverride);
  if (!sortStrategy) return 0;

  if (sortStrategy === FilterConfigEntityLegendSortStrategyEnum.CountDesc) {
    const countDelta = b.count - a.count;
    if (countDelta !== 0) return countDelta;
  }

  return compareFilterGroups(a.group, b.group, filterConfig, legendSortStrategyOverride);
}

export function sortFilterGroups(
  groups: Iterable<string>,
  filterConfig?: FilterConfigEntity,
  legendSortStrategyOverride?: unknown
): string[] {
  return Array.from(new Set(Array.from(groups).map(normalizedGroup).filter(Boolean))).sort((a, b) =>
    compareFilterGroups(a, b, filterConfig, legendSortStrategyOverride)
  );
}

function colorIndexForBucket(bucket: number, colorCount: number, bucketCount: number): number {
  if (colorCount <= 1 || bucketCount <= 1) return 0;
  const maxBucket = bucketCount - 1;
  const clampedBucket = Math.max(0, Math.min(maxBucket, bucket));
  return Math.round((clampedBucket / maxBucket) * (colorCount - 1));
}

export function colorForFilterGroup(
  palette: ColorPaletteLike,
  group: unknown,
  source?: FilterMetadataSource,
  countForStatistics = false
): string {
  const groupText = normalizedGroup(group);
  const bucket = parseNumericBucket(groupText);
  const filterConfig = sourceFilterConfig(source);

  if (shouldUseGradientColorScale(filterConfig, palette) && bucket != null) {
    const colorCount = palette.pColors?.length ?? 0;
    const colorIndex = colorIndexForBucket(bucket, colorCount, gradientBucketCount(source));
    return palette.getColorForGroupAtIndex(groupText, colorIndex, countForStatistics);
  }

  return palette.getColorForGroup(groupText, countForStatistics);
}

export function gradientFilterHelpText(source?: FilterMetadataSource): string {
  const metadata = sourceEffectiveUiMetadata(source).result?.gradient ?? {};
  const metric = metadata.metricLabel || 'Metric';
  const unit = metadata.metricUnit ? ` (${metadata.metricUnit})` : '';
  return `${metric}${unit} is bucketed into ${gradientBucketCount(source)} ordered percentiles. Use a Gradient palette so low and high values read as a continuous scale.`;
}

export function parseFilterRef(ref?: string | null): { filterDomain: string; filterName: string } | null {
  const match = String(ref ?? '').match(/^\/([^/]+)\/([^/]+)$/);
  if (!match) return null;
  return { filterDomain: match[1], filterName: match[2] };
}
