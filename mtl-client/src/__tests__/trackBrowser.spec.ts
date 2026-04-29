import { computed, ref } from 'vue';
import { describe, expect, it } from 'vitest';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import { useTrackBrowser } from '@/components/track-browser/useTrackBrowser';

describe('useTrackBrowser', () => {
  it('searches GPX metadata names used as display names', () => {
    const tracks = ref<GpsTrack[]>([
      {
        id: 1,
        metaName: 'Spazieren: mit Silja über den Laubberg XXXX1.',
        trackDescription: 'Afternoon walk',
      } as GpsTrack,
      {
        id: 2,
        trackName: 'Different track',
      } as GpsTrack,
    ]);

    const browser = useTrackBrowser(computed(() => tracks.value));

    browser.query.value = 'XXXX1';

    expect(browser.rows.value).toHaveLength(1);
    expect(browser.rows.value[0].id).toBe(1);
    expect(browser.rows.value[0].displayName).toBe('Spazieren: mit Silja über den Laubberg XXXX1.');
  });

  it('searches visible table columns beyond track text fields', () => {
    const tracks = ref<GpsTrack[]>([
      {
        id: 1,
        trackName: 'Metric-rich track',
        startDate: new Date('2026-04-18T07:30:00Z'),
        createDate: new Date('2026-04-19T08:15:00Z'),
        trackLengthInMeter: 3500,
        trackDurationInMotionSecs: 1800,
        energyNetTotalWh: 642.4,
        explorationScore: 0.847,
        explorationStatus: 'READY',
      } as GpsTrack,
      {
        id: 2,
        trackName: 'Plain track',
      } as GpsTrack,
    ]);

    const browser = useTrackBrowser(computed(() => tracks.value));

    for (const query of ['2026-04-18', '3.50 km', '30 min', '642 Wh', '84.7%']) {
      browser.query.value = query;

      expect(browser.rows.value.map((row) => row.id)).toEqual([1]);
    }
  });
});
