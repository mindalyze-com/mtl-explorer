import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import {
  TRACK_DETAIL_GRAPH_HEIGHT_DEFAULT,
  TRACK_DETAIL_GRAPH_HEIGHT_MAX,
  TRACK_DETAIL_GRAPH_HEIGHT_MIN,
  TRACK_DETAIL_MINI_MAP_HEIGHT_MAX,
  TRACK_DETAIL_MINI_MAP_HEIGHT_MOBILE_DEFAULT,
  useTrackDetailsPreferencesStore,
} from '@/stores/trackDetailsPreferencesStore';
import { STORAGE_KEYS } from '@/utils/appStorage';
import {
  roundToNiceTrackDetailsChartPointCount,
  TRACK_DETAILS_CHART_POINTS_DEFAULT,
} from '@/utils/trackDetailsChartPointSettings';

describe('useTrackDetailsPreferencesStore', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it('hydrates defaults when no preferences are stored', () => {
    const store = useTrackDetailsPreferencesStore();

    expect(store.graphHeightPx).toBe(TRACK_DETAIL_GRAPH_HEIGHT_DEFAULT);
    expect(store.showRangeBand).toBe(true);
    expect(store.chartPointCount).toBe(TRACK_DETAILS_CHART_POINTS_DEFAULT);
    expect(store.hasMiniMapHeightPreference).toBe(false);
    expect(localStorage.getItem(STORAGE_KEYS.trackDetailsPreferences)).toBeNull();
  });

  it('clamps and rounds stored preferences and persists the sanitized payload', () => {
    const expectedPointCount = roundToNiceTrackDetailsChartPointCount(769);
    localStorage.setItem(
      STORAGE_KEYS.trackDetailsPreferences,
      JSON.stringify({
        graphHeightPx: 9999,
        showRangeBand: false,
        chartPointCount: 769,
        miniMapHeight: 10,
      })
    );

    const store = useTrackDetailsPreferencesStore();

    expect(store.graphHeightPx).toBe(TRACK_DETAIL_GRAPH_HEIGHT_MAX);
    expect(store.showRangeBand).toBe(false);
    expect(store.chartPointCount).toBe(expectedPointCount);
    expect(store.hasMiniMapHeightPreference).toBe(false);
    expect(readStoredTrackPreferences()).toEqual({
      graphHeightPx: TRACK_DETAIL_GRAPH_HEIGHT_MAX,
      showRangeBand: false,
      chartPointCount: expectedPointCount,
    });
  });

  it('clamps, rounds, and persists writes from actions', () => {
    const store = useTrackDetailsPreferencesStore();
    const expectedPointCount = roundToNiceTrackDetailsChartPointCount(769);

    store.setGraphHeight(1);
    store.toggleRangeBand();
    store.setChartPointCount(769);
    store.setMiniMapHeight(9999);

    expect(readStoredTrackPreferences()).toEqual({
      graphHeightPx: TRACK_DETAIL_GRAPH_HEIGHT_MIN,
      showRangeBand: false,
      chartPointCount: expectedPointCount,
      miniMapHeight: TRACK_DETAIL_MINI_MAP_HEIGHT_MAX,
    });
  });

  it('uses responsive mini-map defaults without persisting until the user resizes', () => {
    const store = useTrackDetailsPreferencesStore();

    expect(store.ensureMiniMapHeight(TRACK_DETAIL_MINI_MAP_HEIGHT_MOBILE_DEFAULT)).toBe(
      TRACK_DETAIL_MINI_MAP_HEIGHT_MOBILE_DEFAULT
    );
    expect(localStorage.getItem(STORAGE_KEYS.trackDetailsPreferences)).toBeNull();

    store.setMiniMapHeight(9999);

    expect(readStoredTrackPreferences()).toMatchObject({
      miniMapHeight: TRACK_DETAIL_MINI_MAP_HEIGHT_MAX,
    });
  });
});

function readStoredTrackPreferences(): Record<string, unknown> {
  return JSON.parse(localStorage.getItem(STORAGE_KEYS.trackDetailsPreferences) ?? '{}') as Record<string, unknown>;
}
