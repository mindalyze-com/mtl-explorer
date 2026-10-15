import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LocationSearchResponseDto } from 'x8ing-mtl-api-typescript-fetch';
import LocationSearchSheet from '@/components/map/LocationSearchSheet.vue';
import { SEARCH_DEBOUNCE_MS } from '@/components/map/useLocationSearch';

const mocks = vi.hoisted(() => ({ search: vi.fn(), status: vi.fn() }));

vi.mock('x8ing-mtl-api-typescript-fetch', async (importOriginal) => ({
  ...(await importOriginal<typeof import('x8ing-mtl-api-typescript-fetch')>()),
  LocationSearchControllerApi: class {
    search = mocks.search;
  },
}));
vi.mock('@/utils/locationSearchStatusService', () => ({ fetchLocationSearchStatus: mocks.status }));

const BottomSheetStub = defineComponent({
  name: 'BottomSheet',
  template: '<section><slot /></section>',
});

const porto = { displayName: 'Porto', countryName: 'Portugal', lat: 41.15, lon: -8.61 };
const bern = { displayName: 'Bern', countryName: 'Switzerland', lat: 46.95, lon: 7.45 };
const wrappers: ReturnType<typeof mount<typeof LocationSearchSheet>>[] = [];

function mountSheet() {
  const wrapper = mount(LocationSearchSheet, {
    props: { modelValue: false, mapCenter: null },
    global: { stubs: { BottomSheet: BottomSheetStub } },
  });
  wrappers.push(wrapper);
  return wrapper;
}

function deferredSearch() {
  let resolve!: (response: LocationSearchResponseDto) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<LocationSearchResponseDto>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

async function searchPorto(wrapper: ReturnType<typeof mountSheet>) {
  await wrapper.setProps({ modelValue: true });
  await wrapper.get('input[type="search"]').setValue('Porto');
  await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS);
  await flushPromises();
}

describe('LocationSearchSheet', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    mocks.search.mockReset().mockResolvedValue({ ready: true, results: [porto] });
    mocks.status.mockReset().mockResolvedValue({ ready: true });
  });

  afterEach(() => {
    wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('shows a prompt when the query is empty, including after Clear search', async () => {
    const wrapper = mountSheet();

    expect(wrapper.get('.location-search__state').text()).toContain('Search for a city, peak, or area');

    await wrapper.get('input[type="search"]').setValue('Bern');
    await wrapper.get('[aria-label="Clear search"]').trigger('click');

    expect(wrapper.get('input[type="search"]').element).toHaveProperty('value', '');
    expect(wrapper.get('.location-search__state').text()).toContain('Search for a city, peak, or area');
  });

  it('shows place context and distance while retaining zoom metadata for selection', async () => {
    const result = { ...porto, sourceLayer: 'place', minZoom: 7, maxZoom: 15, distanceMeters: 1200 };
    mocks.search.mockResolvedValueOnce({ ready: true, results: [result] });
    const wrapper = mountSheet();
    await searchPorto(wrapper);

    const button = wrapper.get('.location-search__result');
    expect(button.text()).toContain('Portugal');
    const metadata = button.get('.location-search__result-meta').findAll('span');
    expect(metadata).toHaveLength(2);
    expect(metadata[1].text()).toMatch(/\d/);
    expect(button.text()).not.toMatch(/z7[-–]15/);
    await button.trigger('click');
    expect(wrapper.emitted('select')).toEqual([[result]]);
  });

  it('removes old results immediately and blocks Enter until the new query finishes', async () => {
    const wrapper = mountSheet();
    await searchPorto(wrapper);
    expect(wrapper.get('.location-search__result').text()).toContain('Porto');

    mocks.search.mockResolvedValueOnce({ ready: true, results: [bern] });
    await wrapper.get('input[type="search"]').setValue('Bern');
    expect(wrapper.findAll('.location-search__result')).toHaveLength(0);
    expect(wrapper.get('.location-search__state').text()).toBe('Searching');
    await wrapper.get('input[type="search"]').trigger('keydown', { key: 'Enter' });
    expect(wrapper.emitted('select')).toBeUndefined();

    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS - 1);
    expect(mocks.search).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    await flushPromises();
    await wrapper.get('input[type="search"]').trigger('keydown', { key: 'Enter' });
    expect(wrapper.emitted('select')).toEqual([[bern]]);
  });

  it('ignores an old response arriving during the replacement query debounce', async () => {
    const oldSearch = deferredSearch();
    mocks.search.mockReturnValueOnce(oldSearch.promise);
    const wrapper = mountSheet();
    await searchPorto(wrapper);
    const signal = mocks.search.mock.calls[0][1].signal as AbortSignal;

    await wrapper.get('input[type="search"]').setValue('Bern');
    expect(signal.aborted).toBe(true);
    oldSearch.resolve({ ready: true, results: [porto] });
    await flushPromises();
    expect(wrapper.findAll('.location-search__result')).toHaveLength(0);
    expect(wrapper.get('.location-search__state').text()).toBe('Searching');
    await wrapper.get('input[type="search"]').trigger('keydown', { key: 'Enter' });
    expect(wrapper.emitted('select')).toBeUndefined();

    mocks.search.mockResolvedValueOnce({ ready: true, results: [bern] });
    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS);
    await flushPromises();
    expect(wrapper.get('.location-search__result').text()).toContain('Bern');
  });

  it('handles a cancelled request rejecting after Clear search', async () => {
    const oldSearch = deferredSearch();
    mocks.search.mockReturnValueOnce(oldSearch.promise);
    const wrapper = mountSheet();
    await searchPorto(wrapper);

    await wrapper.get('[aria-label="Clear search"]').trigger('click');
    oldSearch.reject(new DOMException('Cancelled', 'AbortError'));
    await flushPromises();
    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS);
    expect(wrapper.get('.location-search__state').text()).toContain('Search for a city, peak, or area');
    expect(mocks.search).toHaveBeenCalledTimes(1);
  });

  it('clears results immediately when a query becomes too short', async () => {
    const wrapper = mountSheet();
    await searchPorto(wrapper);
    await wrapper.get('input[type="search"]').setValue('B');
    await wrapper.get('input[type="search"]').trigger('keydown', { key: 'Enter' });
    expect(wrapper.findAll('.location-search__result')).toHaveLength(0);
    expect(wrapper.get('.location-search__state').text()).toBe('Keep typing');
    expect(wrapper.emitted('select')).toBeUndefined();
    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS);
    expect(mocks.search).toHaveBeenCalledTimes(1);
  });

  it('cancels search on close and only accepts new results after reopening', async () => {
    const oldSearch = deferredSearch();
    mocks.search.mockReturnValueOnce(oldSearch.promise);
    const wrapper = mountSheet();
    await searchPorto(wrapper);

    await wrapper.setProps({ modelValue: false });
    oldSearch.resolve({ ready: true, results: [porto] });
    await flushPromises();
    expect(wrapper.findAll('.location-search__result')).toHaveLength(0);

    mocks.search.mockResolvedValueOnce({ ready: true, results: [porto] });
    await wrapper.setProps({ modelValue: true });
    await wrapper.get('input[type="search"]').trigger('keydown', { key: 'Enter' });
    expect(wrapper.emitted('select')).toBeUndefined();
    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS);
    await flushPromises();
    expect(wrapper.get('.location-search__result').text()).toContain('Porto');
  });
});
