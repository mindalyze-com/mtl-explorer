import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TrackGpsReview from '@/components/trackdetails/TrackGpsReview.vue';
import { buildTrackFeature, lineStringCoordinates } from '@/utils/tracks/trackFeatureAdapter';
import { buildTrackOverviewFeatures } from '@/components/map/mapGeometry';
import type { GpsTrack, TrackQualityReport } from 'x8ing-mtl-api-typescript-fetch';

const mocks = vi.hoisted(() => ({ load: vi.fn() }));
vi.mock('x8ing-mtl-api-typescript-fetch', async (original) => ({
  ...(await original()),
  TrackQualityControllerApi: class {
    getTrackQuality = mocks.load;
  },
}));
vi.mock('@/utils/openApiClient', () => ({ getApiConfiguration: () => ({}) }));
vi.mock('@/components/map/MiniMap.vue', () => ({
  default: { name: 'MiniMap', props: ['tracksGeoJson', 'mapBounds'], template: '<div data-test="review-map" />' },
}));

const report: TrackQualityReport = {
  trackId: 7,
  rawCoordinates: [
    [8, 47, 500, 0],
    [8.001, 47, 500, 1],
    [8.2, 47, 500, 20],
    [8.201, 47, 500, 21],
  ],
  canonicalCoordinates: [
    [8, 47, 500, 0],
    [8.001, 47, 500, 1],
    [8.2, 47, 500, 20],
    [8.201, 47, 500, 21],
  ],
  rawBreakIndexes: [2],
  canonicalBreakIndexes: [2],
  processingSettings: { algorithm: 'kalman', pipeline: 'gps-reliability-v1', assessment: 'gps-processing-evidence-v1' },
  issues: [{ canonicalPointIndex: 2, reason: 'UNKNOWN_INTERVAL', durationSeconds: 19, reviewScore: 19 }],
  totalIssueCount: 1,
};

beforeEach(() => {
  mocks.load.mockReset();
  mocks.load.mockResolvedValue(report);
});

describe('GPS quality review', () => {
  it('loads on request and preserves separate paths in the review map', async () => {
    const wrapper = mount(TrackGpsReview, { props: { trackId: 7 } });
    expect(mocks.load).not.toHaveBeenCalled();
    await wrapper.get('button').trigger('click');
    await flushPromises();
    const map = wrapper.findComponent({ name: 'MiniMap' });
    expect(map.props('tracksGeoJson').features).toHaveLength(4);
    expect(wrapper.text()).toContain('Connecting movement unknown');
    await wrapper.get('.review-issues button').trigger('click');
    expect(wrapper.get('.selected-issue').text()).toContain('19.0 s');
    await wrapper.setProps({ trackId: 8 });
    expect(wrapper.text()).toContain('Review GPS observations');
    expect(wrapper.find('.review-map').exists()).toBe(false);
  });
  it('uses original source numbering after invalid observations were skipped', async () => {
    mocks.load.mockResolvedValue({
      ...report,
      observationEvidence: [{ sourcePointIndex: 108, sourceRecordingSection: 2, canonicalPointIndex: 0 }],
      issues: [{ rawPointIndex: 0, canonicalPointIndex: 0, reason: 'ELEVATION_REJECTED', reviewScore: 60 }],
    });
    const wrapper = mount(TrackGpsReview, { props: { trackId: 7 } });
    await wrapper.get('.review-button').trigger('click');
    await flushPromises();
    expect(wrapper.get('.review-issues').text()).toContain('Source point 109');
    await wrapper.get('.review-issues button').trigger('click');
    expect(wrapper.get('.selected-issue').text()).toContain('Source point 109 · Section 3');
  });
  it('keeps unavailable cleaning evidence explicit', async () => {
    mocks.load.mockResolvedValue({ ...report, processingSettings: { algorithm: 'kalman' }, issues: [] });
    const wrapper = mount(TrackGpsReview, { props: { trackId: 7 } });
    await wrapper.get('.review-button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('No cleanup details were saved');
    expect(wrapper.text()).not.toContain('No processing issues were recorded');
  });
  it('collapses and reopens the loaded review without requesting it again', async () => {
    const wrapper = mount(TrackGpsReview, { props: { trackId: 7 } });
    await wrapper.get('.review-button').trigger('click');
    await flushPromises();
    await wrapper.get('.review-issues button').trigger('click');
    await wrapper.get('.review-button').trigger('click');
    expect(wrapper.find('.review-map').exists()).toBe(false);
    expect(wrapper.get('.review-button').attributes('aria-expanded')).toBe('false');
    await wrapper.get('.review-button').trigger('click');
    expect(wrapper.find('.review-map').exists()).toBe(true);
    expect(wrapper.get('.selected-issue').text()).toContain('19.0 s');
    expect(mocks.load).toHaveBeenCalledTimes(1);
  });
  it('shows a retry after a request failure', async () => {
    mocks.load.mockRejectedValue(new Error('offline'));
    const wrapper = mount(TrackGpsReview, { props: { trackId: 7 } });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('Try again');
    expect(wrapper.get('button').attributes('disabled')).toBeUndefined();
  });
  it('renders disconnected tracks as multiple paths and retains an overview marker', () => {
    const track: GpsTrack = {
      id: 7,
      trackLengthInMeter: 150,
      gpsTracksData: [{ numberOfPhysicalPoints: 4, recordingBreakBeforePointIndexes: [2] }],
    };
    const feature = buildTrackFeature(track, [
      [8, 47],
      [8.001, 47],
      [8.2, 47],
      [8.201, 47],
    ]);
    expect(feature.geometry.type).toBe('MultiLineString');
    if (feature.geometry.type === 'MultiLineString') expect(feature.geometry.coordinates).toHaveLength(2);
    expect(lineStringCoordinates(feature.geometry)).toHaveLength(4);
    expect(buildTrackOverviewFeatures({ type: 'FeatureCollection', features: [feature] })).toHaveLength(1);
  });
  it('keeps stationary anchor pairs visible as a map point', async () => {
    mocks.load.mockResolvedValue({
      ...report,
      canonicalCoordinates: [
        [8, 47, 500, 0],
        [8, 47, 500, 150],
      ],
      canonicalBreakIndexes: [],
    });
    const wrapper = mount(TrackGpsReview, { props: { trackId: 7 } });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    const features = wrapper.findComponent({ name: 'MiniMap' }).props('tracksGeoJson').features;
    expect(features.filter((feature: GeoJSON.Feature) => feature.geometry.type === 'Point')).toHaveLength(1);
  });
});
