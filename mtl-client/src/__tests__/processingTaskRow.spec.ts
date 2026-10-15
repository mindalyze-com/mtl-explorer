import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ProcessingTaskRow from '@/components/admin/ProcessingTaskRow.vue';

describe('processing row disclosure', () => {
  const props = { label: 'Location search', icon: 'bi bi-search', state: 'running' as const, statusLabel: 'Running' };

  it('keeps details collapsed and retains the user choice across status updates', async () => {
    const wrapper = mount(ProcessingTaskRow, {
      attachTo: document.body,
      props,
      slots: { default: '<p>Imported places and service diagnostics</p>' },
    });
    const toggle = wrapper.get('.processing-task__toggle');
    expect(wrapper.get('.processing-task__details').isVisible()).toBe(false);
    expect(toggle.attributes('aria-expanded')).toBe('false');
    await toggle.trigger('click');
    expect(wrapper.get('.processing-task__details').isVisible()).toBe(true);
    expect(toggle.attributes('aria-controls')).toBe(wrapper.get('.processing-task__details').attributes('id'));
    await wrapper.setProps({ state: 'ready', statusLabel: 'Ready', summary: 'Search is available.' });
    expect(wrapper.get('.processing-task__details').isVisible()).toBe(true);
    expect(wrapper.get('.processing-task__summary').text()).toContain('Ready');
    wrapper.unmount();
  });

  it('shows unknown active progress without inventing a percentage or a completion value', async () => {
    const wrapper = mount(ProcessingTaskRow, {
      props: { ...props, indeterminate: true, progressLabel: 'In progress' },
    });
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBeUndefined();
    expect(wrapper.get('.processing-task__summary').text()).not.toContain('%');
    await wrapper.setProps({ indeterminate: false, state: 'unavailable', statusLabel: 'Unavailable' });
    expect(wrapper.find('progress').exists()).toBe(false);
    expect(wrapper.find('.processing-task__track > span').exists()).toBe(false);
    wrapper.unmount();
  });

  it('changes severity independently of running state and completion percentage', async () => {
    const wrapper = mount(ProcessingTaskRow, { props: { ...props, progress: 50 } });
    expect(wrapper.classes()).not.toContain('processing-task--has-attention');
    await wrapper.setProps({ needsAttention: true });
    expect(wrapper.classes()).toContain('processing-task--has-attention');
    expect(wrapper.get('.processing-task__status').text()).toBe('Running');
    expect(wrapper.get('progress').attributes('value')).toBe('50');
    await wrapper.setProps({ needsAttention: false });
    expect(wrapper.classes()).not.toContain('processing-task--has-attention');
    await wrapper.setProps({ state: 'attention', statusLabel: 'Needs attention', progress: 100 });
    expect(wrapper.classes()).toContain('processing-task--has-attention');
    expect(wrapper.get('progress').attributes('value')).toBe('100');
    wrapper.unmount();
  });

  it('identifies the scope of measured work and clamps invalid percentage limits', async () => {
    const wrapper = mount(ProcessingTaskRow, {
      props: { ...props, label: 'Google Health', progress: 42, progressScope: 'Current pass' },
    });
    expect(wrapper.get('progress').attributes('aria-label')).toBe('Google Health · Current pass');
    expect(wrapper.get('progress').attributes('value')).toBe('42');
    await wrapper.setProps({ progress: 150 });
    expect(wrapper.get('progress').attributes('value')).toBe('100');
    await wrapper.setProps({ progress: NaN });
    expect(wrapper.find('progress').exists()).toBe(false);
    wrapper.unmount();
  });
});
