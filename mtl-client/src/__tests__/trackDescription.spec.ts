import { enableAutoUnmount, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TrackDescription from '@/components/trackdetails/TrackDescription.vue';

enableAutoUnmount(afterEach);
let availableWidth: number;
let textWidth: number;
let resized: ResizeObserverCallback;
const disconnect = vi.fn();
const fontEvents = new EventTarget();
const description =
  'A riverside route with a longer description and a second paragraph.\nScenic return via the bridge.';

beforeEach(() => {
  availableWidth = 320;
  textWidth = 680;
  vi.useFakeTimers();
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockImplementation(function () {
    return this.classList.contains('track-description__measurement') ? availableWidth : 0;
  });
  vi.spyOn(HTMLElement.prototype, 'scrollWidth', 'get').mockImplementation(function () {
    return this.classList.contains('track-description__measurement') ? textWidth : 0;
  });
  vi.stubGlobal(
    'ResizeObserver',
    class {
      constructor(callback: ResizeObserverCallback) {
        resized = callback;
      }
      observe() {}
      disconnect = disconnect;
    }
  );
  Object.defineProperty(document, 'fonts', { configurable: true, value: fontEvents });
});
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  vi.useRealTimers();
  disconnect.mockClear();
  Reflect.deleteProperty(document, 'fonts');
});

async function resize(width: number) {
  availableWidth = width;
  resized([{ contentRect: { width } } as ResizeObserverEntry], {} as ResizeObserver);
  vi.advanceTimersToNextFrame();
  await nextTick();
}

async function mountDescription(text = description) {
  const wrapper = mount(TrackDescription, { props: { text } });
  await nextTick();
  return wrapper;
}

describe('one-line track description disclosure', () => {
  it('shows all fitting text without a disclosure control, including an exact fit', async () => {
    textWidth = availableWidth;
    const wrapper = await mountDescription('Along the river');
    expect(wrapper.get('.track-description__text').text()).toBe('Along the river');
    expect(wrapper.find('button').exists()).toBe(false);
  });

  it('preserves the full description and lets a user expand and collapse it', async () => {
    const wrapper = await mountDescription();
    const toggle = wrapper.get('button');
    const text = wrapper.get('.track-description__text');
    expect(toggle.text()).toBe('More');
    expect(toggle.attributes('aria-expanded')).toBe('false');
    expect(toggle.attributes('aria-controls')).toBe(text.attributes('id'));
    expect(text.element.textContent).toBe(description);
    await toggle.trigger('click');
    expect(toggle.text()).toBe('Less');
    expect(toggle.attributes('aria-expanded')).toBe('true');
    expect(wrapper.classes()).toContain('track-description--expanded');
    await toggle.trigger('click');
    expect(wrapper.classes()).not.toContain('track-description--expanded');
    expect(toggle.text()).toBe('More');
  });

  it('removes the control when widening makes the text fit and restores it when narrowing', async () => {
    const wrapper = await mountDescription();
    await wrapper.get('button').trigger('click');
    await resize(800);
    expect(wrapper.find('button').exists()).toBe(false);
    expect(wrapper.classes()).not.toContain('track-description--expanded');
    await resize(390);
    expect(wrapper.get('button').text()).toBe('More');
    expect(wrapper.get('button').attributes('aria-expanded')).toBe('false');
  });

  it('does not retain expansion when the description changes', async () => {
    const wrapper = await mountDescription();
    await wrapper.get('button').trigger('click');
    await wrapper.setProps({ text: 'Another long description' });
    expect(wrapper.get('button').attributes('aria-expanded')).toBe('false');
    textWidth = 180;
    await wrapper.setProps({ text: 'Short description' });
    expect(wrapper.find('button').exists()).toBe(false);
  });

  it('waits for measurable space when the panel is initially hidden', async () => {
    availableWidth = 0;
    const wrapper = await mountDescription();
    expect(wrapper.find('button').exists()).toBe(false);
    await resize(390);
    expect(wrapper.get('button').text()).toBe('More');
  });

  it('remeasures when fonts load without a change to the panel width', async () => {
    textWidth = 300;
    const wrapper = await mountDescription();
    expect(wrapper.find('button').exists()).toBe(false);
    textWidth = 400;
    fontEvents.dispatchEvent(new Event('loadingdone'));
    await nextTick();
    expect(wrapper.get('button').text()).toBe('More');
  });

  it('hides empty descriptions and measures one added later', async () => {
    const wrapper = await mountDescription('   ');
    expect(wrapper.find('.track-description').exists()).toBe(false);
    await wrapper.setProps({ text: description });
    expect(wrapper.get('button').text()).toBe('More');
  });

  it('gives each description its own control target and releases its observer', async () => {
    const wrapper = mount({
      components: { TrackDescription },
      setup: () => ({ description }),
      template: '<div><TrackDescription :text="description" /><TrackDescription :text="description" /></div>',
    });
    await nextTick();
    const buttons = wrapper.findAll('button');
    expect(buttons).toHaveLength(2);
    expect(buttons[0].attributes('aria-controls')).not.toBe(buttons[1].attributes('aria-controls'));
    wrapper.unmount();
    expect(disconnect).toHaveBeenCalledTimes(2);
  });
});
