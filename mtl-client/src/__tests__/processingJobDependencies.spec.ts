import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import { JobSummaryDtoFromJSON, ProcessingStatusDtoFromJSON } from 'x8ing-mtl-api-typescript-fetch';
import ProcessingJobDependencies from '@/components/admin/ProcessingJobDependencies.vue';
import type { JobSummary } from '@/utils/serverAdminApi';
const labels: Record<string, string> = { GPS: 'Track files', duplicate: 'Duplicate detection' };
const targetLabel = (blocker: { id?: string }) => labels[blocker.id ?? ''] ?? '';

describe('Job dependency details', () => {
  it('shows only this job dependencies and links a waiting prerequisite', async () => {
    const data = ProcessingStatusDtoFromJSON({
      dependencies: [
        { fromKind: 'INDEX', fromId: 'GPS', toJob: 'exploration', kind: 'GATE', scope: 'WHOLE_JOB' },
        { fromKind: 'JOB', fromId: 'duplicate', toJob: 'exploration', kind: 'GATE', scope: 'WHOLE_JOB' },
        { fromKind: 'INDEX', fromId: 'MEDIA', toJob: 'mediaCorrelation', kind: 'GATE', scope: 'WHOLE_JOB' },
      ],
    });
    const job = JobSummaryDtoFromJSON({
      job: 'exploration',
      state: 'WAITING',
      blockedBy: [{ kind: 'JOB', id: 'duplicate' }],
    }) as JobSummary;
    const wrapper = mount(ProcessingJobDependencies, {
      props: {
        kind: 'JOB',
        id: 'exploration',
        blockedBy: job.blockedBy,
        dependencies: data.dependencies!,
        targetLabel,
      },
    });
    expect(wrapper.text()).toContain('Depends on');
    expect(wrapper.text()).toContain('Track files');
    expect(wrapper.text()).toContain('Duplicate detection');
    expect(wrapper.text()).not.toContain('Media files');
    expect(wrapper.find('.bi-clock').exists()).toBe(true);
    await wrapper.findAll('button')[1]!.trigger('click');
    expect(wrapper.emitted('reveal')).toEqual([['JOB:duplicate']]);
    wrapper.unmount();
  });
  it('describes retriggers as updates rather than blocking dependencies', () => {
    const data = ProcessingStatusDtoFromJSON({
      dependencies: [
        { fromKind: 'JOB', fromId: 'duplicate', toJob: 'mediaCorrelation', kind: 'RETRIGGER', scope: 'ITEMS' },
      ],
    });
    const wrapper = mount(ProcessingJobDependencies, {
      props: { kind: 'JOB', id: 'mediaCorrelation', dependencies: data.dependencies!, targetLabel },
    });
    expect(wrapper.text()).toContain('Updates when');
    expect(wrapper.text()).toContain('changes.');
    expect(wrapper.text()).not.toContain('Depends on');
    wrapper.unmount();
  });
  it('lists the jobs an import is needed by and marks those waiting for it', async () => {
    const data = ProcessingStatusDtoFromJSON({
      dependencies: [
        { fromKind: 'INDEX', fromId: 'GPS', toJob: 'duplicate', kind: 'GATE', scope: 'WHOLE_JOB' },
        { fromKind: 'INDEX', fromId: 'GPS', toJob: 'exploration', kind: 'GATE', scope: 'WHOLE_JOB' },
        { fromKind: 'JOB', fromId: 'duplicate', toJob: 'exploration', kind: 'GATE', scope: 'WHOLE_JOB' },
      ],
    });
    const labelsById: Record<string, string> = { duplicate: 'Duplicate detection', exploration: 'Exploration score' };
    const wrapper = mount(ProcessingJobDependencies, {
      props: {
        kind: 'INDEX',
        id: 'GPS',
        waitingDependents: ['duplicate'],
        dependencies: data.dependencies!,
        targetLabel: (blocker: { id?: string }) => labelsById[blocker.id ?? ''] ?? '',
      },
    });
    expect(wrapper.text()).toContain('Needed by');
    expect(wrapper.text()).not.toContain('Depends on');
    const [duplicate, exploration] = wrapper.findAll('button');
    expect(duplicate!.text()).toContain('Waiting');
    expect(exploration!.text()).not.toContain('Waiting');
    await exploration!.trigger('click');
    expect(wrapper.emitted('reveal')).toEqual([['JOB:exploration']]);
    wrapper.unmount();
  });
});
