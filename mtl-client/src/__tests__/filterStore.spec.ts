import { describe, expect, it, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { ClientFilterConfig } from '@/components/filter/FilterService';

type MockFilterConfig = {
  filterInfo?: { filterConfig?: { filterName?: string; filterDomain?: string } };
  filterParams?: { dateTimeParams?: Record<string, unknown> } & Record<string, unknown>;
  palette?: { id?: number; pColors?: string[] } & Record<string, unknown>;
};

// Mock FilterService BEFORE importing the store (which transitively imports it).
// The real FilterService transitively pulls in modules that require browser
// globals (highcharts/highlight.js Vue plugin), so we replace the whole
// module with a self-contained fake.
let __stored: ClientFilterConfig | null = null;
const { fetchResolveFilterMock } = vi.hoisted(() => ({
  fetchResolveFilterMock: vi.fn(),
}));
const __reset = () => {
  __stored = null;
};

vi.mock('@/components/filter/FilterService', () => ({
  ClientFilterConfig: class {
    static of(filterInfo: unknown, filterParams: unknown, palette: unknown) {
      return { filterInfo, filterParams, palette };
    }
  },
  FilterService: {
    loadClientFilterConfig: vi.fn(async () => {
      if (__stored) return __stored;
      __stored = {
        filterInfo: { filterConfig: { filterName: 'SmartBaseFilter', filterDomain: 'GPS_TRACK' } },
        filterParams: {},
        palette: {},
      };
      return __stored;
    }),
    saveClientFilterConfig: vi.fn((cfg: ClientFilterConfig) => {
      __stored = cfg;
    }),
    isStandardFilterWithStandardParams: vi.fn((cfg: MockFilterConfig | null | undefined) => {
      const fc = cfg?.filterInfo?.filterConfig;
      const fp = cfg?.filterParams;
      const isStdFilter = fc?.filterName === 'SmartBaseFilter' && fc?.filterDomain === 'GPS_TRACK';
      const isStdParams =
        !fp || Object.keys(fp).length === 0 || (!fp.dateTimeParams?.DATE_TIME_FROM && !fp.dateTimeParams?.DATE_TIME_TO);
      return isStdFilter && isStdParams;
    }),
    hasActiveFilterConfig: vi.fn((cfg: MockFilterConfig | null | undefined) => {
      const fc = cfg?.filterInfo?.filterConfig;
      const fp = cfg?.filterParams;
      const isStdFilter = fc?.filterName === 'SmartBaseFilter' && fc?.filterDomain === 'GPS_TRACK';
      const isStdParams =
        !fp || Object.keys(fp).length === 0 || (!fp.dateTimeParams?.DATE_TIME_FROM && !fp.dateTimeParams?.DATE_TIME_TO);
      const hasPalette = Boolean(cfg?.palette?.id && cfg?.palette?.pColors?.length);
      return !(isStdFilter && isStdParams) || hasPalette;
    }),
  },
}));

vi.mock('@/utils/filterApi', () => ({
  fetchResolveFilter: fetchResolveFilterMock,
}));

import { useFilterStore } from '@/stores/filterStore';
import { FilterService } from '@/components/filter/FilterService';
import type { FilterResult } from '@/types/filter';
import { useLocale } from '@/composables/useLocale';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';

describe('useFilterStore', () => {
  beforeEach(async () => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    __reset();
  });

  it('isStandard defaults to true before load', () => {
    const store = useFilterStore();
    expect(store.config).toBeNull();
    expect(store.activeResult).toBeNull();
    expect(store.isStandard).toBe(true);
    expect(store.isActive).toBe(false);
  });

  it('ensureLoaded calls FilterService once and caches', async () => {
    const store = useFilterStore();
    const a = await store.ensureLoaded();
    const b = await store.ensureLoaded();
    expect(a).toBe(b);
    expect(FilterService.loadClientFilterConfig).toHaveBeenCalledTimes(1);
  });

  it('reacts to hydrated and updated active filter identities', async () => {
    __stored = {
      filterInfo: {
        filterConfig: {
          displayName: 'Tracks by year',
          filterName: 'TracksByYear',
          filterDomain: 'GPS_TRACK',
        },
      },
      filterParams: {},
      palette: {},
    } as ClientFilterConfig;
    const store = useFilterStore();

    expect(store.activeIdentity).toBe('');
    await store.ensureLoaded();
    expect(store.activeIdentity).toBe('Tracks by year');

    store.save({
      filterInfo: {
        filterConfig: {
          displayName: 'Activities by keyword',
          filterName: 'KeywordSearch',
          filterDomain: 'GPS_TRACK',
        },
        paramDefinitions: [{ name: 'SEARCH_WORD' }],
      },
      filterParams: { stringParams: { SEARCH_WORD: 'Synthetic' } },
      palette: {},
    } as ClientFilterConfig);

    expect(store.activeIdentity).toBe('Activities by keyword · Synthetic');
  });

  it('reacts to measurement changes in a numeric active filter identity', () => {
    const measurementPreference = useMeasurementSystem();
    const localePreference = useLocale();
    const previousMeasurementSystem = measurementPreference.measurementSystem.value;
    const previousLocale = localePreference.formatLocale.value;
    const store = useFilterStore();

    try {
      localePreference.setLocale('de-DE');
      measurementPreference.setMeasurementSystem('US_CUSTOMARY');
      store.save({
        filterInfo: {
          filterConfig: {
            displayName: 'Tracks by distance (gradient)',
            filterName: 'TracksByDistanceGradient',
            filterDomain: 'GPS_TRACK',
          },
          paramDefinitions: [{ name: 'DISTANCE_MAX_KM' }],
          effectiveUiMetadata: {
            params: {
              DISTANCE_MAX_KM: { widget: 'number', unit: 'km' },
            },
          },
        },
        filterParams: { stringParams: { DISTANCE_MAX_KM: '16.09344' } },
        palette: {},
      } as ClientFilterConfig);

      expect(store.activeIdentity).toBe('Tracks by distance (gradient) · 10 mi');

      measurementPreference.setMeasurementSystem('METRIC');
      expect(store.activeIdentity).toBe('Tracks by distance (gradient) · 16,09 km');
    } finally {
      measurementPreference.setMeasurementSystem(previousMeasurementSystem);
      localePreference.setLocale(previousLocale);
    }
  });

  it('ensureLoaded(true) forces a re-fetch', async () => {
    const store = useFilterStore();
    await store.ensureLoaded();
    await store.ensureLoaded(true);
    expect(FilterService.loadClientFilterConfig).toHaveBeenCalledTimes(2);
  });

  it('save() persists and updates the reactive ref', () => {
    const store = useFilterStore();
    const cfg = {
      filterInfo: { filterConfig: { filterName: 'X', filterDomain: 'Y' } },
      filterParams: {},
      palette: {},
    } as ClientFilterConfig;
    expect(store.trackSetRevision).toBe(0);
    store.save(cfg);
    expect(FilterService.saveClientFilterConfig).toHaveBeenCalledWith(cfg);
    expect(store.config).toBe(cfg);
    expect(store.activeResult).toBeNull();
    expect(store.isStandard).toBe(false);
    expect(store.isActive).toBe(true);
    expect(store.trackSetRevision).toBe(1);
  });

  it('can persist style-only changes without marking the track set stale', () => {
    const store = useFilterStore();
    const cfg = {
      filterInfo: { filterConfig: { filterName: 'SmartBaseFilter', filterDomain: 'GPS_TRACK' } },
      filterParams: {},
      palette: { id: 1, pColors: ['#123456'] },
    } as ClientFilterConfig;

    store.save(cfg, { trackSetChanged: false });

    expect(FilterService.saveClientFilterConfig).toHaveBeenCalledWith(cfg);
    expect(store.config).toBe(cfg);
    expect(store.trackSetRevision).toBe(0);
  });

  it('applyResolvedFilter persists config and exposes the active result/request', async () => {
    const store = useFilterStore();
    const cfg = {
      filterInfo: { filterConfig: { filterName: 'NonMotorized', filterDomain: 'GPS_TRACK' } },
      filterParams: {
        stringParams: { ACTIVITY: 'human' },
        resultGroupSelection: { includedGroups: [{ value: 'WALKING' }] },
      },
      palette: {},
    } as ClientFilterConfig;
    const result: FilterResult = {
      trackVersions: new Map([[1, 7]]),
      filterGroups: new Map([[1, 'WALKING']]),
      standardFilterCount: 10,
      groupingAvailable: true,
      availableGroups: [{ key: { value: 'WALKING' }, count: 1 }],
      preGroupSelectionCount: 4,
    };

    expect(store.trackSetRevision).toBe(0);
    store.applyResolvedFilter(cfg, result);

    expect(FilterService.saveClientFilterConfig).toHaveBeenCalledWith(cfg);
    expect(store.config).toBe(cfg);
    expect(store.activeResult).toBe(result);
    expect(store.trackSetRevision).toBe(1);
    expect(store.activeFilterRequest).toEqual({
      filterName: 'NonMotorized',
      filterParams: {
        stringParams: { ACTIVITY: 'human' },
        resultGroupSelection: { includedGroups: [{ value: 'WALKING' }] },
      },
      resolvedTrackIds: [1],
    });
    await expect(store.getActiveFilterRequest()).resolves.toEqual({
      filterName: 'NonMotorized',
      filterParams: {
        stringParams: { ACTIVITY: 'human' },
        resultGroupSelection: { includedGroups: [{ value: 'WALKING' }] },
      },
      resolvedTrackIds: [1],
    });
  });

  it.each([
    { transition: 'import', beforeIds: [], afterIds: [1, 2, 3, 4, 5] },
    { transition: 'deletion', beforeIds: [1, 2, 3, 4, 5], afterIds: [1, 3, 5] },
  ])('re-resolves cached filter IDs after a $transition freshness change', async ({ beforeIds, afterIds }) => {
    const store = useFilterStore();
    const cfg = {
      filterInfo: { filterConfig: { id: 7, filterName: 'SmartBaseFilter', filterDomain: 'GPS_TRACK' } },
      filterParams: {},
      palette: {},
    } as ClientFilterConfig;
    store.applyResolvedFilter(cfg, {
      trackVersions: new Map(beforeIds.map((id) => [id, 1])),
      filterGroups: new Map(),
      standardFilterCount: beforeIds.length,
    });
    const refreshedResult = {
      queryResult: {
        resultEntries: afterIds.map((id) => ({ id })),
        standardFilterCount: afterIds.length,
      },
      filterConfigId: 7,
      trackVersions: new Map(afterIds.map((id) => [id, 2])),
      filterGroups: new Map(),
      standardFilterCount: afterIds.length,
    };
    fetchResolveFilterMock.mockResolvedValueOnce(refreshedResult);
    const revisionBeforeRefresh = store.trackSetRevision;

    await expect(store.refreshResolvedFilter()).resolves.toBe(refreshedResult);

    expect(fetchResolveFilterMock).toHaveBeenCalledWith(7, {}, false);
    expect(store.activeResult).toBe(refreshedResult);
    expect(store.activeFilterRequest?.resolvedTrackIds).toEqual(afterIds);
    expect(store.trackSetRevision).toBe(revisionBeforeRefresh + 1);
    expect(store.dataFreshnessRevision).toBe(1);
  });

  it('keeps the last good result visible while a freshness refresh is in flight', async () => {
    const store = useFilterStore();
    const cfg = {
      filterInfo: { filterConfig: { id: 7, filterName: 'SmartBaseFilter', filterDomain: 'GPS_TRACK' } },
      filterParams: {},
      palette: {},
    } as ClientFilterConfig;
    const previousResult = {
      trackVersions: new Map([[1, 1]]),
      filterGroups: new Map(),
      standardFilterCount: 1,
    };
    const refreshedResult = {
      queryResult: { resultEntries: [{ id: 1 }, { id: 2 }], standardFilterCount: 2 },
      filterConfigId: 7,
      trackVersions: new Map([
        [1, 2],
        [2, 1],
      ]),
      filterGroups: new Map(),
      standardFilterCount: 2,
    };
    let resolveRefresh!: (result: typeof refreshedResult) => void;
    fetchResolveFilterMock.mockReturnValueOnce(
      new Promise<typeof refreshedResult>((resolve) => {
        resolveRefresh = resolve;
      })
    );
    store.applyResolvedFilter(cfg, previousResult);

    const refresh = store.refreshResolvedFilter();
    await Promise.resolve();

    expect(store.refreshingResolvedFilter).toBe(true);
    expect(store.activeResult).toBe(previousResult);

    resolveRefresh(refreshedResult);
    await expect(refresh).resolves.toBe(refreshedResult);

    expect(store.refreshingResolvedFilter).toBe(false);
    expect(store.activeResult).toBe(refreshedResult);
  });

  it('retains the last good result when a freshness refresh fails', async () => {
    const store = useFilterStore();
    const cfg = {
      filterInfo: { filterConfig: { id: 7, filterName: 'SmartBaseFilter', filterDomain: 'GPS_TRACK' } },
      filterParams: {},
      palette: {},
    } as ClientFilterConfig;
    const previousResult = {
      trackVersions: new Map([[1, 1]]),
      filterGroups: new Map(),
      standardFilterCount: 1,
    };
    store.applyResolvedFilter(cfg, previousResult);
    const revisionBeforeRefresh = store.trackSetRevision;
    fetchResolveFilterMock.mockRejectedValueOnce(new Error('offline'));

    await expect(store.refreshResolvedFilter()).rejects.toThrow('offline');

    expect(store.refreshingResolvedFilter).toBe(false);
    expect(store.activeResult).toBe(previousResult);
    expect(store.trackSetRevision).toBe(revisionBeforeRefresh);
    expect(store.dataFreshnessRevision).toBe(0);
  });

  it('treats a default filter with a palette as active', () => {
    const store = useFilterStore();
    const cfg = {
      filterInfo: { filterConfig: { filterName: 'SmartBaseFilter', filterDomain: 'GPS_TRACK' } },
      filterParams: {},
      palette: { id: 1, pColors: ['#123456'] },
    } as ClientFilterConfig;
    store.save(cfg);
    expect(store.isStandard).toBe(true);
    expect(store.isActive).toBe(true);
  });

  it('parallel ensureLoaded calls share one in-flight load', async () => {
    const store = useFilterStore();
    const [a, b] = await Promise.all([store.ensureLoaded(), store.ensureLoaded()]);
    expect(a).toBe(b);
    expect(FilterService.loadClientFilterConfig).toHaveBeenCalledTimes(1);
  });
});
