import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DisplayMeasureResults from '@/components/measure/DisplayMeasureResults.vue';
import DiscoveryTrend from '@/components/measure/DiscoveryTrend.vue';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { fetchSegmentTableHeartRate } from '@/components/measure/segmentAnalysisApi';
import { useAnalysisSession } from '@/components/measure/useAnalysisSession';
import { attempt, result } from './fixtures/segmentAnalysis';
vi.mock('@/components/measure/segmentAnalysisApi', () => ({ fetchSegmentTableHeartRate: vi.fn() }));
const preference = useMeasurementSystem();
function fixture() {
  const a = attempt();
  a.averageSpeedKmh = 16.09344;
  a.distanceMeters = 1609.344;
  a.elapsedSeconds = 360;
  const response = result(a);
  Object.assign(response.crossings![1].segmentSummaries![0], {
    meanVisitSpeedKmh: 16.09344,
    meanElapsedSeconds: 360,
    meanDistanceMeters: 1609.344,
  });
  return response;
}
function mountResults() {
  return mount(DisplayMeasureResults, {
    props: { measureServiceResult: fixture() },
    global: { stubs: { DiscoveryTrend: true, SegmentCompare: true } },
  });
}
const selectMetric = (w: ReturnType<typeof mountResults>, metric: string) =>
  w.get('.discovery-controls label:nth-child(2) select').setValue(metric);
beforeEach(() => {
  preference.setMeasurementSystem('US_CUSTOMARY');
  vi.resetAllMocks();
});
afterEach(() => preference.setMeasurementSystem('METRIC'));
describe('segment result measurement units', () => {
  it('shows observed breaks when a marker wait makes elapsed time unavailable', async () => {
    const waiting = attempt();
    waiting.identity.timingStatus = 'AMBIGUOUS_DWELL';
    waiting.elapsedSeconds = null;
    waiting.averageSpeedKmh = null;
    const response = result(waiting);
    response.crossings![1].crossings![1].segmentNotesSinceLastTriggerPoint = {
      stopCount: 1,
      totalStoppedSec: 60,
      longestStopSec: 60,
      coverageComplete: false,
    };
    const w = mount(DisplayMeasureResults, {
      props: { measureServiceResult: response },
      global: { stubs: { DiscoveryTrend: true, SegmentCompare: true } },
    });
    await w.get('.result-main').trigger('click');
    expect(w.get('.attempt-list').text()).toContain('1 observed breaks');
    expect(w.get('.attempt-list').text()).not.toContain('breaks included in elapsed time');
    w.unmount();
  });

  it('keeps gap endpoint elapsed time inspectable while excluding the attempt from performance trends', async () => {
    const normal = attempt(1, 1, 10);
    const incomplete = attempt(1, 2, 72004);
    incomplete.distanceMeters = null;
    incomplete.averageSpeedKmh = null;
    incomplete.availability = {
      distance: { state: 'UNAVAILABLE', reason: 'RECORDING_GAP' },
      performance: { state: 'UNAVAILABLE', reason: 'RECORDING_GAP' },
    };
    const response = result(normal, incomplete);
    Object.assign(response.crossings![1].segmentSummaries![0], {
      elapsedCount: 1,
      distanceCount: 1,
      speedCount: 1,
      meanElapsedSeconds: 10,
    });
    const w = mount(DisplayMeasureResults, {
      props: { measureServiceResult: response },
      global: { stubs: { DiscoveryTrend: true, SegmentCompare: true } },
    });
    await selectMetric(w, 'time');
    expect(w.findComponent(DiscoveryTrend).props('series')[0].points[0].value).toBe(10);
    expect(w.text()).toContain('1 incomplete visit excluded from performance averages');
    await w.get('.results-heading select').setValue('attempts');
    const points = w.findComponent(DiscoveryTrend).props('series')[0].points;
    expect(points).toHaveLength(1);
    expect(points[0].key).toBe(normal.identity.reference);
    expect(w.findAll('.result-main')).toHaveLength(2);
    const gapRow = w
      .findAll('.result-row')
      .find((row) => row.attributes('data-row-key') === incomplete.identity.reference)!;
    await gapRow.get('.result-main').trigger('click');
    expect(gapRow.get('.attempt-list').text()).toContain('endpoint elapsed time excluded from performance averages');
    expect(gapRow.get('.attempt-list').text()).not.toContain('Timing unavailable');
    w.unmount();
  });

  it.each([
    ['C', 1, 2],
    ['A', 2, 3],
  ] as const)('keeps both server visit ordinals for a repeated %s-zone attempt', async (zone, from, to) => {
    const loop = attempt();
    Object.assign(loop.identity, { fromZone: zone, toZone: zone, fromVisit: from, toVisit: to });
    const session = useAnalysisSession();
    const w = mount(DisplayMeasureResults, {
      props: { measureServiceResult: result(loop), session },
      global: { stubs: { DiscoveryTrend: true, SegmentCompare: true } },
    });
    await w.get('.results-heading select').setValue('attempts');
    expect(w.get('.result-metadata').text()).toContain(`${zone}${from} → ${zone}${to}`);
    expect(w.get('.result-selection input').attributes('aria-label')).toContain(`${zone}${from} → ${zone}${to}`);
    await w.get('.result-selection input').setValue(true);
    await w.get('.primary-action').trigger('click');
    expect(w.getComponent({ name: 'SegmentCompare' }).props('selectedAttempts')).toEqual([loop]);
    w.unmount();
  });

  it('uses server averages and the same canonical values for chart and rows', async () => {
    const w = mountResults();
    expect(w.get('.result-main').text()).toContain('10.00 mph');
    expect(w.findComponent(DiscoveryTrend).props('series')[0].points[0].value).toBe(16.09344);
    expect(w.findComponent(DiscoveryTrend).props('title')).toBe('Your speed trends');
    expect(w.findComponent(DiscoveryTrend).props('description')).toContain('average across its visits');
    await selectMetric(w, 'distance');
    expect(w.get('.result-main').text()).toContain('1.00 mi');
    expect(w.findComponent(DiscoveryTrend).props('series')[0].points[0].value).toBe(1609.344);
    expect(w.findComponent(DiscoveryTrend).props('title')).toBe('Distance over time');
    expect(w.findComponent(DiscoveryTrend).props('description')).toContain('Different routes or GPS recordings');
    preference.setMeasurementSystem('METRIC');
    await nextTick();
    expect(w.get('.result-main').text()).toContain('1.61 km');
    w.unmount();
  });
  it('shows separate server valid counts for every average metric, including zero and unavailable counts', async () => {
    const response = fixture();
    response.crossings![1].attempts!.push(attempt(1, 2));
    Object.assign(response.crossings![1].segmentSummaries![0], {
      visitCount: 2,
      elapsedCount: 1,
      distanceCount: 2,
      speedCount: 0,
    });
    const w = mount(DisplayMeasureResults, {
      props: { measureServiceResult: response },
      global: { stubs: { DiscoveryTrend: true, SegmentCompare: true } },
    });
    for (const metric of ['speed', 'time', 'distance']) {
      await selectMetric(w, metric);
      expect(w.get('.result-valid-counts').text()).toBe('Valid visits: elapsed time 1 · distance 2 · speed 0');
    }
    await w.get('.results-heading select').setValue('attempts');
    expect(w.find('.result-valid-counts').exists()).toBe(false);
    await w.get('.results-heading select').setValue('average');
    delete response.crossings![1].segmentSummaries![0].speedCount;
    await w.setProps({ measureServiceResult: { ...response } });
    expect(w.get('.result-valid-counts').text()).toContain('speed unavailable');
    w.unmount();
  });

  it('keeps unavailable results and does not invent timing or average speed', async () => {
    const w = mountResults(),
      response = fixture();
    response.crossings![1].attempts![0].elapsedSeconds = null;
    response.crossings![1].attempts![0].averageSpeedKmh = null;
    response.crossings![1].attempts![0].identity!.timingStatus = 'MISSING_TIME';
    response.crossings![1].segmentSummaries![0].meanElapsedSeconds = null;
    response.crossings![1].segmentSummaries![0].meanVisitSpeedKmh = null;
    await w.setProps({ measureServiceResult: response });
    await selectMetric(w, 'time');
    expect(w.get('.result-main').text()).toContain('Unavailable');
    expect(w.findComponent(DiscoveryTrend).props('series')[0].points[0].value).toBeNull();
    await w.get('.results-heading select').setValue('attempts');
    expect(w.get('.result-main').text()).toContain('Unavailable');
    await selectMetric(w, 'speed');
    expect(w.get('.result-main').text()).toContain('Unavailable');
    await selectMetric(w, 'distance');
    expect(w.get('.result-main').text()).toContain('1.00 mi');
    await w.get('.result-main').trigger('click');
    expect(w.get('.attempt-list').text()).toContain('Timing unavailable');
    w.unmount();
  });
  it('formats exact visits and does not expose canonical units as display units', async () => {
    const w = mountResults();
    await w.get('.results-heading select').setValue('attempts');
    expect(w.get('.result-main').text()).toContain('10.00 mph');
    await selectMetric(w, 'distance');
    expect(w.get('.result-main').text()).toContain('1.00 mi');
    await selectMetric(w, 'time');
    expect(w.get('.result-main').text()).toContain('00:06:00');
    w.unmount();
  });
  it('uses server covered-time weighted HR groups and exact visit summaries', async () => {
    const w = mountResults(),
      a = fixture().crossings![1].attempts![0];
    vi.mocked(fetchSegmentTableHeartRate).mockResolvedValue({
      trackId: 1,
      revision: a.identity!.revision,
      groups: [{ fromTriggerPoint: 'A', toTriggerPoint: 'B', heartRate: { averageBpm: 145, coveragePercent: 80 } }],
      attempts: [{ identity: a.identity, heartRate: { averageBpm: 160, coveragePercent: 50 } }],
    });
    expect(fetchSegmentTableHeartRate).not.toHaveBeenCalled();
    await selectMetric(w, 'heartRate');
    await flushPromises();
    expect(w.get('.result-main').text()).toContain('145 bpm');
    expect(w.findComponent(DiscoveryTrend).props('series')[0].points[0].value).toBe(145);
    await w.get('.results-heading select').setValue('attempts');
    expect(w.get('.result-main').text()).toContain('160 bpm');
    w.unmount();
  });
  it('shows activity identity and makes the full description available when the row opens', async () => {
    const response = fixture();
    Object.assign(response.crossings![1].gpsTrack!, {
      trackName: 'Morning hill ride',
      trackDescription: 'Three steady laps.\nAn easy finish along the trees.',
      activityType: 'MOUNTAIN_BIKING',
    });
    const w = mount(DisplayMeasureResults, {
      props: { measureServiceResult: response },
      global: { stubs: { DiscoveryTrend: true, SegmentCompare: true } },
    });
    expect(w.get('.result-title').text()).toBe('Morning hill ride');
    expect(w.get('.result-metadata').text()).toContain('Mountain biking');
    expect(w.get('.result-description').text()).toBe('Three steady laps. An easy finish along the trees.');
    await w.get('.result-main').trigger('click');
    expect(w.get('.attempt-description').text()).toBe(w.get('.result-description').text());
    w.unmount();
  });
  it('selects exact visits from a visible result checkbox and lets users refine a partial selection', async () => {
    const session = useAnalysisSession();
    const a = attempt(1),
      b = attempt(1, 2),
      other = attempt(2);
    const w = mount(DisplayMeasureResults, {
      props: { measureServiceResult: result(a, b, other), session },
      global: { stubs: { DiscoveryTrend: true, SegmentCompare: true } },
    });
    expect(session.selected.value).toHaveLength(0);
    expect(w.get('.selection-bar').text()).toContain('Choose attempts to compare or replay');
    await w.get('.result-selection input').setValue(true);
    expect(session.selected.value.map((a) => a.identity.reference)).toEqual([
      a.identity.reference,
      b.identity.reference,
    ]);
    expect(w.get('.primary-action').text()).toBe('Compare 2 attempts');
    expect(w.findComponent(DiscoveryTrend).props('series')[0].points).toHaveLength(2);
    await w.get('.result-main').trigger('click');
    await w.get('.attempt-row input').setValue(false);
    expect((w.get('.result-selection input').element as HTMLInputElement).indeterminate).toBe(true);
    expect(session.selected.value.map((a) => a.identity.reference)).toEqual([b.identity.reference]);
    await w.get('.select-all').trigger('click');
    expect(session.selected.value).toHaveLength(3);
    w.unmount();
  });

  it.each([1, 3])('retains selected visit %s and its reference when reopening charts or replay', async (visit) => {
    const session = useAnalysisSession();
    const first = attempt(1, visit),
      second = attempt(2, visit);
    session.setResults(result(first, second, attempt(1, visit + 1)));
    session.toggleAttempt(first);
    session.toggleAttempt(second);
    session.setReference(second.identity.reference);
    const w = mount(DisplayMeasureResults, {
      props: { measureServiceResult: session.state.result!, session },
      global: { stubs: { DiscoveryTrend: true, SegmentCompare: true } },
    });
    for (const view of ['charts', 'replay', 'charts']) {
      if (view === 'charts') await w.get('.primary-action').trigger('click');
      else {
        const replay = w.findAll('button').find((b) => b.text() === 'Replay');
        await replay!.trigger('click');
      }
      const comparison = w.getComponent({ name: 'SegmentCompare' });
      expect(comparison.props('selectedAttempts').map((a: typeof first) => a.identity.reference)).toEqual([
        first.identity.reference,
        second.identity.reference,
      ]);
      expect(comparison.props('referenceAttempt')).toBe(second.identity.reference);
      expect(session.state.comparisonView).toBe(view);
      comparison.vm.$emit('goto-table');
      await nextTick();
    }
    w.unmount();
  });
});
