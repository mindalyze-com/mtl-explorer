import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import GoogleHealthSyncProgress from '@/features/health/components/GoogleHealthSyncProgress.vue';

describe('GoogleHealthSyncProgress', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it.each(['card', 'row'] as const)(
    'smooths %s batch counts across unchanged polls and automatic continuation jobs',
    async (presentation) => {
      vi.useFakeTimers();
      vi.setSystemTime(0);
      vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => setTimeout(() => callback(0), 10));
      vi.stubGlobal('cancelAnimationFrame', (id: ReturnType<typeof setTimeout>) => clearTimeout(id));
      vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
      const job = { id: 'first-pass', syncId: 'first-sync', state: 'RUNNING' as const, recordsProcessed: 299_901 };
      const wrapper = mount(GoogleHealthSyncProgress, {
        props: { status: { job }, presentation },
        global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
      });
      const count = () => wrapper.get('.animated-count').text();

      for (let poll = 0; poll < 15; poll++) {
        vi.advanceTimersByTime(2_000);
        await wrapper.setProps({ status: { job: { ...job } } });
      }
      const batch = { ...job, recordsProcessed: 329_901 };
      await wrapper.setProps({ status: { job: batch } });
      vi.advanceTimersByTime(7_200);
      await nextTick();
      expect(count()).toBe('317,901');

      await wrapper.setProps({ status: { job: { ...batch, id: 'continuation' } } });
      expect(count()).toBe('317,901');
      vi.advanceTimersByTime(800);
      await nextTick();
      expect(count()).toBe('329,901');

      await wrapper.setProps({
        status: { job: { ...job, id: 'new-pass', syncId: 'new-sync', recordsProcessed: 400_000 } },
      });
      expect(count()).toBe('400,000');
      await wrapper.setProps({
        status: { job: { ...job, id: 'new-pass', syncId: 'new-sync', recordsProcessed: 0 } },
      });
      expect(count()).toBe('0');
      wrapper.unmount();
      expect(vi.getTimerCount()).toBe(0);
    }
  );

  it('shows finalized empty days, parked gaps, budget limits, and retained routes', async () => {
    const wrapper = mount(GoogleHealthSyncProgress, {
      props: {
        status: {
          connectionState: 'CONNECTED',
          routeSummary: { retainedCount: 2 },
          synchronization: {
            snapshotAt: new Date('2026-10-08T12:00:00Z'),
            streams: [
              {
                dataType: 'sleep',
                representation: 'RAW',
                pendingDays: 3,
                parkedDays: 1,
                budgetLimited: true,
                finalizedBackTo: new Date('2026-09-28T00:00:00Z'),
                fullPass: { state: 'ACTIVE', cycles: 2, skipped: 1, handedOver: 1 },
                failures: [
                  { state: 'PARKED', start: new Date('2026-09-27T00:00:00Z'), end: new Date('2026-09-28T00:00:00Z') },
                ],
              },
            ],
          },
        },
        showHistory: true,
      },
    });
    await wrapper.get('button').trigger('click');
    expect(wrapper.text()).toContain('Finalized back to');
    expect(wrapper.text()).toContain('3 day checks remaining');
    expect(wrapper.text()).toContain('1 day checks parked');
    expect(wrapper.text()).toContain('In progress · 2 cycles');
    expect(wrapper.text()).toContain('Fast coverage is incomplete');
    expect(wrapper.text()).toContain('2 routes are no longer in Google Health but are kept here');
    expect(wrapper.text()).toContain('Skipped parts need Retry failed ranges.');
    wrapper.unmount();
  });

  it('shows elapsed time from the server start and updates with status polling', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-03T12:02:05Z'));
    const job = { state: 'RUNNING' as const, startedAt: new Date('2026-09-03T12:00:00Z') };
    const wrapper = mount(GoogleHealthSyncProgress, { attachTo: document.body, props: { status: { job } } });
    expect(wrapper.get('[data-testid="google-health-sync-details"]').isVisible()).toBe(false);
    await wrapper.get('button').trigger('click');
    expect(wrapper.text()).toContain('Elapsed: 2m 05s');

    vi.setSystemTime(new Date('2026-09-03T12:03:05Z'));
    await wrapper.setProps({ status: { job: { ...job } } });
    expect(wrapper.text()).toContain('Elapsed: 3m 05s');
    expect(wrapper.text()).not.toContain('estimated');
    wrapper.unmount();
  });

  it('keeps current-pass progress compact and expands window and mirror details', async () => {
    const wrapper = mount(GoogleHealthSyncProgress, {
      attachTo: document.body,
      props: {
        status: {
          connectionState: 'CONNECTED',
          mirrorSummary: {
            recordCount: 2_700_000,
            earliestRecord: new Date('2025-08-25T19:27:03Z'),
            latestRecord: new Date('2026-09-03T13:08:51Z'),
          },
          job: {
            state: 'RUNNING',
            kind: 'REGULAR',
            totalUnits: 80,
            completedUnits: 20,
            currentDataType: 'heart-rate',
            currentRepresentation: 'HEART_RATE_SAMPLE',
            currentWindowStart: new Date('2026-08-20T00:00:00Z'),
            currentWindowEnd: new Date('2026-09-03T00:00:00Z'),
            currentWindowImported: 1234,
            recordsProcessed: 179946,
          },
          syncStates: [],
        },
      },
    });

    expect(wrapper.text()).toContain('Google Health sync');
    expect(wrapper.text()).toContain('179,946 records processed');
    expect(wrapper.get('[data-testid="google-health-sync-details"]').isVisible()).toBe(false);
    expect(wrapper.get('button').attributes('aria-expanded')).toBe('false');
    await wrapper.get('button').trigger('click');
    expect(wrapper.get('[data-testid="google-health-sync-details"]').isVisible()).toBe(true);
    expect(wrapper.get('button').attributes('aria-expanded')).toBe('true');
    expect(wrapper.text()).toContain('Heart Rate · Samples');
    expect(wrapper.text()).toContain('Current pass: 20 of 80 steps');
    expect(wrapper.text()).toContain('Current window: Aug 20, 2026–Sep 3, 2026');
    expect(wrapper.text()).toContain('1,234 records processed in this window');
    expect(wrapper.text()).toContain('Stored locally');
    expect(wrapper.text()).toContain('2,700,000 unique records');
    expect(wrapper.text()).toContain('Aug 25, 2025–Sep 3, 2026');
    expect(wrapper.text()).toContain('25%');
    expect(wrapper.get('progress').attributes('value')).toBe('25');
    wrapper.unmount();
  });

  it('starts collapsed when no sync job is active and lets the user expand coverage', async () => {
    const wrapper = mount(GoogleHealthSyncProgress, {
      attachTo: document.body,
      props: {
        showHistory: true,
        status: {
          connectionState: 'CONNECTED',
          mirrorSummary: {
            recordCount: 2_700_000,
            earliestRecord: new Date('2025-08-25T19:27:03Z'),
            latestRecord: new Date('2026-09-03T13:08:51Z'),
          },
          job: { state: 'COMPLETED' },
          syncStates: [
            {
              dataType: 'heart-rate',
              oldestSyncedTime: new Date('2026-08-01T00:00:00Z'),
              lastSuccessAt: new Date('2026-09-03T00:00:00Z'),
              backfillComplete: false,
            },
            {
              dataType: 'sleep',
              oldestSyncedTime: new Date('2026-07-01T00:00:00Z'),
              lastSuccessAt: new Date('2026-09-03T00:00:00Z'),
              backfillComplete: false,
            },
          ],
        },
      },
    });

    expect(wrapper.text()).toContain('History coverage');
    expect(wrapper.get('[data-testid="google-health-sync-details"]').isVisible()).toBe(false);
    await wrapper.get('button').trigger('click');
    expect(wrapper.get('[data-testid="google-health-sync-details"]').isVisible()).toBe(true);
    expect(wrapper.text()).toContain('History import is incomplete.');
    expect(wrapper.text()).toContain('Every data type has been checked back to Aug 1, 2026.');
    expect(wrapper.text()).toContain('Some data types have already been checked further back.');
    expect(wrapper.text()).toContain('History pending');
    expect(wrapper.find('progress').exists()).toBe(false);
    wrapper.unmount();
  });

  it('marks history complete only when every sync state completed backfill', () => {
    const wrapper = mount(GoogleHealthSyncProgress, {
      props: {
        showHistory: true,
        status: {
          connectionState: 'CONNECTED',
          job: { state: 'COMPLETED' },
          syncStates: [
            {
              dataType: 'sleep',
              oldestSyncedTime: new Date('1970-01-01T00:00:00Z'),
              lastSuccessAt: new Date('2026-09-03T00:00:00Z'),
              backfillComplete: true,
            },
          ],
        },
      },
    });

    expect(wrapper.text()).toContain('The configured history is synchronized.');
    expect(wrapper.text()).toContain('Complete');
    wrapper.unmount();
  });

  it('keeps the chosen details open across cycle transitions', async () => {
    const status = {
      connectionState: 'CONNECTED' as const,
      job: { state: 'COMPLETED' as const, totalUnits: 140, completedUnits: 140 },
      nextRegularSync: new Date('2026-09-03T20:24:00Z'),
      syncStates: [
        {
          dataType: 'heart-rate',
          representation: 'HEART_RATE_SAMPLE',
          oldestSyncedTime: new Date('2026-06-25T00:00:00Z'),
          backfillComplete: false,
        },
      ],
    };
    const wrapper = mount(GoogleHealthSyncProgress, { attachTo: document.body, props: { status, compact: true } });

    expect(wrapper.text()).toContain('History pending');
    expect(wrapper.get('[data-testid="google-health-sync-details"]').isVisible()).toBe(false);
    await wrapper.get('button').trigger('click');
    expect(wrapper.get('[data-testid="google-health-sync-details"]').isVisible()).toBe(true);
    expect(wrapper.text()).toContain('Heart-rate samples checked back to Jun 25, 2026.');
    expect(wrapper.text()).toContain('Next scheduled check:');
    expect(wrapper.text()).not.toContain('100%');
    expect(wrapper.find('progress').exists()).toBe(false);

    await wrapper.setProps({ status: { ...status, job: { state: 'RUNNING' } } });
    expect(wrapper.get('[data-testid="google-health-sync-details"]').isVisible()).toBe(true);
    expect(wrapper.text()).toContain('Older history advances within each scheduled cycle.');
    expect(wrapper.text()).not.toContain('Next scheduled check:');

    await wrapper.setProps({
      status: { ...status, syncStates: [{ ...status.syncStates[0], backfillComplete: true }] },
    });
    expect(wrapper.find('[data-testid="google-health-sync-progress"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('does not promise automatic progress while disconnected', () => {
    const wrapper = mount(GoogleHealthSyncProgress, {
      props: {
        status: {
          connectionState: 'RECONNECT_REQUIRED',
          syncStates: [{ dataType: 'heart-rate', backfillComplete: false }],
        },
      },
    });
    expect(wrapper.text()).toContain('Reconnect Google Health to continue importing history.');
    expect(wrapper.text()).not.toContain('continue automatically');
    wrapper.unmount();
  });

  it('shows failed history as readable ranges without internal mode names', async () => {
    const wrapper = mount(GoogleHealthSyncProgress, {
      attachTo: document.body,
      props: {
        showHistory: true,
        status: {
          connectionState: 'CONNECTED',
          job: {
            state: 'COMPLETED_WITH_ERRORS',
            error:
              'exercise (RAW): Google Health is temporarily unavailable; calories-in-heart-rate-zone (DAILY_ROLLUP): Google Health is temporarily unavailable',
          },
          syncStates: [
            {
              dataType: 'exercise',
              representation: 'RAW',
              activeWindowStart: new Date('2026-05-22T00:00:00Z'),
              activeWindowEnd: new Date('2026-08-20T00:00:00Z'),
              lastError: 'Google Health is temporarily unavailable',
            },
            {
              dataType: 'calories-in-heart-rate-zone',
              representation: 'DAILY_ROLLUP',
              activeWindowStart: new Date('2026-08-07T00:00:00Z'),
              activeWindowEnd: new Date('2026-08-21T00:00:00Z'),
              lastError: 'Google Health is temporarily unavailable',
            },
          ],
        },
      },
    });

    expect(wrapper.text()).toContain('2 history ranges need another attempt.');
    expect(wrapper.get('.gh-sync-progress__failed-ranges').isVisible()).toBe(true);
    expect(wrapper.get('[data-testid="google-health-sync-details"]').isVisible()).toBe(false);
    expect(wrapper.text()).toContain('Exercise');
    expect(wrapper.text()).toContain('Heart-rate-zone calories');
    expect(wrapper.text()).toContain('May 22, 2026');
    expect(wrapper.text()).toContain('Aug 21, 2026');
    expect(wrapper.text()).toContain(
      'Imported data is kept. Waiting parts are retried automatically. Skipped parts need Retry failed ranges.'
    );
    expect(wrapper.text()).not.toContain('RAW');
    expect(wrapper.text()).not.toContain('DAILY_ROLLUP');
    wrapper.unmount();
  });

  it('keeps failed ranges and expanded details visible when a cycle finishes', async () => {
    const status = {
      connectionState: 'CONNECTED' as const,
      job: { state: 'RUNNING' as const, recordsProcessed: 100 },
      syncStates: [{ dataType: 'sleep', lastError: 'Temporary failure', backfillComplete: false }],
    };
    const wrapper = mount(GoogleHealthSyncProgress, {
      attachTo: document.body,
      props: { status, showHistory: true },
    });
    await wrapper.get('button').trigger('click');
    await wrapper.setProps({ status: { ...status, job: { ...status.job, state: 'COMPLETED_WITH_ERRORS' } } });
    expect(wrapper.get('[data-testid="google-health-sync-details"]').isVisible()).toBe(true);
    expect(wrapper.get('.gh-sync-progress__failed-ranges').isVisible()).toBe(true);
    expect(wrapper.text()).toContain('Imported data is kept.');
    wrapper.unmount();
  });

  it('omits the Details control when there is no optional content', () => {
    const wrapper = mount(GoogleHealthSyncProgress, {
      props: { showHistory: true, status: { connectionState: 'CONNECTED', job: { state: 'IDLE' } } },
    });
    expect(wrapper.find('button').exists()).toBe(false);
    expect(wrapper.find('[data-testid="google-health-sync-details"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('announces job state changes without including animated progress in the live region', async () => {
    const wrapper = mount(GoogleHealthSyncProgress, {
      props: { status: { job: { state: 'RUNNING', recordsProcessed: 100 } } },
    });
    expect(wrapper.get('[aria-live="polite"]').text()).toBe('Google Health sync running.');
    expect(wrapper.get('[aria-live="polite"]').find('.animated-count').exists()).toBe(false);
    await wrapper.setProps({ status: { job: { state: 'COMPLETED_WITH_ERRORS', recordsProcessed: 200 } } });
    expect(wrapper.get('[aria-live="polite"]').text()).toBe('Google Health sync completed with errors.');
    await wrapper.setProps({ status: { job: { state: 'COMPLETED', recordsProcessed: 200 } } });
    expect(wrapper.find('[data-testid="google-health-sync-progress"]').exists()).toBe(false);
    expect(wrapper.get('[aria-live="polite"]').text()).toBe('Google Health sync completed.');
    wrapper.unmount();
  });
});
