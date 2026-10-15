import { enableAutoUnmount, shallowMount } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import TrackDetailOverview from '@/components/trackdetails/TrackDetailOverview.vue';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';

enableAutoUnmount(afterEach);
afterEach(() => useMeasurementSystem().useDefaultMeasurementSystem());

function mountOverview(overrides: Partial<GpsTrack> = {}) {
  return shallowMount(TrackDetailOverview, {
    props: {
      gpsTrack: {
        id: 1,
        indexedFile: { name: 'synthetic-elevation.gpx', index: 'GPS' },
        trackName: 'Synthetic elevation',
        ascentInMeter: 0,
        descentInMeter: 0,
        ...overrides,
      },
      trackDetails: [],
    },
    global: { directives: { tooltip: {} }, stubs: { Dialog: true, Popover: true } },
  });
}

function metrics(wrapper: ReturnType<typeof mountOverview>) {
  return Object.fromEntries(
    wrapper
      .findAll('.metric-tile')
      .map((tile) => [tile.find('.metric-tile__label').text(), tile.find('.metric-tile__value').text()])
  );
}

describe.each(['METRIC', 'US_CUSTOMARY'] as const)('track elevation in %s', (system) => {
  it.each([undefined, Number.NaN, Number.POSITIVE_INFINITY])(
    'keeps unavailable bounds (%s) distinct from zero',
    (value) => {
      useMeasurementSystem().setMeasurementSystem(system);
      const wrapper = mountOverview({ minAltitude: value, maxAltitude: value });
      const values = metrics(wrapper);
      for (const label of [
        'Ascent',
        'Descent',
        'Lowest point',
        'Highest point',
        'Range',
        'Steepest climb',
        'Steepest descent',
      ]) {
        expect(values[label]).toBeUndefined();
      }
      expect(wrapper.text()).toContain('Elevation data is unavailable for this track.');
      expect(wrapper.find('.overview-section--elevation details').exists()).toBe(false);
      expect(wrapper.text()).not.toMatch(/NaN|Infinity/);
    }
  );

  it('preserves measured sea-level elevations and flat grades', () => {
    useMeasurementSystem().setMeasurementSystem(system);
    const wrapper = mountOverview({ minAltitude: 0, maxAltitude: 0, slopePercentageMax: 0, slopePercentageMin: 0 });
    const values = metrics(wrapper);
    const unit = system === 'METRIC' ? 'm' : 'ft';
    for (const label of ['Ascent', 'Descent', 'Lowest point', 'Highest point', 'Range']) {
      expect(values[label]).toBe(`0 ${unit}`);
    }
    expect(values['Steepest climb']).toBe('0.0%');
    expect(values['Steepest descent']).toBe('0.0%');
    expect(wrapper.text()).not.toContain('Elevation data is unavailable');
  });

  it('preserves below-sea-level bounds, positive range and signed descent', () => {
    useMeasurementSystem().setMeasurementSystem(system);
    const wrapper = mountOverview({
      minAltitude: -100,
      maxAltitude: -50,
      descentInMeter: -50,
      slopePercentageMax: 0,
      slopePercentageMin: -26.2,
    });
    const values = metrics(wrapper);
    expect(values['Lowest point']).toBe(system === 'METRIC' ? '-100 m' : '-328 ft');
    expect(values['Highest point']).toBe(system === 'METRIC' ? '-50 m' : '-164 ft');
    expect(values['Range']).toBe(system === 'METRIC' ? '50 m' : '164 ft');
    expect(values['Descent']).toBe(values['Range']);
    expect(values['Steepest descent']).toBe('-26.2%');
  });
});

it('does not invent a range or slope when only one altitude bound is available', () => {
  useMeasurementSystem().setMeasurementSystem('METRIC');
  const values = metrics(mountOverview({ minAltitude: -20 }));
  expect(values['Lowest point']).toBe('-20 m');
  expect(values['Highest point']).toBe('—');
  expect(values['Range']).toBe('—');
  expect(values['Steepest climb']).toBe('—');
  expect(values['Steepest descent']).toBe('—');
});
