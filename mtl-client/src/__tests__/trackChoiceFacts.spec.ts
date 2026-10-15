import { enableAutoUnmount, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import TrackChoiceRow from '@/components/map/TrackChoiceRow.vue';
import { formatTrackChoiceFacts } from '@/components/map/trackChoiceFactFormatting';
import { useLocale } from '@/composables/useLocale';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';

enableAutoUnmount(afterEach);
const locale = useLocale();
const units = useMeasurementSystem();
const originalLocale = locale.formatLocale.value;
const originalUnits = units.explicitMeasurementSystem.value;
const recordedAt = new Date(2026, 7, 18, 12).getTime();

beforeEach(() => {
  locale.setLocale('en-GB');
  units.setMeasurementSystem('METRIC');
});
afterEach(() => {
  locale.setLocale(originalLocale);
  if (originalUnits) units.setMeasurementSystem(originalUnits);
  else units.useDefaultMeasurementSystem();
});

describe('compact map route facts', () => {
  it('formats the recording date, full route distance and ascent with matching labels and icons', () => {
    expect(formatTrackChoiceFacts({ recordedAt: recordedAt, lengthMeters: 48_700, ascentMeters: 420 })).toEqual([
      { key: 'date', icon: 'bi-calendar3', label: 'Date', value: '18/08/2026' },
      { key: 'length', icon: 'bi-arrow-left-right', label: 'Distance', value: '48.7 km' },
      { key: 'ascent', icon: 'bi-graph-up-arrow', label: 'Ascent', value: '420 m' },
    ]);
  });

  it('omits unavailable or invalid facts, while keeping known zero values', () => {
    expect(formatTrackChoiceFacts({})).toEqual([]);
    expect(formatTrackChoiceFacts({ recordedAt: Number.NaN, lengthMeters: -1, ascentMeters: Infinity })).toEqual([]);
    expect(formatTrackChoiceFacts({ fallbackDate: 'No date' })).toEqual([]);
    expect(formatTrackChoiceFacts({ lengthMeters: 0, ascentMeters: 0 }).map((fact) => fact.value)).toEqual([
      '0.00 m',
      '0 m',
    ]);
  });

  it('uses the legacy display date only when no canonical recording date is available', () => {
    expect(formatTrackChoiceFacts({ fallbackDate: 'Legacy date' })[0]?.value).toBe('Legacy date');
    expect(formatTrackChoiceFacts({ recordedAt, fallbackDate: 'stale date' })[0]?.value).toBe('18/08/2026');
  });

  it('updates a visible GPS row when the app date/number format and measurement preferences change', async () => {
    const wrapper = mount(TrackChoiceRow, {
      props: {
        trackId: 7,
        label: 'Coastal ride',
        recordedAt,
        date: 'stale display date',
        lengthMeters: 48_700,
        ascentMeters: 420,
        activityType: 'BICYCLE',
      },
      global: { stubs: { TrackShapePreview: true } },
    });
    expect(wrapper.get('[data-fact="date"]').text()).toBe('18/08/2026');
    expect(wrapper.get('[data-fact="length"]').text()).toBe('48.7 km');
    expect(wrapper.get('[data-fact="ascent"]').attributes('aria-label')).toBe('Ascent: 420 m');
    expect(wrapper.attributes('aria-label')).toContain('Distance: 48.7 km');
    expect(wrapper.text()).not.toContain('stale display date');
    locale.setLocale('de-CH');
    await nextTick();
    expect(wrapper.get('[data-fact="date"]').text()).toBe('18.08.2026');
    locale.setLocale('en-US');
    units.setMeasurementSystem('US_CUSTOMARY');
    await nextTick();
    expect(wrapper.get('[data-fact="date"]').text()).toBe('08/18/2026');
    expect(wrapper.get('[data-fact="length"]').text()).toBe('30.3 mi');
    expect(wrapper.get('[data-fact="ascent"]').text()).toBe('1,378 ft');
    expect(wrapper.attributes('aria-label')).toContain('Ascent: 1,378 ft');
  });
});
