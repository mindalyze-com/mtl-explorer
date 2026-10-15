import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AllVersionsReport from '@/components/admin/AllVersionsReport.vue';
import { writeTextToClipboard } from '@/utils/clipboard';

vi.mock('@/utils/clipboard', () => ({ writeTextToClipboard: vi.fn() }));
const report = 'MTL Explorer — all versions\nApplication server\nImage version: 1.501\nDatabase\nPostgreSQL: 18.3';
function mountReport(loading = false) {
  return mount(AllVersionsReport, {
    props: { report, loading },
    global: {
      stubs: {
        Button: { props: ['label', 'disabled'], template: '<button :disabled="disabled">{{ label }}</button>' },
        Dialog: {
          props: ['visible'],
          template: '<section v-if="visible" role="dialog"><slot /><slot name="footer" /></section>',
        },
      },
    },
  });
}

describe('copy all versions preview', () => {
  afterEach(() => vi.resetAllMocks());

  it('previews and copies the exact same snapshot despite background refreshes', async () => {
    const wrapper = mountReport();
    await wrapper.get('button').trigger('click');
    expect(writeTextToClipboard).not.toHaveBeenCalled();
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe(report);
    expect(wrapper.get('textarea').attributes('readonly')).toBeDefined();
    await wrapper.setProps({ report: 'A newer background snapshot' });
    await wrapper.findAll('[role="dialog"] button').at(-1)!.trigger('click');
    await flushPromises();
    expect(writeTextToClipboard).toHaveBeenCalledWith(report);
    expect(wrapper.get('[role="status"]').text()).toBe('All versions copied.');
  });

  it('keeps the report selectable when clipboard access fails', async () => {
    vi.mocked(writeTextToClipboard).mockRejectedValue(new Error('Clipboard denied'));
    const wrapper = mountReport();
    await wrapper.get('button').trigger('click');
    await wrapper.findAll('[role="dialog"] button').at(-1)!.trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('Copy failed');
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe(report);
    expect(wrapper.get('[role="status"]').text()).toBe('');
  });

  it('prevents capturing an incomplete report while versions are loading', () => {
    expect(mountReport(true).get('button').attributes('disabled')).toBeDefined();
  });
});
