import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import HomeView from '@/views/HomeView.vue';

vi.mock('@/utils/backgrounds', () => ({
  getRandomBackground: () => '/test-background.webp',
}));

describe('HomeView startup failure', () => {
  const STARTUP_TIMEOUT_MS = 12_000;

  beforeEach(() => {
    vi.useFakeTimers();
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function mountHomeView() {
    return mount(HomeView, {
      global: {
        stubs: {
          Map: { name: 'Map', template: '<div data-test="map-stub"></div>' },
          Button: { props: ['label'], template: '<button>{{ label }}</button>' },
        },
      },
    });
  }

  it('shows Retry instead of exposing the map shell when startup exceeds the timeout', async () => {
    const wrapper = mountHomeView();

    await vi.advanceTimersByTimeAsync(STARTUP_TIMEOUT_MS);

    expect(wrapper.find('.curtain-wrapper').exists()).toBe(true);
    expect(wrapper.find('.curtain-error').text()).toContain('Track loading is taking longer than expected');
    expect(wrapper.find('.curtain-error button').text()).toBe('Retry');

    wrapper.unmount();
  });

  it('shows the explicit failure when track loading fails after the timeout', async () => {
    const wrapper = mountHomeView();

    await vi.advanceTimersByTimeAsync(STARTUP_TIMEOUT_MS);
    wrapper.findComponent({ name: 'Map' }).vm.$emit('load-failed');
    await wrapper.vm.$nextTick();

    expect(wrapper.find('.curtain-wrapper').exists()).toBe(true);
    expect(wrapper.find('.curtain-error').text()).toContain('Unable to load tracks');
    expect(wrapper.find('.curtain-error button').text()).toBe('Retry');

    wrapper.unmount();
  });

  it('reveals the map when tracks arrive after the timeout warning', async () => {
    const wrapper = mountHomeView();

    await vi.advanceTimersByTimeAsync(STARTUP_TIMEOUT_MS);
    wrapper.findComponent({ name: 'Map' }).vm.$emit('tracks-loaded');
    await wrapper.vm.$nextTick();

    expect(wrapper.find('.curtain-wrapper').exists()).toBe(false);

    wrapper.unmount();
  });
});
