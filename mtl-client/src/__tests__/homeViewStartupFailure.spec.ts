import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import HomeView from '@/views/HomeView.vue';

vi.mock('@/utils/backgrounds', () => ({
  getRandomBackground: () => '/test-background.webp',
}));

describe('HomeView startup loading', () => {
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

  it('allows a twenty-second startup without showing an error or Retry', async () => {
    const wrapper = mountHomeView();

    await vi.advanceTimersByTimeAsync(20_000);

    expect(wrapper.find('.curtain-wrapper').exists()).toBe(true);
    expect(wrapper.find('.photo-loader').exists()).toBe(true);
    expect(wrapper.find('.curtain-error').exists()).toBe(false);
    expect(wrapper.find('button').exists()).toBe(false);

    wrapper.findComponent({ name: 'Map' }).vm.$emit('tracks-loaded');
    await wrapper.vm.$nextTick();

    expect(wrapper.find('.curtain-wrapper').exists()).toBe(false);

    wrapper.unmount();
  });

  it('keeps loading with a neutral notice and optional Retry after thirty seconds', async () => {
    const wrapper = mountHomeView();

    await vi.advanceTimersByTimeAsync(29_999);
    expect(wrapper.find('button').exists()).toBe(false);

    await vi.advanceTimersByTimeAsync(1);

    expect(wrapper.find('.curtain-wrapper').exists()).toBe(true);
    expect(wrapper.find('.photo-status').text()).toContain('Still loading. Your server may be busy.');
    expect(wrapper.find('.photo-progress-track').exists()).toBe(true);
    expect(wrapper.find('.photo-loader button').text()).toBe('Retry');
    expect(wrapper.find('.curtain-error').exists()).toBe(false);

    wrapper.unmount();
  });

  it.each([1_000, 35_000])('shows actual track failures immediately after %i ms', async (delay) => {
    const wrapper = mountHomeView();

    await vi.advanceTimersByTimeAsync(delay);
    wrapper.findComponent({ name: 'Map' }).vm.$emit('load-failed');
    await wrapper.vm.$nextTick();

    expect(wrapper.find('.curtain-wrapper').exists()).toBe(true);
    expect(wrapper.find('.curtain-error').text()).toContain('Unable to load tracks');
    expect(wrapper.find('.curtain-error button').text()).toBe('Retry');
    expect(wrapper.find('.photo-loader').exists()).toBe(false);

    wrapper.unmount();
  });

  it('reveals the map automatically even when loading takes over a minute', async () => {
    const wrapper = mountHomeView();

    await vi.advanceTimersByTimeAsync(75_000);
    expect(wrapper.find('.photo-loader').exists()).toBe(true);
    expect(wrapper.find('.curtain-error').exists()).toBe(false);
    wrapper.findComponent({ name: 'Map' }).vm.$emit('tracks-loaded');
    await wrapper.vm.$nextTick();

    expect(wrapper.find('.curtain-wrapper').exists()).toBe(false);

    wrapper.unmount();
  });

  it('cleans up the slow notice timer when unmounted during loading', () => {
    const wrapper = mountHomeView();
    wrapper.unmount();

    expect(vi.getTimerCount()).toBe(0);
  });
});
