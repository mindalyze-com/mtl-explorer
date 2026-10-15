import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PrimeVue from 'primevue/config';
import BuildVersionInfo from '@/components/ui/BuildVersionInfo.vue';

const revision = '48a4874aa741dc980574531172b98f11c91d6017';
const mountedWrappers: ReturnType<typeof mount>[] = [];

function mountVersion(props: { version: string; revision?: string }) {
  const wrapper = mount(BuildVersionInfo, {
    props,
    attachTo: document.body,
    global: { plugins: [PrimeVue], stubs: { transition: false } },
  });
  mountedWrappers.push(wrapper);
  return wrapper;
}

beforeEach(() => {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    }
  );
});

afterEach(() => {
  mountedWrappers.splice(0).forEach((wrapper) => wrapper.unmount());
  vi.unstubAllGlobals();
});

describe('compact build identity', () => {
  it('reveals the complete revision in a dismissible overlay with an accessible button', async () => {
    const wrapper = mountVersion({ version: '1.501', revision });
    const toggle = wrapper.get('[aria-label="Show full Git revision"]');
    expect(toggle.text()).toBe('48a4874a');
    expect(toggle.attributes('aria-expanded')).toBe('false');
    expect(wrapper.find('.build-version__details').exists()).toBe(false);

    await wrapper.vm.$nextTick();
    await toggle.trigger('click');
    await vi.waitFor(() => expect(toggle.attributes('aria-expanded')).toBe('true'));
    const overlay = document.getElementById(toggle.attributes('aria-controls'))!;
    expect(overlay.parentElement).toBe(document.body);
    expect(overlay.getAttribute('role')).toBe('dialog');
    expect(overlay.getAttribute('aria-label')).toBe('Full Git revision');
    expect(overlay.querySelector('code')?.textContent).toBe(revision);
    expect(wrapper.find('.build-version__details').exists()).toBe(false);

    await toggle.trigger('click');
    await vi.waitFor(() => expect(document.getElementById(overlay.id)).toBeNull());

    await toggle.trigger('click');
    await vi.waitFor(() => expect(toggle.attributes('aria-expanded')).toBe('true'));
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
    await vi.waitFor(() => expect(document.getElementById(overlay.id)).toBeNull());
    expect(toggle.attributes('aria-expanded')).toBe('false');

    await toggle.trigger('click');
    await vi.waitFor(() => expect(toggle.attributes('aria-expanded')).toBe('true'));
    document.body.click();
    await vi.waitFor(() => expect(document.getElementById(overlay.id)).toBeNull());
    expect(toggle.attributes('aria-expanded')).toBe('false');
  });

  it('offers no individual copy action, including when metadata is absent', () => {
    const wrapper = mountVersion({ version: '1.501', revision });
    expect(wrapper.findAll('button')).toHaveLength(1);
    expect(wrapper.get('button').attributes('aria-label')).toBe('Show full Git revision');
    wrapper.unmount();
    const older = mountVersion({ version: '1.414' });
    expect(older.text()).toBe('1.414');
    expect(older.find('button').exists()).toBe(false);
    older.unmount();
  });

  it('keeps a dirty-workspace marker visible and updates when metadata changes', async () => {
    const wrapper = mountVersion({ version: 'development', revision: `${revision}-dirty` });
    expect(wrapper.get('.build-version__revision').text()).toBe('48a4874a-dirty');
    await wrapper.vm.$nextTick();
    await wrapper.get('.build-version__revision').trigger('click');
    await vi.waitFor(() => expect(document.querySelector('[aria-label="Full Git revision"]')).not.toBeNull());
    await wrapper.setProps({ version: '1.502', revision: 'a'.repeat(40) });
    await vi.waitFor(() => expect(document.querySelector('[aria-label="Full Git revision"]')).toBeNull());
    expect(wrapper.get('.build-version__summary').text()).toBe('1.502·aaaaaaaa');
    wrapper.unmount();
  });
});
