import { describe, expect, it } from 'vitest';
import { mount, RouterLinkStub } from '@vue/test-utils';
import { JobSummaryDtoFromJSON } from 'x8ing-mtl-api-typescript-fetch';
import ProcessingJobRow from '@/components/admin/ProcessingJobRow.vue';
import type { JobSummary } from '@/utils/serverAdminApi';
import { rowNotice } from '@/utils/processingJobPresentation';
function report(value: object): JobSummary {
  return JobSummaryDtoFromJSON({
    job: 'exploration',
    pending: 120,
    done: 880,
    total: 1000,
    progressPercent: 88,
    failed: 0,
    unavailable: 0,
    progressKnown: true,
    ...value,
  }) as JobSummary;
}
describe('Processing job row', () => {
  it('keeps totals, waiting counts and details on the same snapshot during ingestion', async () => {
    const job = (total: number) =>
      report({
        job: 'energy',
        state: 'WAITING',
        done: 0,
        pending: total,
        total,
        progressPercent: 0,
        work: { ready: 0, waiting: total, retrying: 0 },
        blockedBy: [{ kind: 'JOB', id: 'activityType', scope: 'ITEMS', resolution: 'AUTOMATIC' }],
      });
    const wrapper = mount(ProcessingJobRow, {
      props: { job: job(1329), targetLabel: () => 'Activity classification' },
    });
    for (const total of [1358, 4752, 4694]) {
      await wrapper.setProps({ job: job(total) });
      const count = total.toLocaleString('en-US');
      expect(wrapper.get('.processing-task__caption').text()).toBe(`0 of ${count} tracks processed`);
      expect(wrapper.get('.processing-task__notice').text()).toBe(
        `${count} tracks wait for Activity classification. Resumes automatically.`
      );
      expect(wrapper.get('.processing-facts').text()).toContain(`Pending${count}`);
      expect(wrapper.get('[role="progressbar"]').attributes('aria-valuetext')).toBe(`0% complete, ${count} waiting`);
    }
    wrapper.unmount();
  });
  it('shows a stationary waiting icon and opens its prerequisite without hiding the reason', async () => {
    const wrapper = mount(ProcessingJobRow, {
      props: {
        job: report({
          state: 'WAITING',
          work: { ready: 0, waiting: 120, retrying: 0 },
          blockedBy: [{ kind: 'JOB', id: 'duplicate', scope: 'WHOLE_JOB', resolution: 'AUTOMATIC' }],
        }),
        targetLabel: () => 'Duplicate detection',
      },
    });
    expect(wrapper.find('.processing-task__status .bi-clock').exists()).toBe(true);
    expect(wrapper.text()).toContain('Waiting for');
    expect(wrapper.text()).toContain('Resumes automatically');
    const meter = wrapper.get('[role="progressbar"]');
    expect(meter.attributes('aria-valuenow')).toBe('88');
    expect(meter.attributes('aria-valuetext')).toBe('88% complete, 120 waiting');
    expect(meter.find('.processing-segment--waiting').attributes('style')).toContain('12%');
    // A single kind of outstanding work needs no legend.
    expect(wrapper.find('.processing-breakdown').exists()).toBe(false);
    await wrapper.get('.processing-blocker button').trigger('click');
    expect(wrapper.emitted('reveal')).toEqual([['JOB:duplicate']]);
    wrapper.unmount();
  });
  it('shows synthetic health preparation without inventing a total and then measures days', async () => {
    const wrapper = mount(ProcessingJobRow, {
      props: {
        job: report({ job: 'demoHealth', state: 'WAITING', progressKnown: false, unit: 'days' }),
        targetLabel: () => 'Energy calculation',
      },
    });
    // Synthetic data reads like the real connection; only the caption marks it.
    expect(wrapper.get('.processing-task__name').text()).toBe('Google Health (beta)');
    expect(wrapper.get('.processing-task__caption').text()).toContain(
      'Synthetic demo data · Waiting for track processing.'
    );
    expect(wrapper.text()).not.toContain('days processed');
    expect(wrapper.find('progress').exists()).toBe(false);
    expect(wrapper.find('.processing-facts').exists()).toBe(false);
    expect(wrapper.find('.processing-technical').exists()).toBe(false);
    await wrapper.setProps({
      job: report({
        job: 'demoHealth',
        unit: 'days',
        state: 'RUNNING',
        pending: 2,
        done: 5,
        total: 7,
        progressPercent: 71,
      }),
    });
    expect(wrapper.text()).toContain('5 of 7 days processed');
    expect(wrapper.get('progress').attributes('value')).toBe('71');
    wrapper.unmount();
  });
  it('keeps attention independent of execution state', () => {
    const wrapper = mount(ProcessingJobRow, {
      props: {
        job: report({ state: 'RUNNING', attentionRequired: 2 }),
        targetLabel: () => 'Other work',
      },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Running');
    expect(wrapper.get('.processing-task__attention').text()).toContain('Needs attention');
    wrapper.unmount();
  });
  it('shows mixed outstanding work as a legend and counts item waits', () => {
    const wrapper = mount(ProcessingJobRow, {
      props: {
        job: report({
          job: 'activityType',
          state: 'RUNNING',
          pending: 160,
          done: 840,
          progressPercent: 84,
          work: { ready: 40, waiting: 120, retrying: 0 },
          blockedBy: [{ kind: 'JOB', id: 'duplicate', scope: 'ITEMS', resolution: 'AUTOMATIC' }],
        }),
        targetLabel: () => 'Duplicate detection',
      },
    });
    expect(wrapper.get('.processing-breakdown').text()).toBe('40 ready · 120 waiting');
    expect(wrapper.text()).toContain('120 tracks wait for Duplicate detection. Resumes automatically.');
    wrapper.unmount();
  });
  it('points to a shared wait instead of repeating its explanation', () => {
    const job = report({
      state: 'WAITING',
      blockedBy: [{ kind: 'INDEX', id: 'GPS', scope: 'WHOLE_JOB', resolution: 'AUTOMATIC' }],
    });
    const wrapper = mount(ProcessingJobRow, {
      props: {
        job,
        notice: rowNotice(job, new Map(), new Set(['INDEX:GPS'])),
        targetLabel: () => 'Track files',
      },
    });
    expect(wrapper.get('.processing-task__notice').text()).toBe('Starts after Track files.');
    expect(wrapper.text()).not.toContain('Resumes automatically');
    wrapper.unmount();
  });
  it('treats finished work that needs an activity as attention with a way to find the tracks', () => {
    const wrapper = mount(ProcessingJobRow, {
      props: {
        job: report({
          job: 'energy',
          state: 'COMPLETE',
          pending: 0,
          done: 988,
          unavailable: 12,
          total: 1000,
          progressPercent: 100,
          attentionRequired: 12,
          unavailableReasons: { noDataPoints: 0, unsupportedTiming: 0, missingActivity: 12, missingCalculation: 0 },
          blockedBy: [{ kind: 'USER', id: 'track-review', reason: 'MISSING_ACTIVITY', resolution: 'USER_ACTION' }],
        }),
        targetLabel: () => 'Other work',
      },
      global: { stubs: { RouterLink: RouterLinkStub } },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Needs attention');
    expect(wrapper.find('.processing-task__status .bi-clock').exists()).toBe(false);
    expect(wrapper.get('.processing-blocker--action').text()).toContain('12 tracks need an activity.');
    expect(wrapper.getComponent(RouterLinkStub).props('to')).toEqual({ name: 'stats' });
    wrapper.unmount();
  });
  it('says when queued work starts, but not when it waits for the shared slot', () => {
    const nextRunAt = new Date(Date.now() + 90_000).toISOString();
    const queued = mount(ProcessingJobRow, {
      props: {
        job: report({ state: 'QUEUED', work: { ready: 120, waiting: 0, retrying: 0 }, nextRunAt }),
        targetLabel: () => 'Duplicate detection',
      },
    });
    expect(queued.text()).toContain('Starts in 2 minutes.');
    expect(queued.find('.processing-task__status .bi-hourglass-split').exists()).toBe(true);
    queued.unmount();
    const behindLock = mount(ProcessingJobRow, {
      props: {
        job: report({
          state: 'QUEUED',
          work: { ready: 120, waiting: 0, retrying: 0 },
          nextRunAt,
          blockedBy: [{ kind: 'LOCK', id: 'track-processing', heldBy: 'duplicate', resolution: 'AUTOMATIC' }],
        }),
        targetLabel: () => 'Duplicate detection',
      },
    });
    expect(behindLock.get('.processing-task__notice').text()).toBe('Queued behind Duplicate detection.');
    behindLock.unmount();
  });
});
