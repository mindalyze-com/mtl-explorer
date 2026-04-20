import { describe, expect, it, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';

// Mock FilterService BEFORE importing the store (which transitively imports it).
// The real FilterService transitively pulls in modules that require browser
// globals (highcharts/highlight.js Vue plugin), so we replace the whole
// module with a self-contained fake.
let __stored: any = null;
const __reset = () => { __stored = null; };

vi.mock('@/components/filter/FilterService', () => ({
  ClientFilterConfig: class { static of(filterInfo: any, filterParams: any, palette: any) { return { filterInfo, filterParams, palette }; } },
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
    saveClientFilterConfig: vi.fn((cfg: any) => { __stored = cfg; }),
    isStandardFilterWithStandardParams: vi.fn((cfg: any) => {
      const fc = cfg?.filterInfo?.filterConfig;
      const fp = cfg?.filterParams;
      const isStdFilter = fc?.filterName === 'SmartBaseFilter' && fc?.filterDomain === 'GPS_TRACK';
      const isStdParams = !fp || Object.keys(fp).length === 0
        || (!fp.dateTimeParams?.DATE_TIME_FROM && !fp.dateTimeParams?.DATE_TIME_TO);
      return isStdFilter && isStdParams;
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
    const cfg = { filterInfo: { filterConfig: { filterName: 'X', filterDomain: 'Y' } }, filterParams: {}, palette: {} } as any;
    store.save(cfg);
    expect(FilterService.saveClientFilterConfig).toHaveBeenCalledWith(cfg);
    expect(store.config).toBe(cfg);
    expect(store.isStandard).toBe(false);
  });

  it('parallel ensureLoaded calls share one in-flight load', async () => {
    const store = useFilterStore();
    const [a, b] = await Promise.all([store.ensureLoaded(), store.ensureLoaded()]);
    expect(a).toBe(b);
    expect(FilterService.loadClientFilterConfig).toHaveBeenCalledTimes(1);
  });
});
