import { createPinia, setActivePinia } from 'pinia';
import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import AdminProcessingSection from '@/components/admin/AdminProcessingSection.vue';
import type { GHStatusResponse } from '@/features/health/api/googleHealthApi';

const pendingStatus: GHStatusResponse = {
  configurationState: 'CONFIGURED',
  connectionState: 'CONNECTED',
  job: { state: 'COMPLETED', totalUnits: 140, completedUnits: 140 },
  syncStates: [
    {
      dataType: 'heart-rate',
      representation: 'HEART_RATE_SAMPLE',
      oldestSyncedTime: new Date('2026-06-25T00:00:00Z'),
      backfillComplete: false,
    },
  ],
};

function mountProcessing(status = pendingStatus, demoMode = false) {
  return mount(AdminProcessingSection, {
    props: { googleHealthStatus: status, demoMode },
    global: {
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        IndexerStatusTab: { template: '<div><slot name="connections" />Indexers and track processing jobs</div>' },
      },
    },
  });
}

describe('AdminProcessingSection', () => {
  it('keeps renewal recovery available even before the first sync has produced history', () => {
    const wrapper = mountProcessing({
      configurationState: 'CONFIGURED',
      connectionState: 'RECONNECT_REQUIRED',
      authorizationAction: 'REAUTHORIZE',
      refreshTokenExpiresAt: new Date('2000-01-01T00:00:00Z'),
      job: { state: 'IDLE' },
    });
    expect(wrapper.text()).toContain('Google Health authorization has expired.');
    expect(wrapper.text()).toContain('Reauthorize Google Health');
    expect(wrapper.text()).toContain('Google limits access to seven days');
    expect(wrapper.text()).toContain('Synchronization is paused until authorization is renewed.');
    expect(wrapper.text()).not.toContain('continue in scheduled cycles');
    wrapper.unmount();
  });

  it('keeps health history compact alongside regular processing and expands on request', async () => {
    const wrapper = mountProcessing();
    const progress = wrapper.get('[data-testid="google-health-sync-progress"]');

    expect(wrapper.get('h2').text()).toBe('Processing');
    expect(wrapper.text()).toContain('Indexers and track processing jobs');
    expect(progress.element.closest('details')).toBeNull();
    expect(progress.isVisible()).toBe(true);
    expect(progress.text()).toContain('History pending');
    expect(progress.get('.processing-task__toggle').attributes('aria-expanded')).toBe('false');
    await progress.get('.processing-task__toggle').trigger('click');
    expect(progress.get('.processing-task__toggle').attributes('aria-expanded')).toBe('true');
    expect(progress.text()).toContain('Heart-rate samples checked back to Jun 25, 2026.');
    expect(progress.text()).not.toContain('100%');
    wrapper.unmount();
  });

  it('keeps the health job visible after a pass finishes until all history is complete', async () => {
    const wrapper = mountProcessing({
      ...pendingStatus,
      job: { state: 'RUNNING', totalUnits: 140, completedUnits: 70 },
    });
    expect(wrapper.get('progress').attributes('value')).toBe('50');

    await wrapper.setProps({ googleHealthStatus: pendingStatus });
    expect(wrapper.get('[data-testid="google-health-sync-progress"]').text()).toContain('History pending');
    expect(wrapper.find('progress').exists()).toBe(false);

    await wrapper.setProps({
      googleHealthStatus: {
        ...pendingStatus,
        syncStates: [{ ...pendingStatus.syncStates![0], backfillComplete: true }],
      },
    });
    expect(wrapper.get('[data-testid="google-health-sync-progress"]').text()).toContain(
      'The configured history is synchronized.'
    );
    expect(wrapper.text()).not.toContain('History pending');
    wrapper.unmount();
  });

  it.each([0, 1])('colors active health progress by route failures: %i', (failedCount) => {
    const wrapper = mountProcessing({
      ...pendingStatus,
      job: { state: 'RUNNING', totalUnits: 100, completedUnits: 50 },
      routeSummary: { failedCount },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Running');
    expect(wrapper.get('progress').attributes('value')).toBe('50');
    expect(wrapper.get('.processing-task').classes().includes('processing-task--has-attention')).toBe(failedCount > 0);
    wrapper.unmount();
  });

  it('shows both lanes without presenting growing cycle totals as a percentage', async () => {
    const wrapper = mountProcessing({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      job: {
        state: 'RUNNING',
        totalUnits: 1,
        completedUnits: 1,
        recordsProcessed: 200,
        statistics: { totalFastStreams: 2, completeFastStreams: 1, fetched: 250, changed: 10, deleted: 0 },
      },
      mirrorSummary: { recordCount: 0 },
      synchronization: {
        snapshotAt: new Date('2026-10-08T12:00:00Z'),
        streams: [
          {
            dataType: 'heart-rate',
            representation: 'HEART_RATE_SAMPLE',
            pendingDays: 5,
            parkedDays: 0,
            fullPass: { state: 'ACTIVE', cycles: 2 },
          },
          { dataType: 'sleep', representation: 'RAW', pendingDays: 3, parkedDays: 0, fullPass: { state: 'COMPLETED' } },
        ],
      },
    });
    expect(wrapper.get('[aria-label="Recent data refresh"]').text()).toContain('Full refresh pending for 1 data type');
    expect(wrapper.get('[aria-label="Historical checks"]').text()).toContain('8 day checks remaining');
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBeUndefined();
    expect(wrapper.text()).not.toContain('100%');
    const caption = wrapper.get('.processing-task__caption');
    expect(caption.text()).toContain('200 records processed this cycle');
    expect(caption.get('.animated-count').isVisible()).toBe(true);
    expect(wrapper.get('[data-testid="google-health-sync-details"]').isVisible()).toBe(false);
    await wrapper.get('.processing-task__toggle').trigger('click');
    expect(wrapper.text()).toContain('0 unique records');
    expect(wrapper.get('[aria-label="Current cycle record counts"]').text()).toContain('Content updated10');
    expect(wrapper.get('.processing-facts .animated-count').text()).toBe(caption.get('.animated-count').text());
    const streamRows = wrapper.findAll('.gh-stream');
    expect(streamRows).toHaveLength(2);
    expect(streamRows.every((row) => !row.attributes('open'))).toBe(true);
    await wrapper.get('input[type="search"]').setValue('sleep');
    expect(wrapper.findAll('.gh-stream')).toHaveLength(1);
    expect(wrapper.get('.gh-stream summary').text()).toContain('Sleep');
    wrapper.unmount();
  });

  it('keeps an idle unfinished recent refresh pending even when history is complete', () => {
    const wrapper = mountProcessing({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      job: { state: 'COMPLETED' },
      synchronization: {
        streams: [{ dataType: 'sleep', representation: 'RAW', pendingDays: 0, fullPass: { state: 'ACTIVE' } }],
      },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Pending');
    expect(wrapper.get('[aria-label="Historical checks"]').text()).toContain('No historical checks pending');
    wrapper.unmount();
  });

  it('shows a quota pause and retry details instead of pretending the job is complete', async () => {
    const wrapper = mountProcessing({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      job: { state: 'COMPLETED' },
      synchronization: {
        cooldownUntil: new Date(Date.now() + 60000),
        streams: [
          {
            dataType: 'sleep',
            representation: 'RAW',
            pendingDays: 1,
            parkedDays: 1,
            failures: [
              {
                state: 'PARKED',
                attempts: 3,
                start: new Date('2026-09-30T00:00:00Z'),
                end: new Date('2026-10-01T00:00:00Z'),
              },
            ],
          },
        ],
      },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Paused');
    expect(wrapper.text()).toContain('No requests run during this cooldown.');
    await wrapper.get('.processing-task__toggle').trigger('click');
    await wrapper.get('.gh-stream summary').trigger('click');
    expect(wrapper.text()).toContain('Use Retry failed ranges in Manage connection.');
    expect(wrapper.text()).not.toContain('Retry during a cycle after');
    wrapper.unmount();
  });

  it('allows filtering attention states while retaining successful streams', async () => {
    const wrapper = mountProcessing({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      job: { state: 'COMPLETED', statistics: { budgetLimited: true } },
      synchronization: {
        streams: [
          { dataType: 'sleep', representation: 'RAW', pendingDays: 2, budgetLimited: true },
          { dataType: 'steps', representation: 'DAILY_ROLLUP', pendingDays: 0 },
        ],
      },
    });
    expect(wrapper.text()).toContain('Recent coverage is incomplete');
    await wrapper.get('.processing-task__toggle').trigger('click');
    await wrapper.get('input[type="checkbox"]').setValue(true);
    expect(wrapper.findAll('.gh-stream')).toHaveLength(1);
    await wrapper.get('input[type="checkbox"]').setValue(false);
    expect(wrapper.findAll('.gh-stream')).toHaveLength(2);
    wrapper.unmount();
  });

  it('keeps cached processing information visible when a status update fails', () => {
    const wrapper = mountProcessing();
    wrapper.setProps({ googleHealthStatusError: 'Server unavailable' });
    return wrapper.vm.$nextTick().then(() => {
      expect(wrapper.text()).toContain('Showing the last received status.');
      expect(wrapper.get('[data-testid="google-health-sync-progress"]').exists()).toBe(true);
      wrapper.unmount();
    });
  });

  it('shows route failures independently of completed record checks', () => {
    const wrapper = mountProcessing({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      job: { state: 'COMPLETED' },
      routeSummary: { failedCount: 2, retainedCount: 5 },
      synchronization: {
        streams: [{ dataType: 'sleep', representation: 'RAW', pendingDays: 0, fullPass: { state: 'COMPLETED' } }],
      },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Needs attention');
    expect(wrapper.get('[aria-label="Exercise routes"]').text()).toContain('2 exports need retry');
    expect(wrapper.text()).toContain('5 published routes retained');
    wrapper.unmount();
  });

  it('leaves regular processing available without exposing health data in demo mode', () => {
    const wrapper = mountProcessing(pendingStatus, true);
    expect(wrapper.text()).toContain('Indexers and track processing jobs');
    expect(wrapper.find('[data-testid="google-health-sync-progress"]').exists()).toBe(false);
    wrapper.unmount();
  });
});

beforeEach(() => setActivePinia(createPinia()));
