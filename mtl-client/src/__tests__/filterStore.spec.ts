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

import { useFilterStore } from '@/stores/filterStore';
import { FilterService } from '@/components/filter/FilterService';

describe('useFilterStore', () => {
  beforeEach(async () => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    __reset();
  });

  it('isStandard defaults to true before load', () => {
    const store = useFilterStore();
    expect(store.config).toBeNull();
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
    store.save(cfg);
    expect(FilterService.saveClientFilterConfig).toHaveBeenCalledWith(cfg);
    expect(store.config).toBe(cfg);
    expect(store.isStandard).toBe(false);
    expect(store.isActive).toBe(true);
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
