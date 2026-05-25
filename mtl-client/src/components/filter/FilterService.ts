import { isEmptyOrNil } from '@/utils/Utils';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterParamsRequest } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterParamsRequest';
import type { GeoCircle } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/GeoCircle';
import type { GeoRectangle } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/GeoRectangle';
import type { GeoPolygon } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/GeoPolygon';
import type { FilterConfigEntityLegendSortStrategyEnum } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterConfigEntity';
import _ from 'lodash'; // Import the entire lodash library
import { fetchFilterInfo, getServerBuildInfo } from '@/utils/ServiceHelper';
import { ColorPalette } from '@/components/filter/ColorPalette';
import { markRaw } from 'vue';
import { hasCompleteStringParamsForDefinitions, pruneFilterParamsForDefinitions } from '@/utils/filterParams';

/**
 * @deprecated Use FilterParamsRequest instead. Kept for backward compat with localStorage migration.
 */
export type FilterParams = {
  [key: string]: string; // Both keys and values are strings
};
import type { ConfigEntity } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ConfigEntity';

export type { FilterParamsRequest, GeoCircle, GeoRectangle, GeoPolygon };

export class ClientFilterConfig {
  filterInfo!: FilterInfo;
  filterParams!: FilterParamsRequest;
  palette!: ColorPalette;
  legendSortStrategy?: FilterConfigEntityLegendSortStrategyEnum | null;

  static of(
    filterInfo: FilterInfo,
    filterParams?: FilterParamsRequest,
    palette?: ConfigEntity | ColorPalette | null,
    legendSortStrategy?: FilterConfigEntityLegendSortStrategyEnum | null
  ): ClientFilterConfig {
    const inst = new ClientFilterConfig();
    inst.filterInfo = filterInfo;
    inst.filterParams = filterParams || {};
    inst.palette = markRaw(ColorPalette.of(palette || {}));
    inst.legendSortStrategy = legendSortStrategy ?? null;
    return inst;
  }
}

export class FilterService {
  private static readonly FILTER_DOMAIN_GPS: string = 'GPS_TRACK';
  private static defaultGpsFilterName: string | null = null;
  private static defaultGpsFilterNamePromise: Promise<string> | null = null;
  private static filterInfoHydrationCache = new Map<string, Promise<FilterInfo>>();

  private static async getDefaultGpsFilterName(): Promise<string> {
    if (!this.defaultGpsFilterNamePromise) {
      this.defaultGpsFilterNamePromise = getServerBuildInfo()
        .then((info) => {
          this.defaultGpsFilterName = info.defaultGpsTrackFilterName || 'SmartBaseFilter';
          return this.defaultGpsFilterName;
        })
        .catch(() => {
          this.defaultGpsFilterName = 'SmartBaseFilter';
          return this.defaultGpsFilterName;
        });
    }
    return this.defaultGpsFilterNamePromise;
  }

  private static readonly STORAGE_KEY_CLIENT_FILTER_CONFIG = 'clientFilterConfig';

  private static hasFilterUiMetadata(filterInfo?: FilterInfo | null): boolean {
    const filterConfig = filterInfo?.filterConfig;
    const effectiveUiMetadata = (filterInfo as (FilterInfo & { effectiveUiMetadata?: unknown }) | null | undefined)
      ?.effectiveUiMetadata;
    return Boolean(filterConfig?.groupSemantics && filterConfig?.coloringStrategy && effectiveUiMetadata);
  }

  private static fetchFilterInfoCached(filterDomain: string, filterName: string): Promise<FilterInfo> {
    const key = `${filterDomain}:${filterName}`;
    if (!this.filterInfoHydrationCache.has(key)) {
      this.filterInfoHydrationCache.set(key, fetchFilterInfo(filterDomain, filterName));
    }
    return this.filterInfoHydrationCache.get(key)!;
  }

  private static async hydrateFilterInfoMetadata(
    clientFilterConfig: ClientFilterConfig,
    defaultFilterName: string
  ): Promise<boolean> {
    if (this.hasFilterUiMetadata(clientFilterConfig.filterInfo)) return false;

    const filterConfig = clientFilterConfig.filterInfo?.filterConfig;
    const filterDomain = String(filterConfig?.filterDomain || this.FILTER_DOMAIN_GPS);
    const filterName = String(filterConfig?.filterName || defaultFilterName);
    if (!filterName) return false;

    try {
      clientFilterConfig.filterInfo = await this.fetchFilterInfoCached(filterDomain, filterName);
      return true;
    } catch (error) {
      console.warn('FilterService: keeping stored filter metadata because refresh failed', error);
      return false;
    }
  }

  // General-purpose method to save an object to localStorage
  private static saveToLocalStorage<T>(key: string, object: T): void {
    if (object !== null && object !== undefined) {
      const seen = new WeakSet();
      const jsonObject = JSON.stringify(object, (_key, value) => {
        if (typeof value === 'object' && value !== null) {
          if (value instanceof Map || value instanceof Set) return undefined;
          if (seen.has(value)) return undefined;
          seen.add(value);
        }
        return value;
      });
      localStorage.setItem(key, jsonObject);
    }
  }

  // General-purpose method to load an object from localStorage
  private static loadFromLocalStorage<T>(key: string): T | null {
    const jsonString = localStorage.getItem(key);
    if (isEmptyOrNil(jsonString) || jsonString == null) {
      return null;
    }
    return JSON.parse(jsonString) as T;
  }

  static saveClientFilterConfig(clientFilterConfig: ClientFilterConfig | null): void {
    // Convert the FilterInfo object into JSON before saving
    this.saveToLocalStorage(this.STORAGE_KEY_CLIENT_FILTER_CONFIG, clientFilterConfig);
  }

  static async loadClientFilterConfig(): Promise<ClientFilterConfig> {
    let clientFilterConfig = this.loadFromLocalStorage<ClientFilterConfig>(this.STORAGE_KEY_CLIENT_FILTER_CONFIG);
    const defaultFilter = await this.getDefaultGpsFilterName();

    if (!clientFilterConfig) {
      // If there's nothing in localStorage, fetch the default filter info
      const fresh = new ClientFilterConfig();
      fresh.filterInfo = await fetchFilterInfo(FilterService.FILTER_DOMAIN_GPS, defaultFilter);
      fresh.filterParams = {};
      fresh.palette = ColorPalette.of(undefined);
      clientFilterConfig = fresh;
      this.saveClientFilterConfig(clientFilterConfig); // Save the fetched filter info to localStorage
    }

    const filterInfoHydrated = await this.hydrateFilterInfoMetadata(clientFilterConfig, defaultFilter);

    // Migrate old flat FilterParams format to FilterParamsRequest
    clientFilterConfig.filterParams = pruneFilterParamsForDefinitions(
      FilterService.migrateFilterParams(clientFilterConfig.filterParams),
      clientFilterConfig.filterInfo?.paramDefinitions || []
    );

    if (
      !hasCompleteStringParamsForDefinitions(
        clientFilterConfig.filterParams,
        clientFilterConfig.filterInfo?.paramDefinitions || [],
        clientFilterConfig.filterInfo
      )
    ) {
      clientFilterConfig.filterInfo = await fetchFilterInfo(FilterService.FILTER_DOMAIN_GPS, defaultFilter);
      clientFilterConfig.filterParams = {};
      clientFilterConfig.palette = ColorPalette.of(undefined);
      clientFilterConfig.legendSortStrategy = null;
      this.saveClientFilterConfig(clientFilterConfig);
      return clientFilterConfig;
    }

    // Hack: Manually inject a proper object for ColorPalette
    clientFilterConfig.palette = ColorPalette.of(clientFilterConfig.palette);

    if (filterInfoHydrated) {
      this.saveClientFilterConfig(clientFilterConfig);
    }

    return clientFilterConfig;
  }

  /**
   * Migrate old flat {key: value} FilterParams to the new FilterParamsRequest structure.
   * Detects old format by checking if any top-level key is NOT a known FilterParamsRequest field.
   */
  static migrateFilterParams(params: unknown): FilterParamsRequest {
    if (!params || typeof params !== 'object') return {};
    // Already new format: has at least one recognized field and no unrecognized ones
    const knownFields = new Set(['stringParams', 'dateTimeParams', 'geoCircles', 'geoRectangles', 'geoPolygons']);
    const rawParams = params as Record<string, unknown>;
    const keys = Object.keys(rawParams);
    if (keys.length === 0) return {};
    const hasOnlyKnownFields = keys.every((k) => knownFields.has(k));
    if (hasOnlyKnownFields) return params as FilterParamsRequest;
    // Old format: flat map with keys like DATE_TIME_FROM, etc.
    const result: FilterParamsRequest = { stringParams: {}, dateTimeParams: {} };
    for (const [key, value] of Object.entries(rawParams)) {
      if (knownFields.has(key)) continue; // skip if it's a new-format field mixed in
      if (key.startsWith('DATE_TIME')) {
        result.dateTimeParams![key] = String(value ?? '');
      } else {
        result.stringParams![key] = String(value ?? '');
      }
    }
    return result;
  }

  private static hasColorPalette(clientFilterConfig: { palette?: ColorPalette | null } | null | undefined): boolean {
    const palette = clientFilterConfig?.palette;
    if (!palette) return false;
    if (typeof palette.isEmptyColorPalette === 'function') {
      return !palette.isEmptyColorPalette();
    }
    const paletteLike = palette as Partial<ColorPalette>;
    return Boolean(paletteLike.id && Array.isArray(paletteLike.pColors) && paletteLike.pColors.length > 0);
  }

  static isStandardFilterWithStandardParams(
    clientFilterConfig: { filterInfo?: FilterInfo; filterParams?: FilterParamsRequest } | null | undefined
  ): boolean {
    const filterConfig = clientFilterConfig?.filterInfo?.filterConfig;
    const filterParams = clientFilterConfig?.filterParams;
    const isStandardFilter =
      (this.defaultGpsFilterName || 'SmartBaseFilter') === filterConfig?.filterName &&
      FilterService.FILTER_DOMAIN_GPS === filterConfig?.filterDomain;
    const isStandardParams =
      _.isEmpty(filterParams) ||
      ((!filterParams.dateTimeParams ||
        (filterParams.dateTimeParams.DATE_TIME_FROM == null && filterParams.dateTimeParams.DATE_TIME_TO == null)) &&
        _.isEmpty(filterParams.stringParams) &&
        _.isEmpty(filterParams.geoCircles) &&
        _.isEmpty(filterParams.geoRectangles) &&
        _.isEmpty(filterParams.geoPolygons));
    return isStandardFilter && isStandardParams;
  }

  static hasActiveFilterConfig(
    clientFilterConfig:
      | { filterInfo?: FilterInfo; filterParams?: FilterParamsRequest; palette?: ColorPalette | null }
      | null
      | undefined
  ): boolean {
    if (
      !hasCompleteStringParamsForDefinitions(
        clientFilterConfig?.filterParams,
        clientFilterConfig?.filterInfo?.paramDefinitions || [],
        clientFilterConfig?.filterInfo
      )
    ) {
      return false;
    }
    return !this.isStandardFilterWithStandardParams(clientFilterConfig) || this.hasColorPalette(clientFilterConfig);
  }
}
