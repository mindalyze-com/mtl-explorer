import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useSourceIssueStore } from '@/stores/sourceIssueStore';
import FileIndexerFailures from '@/components/admin/FileIndexerFailures.vue';

const mocks = vi.hoisted(() => ({ load: vi.fn() }));
vi.mock('@/utils/serverAdminApi', () => ({ getIndexerFileFailures: mocks.load }));
const response = {
  files: [{ id: 1, name: 'broken.gpx', path: 'recordings', message: 'No usable GPS observations' }],
  total: 26,
  page: 0,
  size: 25,
};
const create = () =>
  mount(FileIndexerFailures, {
    props: { index: 'GPS', count: 26, refreshKey: 0 },
    global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
  });

beforeEach(() => {
  setActivePinia(createPinia());
  mocks.load.mockReset();
  mocks.load.mockResolvedValue(response);
});

describe('failed file diagnostics', () => {
  it('loads on expansion, shows the saved reason and folder, and pages through all failures', async () => {
    const wrapper = create();
    expect(mocks.load).not.toHaveBeenCalled();
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(mocks.load).toHaveBeenCalledWith('GPS', 0, 25, expect.any(AbortSignal), undefined);
    expect(wrapper.text()).toContain('broken.gpx');
    expect(wrapper.text()).toContain('Folder: recordings');
    expect(wrapper.text()).toContain('No usable GPS observations');
    expect(wrapper.text()).toContain('rescan GPS files in Maintenance');
    mocks.load.mockResolvedValue({ ...response, files: [{ id: 2, name: 'last.gpx', path: '', message: '' }], page: 1 });
    await wrapper.get('.file-failures__pages button:last-child').trigger('click');
    await flushPromises();
    expect(mocks.load).toHaveBeenLastCalledWith('GPS', 1, 25, expect.any(AbortSignal), undefined);
    expect(wrapper.text()).toContain('last.gpx');
    expect(wrapper.text()).not.toContain('broken.gpx');
    expect(wrapper.text()).toContain('Folder: (root)');
    expect(wrapper.text()).toContain('No error message was saved');
    expect(wrapper.text()).toContain('Page 2');
    expect(wrapper.get('.file-failures__pages button:last-child').attributes('disabled')).toBeDefined();
  });

  it('allows retry after an error and retains loaded details across collapse', async () => {
    mocks.load.mockRejectedValueOnce(new Error('offline'));
    const wrapper = create();
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('Try again');
    await wrapper.get('.file-failures__content button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('broken.gpx');
    await wrapper.get('button').trigger('click');
    expect(wrapper.find('.file-failures__content').exists()).toBe(false);
    await wrapper.get('button').trigger('click');
    expect(wrapper.text()).toContain('broken.gpx');
    expect(mocks.load).toHaveBeenCalledTimes(2);
  });

  it('refreshes expanded details and ignores outdated responses when findings change', async () => {
    let resolveOld!: (value: typeof response) => void;
    mocks.load.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveOld = resolve;
      })
    );
    const wrapper = create();
    await wrapper.get('button').trigger('click');
    const oldSignal = mocks.load.mock.calls[0]![3] as AbortSignal;
    mocks.load.mockResolvedValue({ files: [], total: 0, page: 0, size: 25 });
    useSourceIssueStore().revision++;
    await flushPromises();
    resolveOld(response);
    await flushPromises();
    expect(oldSignal.aborted).toBe(true);
    expect(wrapper.text()).toContain('No failed files remain');
    expect(wrapper.text()).not.toContain('broken.gpx');
    await wrapper.setProps({ refreshKey: 1 });
    await flushPromises();
    expect(mocks.load).toHaveBeenCalledTimes(3);
  });

  it('retains the current page when polling updates counts, findings, or the refresh key', async () => {
    const wrapper = create();
    await wrapper.get('button').trigger('click');
    await flushPromises();
    await wrapper.get('.file-failures__pages button:last-child').trigger('click');
    await flushPromises();
    for (const update of [{ count: 27 }, { refreshKey: 1 }]) {
      await wrapper.setProps(update);
      await flushPromises();
      expect(mocks.load).toHaveBeenLastCalledWith('GPS', 1, 25, expect.any(AbortSignal), undefined);
      expect(wrapper.text()).toContain('Page 2');
    }
    await wrapper.setProps({ index: 'MEDIA' });
    await flushPromises();
    expect(mocks.load).toHaveBeenLastCalledWith('MEDIA', 0, 25, expect.any(AbortSignal), undefined);
    wrapper.unmount();
  });

  it('moves to the last remaining page when refreshed failures shrink below the current page', async () => {
    const wrapper = create();
    await wrapper.get('button').trigger('click');
    await flushPromises();
    await wrapper.get('.file-failures__pages button:last-child').trigger('click');
    await flushPromises();
    mocks.load.mockResolvedValueOnce({ files: [], total: 1, page: 1, size: 25 });
    mocks.load.mockResolvedValueOnce({ ...response, total: 1 });
    await wrapper.setProps({ count: 1 });
    await flushPromises();
    expect(mocks.load).toHaveBeenLastCalledWith('GPS', 0, 25, expect.any(AbortSignal), undefined);
    expect(wrapper.text()).toContain('broken.gpx');
    expect(wrapper.find('.file-failures__pages').exists()).toBe(false);
    wrapper.unmount();
  });
});
