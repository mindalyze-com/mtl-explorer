import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import SourceFindings from '@/features/track-review/SourceFindings.vue';
import SourceUsageSelect from '@/features/track-review/SourceUsageSelect.vue';
import SourcePathComparison from '@/features/track-review/SourcePathComparison.vue';
import ReviewMap from '@/features/track-review/ReviewMap.vue';
import type { TrackFinding, TrackObservation, TrackQualityReport } from 'x8ing-mtl-api-typescript-fetch';

const mocks = vi.hoisted(() => ({ quality: vi.fn(), points: vi.fn(), refit: vi.fn() }));
vi.mock('@/features/track-review/api', async (original) => ({
  ...(await original()),
  reviewApi: () => ({ getSourceReviewPoints: mocks.points }),
}));
vi.mock('x8ing-mtl-api-typescript-fetch', async (original) => ({
  ...(await original()),
  TrackQualityControllerApi: class {
    getTrackQuality = mocks.quality;
  },
}));
vi.mock('@/utils/openApiClient', () => ({ getApiConfiguration: () => ({}) }));
vi.mock('@/components/map/MiniMap.vue', () => ({
  default: {
    name: 'MiniMap',
    props: { tracksGeoJson: Object, mapBounds: Array, mapFocus: Object, showZoomControls: Boolean },
    methods: { refitView: mocks.refit },
    template: '<div />',
  },
}));
const points: TrackObservation[] = [0, 1, 2, 3].map((pointIndex) => ({
  pointIndex,
  sectionIndex: 0,
  longitude: 8 + pointIndex / 1000,
  latitude: 47,
}));
const report: TrackQualityReport = {
  trackId: 7,
  sourceHash: 'current',
  canonicalCoordinates: points.map((point) => [point.longitude!, point.latitude!]),
  canonicalBreakIndexes: [2],
};
enableAutoUnmount(afterEach);
beforeEach(() => {
  mocks.quality.mockReset();
  mocks.quality.mockResolvedValue(report);
  mocks.points.mockReset();
  mocks.refit.mockReset();
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: vi.fn() });
});

it('keeps a close view centered on one jump endpoint even when neighboring points are far away', async () => {
  const finding: TrackFinding = { reason: 'POSITION_JUMP', firstPointIndex: 1, lastPointIndex: 2, affectedCount: 1 };
  const distant = [...points.slice(0, 2), { ...points[2]!, longitude: 12 }, { ...points[3]!, longitude: 13 }];
  const focus = { finding, points: distant, pointIndex: 1 };
  const wrapper = mount(ReviewMap, {
    props: { points: distant, color: 'red', label: 'Original', processed: [report], focus },
  });
  const map = wrapper.findComponent({ name: 'MiniMap' });
  expect(map.props('mapFocus')).toEqual({ center: [8.001, 47], spanMetres: 50 });
  expect(map.props('showZoomControls')).toBe(true);
  const markers = map
    .props('tracksGeoJson')
    .features.filter((feature: GeoJSON.Feature) => feature.properties?.type === 'locator');
  expect(markers.map((feature: GeoJSON.Feature) => feature.properties?.label)).toEqual(['2', '3']);
  await wrapper.get('input').setValue(false);
  expect(
    map.props('tracksGeoJson').features.filter((feature: GeoJSON.Feature) => feature.properties?.type === 'locator')
  ).toHaveLength(2);
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Show transition')!
    .trigger('click');
  expect(map.props('mapFocus')).toBeNull();
  expect(map.props('mapBounds')).toEqual([
    [8.001 - 0.0002, 47 - 0.0002],
    [12 + 0.0002, 47 + 0.0002],
  ]);
  await wrapper.setProps({ focus: { ...focus, pointIndex: 2 } });
  expect(map.props('mapFocus')).toEqual({ center: [12, 47], spanMetres: 50 });
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Show whole track')!
    .trigger('click');
  expect(wrapper.emitted('reset')).toEqual([[]]);
  await wrapper.setProps({ focus: null });
  expect(map.props('mapFocus')).toBeNull();
  expect(map.props('mapBounds')[1][0]).toBe(13 + 0.0002);
});

it('loads exact observations missing from a sampled preview and recenters on repeated requests', async () => {
  const exact = Array.from({ length: 11 }, (_, index) => ({
    pointIndex: 95 + index,
    sectionIndex: 0,
    longitude: 8 + index / 100000,
    latitude: 47,
  }));
  mocks.points.mockResolvedValue({ points: exact });
  const finding: TrackFinding = {
    reason: 'ELEVATION_SPIKE',
    sectionIndex: 0,
    firstPointIndex: 100,
    lastPointIndex: 500,
    affectedCount: 3,
  };
  const wrapper = mount(SourcePathComparison, {
    props: { points, tracks: [], sourceId: 1, sourceHash: 'current' },
    attachTo: document.body,
  });
  await wrapper.vm.centerFinding(finding);
  expect(mocks.points).toHaveBeenCalledWith(
    { sourceId: 1, sourceHash: 'current', from: 95, limit: 11 },
    expect.objectContaining({ signal: expect.any(AbortSignal) })
  );
  const map = wrapper.findComponent({ name: 'MiniMap' });
  expect(map.props('mapFocus')).toEqual({ center: [8.00005, 47], spanMetres: 50 });
  expect(document.activeElement).toBe(wrapper.get('figure').element);
  expect(wrapper.text()).toContain('First reported occurrence');
  const calls = mocks.refit.mock.calls.length;
  await wrapper.vm.centerFinding(finding);
  expect(mocks.refit.mock.calls.length).toBeGreaterThan(calls);
  expect(mocks.points).toHaveBeenCalledOnce();
});

it.each([
  {
    finding: { reason: 'RECORDING_GAP', firstPointIndex: 100 } as TrackFinding,
    requests: [{ from: 95, limit: 12 }],
    target: 101,
  },
  {
    finding: { reason: 'TIME_BACKWARD', firstPointIndex: 100 } as TrackFinding,
    requests: [{ from: 94, limit: 12 }],
    target: 99,
  },
  {
    finding: { reason: 'RECORDING_GAP', firstPointIndex: 0 } as TrackFinding,
    requests: [{ from: 0, limit: 11 }],
    target: 1,
  },
  {
    finding: { reason: 'POSITION_JUMP', firstPointIndex: 100, lastPointIndex: 200, affectedCount: 1 } as TrackFinding,
    requests: [
      { from: 95, limit: 11 },
      { from: 195, limit: 11 },
    ],
    target: 200,
  },
])('loads bounded context for $finding.reason at $finding.firstPointIndex', async ({ finding, requests, target }) => {
  mocks.points.mockImplementation(async ({ from, limit }: { from: number; limit: number }) => ({
    points: Array.from({ length: limit }, (_, index) => ({
      pointIndex: from + index,
      sectionIndex: 0,
      latitude: 47,
      longitude: 8 + (from + index) / 100000,
    })),
  }));
  const wrapper = mount(SourcePathComparison, { props: { points, tracks: [], sourceId: 1, sourceHash: 'current' } });
  await wrapper.vm.centerFinding(finding);
  expect(mocks.points.mock.calls.map(([request]) => request)).toEqual(
    requests.map((request) => ({ sourceId: 1, sourceHash: 'current', ...request }))
  );
  await wrapper.vm.centerFinding(finding, target);
  expect(mocks.points).toHaveBeenCalledTimes(requests.length);
  expect(wrapper.findComponent({ name: 'MiniMap' }).props('mapFocus')).toEqual({
    center: [8 + target / 100000, 47],
    spanMetres: 50,
  });
});

it('ignores an old issue location after navigating to another file', async () => {
  let finish!: (value: { points: TrackObservation[] }) => void;
  mocks.points.mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    })
  );
  const wrapper = mount(SourcePathComparison, { props: { points, tracks: [], sourceId: 1, sourceHash: 'current' } });
  const pending = wrapper.vm.centerFinding({ reason: 'TIME_MISSING', firstPointIndex: 100 });
  await wrapper.setProps({ sourceId: 2 });
  finish({ points: [{ pointIndex: 100, latitude: 47, longitude: 8 }] });
  await pending;
  expect(wrapper.findComponent({ name: 'MiniMap' }).props('mapFocus')).toBeNull();
  expect(wrapper.text()).not.toContain('Loading issue location');
});

it('uses an explicit nearby location for invalid coordinates and reports when none can be located', async () => {
  const finding: TrackFinding = { reason: 'POSITION_INVALID', sectionIndex: 0, firstPointIndex: 100 };
  mocks.points.mockResolvedValue({
    points: [
      { pointIndex: 100, sectionIndex: 0, latitude: NaN, longitude: 8 },
      { pointIndex: 101, sectionIndex: 0, latitude: 47, longitude: 8.002 },
    ],
  });
  const wrapper = mount(SourcePathComparison, { props: { points, tracks: [], sourceId: 1, sourceHash: 'current' } });
  await wrapper.vm.centerFinding(finding);
  expect(wrapper.findComponent({ name: 'MiniMap' }).props('mapFocus')).toEqual({ center: [8.002, 47], spanMetres: 50 });
  expect(wrapper.text()).toContain('Point 101 has no usable GPS position. Centered on nearby point 102.');
  await wrapper.setProps({ sourceHash: 'changed' });
  mocks.points.mockResolvedValue({ points: [{ pointIndex: 100, sectionIndex: 0, latitude: 200, longitude: 8 }] });
  await wrapper.vm.centerFinding(finding);
  expect(wrapper.get('[role="alert"]').text()).toBe('No usable GPS position near this finding.');
  expect(wrapper.findComponent({ name: 'MiniMap' }).props('mapFocus')).toBeNull();
});

it('places review findings before optional information and identifies the actual transition', async () => {
  const warning: TrackFinding = {
    reason: 'POSITION_JUMP',
    severity: 'WARNING',
    firstPointIndex: 23,
    lastPointIndex: 24,
    message: 'Unsupported transition',
  };
  const info: TrackFinding = { reason: 'ELEVATION_MISSING', severity: 'INFORMATION', message: 'No altitude' };
  const wrapper = mount(SourceFindings, { props: { diagnosis: { findings: [info, warning] }, inspectable: true } });
  expect(wrapper.get('section').text()).toContain('GPS jumps');
  expect(wrapper.get('section').text()).not.toContain('No altitude');
  expect(wrapper.get('details summary').text()).toBe('Information (1)');
  expect(wrapper.get('details').attributes('open')).toBeUndefined();
  expect(wrapper.get('details').text()).toContain('Missing elevation is allowed');
  await wrapper.get('section button').trigger('click');
  expect(wrapper.get('section button').text()).toBe('Inspect transition 24 → 25');
  expect(wrapper.emitted('select')?.[0]).toEqual([warning]);
  const targets = wrapper.findAll('section button').slice(1);
  expect(targets.map((button) => button.text())).toEqual(['Center point 24', 'Center point 25']);
  await targets[1]!.trigger('click');
  expect(wrapper.emitted('center')?.[0]).toEqual([warning, 24]);
});

it('shows saved exclusions while new findings await acknowledgement', async () => {
  const wrapper = mount(SourceUsageSelect, { props: { state: 'NEEDS_REVIEW', decision: 'EXCLUDE_STATISTICS' } });
  expect(wrapper.get<HTMLInputElement>('input:checked').element.value).toBe('EXCLUDE_STATISTICS');
  await wrapper.get('form').trigger('submit');
  expect(wrapper.emitted('save')?.[0]).toEqual(['EXCLUDE_STATISTICS']);
});

it('overlays the processed spans without drawing a connecting bridge', async () => {
  const wrapper = mount(ReviewMap, { props: { points, color: 'red', label: 'Original', processed: [report] } });
  const map = wrapper.findComponent({ name: 'MiniMap' });
  const features = map.props('tracksGeoJson').features;
  expect(features).toHaveLength(3);
  expect(features[0].geometry.coordinates).toHaveLength(4);
  expect(
    features.slice(1).map((feature: GeoJSON.Feature) => (feature.geometry as GeoJSON.LineString).coordinates.length)
  ).toEqual([2, 2]);
  await wrapper.get('input').setValue(false);
  expect(map.props('tracksGeoJson').features).toHaveLength(2);
});

it('rejects older source revisions and ignores a comparison response after navigation', async () => {
  let finish: (report: TrackQualityReport) => void = () => {};
  mocks.quality.mockImplementationOnce(
    () =>
      new Promise<TrackQualityReport>((resolve) => {
        finish = resolve;
      })
  );
  const wrapper = mount(SourcePathComparison, { props: { points, tracks: [{ trackId: 7 }], sourceHash: 'previous' } });
  await wrapper.setProps({ sourceHash: 'current', tracks: [{ trackId: 8 }] });
  await flushPromises();
  finish({ ...report, sourceHash: 'previous' });
  await flushPromises();
  expect(wrapper.findComponent(ReviewMap).props('processed')).toEqual([report]);
  mocks.quality.mockResolvedValue({ ...report, sourceHash: 'previous' });
  await wrapper.setProps({ tracks: [{ trackId: 9 }] });
  await flushPromises();
  expect(wrapper.findComponent(ReviewMap).props('processed')).toEqual([]);
  expect(wrapper.get('[role="alert"]').text()).toContain('an older version of this file');
});
