import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import ServerLogTab from '@/components/admin/ServerLogTab.vue';
import { getServerLog } from '@/utils/ServiceHelper';

vi.mock('@/utils/ServiceHelper', () => ({ getServerLog: vi.fn() }));
enableAutoUnmount(afterEach);
beforeEach(() => vi.mocked(getServerLog).mockReset());

async function activateLog(response: string) {
  vi.mocked(getServerLog).mockResolvedValueOnce(response);
  const wrapper = mount(ServerLogTab, { global: { stubs: { Button: true, Select: true } } });
  (wrapper.vm as unknown as { activate(): void }).activate();
  await flushPromises();
  return wrapper;
}

it('explains a disabled server log and hides its irrelevant controls', async () => {
  const wrapper = await activateLog('function disabled');
  expect(wrapper.text()).toContain('Server log viewer is disabled for this instance.');
  expect(wrapper.find('.log-toolbar').exists()).toBe(false);
  expect(wrapper.find('.log-pre').exists()).toBe(false);
});

it('shows an empty enabled log without treating it as disabled', async () => {
  const wrapper = await activateLog('');
  expect(wrapper.text()).toContain('No log output yet.');
  expect(wrapper.find('.log-toolbar').exists()).toBe(true);
});
