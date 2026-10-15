import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import SegmentReplay from '@/components/measure/SegmentReplay.vue';
import MiniMap from '@/components/map/MiniMap.vue';
import type { MiniMapGeoJson } from '@/components/map/useMiniMap';
import { attempt, core } from './fixtures/segmentAnalysis';
import type { AttemptData } from '@/components/measure/useSegmentAttempts';
import { formatDateCompact, formatDistanceSmart } from '@/utils/Utils';
function entry(visit: number): AttemptData {
  const selected = attempt(1, visit, 60);
  return {
    attempt: selected,
    core: core(selected),
    track: { indexedFile: { name: 'Synthetic repeats' }, activityType: 'RUNNING' },
    loading: false,
    healthLoading: false,
    comparisonLoading: false,
    healthRequest: 0,
  };
}
describe('shared attempt replay', () => {
  it('disables replay for an ambiguous dwell even when geometry carries timestamps', () => {
    const selected = entry(1);
    selected.attempt.identity.timingStatus = 'AMBIGUOUS_DWELL';
    const w = mount(SegmentReplay, {
      props: { entries: [selected], colors: ['red'] },
      global: { stubs: { MiniMap: true } },
    });
    try {
      expect(
        w
          .findAll('button')
          .find((button) => button.text() === 'Play')!
          .attributes('disabled')
      ).toBeDefined();
      expect(w.get('input[type="range"]').attributes('disabled')).toBeDefined();
      expect(w.get('[role="status"]').text()).toContain('Replay needs timing');
    } finally {
      w.unmount();
    }
  });
  it('disables ranked replay when observed endpoint timing survives an unknown path distance', () => {
    const selected = entry(1);
    selected.attempt.distanceMeters = null;
    selected.attempt.averageSpeedKmh = null;
    selected.core!.geometry![1].breakBefore = true;
    selected.core!.geometry![1].distanceMeters = null;
    selected.core!.geometry![2].distanceMeters = null;
    const w = mount(SegmentReplay, {
      props: { entries: [selected], colors: ['red'] },
      global: { stubs: { MiniMap: true } },
    });
    try {
      expect(w.findAll('button').find((button) => button.text() === 'Play')!.attributes('disabled')).toBeDefined();
      expect(w.get('input[type="range"]').attributes('disabled')).toBeDefined();
      expect(w.get('[role="status"]').text()).toContain('Replay needs timing');
    } finally {
      w.unmount();
    }
  });
  it('pauses live playback while seeking, removes future trails on rewind, and resumes from the chosen time', async () => {
    vi.useFakeTimers();
    let now = 0;
    vi.spyOn(performance, 'now').mockImplementation(() => now);
    const w = mount(SegmentReplay, {
      props: { entries: [entry(1)], colors: ['red'] },
      global: { stubs: { MiniMap: true } },
    });
    try {
      await w
        .findAll('button')
        .find((b) => b.text() === 'Play')!
        .trigger('click');
      now = 1_000;
      await vi.advanceTimersByTimeAsync(1_000);
      expect(w.get('input[type="range"]').element.value).toBe('10');
      await w.get('input[type="range"]').setValue(40);
      expect(w.findAll('button')[0].text()).toBe('Play');
      const trail = () =>
        (w.findComponent(MiniMap).props('tracksGeoJson') as MiniMapGeoJson).features.filter(
          (feature) => feature.properties.type === 'trail'
        );
      expect(trail()[0].geometry).toMatchObject({
        coordinates: [
          [8, 47],
          [8.005, 47],
        ],
      });
      now = 11_000;
      await vi.advanceTimersByTimeAsync(10_000);
      expect(w.get('input[type="range"]').element.value).toBe('40');
      await w.get('input[type="range"]').setValue(15);
      expect(trail()).toEqual([]);
      await w
        .findAll('button')
        .find((b) => b.text() === 'Play')!
        .trigger('click');
      now = 11_500;
      await vi.advanceTimersByTimeAsync(500);
      expect(w.get('input[type="range"]').element.value).toBe('20');
      await w
        .findAll('button')
        .find((b) => b.text() === 'Reset')!
        .trigger('click');
      now = 12_000;
      await vi.advanceTimersByTimeAsync(500);
      expect(w.get('input[type="range"]').element.value).toBe('0');
    } finally {
      w.unmount();
      vi.restoreAllMocks();
      vi.useRealTimers();
    }
  });
  it('keeps map numbers aligned with the rankings when attempts overtake and finish', async () => {
    const first = entry(1);
    const second = entry(2);
    second.attempt.elapsedSeconds = 50;
    second.core = core(second.attempt);
    const midpoint = second.core.geometry![1];
    midpoint.elapsedSeconds = 40;
    midpoint.timestamp = new Date(second.attempt.identity.start!.getTime() + 40_000);
    const w = mount(SegmentReplay, {
      props: { entries: [first, second], colors: ['red', 'blue'] },
      global: { stubs: { MiniMap: true } },
    });
    function expectRanking(visits: number[], colors: string[]) {
      const legend = w.findAll('.replay-rankings li');
      const markers = (w.findComponent(MiniMap).props('tracksGeoJson') as MiniMapGeoJson).features.filter(
        (feature) => feature.properties.type === 'racer'
      );
      visits.forEach((visit, rank) => {
        const label = `A${visit} → B${visit}`;
        expect(legend[rank].text()).toContain(label);
        expect(legend[rank].get('[aria-hidden="true"]').attributes('style')).toContain(colors[rank]);
        expect(markers.find((marker) => marker.properties.name === label)?.properties).toMatchObject({
          color: colors[rank],
          label: String(rank + 1),
          popupTitle: `#${rank + 1} · Synthetic repeats`,
        });
      });
    }
    expectRanking([1, 2], ['red', 'blue']);
    await w.get('input[type="range"]').setValue(30);
    expectRanking([1, 2], ['red', 'blue']);
    await w.get('input[type="range"]').setValue(48);
    expectRanking([2, 1], ['blue', 'red']);
    await w.get('input[type="range"]').setValue(55);
    expectRanking([2, 1], ['blue', 'red']);
    expect(w.findAll('.replay-rankings li')[0].text()).toContain('Finished');
    await w.get('input[type="range"]').setValue(60);
    expectRanking([2, 1], ['blue', 'red']);
    await w
      .findAll('button')
      .find((button) => button.text() === 'Reset')!
      .trigger('click');
    expectRanking([1, 2], ['red', 'blue']);
    w.unmount();
  });
  it('opens paused at the requested elapsed time, clamps it, and keeps reset at the start', async () => {
    const w = mount(SegmentReplay, {
      props: { entries: [entry(1)], colors: ['red'], initialElapsedSeconds: 24 },
      global: { stubs: { MiniMap: true } },
    });
    expect(w.get('input[type="range"]').element.value).toBe('24');
    expect(w.findAll('button')[0].text()).toBe('Play');
    const racers = (w.findComponent(MiniMap).props('tracksGeoJson') as MiniMapGeoJson).features.filter(
      (feature) => feature.properties.type === 'racer'
    );
    expect(racers[0].geometry).toMatchObject({ coordinates: [expect.closeTo(8.004, 8), 47] });
    expect(racers[0].id).toBe('1-1');
    expect(racers[0].properties.popupTitle).toBe('#1 · Synthetic repeats');
    expect(racers[0].properties.popupDetails).toContain(
      `A1 → B1 · ${formatDateCompact(entry(1).attempt.identity.start)}`
    );
    expect(racers[0].properties.popupDetails).toContain('Elapsed time: 00:00:24 / 00:01:00');
    expect(racers[0].properties.popupDetails).toContain(
      `Distance traveled: ${formatDistanceSmart(400)} / ${formatDistanceSmart(1000)}`
    );
    await w.setProps({ initialElapsedSeconds: 100 });
    expect(w.get('input[type="range"]').element.value).toBe('60');
    const finished = (w.findComponent(MiniMap).props('tracksGeoJson') as MiniMapGeoJson).features.find(
      (feature) => feature.properties.type === 'racer'
    )!;
    expect(finished.properties.popupDetails).toContain('Elapsed time: 00:01:00 / 00:01:00');
    expect(finished.properties.popupDetails).toContain('Finished');
    await w
      .findAll('button')
      .find((b) => b.text() === 'Reset')!
      .trigger('click');
    expect(w.get('input[type="range"]').element.value).toBe('0');
    await w.setProps({ initialElapsedSeconds: -10 });
    expect(w.get('input[type="range"]').element.value).toBe('0');
    w.unmount();
  });
  it('scrubs two visits of one track using resolved geometry, distinct colors and stable identities', async () => {
    const w = mount(SegmentReplay, {
      props: { entries: [entry(1), entry(2)], colors: ['red', 'blue'] },
      global: { stubs: { MiniMap: true } },
    });
    expect(w.get('.replay-rankings').text()).toContain('A1 → B1');
    expect(w.get('.replay-rankings').text()).toContain('A2 → B2');
    expect(w.findAll('.replay-rankings li')).toHaveLength(2);
    expect(w.findAll('.replay-rankings .activity-badge').map((badge) => badge.text())).toEqual(['Running', 'Running']);
    await w.get('input[type="range"]').setValue(30);
    const features = w.findComponent(MiniMap).props('tracksGeoJson').features;
    const racers = features.filter((f: { properties: { type: string } }) => f.properties.type === 'racer');
    expect(racers.map((r: { properties: { color: string } }) => r.properties.color)).toEqual(['red', 'blue']);
    expect(racers[0].geometry.coordinates[0]).toBeCloseTo(8.005);
    expect(w.text()).toContain('00:00:30');
    await w
      .findAll('button')
      .find((b) => b.text() === 'Reset')!
      .trigger('click');
    expect(w.get('input[type="range"]').element.value).toBe('0');
    w.unmount();
  });
  it('hides the racer throughout a gap, freezes supported progress, and keeps trails split after resuming', async () => {
    const selected = entry(1);
    selected.attempt.elapsedSeconds = 320;
    selected.attempt.distanceMeters = 200;
    selected.core!.geometry = [
      { longitude: 8, latitude: 47, elapsedSeconds: 0, distanceMeters: 0 },
      { longitude: 8.002, latitude: 47, elapsedSeconds: 10, distanceMeters: 100 },
      { longitude: 8.008, latitude: 47, elapsedSeconds: 310, distanceMeters: 100, breakBefore: true },
      { longitude: 8.01, latitude: 47, elapsedSeconds: 320, distanceMeters: 200 },
    ];
    const w = mount(SegmentReplay, {
      props: { entries: [selected], colors: ['red'] },
      global: { stubs: { MiniMap: true } },
    });
    const features = () => (w.findComponent(MiniMap).props('tracksGeoJson') as MiniMapGeoJson).features;
    const racers = () => features().filter((f) => f.properties.type === 'racer');
    const trails = () => features().filter((f) => f.properties.type === 'trail');
    await w.get('input[type="range"]').setValue(5);
    expect(racers()[0].geometry).toMatchObject({ coordinates: [expect.closeTo(8.001, 8), 47] });
    for (const seconds of [150, 250]) {
      await w.get('input[type="range"]').setValue(seconds);
      expect(w.get('.replay-rankings').text()).toContain('Recording gap');
      expect(racers()).toEqual([]);
      expect(trails()).toHaveLength(1);
      expect(trails()[0].geometry).toMatchObject({
        coordinates: [
          [8, 47],
          [8.002, 47],
        ],
      });
    }
    await w.get('input[type="range"]').setValue(310);
    expect(racers()[0].geometry).toMatchObject({ coordinates: [8.008, 47] });
    expect(racers()[0].properties.popupDetails).toContain(`Distance traveled: ${formatDistanceSmart(100)}`);
    await w.get('input[type="range"]').setValue(320);
    expect(w.get('.replay-rankings').text()).toContain('Finished');
    expect(trails().map((f) => f.geometry)).toEqual([
      {
        type: 'LineString',
        coordinates: [
          [8, 47],
          [8.002, 47],
        ],
      },
      {
        type: 'LineString',
        coordinates: [
          [8.008, 47],
          [8.01, 47],
        ],
      },
    ]);
    await w.get('input[type="range"]').setValue(5);
    expect(trails()).toEqual([]);
    expect(racers()[0].geometry).toMatchObject({ coordinates: [expect.closeTo(8.001, 8), 47] });
    w.unmount();
  });
  it.each(['MISSING_TIME', 'RECORDING_GAP', 'INVALID_TIME'] as const)(
    'keeps %s selections visible and disables replay instead of dropping visits',
    (timingStatus) => {
      const selected = entry(2);
      selected.attempt.identity.timingStatus = timingStatus;
      const w = mount(SegmentReplay, {
        props: { entries: [entry(1), selected], colors: ['red', 'blue'] },
        global: { stubs: { MiniMap: true } },
      });
      expect(w.findAll('.replay-rankings li')).toHaveLength(2);
      expect(w.findAll('button')[0].attributes()).toHaveProperty('disabled');
      expect(w.get('input[type="range"]').attributes()).toHaveProperty('disabled');
      expect(w.text()).toContain('every selected attempt');
      w.unmount();
    }
  );
});
