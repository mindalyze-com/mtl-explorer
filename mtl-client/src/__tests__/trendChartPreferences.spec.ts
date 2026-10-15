import { effectScope, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { STORAGE_KEYS } from '@/utils/appStorage';
import {
  TREND_CHART_HEIGHT_DEFAULT,
  TREND_CHART_ORDER,
  useTrendChartPreferences,
} from '@/components/statistics/trendChartPreferences';

describe('trend chart preferences', () => {
  let scope = effectScope();
  beforeEach(() => {
    localStorage.clear();
    vi.stubGlobal('innerHeight', 1080);
    scope = effectScope();
  });
  afterEach(() => {
    scope.stop();
    vi.unstubAllGlobals();
  });
  const createPreferences = () => scope.run(useTrendChartPreferences)!;

  it('restores defaults for corrupt storage and normalizes stale chart lists', () => {
    localStorage.setItem(STORAGE_KEYS.trendChartPreferences, '{broken');
    expect(createPreferences().isDefault.value).toBe(true);
    localStorage.setItem(
      STORAGE_KEYS.trendChartPreferences,
      JSON.stringify({
        height: 9999,
        order: ['media', 'media', 'unknown', null, 'distance'],
      })
    );
    const preferences = createPreferences();
    expect(preferences.height.value).toBe(1080);
    expect(preferences.order.value).toEqual([
      'media',
      'distance',
      'duration',
      'activity',
      'energy',
      'intensity',
      'trainingLoad',
      'exploration',
    ]);
  });

  it('allows browser-height charts and preserves the chosen height across smaller viewports', async () => {
    const preferences = createPreferences();
    preferences.setHeight(1080);
    await nextTick();
    expect(preferences.height.value).toBe(1080);
    expect(preferences.maxHeight.value).toBe(1080);

    vi.stubGlobal('innerHeight', 604);
    window.dispatchEvent(new Event('resize'));
    expect(preferences.height.value).toBe(600);
    expect(preferences.maxHeight.value).toBe(600);
    expect(JSON.parse(localStorage.getItem(STORAGE_KEYS.trendChartPreferences)!).height).toBe(1080);

    vi.stubGlobal('innerHeight', 1200);
    window.dispatchEvent(new Event('resize'));
    expect(preferences.height.value).toBe(1080);
    expect(preferences.maxHeight.value).toBe(1200);
    preferences.setHeight(2000);
    expect(preferences.height.value).toBe(1200);
  });

  it('tracks the mobile visual viewport and removes resize listeners on disposal', () => {
    const viewport = Object.assign(new EventTarget(), { height: 844 });
    vi.stubGlobal('visualViewport', viewport);
    const preferences = createPreferences();
    preferences.setHeight(1000);
    expect(preferences.height.value).toBe(840);
    viewport.height = 700;
    viewport.dispatchEvent(new Event('resize'));
    expect(preferences.height.value).toBe(700);
    scope.stop();
    viewport.height = 500;
    viewport.dispatchEvent(new Event('resize'));
    window.dispatchEvent(new Event('resize'));
    expect(preferences.maxHeight.value).toBe(700);
  });

  it('keeps unavailable charts when moving visible charts and persists height and order', async () => {
    const preferences = createPreferences();
    preferences.setHeight(365);
    preferences.moveChart('media', -1, ['duration', 'distance', 'activity', 'media']);
    await nextTick();
    const reloaded = createPreferences();
    expect(reloaded.height.value).toBe(370);
    expect(reloaded.order.value).toEqual([
      'duration',
      'distance',
      'media',
      'energy',
      'intensity',
      'trainingLoad',
      'exploration',
      'activity',
    ]);
    reloaded.reset();
    await nextTick();
    const reset = createPreferences();
    expect(reset.height.value).toBe(TREND_CHART_HEIGHT_DEFAULT);
    expect(reset.order.value).toEqual(TREND_CHART_ORDER);
    expect(reset.isDefault.value).toBe(true);
  });
});
