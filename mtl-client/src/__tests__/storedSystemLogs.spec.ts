import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ResponseError } from 'x8ing-mtl-api-typescript-fetch';
import StoredSystemLogs from '@/components/admin/StoredSystemLogs.vue';

const api = vi.hoisted(() => ({ list: vi.fn(), detail: vi.fn() }));
vi.mock('@/utils/openApiClient', () => ({ getApiConfiguration: () => ({}) }));
vi.mock('x8ing-mtl-api-typescript-fetch', async (importOriginal) => ({
  ...(await importOriginal<typeof import('x8ing-mtl-api-typescript-fetch')>()),
  SystemLogControllerApi: class {
    listSystemLogs = api.list;
    getSystemLog = api.detail;
  },
}));
enableAutoUnmount(afterEach);
beforeEach(() => {
  api.list.mockReset();
  api.detail.mockReset();
});
const options = {
  props: { active: true },
  global: { stubs: { BottomSheet: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' } } },
};

it('shows occurrence time first and receipt time and repetition details separately', async () => {
  const occurredAt = new Date('2026-10-01T12:00:00Z');
  const createDate = new Date('2026-10-02T12:00:00Z');
  const event = {
    id: 1,
    message: 'Delayed',
    occurredAt,
    createDate,
    occurrenceCount: 5,
    lastOccurredAt: new Date('2026-10-01T12:00:10Z'),
  };
  api.list.mockResolvedValue({ items: [event], totalPages: 1, totalElements: 1 });
  api.detail.mockResolvedValue({
    event,
    originalClientLoginRef: 'historical-session',
    clientLoginRef: 'upload-session',
  });
  const wrapper = mount(StoredSystemLogs, options);
  await flushPromises();
  expect(wrapper.get('.event-meta').text()).toContain(occurredAt.toLocaleString());
  expect(wrapper.get('.event-meta').text()).not.toContain(createDate.toLocaleString());
  await wrapper.get('.event-row').trigger('click');
  await flushPromises();
  expect(wrapper.text()).toContain(`Received: ${createDate.toLocaleString()}`);
  expect(wrapper.text()).toContain('Occurrences: 5');
  expect(wrapper.text()).toContain('historical-session');
});

it('renders null classification and unsafe stored markup as text and loads details on demand', async () => {
  api.list.mockResolvedValue({
    items: [{ id: 1, createDate: new Date(), source: null, severity: null, message: '<script>unsafe()</script>' }],
    page: 0,
    totalPages: 1,
    totalElements: 1,
  });
  api.detail.mockResolvedValue({
    event: { id: 1, message: 'Synthetic' },
    stackTrace: 'Error: synthetic\n at a (app.js:1:2)',
    context: { status: 503 },
    clientLoginRef: 'synthetic-login',
  });
  const wrapper = mount(StoredSystemLogs, options);
  await flushPromises();
  expect(wrapper.text()).toContain('Unspecified');
  expect(wrapper.find('script').exists()).toBe(false);
  expect(api.detail).not.toHaveBeenCalled();
  await wrapper.get('.event-row').trigger('click');
  await flushPromises();
  expect(wrapper.find('pre').text()).toContain('\n at a (app.js:1:2)');
  expect(wrapper.text()).toContain('synthetic-login');
});

it('applies source, severity, topic and date filters and requests the next page manually', async () => {
  api.list.mockResolvedValue({ items: [{ id: 1, message: 'Synthetic' }], page: 0, totalPages: 2, totalElements: 51 });
  const wrapper = mount(StoredSystemLogs, options);
  await flushPromises();
  await wrapper.findAll('select')[0]!.setValue('CLIENT');
  await wrapper.findAll('select')[1]!.setValue('WARN');
  await wrapper.findAll('input')[0]!.setValue('media');
  await wrapper.findAll('input')[3]!.setValue('2026-10-01T12:00');
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  expect(api.list).toHaveBeenLastCalledWith(
    expect.objectContaining({
      page: 0,
      size: 50,
      source: 'CLIENT',
      severity: 'WARN',
      topic1: 'media',
      from: expect.any(Date),
    })
  );
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Next')!
    .trigger('click');
  await flushPromises();
  expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
});

it('handles empty, disabled and failed states and ignores late detail responses after deactivation', async () => {
  api.list.mockResolvedValue({ items: [], totalPages: 0, totalElements: 0 });
  const wrapper = mount(StoredSystemLogs, options);
  await flushPromises();
  expect(wrapper.text()).toContain('No stored events');
  api.list.mockRejectedValue(new ResponseError(new Response(null, { status: 403 })));
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  expect(wrapper.text()).toContain('disabled for this instance');
  api.list.mockRejectedValue(new Error('synthetic network failure'));
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  expect(wrapper.get('[role="alert"]').text()).toContain('Could not load stored events');
  api.list.mockResolvedValue({ items: [{ id: 1, message: 'Synthetic' }], totalPages: 1, totalElements: 1 });
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  let resolve: (value: unknown) => void = () => {};
  api.detail.mockReturnValue(
    new Promise((done) => {
      resolve = done;
    })
  );
  await wrapper.get('.event-row').trigger('click');
  expect(wrapper.text()).toContain('Loading event');
  await wrapper.setProps({ active: false });
  resolve({ event: { message: 'Late detail' } });
  await flushPromises();
  expect(wrapper.text()).not.toContain('Late detail');
});

it('keeps pagination on the applied filters until Refresh applies edits and resets the page', async () => {
  api.list.mockResolvedValue({ items: [{ id: 1, message: 'Synthetic' }], totalPages: 2, totalElements: 51 });
  const wrapper = mount(StoredSystemLogs, options);
  await flushPromises();
  await wrapper.findAll('select')[0]!.setValue('CLIENT');
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  await wrapper.findAll('select')[0]!.setValue('SERVER');
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Next')!
    .trigger('click');
  await flushPromises();
  expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, source: 'CLIENT' }));
  await wrapper.get('form').trigger('submit');
  await flushPromises();
  expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, source: 'SERVER' }));
});
