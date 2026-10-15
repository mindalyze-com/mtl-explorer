import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import { useTrackDetailsPreferencesStore } from '@/stores/trackDetailsPreferencesStore';
import GraphPointCountControl from '@/components/ui/GraphPointCountControl.vue';
import MtlSlider from '@/components/ui/MtlSlider.vue';
import type { Map as MapLibreMap } from 'maplibre-gl';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import SegmentCompare from '@/components/measure/SegmentCompare.vue';
import DisplayMeasureResults from '@/components/measure/DisplayMeasureResults.vue';
import ComparisonChart from '@/components/measure/ComparisonChart.vue';
import SegmentInspectionPanel from '@/components/measure/SegmentInspectionPanel.vue';
import SegmentPositionMap from '@/components/measure/SegmentPositionMap.vue';
import SegmentReplay from '@/components/measure/SegmentReplay.vue';
import MiniMap from '@/components/map/MiniMap.vue';
import type { MiniMapGeoJson } from '@/components/map/useMiniMap';
import { observeElementResize } from '@/utils/observeElementResize';
import RacerCard from '@/components/ui/RacerCard.vue';
import { useAnalysisSession } from '@/components/measure/useAnalysisSession';
import {
  fetchSegmentAnalysis,
  fetchSegmentHealth,
  fetchSegmentComparison,
  fetchSegmentTableHeartRate,
} from '@/components/measure/segmentAnalysisApi';
import { attempt, core, health, comparison, ghostPoints, result, selectedSegment } from './fixtures/segmentAnalysis';
import type { Attempt, AttemptData } from '@/components/measure/useSegmentAttempts';
import type { SegmentHealthAnalysis, SegmentCoreAnalysis } from 'x8ing-mtl-api-typescript-fetch';

vi.mock('@/components/measure/segmentAnalysisApi', () => ({
  fetchSegmentAnalysis: vi.fn(),
  fetchSegmentHealth: vi.fn(),
  fetchSegmentComparison: vi.fn(),
  fetchSegmentTableHeartRate: vi.fn(),
}));
vi.mock('@/utils/observeElementResize', () => ({ observeElementResize: vi.fn(() => vi.fn()) }));
type MockMapMarker = {
  element: HTMLElement;
  setLngLat: ReturnType<typeof vi.fn>;
  addTo: ReturnType<typeof vi.fn>;
  remove: ReturnType<typeof vi.fn>;
  getElement: () => HTMLElement;
};
const mapMarkerMocks = vi.hoisted(() => ({ created: [] as MockMapMarker[] }));
vi.mock('maplibre-gl', async (importOriginal) => ({
  ...(await importOriginal<typeof import('maplibre-gl')>()),
  Marker: class {
    element: HTMLElement;
    setLngLat = vi.fn().mockReturnThis();
    addTo = vi.fn().mockReturnThis();
    remove = vi.fn();
    getElement = () => this.element;
    constructor(options: { element: HTMLElement }) {
      this.element = options.element;
      mapMarkerMocks.created.push(this);
    }
  },
}));
const wrappers: VueWrapper[] = [];
const attempts = new Map<string, Attempt>();
const a = attempt(1),
  b = attempt(2, 1, 100),
  repeated = attempt(1, 2, 180);
const stubCard = defineComponent({
  name: 'RacerCard',
  props: ['stats', 'name'],
  template: '<div>{{ name }}<span v-for="s in stats">{{ s.text }}</span><slot name="details" /></div>',
});
const stubs = {
  MiniMap: true,
  ComparisonChart: defineComponent({
    name: 'ComparisonChart',
    props: ComparisonChart.props,
    template: '<div><slot name="actions" /></div>',
  }),
  RacerCard: stubCard,
  SegmentCardiacCost: true,
};
const vm = (w: VueWrapper) =>
  w.vm as unknown as {
    data: AttemptData[];
    reference: string | null;
    mapGeoJson: any;
    hover: any;
    positionPinned: boolean;
  };
async function mountComparison(
  values: Attempt[] = [a, b],
  measureServiceResult = result(...values),
  map?: MapLibreMap
) {
  const wrapper = mount(SegmentCompare, {
    props: {
      map,
      selectedTrackIds: new Set(values.map((a) => a.identity.trackId)),
      selectedAttempts: values,
      selectedSegment,
      availableSegments: [{ name: 'A - B', code: selectedSegment }],
      measureServiceResult,
    },
    global: { stubs },
  });
  wrappers.push(wrapper);
  await flushPromises();
  return wrapper;
}
async function metric(w: VueWrapper, title: string) {
  const existing = w.findAllComponents(ComparisonChart).find((chart) => chart.props('title') === title);
  if (existing) return existing;
  const selected = w.findAll('.sc-add-chart option').find((o) => o.text() === title)!;
  await w.get('.sc-add-chart select').setValue(selected.attributes('value'));
  await flushPromises();
  return w.findAllComponents(ComparisonChart).find((c) => c.props('title') === title)!;
}
beforeEach(() => {
  localStorage.clear();
  setActivePinia(createPinia());
  useMeasurementSystem().setMeasurementSystem('METRIC');
  vi.resetAllMocks();
  attempts.clear();
  mapMarkerMocks.created.length = 0;
  [a, b, repeated].forEach((a) => attempts.set(a.identity.reference, a));
  vi.mocked(fetchSegmentAnalysis).mockImplementation(async (reference) => core(attempts.get(reference)!));
  vi.mocked(fetchSegmentHealth).mockImplementation(async (reference) => health(attempts.get(reference)!));
  vi.mocked(fetchSegmentComparison).mockImplementation(async (reference, against) =>
    comparison(attempts.get(reference)!, attempts.get(against)!)
  );
  vi.mocked(fetchSegmentTableHeartRate).mockResolvedValue({});
});
afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount());
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('server attempt comparison and optional health', () => {
  it('hides graph settings initially and shares the saved Points preference with track details', async () => {
    vi.useFakeTimers();
    const preferences = useTrackDetailsPreferencesStore();
    preferences.setChartPointCount(1000);
    const w = await mountComparison();
    expect(w.find('#comparison-graph-settings').exists()).toBe(false);
    expect(w.get('.sc-graph-settings-toggle').attributes('aria-expanded')).toBe('false');
    expect(fetchSegmentAnalysis).toHaveBeenCalledWith(a.identity.reference, expect.any(AbortSignal), {
      pointCount: 1000,
      axis: 'distance',
    });
    await w.get('.sc-graph-settings-toggle').trigger('click');
    const control = w.getComponent(GraphPointCountControl);
    expect(control.props('modelValue')).toBe(1000);
    await metric(w, 'Heart rate');
    const chart = await metric(w, 'Speed');
    chart.vm.$emit('pin-position', { x: 0.5, axis: 'distance' });
    await flushPromises();
    const pinnedPosition = vm(w).hover;
    const reference = vm(w).reference;
    const comparisons = vi.mocked(fetchSegmentComparison).mock.calls.length;
    vi.mocked(fetchSegmentAnalysis).mockClear();
    vi.mocked(fetchSegmentHealth).mockClear();
    control.getComponent(MtlSlider).vm.$emit('change', 300);
    await vi.advanceTimersByTimeAsync(350);
    await flushPromises();
    expect(preferences.chartPointCount).toBe(3000);
    expect(fetchSegmentAnalysis).toHaveBeenCalledWith(a.identity.reference, expect.any(AbortSignal), {
      pointCount: 3000,
      axis: 'distance',
    });
    expect(fetchSegmentHealth).toHaveBeenCalledWith(a.identity.reference, expect.any(AbortSignal), {
      pointCount: 3000,
      axis: 'distance',
    });
    expect(fetchSegmentComparison).toHaveBeenCalledTimes(comparisons);
    expect(vm(w).reference).toBe(reference);
    expect(vm(w).hover).toEqual(pinnedPosition);
    expect(vm(w).positionPinned).toBe(true);
    await w.get('.sc-graph-settings-toggle').trigger('click');
    expect(w.find('#comparison-graph-settings').exists()).toBe(false);
  });

  it('requests fresh axis buckets without changing the reference', async () => {
    vi.useFakeTimers();
    const w = await mountComparison();
    const chart = await metric(w, 'Speed');
    chart.vm.$emit('pin-position', { x: 0.5, axis: 'distance' });
    await flushPromises();
    const reference = vm(w).reference;
    vi.mocked(fetchSegmentAnalysis).mockClear();
    await w.get('.sc-toolbar label select').setValue('time');
    await vi.advanceTimersByTimeAsync(350);
    await flushPromises();
    expect(fetchSegmentAnalysis).toHaveBeenCalledWith(a.identity.reference, expect.any(AbortSignal), {
      pointCount: 350,
      axis: 'time',
    });
    expect(vm(w).reference).toBe(reference);
    expect(vm(w).hover).toBeNull();
  });

  it('locates one named attempt by distance and all attempts by elapsed time without spanning gaps', async () => {
    vi.mocked(fetchSegmentAnalysis).mockImplementation(async (reference) => {
      const response = core(attempts.get(reference)!);
      if (reference === b.identity.reference) for (const point of response.geometry!) point.latitude! += 0.002;
      return response;
    });
    const w = await mountComparison();
    await w.get('.sc-ghost-toggle').trigger('click');
    const positionMap = w.findComponent(SegmentPositionMap);
    expect(positionMap.get('button').attributes('aria-expanded')).toBe('true');
    const minimap = positionMap.findComponent(MiniMap);
    const points = () =>
      (positionMap.findComponent(MiniMap).props('tracksGeoJson') as MiniMapGeoJson).features.filter(
        (feature) => feature.properties.type === 'racer' || feature.properties.type === 'locator'
      );
    const initialBounds = minimap.props('mapBounds');
    const chart = await metric(w, 'Speed');
    chart.vm.$emit('hover-x', { x: 0.5, axis: 'distance' });
    await flushPromises();
    expect(points().map((feature) => feature.geometry)).toEqual([{ type: 'Point', coordinates: [8.005, 47] }]);
    expect(points().map((feature) => feature.properties)).toEqual([
      expect.objectContaining({ type: 'locator', label: 'R', trackIndex: 0, color: chart.props('series')[0].color }),
    ]);
    expect(positionMap.text()).toContain('Reference · Synthetic 1 · A1 → B1 · 0.50 km');
    expect(minimap.props('mapBounds')).toBe(initialBounds);
    chart.vm.$emit('scrub-position', { x: 0.8, axis: 'distance' });
    await flushPromises();
    chart.vm.$emit('hover-x', { x: 0.2, axis: 'distance' });
    await flushPromises();
    expect(points()[0].geometry).toMatchObject({ coordinates: [expect.closeTo(8.008, 8), 47] });
    await w.findComponent(SegmentInspectionPanel).findAll('.position-card')[1].trigger('click');
    expect(minimap.props('highlightedTrackIndex')).toBe(1);
    expect(points()).toHaveLength(1);
    expect(points()[0].geometry).toMatchObject({ coordinates: [expect.closeTo(8.008, 8), 47.002] });
    expect(positionMap.text()).toContain('Highlighted attempt · Synthetic 2');
    await w.get('.sc-toolbar select').setValue('time');
    chart.vm.$emit('scrub-position', { x: 30000, axis: 'time' });
    await flushPromises();
    expect(points().map((feature) => feature.geometry)).toEqual([
      { type: 'Point', coordinates: [8.005, 47] },
      { type: 'Point', coordinates: [expect.closeTo(8.003, 8), 47.002] },
    ]);
    vm(w).data[1].core!.geometry![1].breakBefore = true;
    await flushPromises();
    expect(points()).toHaveLength(1);
    await positionMap.get('button').trigger('click');
    expect(positionMap.findComponent(MiniMap).exists()).toBe(false);
    await positionMap.get('button').trigger('click');
    expect(points()).toHaveLength(1);
  });
  it('keeps the expanded mini map above mobile charts and moves it back into the rail when the workspace widens', async () => {
    vi.stubGlobal('ResizeObserver', class {});
    const stopObserving = vi.fn();
    vi.mocked(observeElementResize).mockReturnValue(stopObserving);
    const w = await mountComparison();
    const positionMap = w.findComponent(SegmentPositionMap);
    await positionMap.get('button').trigger('click');
    const mobileTarget = w.get('.sc-mobile-map');
    expect(mobileTarget.find('.position-mini-map').exists()).toBe(true);
    const chart = await metric(w, 'Speed');
    await mobileTarget.get('input').setValue(0.5);
    expect(vm(w).hover).toEqual({ x: 0.5, axis: 'distance' });
    chart.vm.$emit('scrub-position', { x: 0.8, axis: 'distance' });
    await flushPromises();
    expect(mobileTarget.get('input').element.value).toBe('0.8');
    const resize = vi.mocked(observeElementResize).mock.calls[0][1];
    resize({ contentRect: { width: 1000 } } as ResizeObserverEntry);
    await flushPromises();
    expect(mobileTarget.find('.position-mini-map').exists()).toBe(false);
    expect(w.get('.sc-inspection-rail').find('.position-mini-map').exists()).toBe(true);
    expect(positionMap.get('button').attributes('aria-expanded')).toBe('true');
    expect(vm(w).hover).toEqual({ x: 0.8, axis: 'distance' });
    w.unmount();
    expect(stopObserving).toHaveBeenCalledOnce();
  });
  it.each([44, 320])('reveals values and charts below the %ipx sticky position map', async (mapHeight) => {
    vi.stubGlobal('ResizeObserver', class {});
    const w = await mountComparison();
    const chart = await metric(w, 'Speed');
    chart.vm.$emit('scrub-position', { x: 0.5, axis: 'distance' });
    await flushPromises();

    const workspace = w.get('.sc-workspace').element as HTMLElement;
    const map = w.get('.sc-mobile-map').element;
    const rail = w.get('.sc-inspection-rail').element;
    const charts = w.get('.sc-chart-region').element;
    (map.parentElement as HTMLElement).style.rowGap = '16px';
    workspace.scrollTop = 500;
    const scrollTo = vi.fn();
    workspace.scrollTo = scrollTo;
    vi.spyOn(workspace, 'getBoundingClientRect').mockReturnValue({ top: 200 } as DOMRect);
    vi.spyOn(map, 'getBoundingClientRect').mockReturnValue({ height: mapHeight } as DOMRect);
    vi.spyOn(rail, 'getBoundingClientRect').mockReturnValue({ top: 900 } as DOMRect);
    vi.spyOn(charts, 'getBoundingClientRect').mockReturnValue({ top: 100 } as DOMRect);
    const headingFocus = vi.spyOn(w.get('.position-controls h3').element as HTMLElement, 'focus');
    const chartFocus = vi.spyOn(charts as HTMLElement, 'focus');

    await w.get('.sc-view-values').trigger('click');
    expect(scrollTo).toHaveBeenLastCalledWith({ top: 1200 - mapHeight - 16, behavior: 'smooth' });
    expect(headingFocus).toHaveBeenCalledWith({ preventScroll: true });

    await w.get('.back-to-charts').trigger('click');
    expect(scrollTo).toHaveBeenLastCalledWith({ top: 400 - mapHeight - 16, behavior: 'smooth' });
    expect(chartFocus).toHaveBeenCalledWith({ preventScroll: true });

    const resize = vi.mocked(observeElementResize).mock.calls[0][1];
    resize({ contentRect: { width: 1000 } } as ResizeObserverEntry);
    await flushPromises();
    await w.get('.sc-view-values').trigger('click');
    expect(scrollTo).toHaveBeenLastCalledWith({ top: 1200, behavior: 'smooth' });
  });
  it('shows one readable legend, pins shared values, and highlights a chosen attempt without pointer changes', async () => {
    vi.mocked(fetchSegmentHealth).mockImplementation(async (reference) => {
      const response = health(attempts.get(reference)!);
      response.points![0].averageBpm = reference === a.identity.reference ? 180 : 155;
      return response;
    });
    const response = result(a, b);
    response.crossings![1].gpsTrack!.activityType = 'RUNNING';
    const w = await mountComparison([a, b], response);
    const hr = await metric(w, 'Heart rate');
    const speed = w.findAllComponents(ComparisonChart).find((chart) => chart.props('title') === 'Speed')!;
    const panel = w.findComponent(SegmentInspectionPanel);
    expect(w.findAll('[aria-label="Track legend"]')).toHaveLength(1);
    expect(panel.findAll('.position-attempt')).toHaveLength(2);
    expect(w.find('.sc-summary .bi-lightning-charge').exists()).toBe(true);
    hr.vm.$emit('hover-x', { x: 0.2, axis: 'distance' });
    await flushPromises();
    expect(panel.text()).toContain('180 bpm');
    expect(panel.text()).toContain('155 bpm');
    expect(panel.findAll('.position-value-label').map((label) => label.text())).toEqual([
      'Time vs. reference',
      'Speed',
      'Heart rate',
      'Ghost',
      'Time vs. reference',
      'Speed',
      'Heart rate',
    ]);
    hr.vm.$emit('pin-position', { x: 0.2, axis: 'distance' });
    await flushPromises();
    speed.vm.$emit('hover-x', { x: 0.7, axis: 'distance' });
    await flushPromises();
    expect(vm(w).hover).toEqual({ x: 0.2, axis: 'distance' });
    expect(panel.text()).toContain('Pinned');
    const card = panel.findAll('.position-card')[1];
    expect(card.element.tagName).toBe('BUTTON');
    expect(card.attributes('aria-describedby')).toBe(panel.findAll('.position-values')[1].attributes('id'));
    // The value area itself must activate the same native button as the identity.
    await card.get('.position-value').trigger('click');
    expect(card.attributes('aria-pressed')).toBe('true');
    expect(card.text()).toContain('Highlighted');
    expect(vm(w).reference).toBe(a.identity.reference);
    expect(vm(w).hover).toEqual({ x: 0.2, axis: 'distance' });
    for (const chart of [hr, speed]) expect(chart.props('activeAttempt')).toBe(b.identity.reference);
    expect(panel.findAll('.position-attempt')[1].text()).toContain('155 bpm');
    await card.get('.position-value-label').trigger('click');
    expect(card.attributes('aria-pressed')).toBe('false');
    for (const chart of [hr, speed]) expect(chart.props('activeAttempt')).toBeNull();
    await card.get('.position-identity').trigger('click');
    await panel
      .findAll('button')
      .find((button) => button.text() === 'Follow pointer')!
      .trigger('click');
    speed.vm.$emit('hover-x', { x: 0.7, axis: 'distance' });
    await flushPromises();
    expect(vm(w).hover?.x).toBe(0.7);
    expect(panel.findAll('.position-values')[1].text()).toContain('Unavailable');
    expect(speed.props('activeAttempt')).toBe(b.identity.reference);
    await panel.get('input').setValue(0.2);
    expect(panel.props('pinned')).toBe(true);
    await w.get('.sc-toolbar select').setValue('time');
    expect(panel.props('pinned')).toBe(false);
    expect(panel.props('position')).toBeNull();
    const gap = await metric(w, 'Time difference at equal traveled distance');
    gap.vm.$emit('hover-x', { x: 0.2, axis: 'distance' });
    await flushPromises();
    expect(panel.findAll('.position-value-label').map((label) => label.text())).toEqual([
      'Time vs. reference',
      'Ghost',
      'Time vs. reference',
    ]);
  });
  it('uses the same single distance locator on the main map and clears inspection markers', async () => {
    const map = {} as MapLibreMap;
    const w = await mountComparison([a, b], result(a, b), map);
    await w.get('.sc-ghost-toggle').trigger('click');
    const chart = await metric(w, 'Speed');
    chart.vm.$emit('hover-x', { x: 0.2, axis: 'distance' });
    await flushPromises();
    expect(mapMarkerMocks.created).toHaveLength(1);
    const marker = mapMarkerMocks.created[0];
    expect(marker.addTo).toHaveBeenCalledWith(map);
    expect(marker.setLngLat.mock.lastCall?.[0][0]).toBeCloseTo(8.002, 10);
    expect(marker.setLngLat.mock.lastCall?.[0][1]).toBe(47);
    expect(marker.element.getAttribute('aria-label')).toContain('A1 → B1');
    expect(marker.element.classList.contains('segment-inspection-marker--locator')).toBe(true);
    chart.vm.$emit('pin-position', { x: 0.4, axis: 'distance' });
    chart.vm.$emit('hover-x', { x: 0.8, axis: 'distance' });
    await flushPromises();
    expect(marker.setLngLat.mock.lastCall?.[0][0]).toBeCloseTo(8.004, 10);
    expect(mapMarkerMocks.created).toHaveLength(1);
    await w.get('.sc-toolbar select').setValue('time');
    expect(marker.remove).toHaveBeenCalledOnce();
    chart.vm.$emit('hover-x', { x: 30000, axis: 'time' });
    await flushPromises();
    expect(mapMarkerMocks.created).toHaveLength(3);
    w.unmount();
    expect(mapMarkerMocks.created[1].remove).toHaveBeenCalledOnce();
  });
  it('shows server ghosts by default on the map, charts and cards while R stays anchored', async () => {
    const map = {} as MapLibreMap;
    const w = await mountComparison([a, b], result(a, b), map);
    const reference = vm(w).reference;
    const other = [a, b].find((candidate) => candidate.identity.reference !== reference)!;
    const ref = [a, b].find((candidate) => candidate.identity.reference === reference)!;
    const ghost = ghostPoints(other, ref)[1];
    const speed = await metric(w, 'Speed');
    expect(w.get('.sc-ghost-toggle').attributes('aria-pressed')).toBe('true');
    speed.vm.$emit('hover-x', { x: 0.5, axis: 'distance' });
    await flushPromises();

    const features = vm(w).mapGeoJson.features;
    const ghostFeature = features.find((f: any) => f.properties.type === 'ghost');
    expect(ghostFeature.geometry.coordinates[0]).toBeCloseTo(8 + (0.01 * ghost.distanceMeters) / 1000, 10);
    expect(ghostFeature.properties.popupDetails).toBe(
      `${Math.round(Math.abs(ghost.leadMeters))} m ${ghost.leadMeters > 0 ? 'ahead' : 'behind'}`
    );
    expect(features.filter((f: any) => f.properties.type === 'ghost-trail').length).toBeGreaterThan(0);
    expect(features.find((f: any) => f.properties.type === 'locator').properties).toMatchObject({ label: 'R' });
    expect(speed.props('ghosts')).toEqual([
      { seriesId: other.identity.reference, x: ghost.distanceMeters / 1000, emphasized: true },
    ]);
    const gap = w
      .findAllComponents(ComparisonChart)
      .find((chart) => chart.props('title').startsWith('Time difference'));
    expect(gap?.props('ghosts')).toEqual([]);
    expect(w.findComponent(SegmentInspectionPanel).text()).toContain(`Ghost${ghostFeature.properties.popupDetails}`);
    expect(mapMarkerMocks.created.some((m) => m.element.classList.contains('segment-inspection-marker--ghost'))).toBe(
      true
    );

    // Highlighting keeps R on the reference instead of moving the locator.
    await w.findComponent(SegmentInspectionPanel).vm.$emit('update:activeAttempt', other.identity.reference);
    await flushPromises();
    expect(vm(w).mapGeoJson.features.find((f: any) => f.properties.type === 'locator').properties.label).toBe('R');

    const lead = await metric(w, 'Distance ahead at the reference\u2019s time');
    expect(lead.props('xMode')).toBe('distance');
    expect(lead.props('series').find((s: any) => s.id === other.identity.reference).data[1]).toEqual([
      0.5,
      ghost.leadMeters,
    ]);
  });
  it('keeps the reference identity and explains when the cursor passes its end', async () => {
    const longer = { ...b, distanceMeters: 1300 };
    attempts.set(longer.identity.reference, longer);
    vi.mocked(fetchSegmentAnalysis).mockImplementation(async (reference) => {
      const response = core(attempts.get(reference)!);
      if (reference === longer.identity.reference) for (const point of response.geometry!) point.distanceMeters! *= 1.3;
      return response;
    });
    const w = await mountComparison([a, longer]);
    await w.get('.sc-ghost-toggle').trigger('click');
    const chart = await metric(w, 'Speed');
    chart.vm.$emit('pin-position', { x: 1.1, axis: 'distance' });
    await flushPromises();
    const panel = w.findComponent(SegmentInspectionPanel);
    const positionMap = panel.findComponent(SegmentPositionMap);
    const locators = () =>
      (vm(w).mapGeoJson as MiniMapGeoJson).features.filter((feature) => feature.properties.type === 'locator');
    const replayHere = () => panel.findAll('button').find((button) => button.text() === 'Open Replay here')!;
    expect(positionMap.text()).toContain('Reference · Synthetic 1');
    expect(positionMap.text()).toContain('Reference ends at 1.00 km.');
    expect(locators()).toHaveLength(0);
    expect(replayHere().attributes()).toHaveProperty('disabled');
    await panel.findAll('.position-card')[1].trigger('click');
    expect(locators()).toHaveLength(1);
    expect(positionMap.text()).toContain('Highlighted attempt · Synthetic 2');
    expect(replayHere().attributes()).toHaveProperty('disabled');
    expect(panel.text()).toContain('Reference ends at 1.00 km.');
    await w.get('.sc-roster select').setValue(longer.identity.reference);
    await flushPromises();
    expect(positionMap.text()).toContain('Reference · Synthetic 2');
    expect(replayHere().attributes()).not.toHaveProperty('disabled');
  });
  it('explains reference gaps and missing timing without disabling ordinary Replay', async () => {
    const w = await mountComparison();
    const chart = await metric(w, 'Speed');
    const panel = w.findComponent(SegmentInspectionPanel);
    const replayHere = () => panel.findAll('button').find((button) => button.text() === 'Open Replay here')!;
    vm(w).data[0].core!.geometry![1].breakBefore = true;
    chart.vm.$emit('pin-position', { x: 0.25, axis: 'distance' });
    await flushPromises();
    expect(panel.text()).toContain('Reference has a recording gap here.');
    expect(
      (vm(w).mapGeoJson as MiniMapGeoJson).features.filter((feature) => feature.properties.type === 'locator')
    ).toHaveLength(0);
    expect(replayHere().attributes()).toHaveProperty('disabled');
    const replayButton = w.findAll('.sc-toolbar button').find((button) => button.text() === 'Replay')!;
    expect(replayButton.attributes()).not.toHaveProperty('disabled');
    chart.vm.$emit('pin-position', { x: 0.5, axis: 'distance' });
    await flushPromises();
    expect(replayHere().attributes()).not.toHaveProperty('disabled');
    vm(w).data[0].core!.geometry![1].elapsedSeconds = null;
    await flushPromises();
    expect(panel.text()).toContain('Reference timing unavailable here.');
    expect(replayHere().attributes()).toHaveProperty('disabled');
    expect(
      (vm(w).mapGeoJson as MiniMapGeoJson).features.filter((feature) => feature.properties.type === 'locator')
    ).toHaveLength(1);
    const unsupported = { ...b, identity: { ...b.identity, timingStatus: 'MISSING_TIME' } };
    vm(w).data[1].attempt = unsupported;
    await flushPromises();
    chart.vm.$emit('pin-position', { x: 0.5, axis: 'distance' });
    await flushPromises();
    expect(panel.text()).toContain('Replay needs timing and complete recorded geometry for every selected attempt.');
    expect(replayButton.attributes()).toHaveProperty('disabled');
  });
  it('opens paused Replay at reference arrival or the inspected elapsed time, independently of the highlighted attempt', async () => {
    const w = await mountComparison();
    const chart = await metric(w, 'Speed');
    chart.vm.$emit('pin-position', { x: 0.4, axis: 'distance' });
    await flushPromises();
    const panel = w.findComponent(SegmentInspectionPanel);
    await panel.findAll('.position-card')[1].trigger('click');
    await panel
      .findAll('button')
      .find((button) => button.text() === 'Open Replay here')!
      .trigger('click');
    let replay = w.findComponent(SegmentReplay);
    expect(replay.props('initialElapsedSeconds')).toBe(24);
    expect(replay.get('input[type="range"]').element.value).toBe('24');
    expect(replay.findAll('button')[0].text()).toBe('Play');
    await w
      .findAll('.sc-toolbar button')
      .find((button) => button.text() === 'Charts')!
      .trigger('click');
    expect(w.findComponent(SegmentInspectionPanel).get('h3').text()).toBe('At 0.40 km traveled');
    await w.get('.sc-toolbar select').setValue('time');
    const timeChart = await metric(w, 'Speed');
    timeChart.vm.$emit('pin-position', { x: 45000, axis: 'time' });
    await flushPromises();
    const timePanel = w.findComponent(SegmentInspectionPanel);
    expect(timePanel.get('h3').text()).toContain('elapsed');
    expect(timePanel.findComponent(SegmentPositionMap).text()).toContain('Compare positions at the same elapsed time.');
    await timePanel
      .findAll('button')
      .find((button) => button.text() === 'Open Replay here')!
      .trigger('click');
    replay = w.findComponent(SegmentReplay);
    expect(replay.props('initialElapsedSeconds')).toBe(45);
    expect(replay.get('input[type="range"]').element.value).toBe('45');
    await w
      .findAll('.sc-toolbar button')
      .find((button) => button.text() === 'Charts')!
      .trigger('click');
    await w
      .findAll('.sc-toolbar button')
      .find((button) => button.text() === 'Replay')!
      .trigger('click');
    expect(w.findComponent(SegmentReplay).get('input[type="range"]').element.value).toBe('0');
  });
  it('uses first arrival when opening Replay at a stopped distance', async () => {
    const w = await mountComparison();
    const geometry = vm(w).data[0].core!.geometry!;
    geometry[1].elapsedSeconds = 20;
    geometry.splice(2, 0, { ...geometry[1], elapsedSeconds: 40 });
    const chart = await metric(w, 'Speed');
    chart.vm.$emit('pin-position', { x: 0.5, axis: 'distance' });
    await flushPromises();
    await w
      .findComponent(SegmentInspectionPanel)
      .findAll('button')
      .find((button) => button.text() === 'Open Replay here')!
      .trigger('click');
    expect(w.findComponent(SegmentReplay).props('initialElapsedSeconds')).toBe(20);
  });
  it('keeps pinned values and the slider inside the full segment range without mixing axes', async () => {
    const w = await mountComparison();
    const speed = await metric(w, 'Speed');
    const panel = w.findComponent(SegmentInspectionPanel);
    speed.vm.$emit('pin-position', { x: 0.8, axis: 'distance' });
    await flushPromises();
    expect(panel.props('position')).toEqual({ x: 0.8, axis: 'distance' });
    expect(panel.props('pinned')).toBe(true);
    expect(panel.get('input').element.value).toBe('0.8');
    expect(panel.props()).toMatchObject({ min: 0, max: 1 });
    await panel.get('input').setValue(0.4);
    await flushPromises();
    expect(panel.props('position')).toEqual({ x: 0.4, axis: 'distance' });
    await w.get('.sc-toolbar select').setValue('time');
    const gap = await metric(w, 'Time difference at equal traveled distance');
    speed.vm.$emit('pin-position', { x: 40000, axis: 'time' });
    gap.vm.$emit('hover-x', { x: 0.5, axis: 'distance' });
    await flushPromises();
    expect(panel.props('position')).toEqual({ x: 40000, axis: 'time' });
    expect(panel.props()).toMatchObject({ min: 0, max: 100000 });
  });
  it('labels paired stop limits but omits repetitive notes at ordinary comparison samples', async () => {
    vi.mocked(fetchSegmentComparison).mockImplementation(async (selected, against) => ({
      ...comparison(attempts.get(selected)!, attempts.get(against)!),
      points: [
        { distanceMeters: 0, differenceSeconds: 0 },
        { distanceMeters: 500, differenceSeconds: 3 },
        { distanceMeters: 500, differenceSeconds: 7, positionMeaning: 'DEPARTURE_LIMIT' },
        { distanceMeters: 1000, differenceSeconds: 8 },
      ],
    }));
    const w = await mountComparison();
    const chart = await metric(w, 'Time difference at equal traveled distance');
    const series = chart.props('series')[1];
    expect(series.pointLabels[0]).toBeUndefined();
    expect(series.pointLabels[1]).toBe('Before stop');
    expect(series.pointLabels[2]).toBe('After stop');
    expect(series.pointLabels[3]).toBeUndefined();
    chart.vm.$emit('pin-position', { x: 0.5, axis: 'distance' });
    await flushPromises();
    const panel = w.findComponent(SegmentInspectionPanel);
    expect(panel.findAll('.position-card')[1].text()).toContain('3 sec slower');
    expect(panel.findAll('.position-card')[1].text()).toContain('Before stop');
    expect(panel.findAll('.position-card')[0].text()).not.toContain('Same time');
  });
  it('aligns sparse and added metrics to the full selected attempts after axis and selection changes', async () => {
    const longer = { ...b, distanceMeters: 1300 };
    attempts.set(longer.identity.reference, longer);
    const w = await mountComparison([a, longer]);
    const speed = await metric(w, 'Speed');
    const hr = await metric(w, 'Heart rate');
    const gap = await metric(w, 'Time difference at equal traveled distance');
    expect(hr.props('series')[0].data.at(-1)?.[0]).toBe(0.2);
    expect(gap.props('series')[0].data.at(-1)?.[0]).toBe(1);
    for (const chart of [speed, hr, gap]) expect(chart.props('xMax')).toBe(1.3);
    const altitude = await metric(w, 'Altitude');
    for (const chart of [speed, hr, gap, altitude]) expect(chart.props('xMax')).toBe(1.3);
    await w.get('.sc-toolbar select').setValue('time');
    for (const chart of [speed, hr, altitude]) {
      expect(chart.props('xMax')).toBe(100000);
    }
    expect(gap.props('xMax')).toBe(1.3);
    expect(w.findAll('button').some((button) => button.text() === 'Reset zoom')).toBe(false);
    expect(w.get('.sc-warning').text()).not.toContain('zoom');
    await w.setProps({ selectedAttempts: [a], selectedTrackIds: new Set([1]) });
    await flushPromises();
    expect(speed.props('xMax')).toBe(60000);
  });
  it('R1/R4 renders authoritative summary values and endpoint maximum independently of chart samples', async () => {
    const w = await mountComparison([a]);
    await w.get('.sc-summary-identity').trigger('click');
    await flushPromises();
    expect(w.text()).toContain('900 W');
    expect(w.text()).toContain('60.0 km/h');
    expect(w.text()).toContain('1.00 km');
    const chart = w.findAllComponents(ComparisonChart).find((c) => c.props('title') === 'Speed')!;
    expect(chart.props('series')[0].data.map((p: number[]) => p[1])).toEqual([25, 25, 25]);
  });
  it('displays resolved elevation, energy and power summaries while keeping missing values distinct from zero', async () => {
    vi.mocked(fetchSegmentAnalysis).mockImplementation(async (reference) => {
      const resolved = core(attempts.get(reference)!);
      Object.assign(
        resolved.summary!,
        reference === a.identity.reference
          ? { ascentMeters: 32.98, descentMeters: 12.44, energyWh: 7.464, maximumPowerWatts: 879.37 }
          : { ascentMeters: null, descentMeters: 0, energyWh: null, maximumPowerWatts: null }
      );
      return resolved;
    });
    const w = await mountComparison();
    await w
      .findAll('button')
      .find((button) => button.text() === 'Summary metrics')!
      .trigger('click');
    for (const label of w.findAll('.sc-column-chooser label')) {
      if (['Ascent', 'Descent', 'Mechanical energy', 'Maximum power'].includes(label.text()))
        await label.get('input').setValue(true);
    }
    const rows = w.findAll('.sc-summary-row');
    const cell = (row: (typeof rows)[number], name: string) =>
      row
        .findAll('[role="cell"]')
        .find((value) => value.find('small').text() === name)!
        .text();
    expect(cell(rows[0], 'Ascent')).toContain('33 m');
    expect(cell(rows[0], 'Descent')).toContain('12 m');
    expect(cell(rows[0], 'Mechanical energy')).toContain('7.5 Wh');
    expect(cell(rows[0], 'Maximum power')).toContain('879 W');
    expect(cell(rows[1], 'Ascent')).toContain('Unavailable');
    expect(cell(rows[1], 'Descent')).toContain('0 m');
    expect(cell(rows[1], 'Mechanical energy')).toContain('Unavailable');
    expect(cell(rows[1], 'Maximum power')).toContain('Unavailable');
  });
  it('R5 compares both explicitly selected visits from the same track with distinct colors', async () => {
    const w = await mountComparison([a, repeated]);
    expect(fetchSegmentAnalysis).toHaveBeenCalledWith(
      repeated.identity.reference,
      expect.any(AbortSignal),
      expect.objectContaining({ pointCount: 350 })
    );
    expect(vm(w).data.map((d) => d.attempt.identity.reference)).toEqual([
      a.identity.reference,
      repeated.identity.reference,
    ]);
    const chart = w.findComponent(ComparisonChart);
    expect(new Set(chart.props('series').map((s: { color: string }) => s.color)).size).toBe(2);
  });
  it('R8/R11 requests health with the exact attempt identity and keeps server average separate from buckets', async () => {
    const w = await mountComparison([a]);
    expect(fetchSegmentHealth).not.toHaveBeenCalled();
    await w.get('.sc-summary-identity').trigger('click');
    await flushPromises();
    expect(fetchSegmentHealth).toHaveBeenCalledWith(
      a.identity.reference,
      expect.any(AbortSignal),
      expect.objectContaining({ pointCount: 350 })
    );
    expect(w.text()).toContain('146 bpm');
    const chart = await metric(w, 'Heart rate');
    expect(chart.props('series')[0].data).toEqual([[0.2, 180]]);
    expect(vm(w).data[0].health?.cardiacCost?.wattsPerBpm).toBe(1.4);
  });
  it('keeps core charts independent of a slow optional request and rejects late health after deselection', async () => {
    let resolve!: (r: SegmentHealthAnalysis) => void;
    vi.mocked(fetchSegmentHealth).mockReturnValue(
      new Promise((r) => {
        resolve = r;
      })
    );
    const w = await mountComparison([a]);
    expect(w.findComponent(ComparisonChart).exists()).toBe(true);
    await w.get('.sc-summary-identity').trigger('click');
    await flushPromises();
    expect(w.text()).toContain('Loading heart rate');
    await w.setProps({ selectedTrackIds: new Set(), selectedAttempts: [] });
    resolve(health(a));
    await flushPromises();
    expect(vm(w).data).toEqual([]);
  });
  it('keeps successful tracks when one core request fails, then retries that track', async () => {
    vi.mocked(fetchSegmentAnalysis).mockImplementation(async (ref) => {
      if (ref === a.identity.reference) throw new Error('failure');
      return core(attempts.get(ref)!);
    });
    const w = await mountComparison([a, b, repeated]);
    expect(vm(w).data.filter((d) => d.core)).toHaveLength(2);
    expect(vm(w).reference).toBe(a.identity.reference);
    const speed = await metric(w, 'Speed');
    expect(speed.props('series').map((series: { id: string }) => series.id)).toEqual([
      b.identity.reference,
      repeated.identity.reference,
    ]);
    expect(w.text()).toContain('Could not load this result. Retry.');
    vi.mocked(fetchSegmentAnalysis).mockClear();
    vi.mocked(fetchSegmentAnalysis).mockImplementation(async (ref) => core(attempts.get(ref)!));
    await w
      .findAll('button')
      .find((b) => b.text() === 'Retry attempt')!
      .trigger('click');
    await flushPromises();
    expect(vm(w).data.filter((d) => d.core)).toHaveLength(3);
    expect(fetchSegmentAnalysis).toHaveBeenCalledOnce();
    expect(vi.mocked(fetchSegmentAnalysis).mock.calls[0][0]).toBe(a.identity.reference);
    expect(vm(w).reference).toBe(a.identity.reference);
    expect(speed.props('series')).toHaveLength(3);
    expect(w.text()).not.toContain('Could not load this result. Retry.');
  });
  it('R9 keeps a valid reference after removal', async () => {
    const w = await mountComparison();
    expect(vm(w).reference).toBe(a.identity.reference);
    await w.setProps({ selectedTrackIds: new Set([2]), selectedAttempts: [b] });
    await flushPromises();
    expect(vm(w).reference).toBe(b.identity.reference);
  });
  it('keeps healthy selected attempts and their reference when a removed attempt returns late', async () => {
    let resolveLate!: (value: SegmentCoreAnalysis) => void;
    const late = new Promise<SegmentCoreAnalysis>((resolve) => (resolveLate = resolve));
    vi.mocked(fetchSegmentAnalysis).mockImplementation(async (ref) =>
      ref === a.identity.reference ? late : core(attempts.get(ref)!)
    );
    const w = await mountComparison([a, b, repeated]);
    expect(vm(w).data.filter((entry) => entry.core)).toHaveLength(2);
    await w.setProps({ selectedTrackIds: new Set([1, 2]), selectedAttempts: [b, repeated] });
    await flushPromises();
    expect(vm(w).reference).toBe(b.identity.reference);
    const before = vm(w).data.map((entry) => entry.attempt.identity.reference);
    resolveLate(core(a));
    await flushPromises();
    expect(vm(w).data.map((entry) => entry.attempt.identity.reference)).toEqual(before);
    expect(vm(w).reference).toBe(b.identity.reference);
    expect((await metric(w, 'Speed')).props('series')).toHaveLength(2);
    expect(w.find('.sc-request-error').exists()).toBe(false);
  });
  it('recognizes a removed attempt and offers current-results refresh without hiding healthy charts', async () => {
    vi.mocked(fetchSegmentAnalysis).mockImplementation(async (ref) => {
      if (ref === a.identity.reference) throw Object.assign(new Error('Gone'), { response: { status: 410 } });
      return core(attempts.get(ref)!);
    });
    const w = await mountComparison([b, repeated, a]);
    expect(w.text()).toContain('This attempt expired or the track changed. Refresh the segment results.');
    expect((await metric(w, 'Speed')).props('series')).toHaveLength(2);
    expect(vm(w).reference).toBe(b.identity.reference);
    await w
      .find('.sc-request-error')
      .findAll('button')
      .find((button) => button.text() === 'Refresh results')!
      .trigger('click');
    expect(w.emitted('refresh-results')).toHaveLength(1);
    expect(vm(w).reference).toBe(b.identity.reference);
  });
  it('R9 uses the first selected timing-supported attempt regardless of duration', async () => {
    const tied = attempt(2, 1, 60);
    attempts.set(tied.identity.reference, tied);
    const w = await mountComparison([tied, a]);
    expect(vm(w).reference).toBe(tied.identity.reference);
  });
  it('rejects a response carrying another attempt or source revision', async () => {
    vi.mocked(fetchSegmentAnalysis).mockResolvedValue(core(b));
    const w = await mountComparison([a]);
    expect(vm(w).data[0].core).toBeUndefined();
    expect(w.text()).toContain('another attempt');
  });
  it('rejects late comparison responses after a manual reference change', async () => {
    const pending: Array<() => void> = [];
    vi.mocked(fetchSegmentComparison).mockImplementation((selected, against) =>
      against === a.identity.reference
        ? new Promise((resolve) => pending.push(() => resolve(comparison(attempts.get(selected)!, a))))
        : Promise.resolve(comparison(attempts.get(selected)!, b))
    );
    const w = await mountComparison();
    await w.get('.sc-roster select').setValue(b.identity.reference);
    await flushPromises();
    pending.forEach((resolve) => resolve());
    await flushPromises();
    expect(vm(w).reference).toBe(b.identity.reference);
    expect(vm(w).data.every((d) => d.comparison?.referenceIdentity?.reference === b.identity.reference)).toBe(true);
  });
  it('keeps completed comparison lines while another selected attempt finishes loading', async () => {
    const c = attempt(3, 1, 120);
    attempts.set(c.identity.reference, c);
    let resolveCore!: (response: SegmentCoreAnalysis) => void;
    let resolveComparison!: (response: ReturnType<typeof comparison>) => void;
    vi.mocked(fetchSegmentAnalysis).mockImplementation((reference) =>
      reference === c.identity.reference
        ? new Promise((resolve) => {
            resolveCore = resolve;
          })
        : Promise.resolve(core(attempts.get(reference)!))
    );
    const calls = new Map<string, number>();
    vi.mocked(fetchSegmentComparison).mockImplementation((reference, against) => {
      const count = (calls.get(reference) ?? 0) + 1;
      calls.set(reference, count);
      if (reference === c.identity.reference)
        return new Promise((resolve) => {
          resolveComparison = resolve;
        });
      if (reference === b.identity.reference && count > 1) return new Promise(() => {});
      return Promise.resolve(comparison(attempts.get(reference)!, attempts.get(against)!));
    });
    const w = await mountComparison([a, b, c]);
    const gap = await metric(w, 'Time difference at equal traveled distance');
    const lineIds = () =>
      gap
        .props('series')
        .filter((series: { data: unknown[] }) => series.data.length)
        .map((series: { id: string }) => series.id);
    expect(lineIds()).toEqual([a.identity.reference, b.identity.reference]);
    resolveCore(core(c));
    await flushPromises();
    expect(gap.props('series')).toHaveLength(3);
    expect(lineIds()).toEqual([a.identity.reference, b.identity.reference]);
    expect(w.text()).toContain('Loading time comparisons… 2 of 3 ready.');
    expect(calls.get(a.identity.reference)).toBe(1);
    expect(calls.get(b.identity.reference)).toBe(1);
    resolveComparison(comparison(c, a));
    await flushPromises();
    expect(lineIds()).toEqual([a.identity.reference, b.identity.reference, c.identity.reference]);
    expect(w.text()).not.toContain('Loading time comparisons…');
  });
  it('keeps pending comparisons and the request limit when more attempts become available', async () => {
    const values = Array.from({ length: 7 }, (_, i) => attempt(i + 1));
    values.forEach((value) => attempts.set(value.identity.reference, value));
    const last = values.at(-1)!;
    let resolveCore!: (response: SegmentCoreAnalysis) => void;
    vi.mocked(fetchSegmentAnalysis).mockImplementation((reference) =>
      reference === last.identity.reference
        ? new Promise((resolve) => {
            resolveCore = resolve;
          })
        : Promise.resolve(core(attempts.get(reference)!))
    );
    const pending = new Map<string, () => void>();
    const signals: AbortSignal[] = [];
    vi.mocked(fetchSegmentComparison).mockImplementation(
      (reference, against, signal) =>
        new Promise((resolve) => {
          signals.push(signal!);
          pending.set(reference, () => resolve(comparison(attempts.get(reference)!, attempts.get(against)!)));
        })
    );
    const w = await mountComparison(values);
    expect(fetchSegmentComparison).toHaveBeenCalledTimes(4);
    expect(
      vm(w)
        .data.slice(0, 6)
        .every((entry) => entry.comparisonLoading)
    ).toBe(true);
    resolveCore(core(last));
    await flushPromises();
    expect(fetchSegmentComparison).toHaveBeenCalledTimes(4);
    expect(signals.every((signal) => !signal.aborted)).toBe(true);
    while (pending.size) {
      const [reference, resolve] = pending.entries().next().value!;
      pending.delete(reference);
      resolve();
      await flushPromises();
      expect(pending.size).toBeLessThanOrEqual(4);
    }
    expect(fetchSegmentComparison).toHaveBeenCalledTimes(values.length);
    expect(new Set(vi.mocked(fetchSegmentComparison).mock.calls.map(([reference]) => reference)).size).toBe(
      values.length
    );
    expect(vm(w).data.every((entry) => entry.comparison && !entry.comparisonLoading)).toBe(true);
  });
  it('retries a failed comparison without removing or fetching completed lines again', async () => {
    let failed = false;
    vi.mocked(fetchSegmentComparison).mockImplementation(async (reference, against) => {
      if (reference === b.identity.reference && !failed) {
        failed = true;
        throw new Error('temporary comparison failure');
      }
      return comparison(attempts.get(reference)!, attempts.get(against)!);
    });
    const w = await mountComparison();
    const completed = vm(w).data[0].comparison;
    expect(vm(w).data[1].comparisonError).toBeTruthy();
    await w
      .findAll('button')
      .find((button) => button.text() === 'Retry comparison')!
      .trigger('click');
    await flushPromises();
    expect(vm(w).data[0].comparison).toBe(completed);
    expect(vm(w).data.every((entry) => entry.comparison && !entry.comparisonError)).toBe(true);
    expect(fetchSegmentComparison).toHaveBeenCalledTimes(3);
    expect(
      vi.mocked(fetchSegmentComparison).mock.calls.filter(([reference]) => reference === a.identity.reference)
    ).toHaveLength(1);
  });
  it('preserves missing HR distance as a gap and retains the sample on the time axis', async () => {
    const response = health(a);
    response.points = [
      { distanceMeters: 0, elapsedSeconds: 0, averageBpm: 120 },
      { distanceMeters: null, elapsedSeconds: 5, averageBpm: 180 },
      { distanceMeters: 1000, elapsedSeconds: 60, averageBpm: 140 },
    ];
    vi.mocked(fetchSegmentHealth).mockResolvedValue(response);
    const w = await mountComparison([a]);
    const chart = await metric(w, 'Heart rate');
    expect(chart.props('series')[0].data).toEqual([
      [0, 120],
      [1, null],
      [1, 140],
    ]);
    await w.get('.sc-toolbar select').setValue('time');
    await flushPromises();
    expect(chart.props('series')[0].data).toEqual([
      [0, 120],
      [5000, 180],
      [60000, 140],
    ]);
  });
  it('keeps core data during a health failure and retries health', async () => {
    vi.mocked(fetchSegmentHealth).mockRejectedValueOnce(new Error('failure'));
    const w = await mountComparison([a]);
    await w.get('.sc-summary-identity').trigger('click');
    await flushPromises();
    expect(w.findComponent(ComparisonChart).exists()).toBe(true);
    await w
      .findAll('button')
      .find((b) => b.text() === 'Retry health')!
      .trigger('click');
    await flushPromises();
    expect(w.text()).toContain('146 bpm');
  });
  it('R3 preserves nulls and recording breaks through chart conversion', async () => {
    const response = core(a);
    response.points![1].powerWatts = null;
    response.points![2].breakBefore = true;
    vi.mocked(fetchSegmentAnalysis).mockResolvedValue(response);
    const w = await mountComparison([a]);
    const chart = await metric(w, 'Est. Power');
    expect(chart.props('series')[0].data).toEqual([
      [0, 900],
      [0.5, null],
      [1, null],
      [1, 100],
    ]);
  });
  it('R7/R12 keeps equal-distance gaps on a distance axis and hover uses that actual domain', async () => {
    const w = await mountComparison([a]);
    await w.get('.sc-toolbar select').setValue('time');
    const chart = await metric(w, 'Time difference at equal traveled distance');
    expect(chart.props('xMode')).toBe('distance');
    expect(chart.props('series')[0].data.at(-1)).toEqual([1, 4.09]);
    expect(w.text()).toContain('Compared with reference');
    chart.vm.$emit('hover-x', { x: 0.5, axis: 'distance' });
    await flushPromises();
    const marker = vm(w).mapGeoJson.features.find((f: any) => f.properties.type === 'locator');
    expect(marker.geometry.coordinates[0]).toBeCloseTo(8.005);
    const panel = w.findComponent(SegmentInspectionPanel);
    expect(panel.get('h3').text()).toBe('At 0.50 km traveled');
    expect(panel.findComponent(SegmentPositionMap).text()).toContain('Compare after traveling the same distance.');
    await w.get('.sc-toolbar select').setValue('distance');
    expect(vm(w).hover).toBeNull();
  });
  it('R10 keeps exact visits through grouping, comparison, replay and back navigation', async () => {
    const session = useAnalysisSession();
    const w = mount(DisplayMeasureResults, {
      props: { measureServiceResult: result(a, repeated), session },
      global: { stubs: { ...stubs, DiscoveryTrend: true, SegmentReplay: true } },
    });
    wrappers.push(w);
    await flushPromises();
    expect(fetchSegmentAnalysis).not.toHaveBeenCalled();
    await w.get('.result-main').trigger('click');
    for (const checkbox of w.findAll('.attempt-row input')) await checkbox.setValue(true);
    await w.get('.results-heading select').setValue('attempts');
    await w.get('.primary-action').trigger('click');
    await flushPromises();
    expect(w.findAllComponents(ComparisonChart).map((chart) => chart.props('title'))).toEqual([
      'Time difference at equal traveled distance',
      'Speed',
    ]);
    expect(vm(w.findComponent(SegmentCompare)).data.map((d) => d.attempt.identity.reference)).toEqual([
      a.identity.reference,
      repeated.identity.reference,
    ]);
    expect(w.get('.sc-ghost-toggle').attributes('aria-pressed')).toBe('true');
    await w.get('.sc-ghost-toggle').trigger('click');
    const colors = w.findComponent(SegmentInspectionPanel).props('colors');
    for (const chart of w.findAllComponents(ComparisonChart))
      expect(chart.props('series').map((series: { color: string }) => series.color)).toEqual(colors);
    await w.get('[aria-label="Remove Time difference at equal traveled distance chart"]').trigger('click');
    await metric(w, 'Altitude');
    await w
      .findAll('.sc-toolbar button')
      .find((b) => b.text() === 'Replay')!
      .trigger('click');
    expect(w.findComponent({ name: 'SegmentReplay' }).props('entries')).toHaveLength(2);
    expect(w.findComponent({ name: 'SegmentReplay' }).props('colors')).toEqual(colors);
    await w
      .findAll('.workspace-context button')
      .find((b) => b.text().includes('Back to results'))!
      .trigger('click');
    expect(session.state.grouping).toBe('attempts');
    expect(session.selected.value).toHaveLength(2);
    await w.get('.primary-action').trigger('click');
    await flushPromises();
    expect(w.findAllComponents(ComparisonChart).map((chart) => chart.props('title'))).toEqual(['Speed', 'Altitude']);
    expect(w.findComponent(SegmentInspectionPanel).props('colors')).toEqual(colors);
    await w
      .findAll('.sc-toolbar button')
      .find((button) => button.text() === 'Charts')!
      .trigger('click');
    expect(w.get('.sc-ghost-toggle').attributes('aria-pressed')).toBe('false');
  });
  it('rejects an old core response after the selection becomes empty', async () => {
    let resolve!: (response: SegmentCoreAnalysis) => void;
    vi.mocked(fetchSegmentAnalysis).mockReturnValue(
      new Promise((r) => {
        resolve = r;
      })
    );
    const w = await mountComparison([a]);
    await w.setProps({ selectedTrackIds: new Set(), selectedAttempts: [] });
    resolve(core(a));
    await flushPromises();
    expect(vm(w).data).toEqual([]);
    expect(w.findComponent(RacerCard).exists()).toBe(false);
  });
});
