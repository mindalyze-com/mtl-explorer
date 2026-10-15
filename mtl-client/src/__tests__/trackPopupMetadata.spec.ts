import { afterEach, describe, expect, it } from 'vitest';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import { useTrackLayers } from '@/components/map/composables/useTrackLayers';
import type { MapControllerRuntime } from '@/components/map/composables/mapControllerRuntime';
import { useLocale } from '@/composables/useLocale';

const locale = useLocale();
const initialLocale = locale.formatLocale.value;

afterEach(() => locale.setLocale(initialLocale));

function metadata(track?: GpsTrack) {
  const runtime = {
    gpsTracksById: new Map(track ? [[5, track]] : []),
  } as unknown as MapControllerRuntime;
  return useTrackLayers().getTrackPopupMeta.call(runtime, 5);
}

describe('GPS popup preview metadata', () => {
  it('uses cached canonical recording and total metrics while preserving existing identity metadata', () => {
    const startDate = new Date('2026-10-11T12:00:00Z');
    locale.setLocale('en-GB');
    expect(
      metadata({
        id: 5,
        trackName: ' Morning ride ',
        trackDescription: ' Along the river ',
        activityType: 'BICYCLE',
        startDate,
        trackLengthInMeter: 48_700,
        ascentInMeter: 420,
      })
    ).toEqual({
      id: 5,
      name: 'Morning ride',
      displayName: 'Morning ride',
      description: 'Along the river',
      activityType: 'BICYCLE',
      date: '11/10/2026',
      recordedAt: startDate.getTime(),
      lengthMeters: 48_700,
      ascentMeters: 420,
    });
  });

  it('keeps the same canonical timestamp across date format settings', () => {
    const track = { startDate: new Date('2026-10-11T12:00:00Z') };
    locale.setLocale('en-US');
    const us = metadata(track);
    locale.setLocale('de-CH');
    const swiss = metadata(track);
    expect(us.date).toBe('10/11/2026');
    expect(swiss.date).toBe('11.10.2026');
    expect(swiss.recordedAt).toBe(us.recordedAt);
  });

  it('retains legitimate zero metrics and an epoch recording time', () => {
    expect(metadata({ startDate: new Date(0), trackLengthInMeter: 0, ascentInMeter: 0 })).toMatchObject({
      recordedAt: 0,
      lengthMeters: 0,
      ascentMeters: 0,
    });
  });

  it.each([undefined, -1, Number.NaN, Number.POSITIVE_INFINITY])('omits missing or invalid metrics (%s)', (value) => {
    const result = metadata({ trackLengthInMeter: value, ascentInMeter: value });
    expect(result.lengthMeters).toBeUndefined();
    expect(result.ascentMeters).toBeUndefined();
  });

  it('omits invalid recording dates and preserves the existing missing track fallback', () => {
    expect(metadata({ startDate: new Date(Number.NaN) })).toMatchObject({ date: 'No date', recordedAt: undefined });
    expect(metadata()).toEqual({
      id: 5,
      name: 'Track 5',
      displayName: 'Track 5',
      description: '',
      activityType: '',
      date: 'No date',
      recordedAt: undefined,
      lengthMeters: undefined,
      ascentMeters: undefined,
    });
  });
});
