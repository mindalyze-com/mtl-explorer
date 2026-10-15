import { mount, type VueWrapper } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AnimatedCount from '@/components/common/AnimatedCount.vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import { observeCountAnimation } from '@/utils/countAnimation';

const wrappers: VueWrapper[] = [];
function counter(value = 100) {
  const wrapper = mount(AnimatedCount, { props: { value, sample: {}, resetKey: 'first' } });
  wrappers.push(wrapper);
  return wrapper;
}

describe('AnimatedCount', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(0);
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => setTimeout(() => callback(0), 10));
    vi.stubGlobal('cancelAnimationFrame', (id: ReturnType<typeof setTimeout>) => clearTimeout(id));
    vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
  });
  afterEach(() => {
    wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it.each([undefined, null, Number.NaN])('waits for a real baseline when the initial count is %s', async (value) => {
    const wrapper = mount(AnimatedCount, { props: { value } });
    wrappers.push(wrapper);
    expect(wrapper.text()).toBe('—');
    expect(wrapper.attributes('aria-label')).toBe('Count unavailable');
    vi.advanceTimersByTime(5_000);
    await wrapper.setProps({ value: 400_000, sample: {} });
    expect(wrapper.text()).toBe('400,000');
    expect(vi.getTimerCount()).toBe(0);

    vi.advanceTimersByTime(8_000);
    await wrapper.setProps({ value: 440_000, sample: {} });
    expect(wrapper.text()).toBe('400,000');
    vi.advanceTimersByTime(4_000);
    await nextTick();
    expect(wrapper.text()).toBe('420,000');
  });

  it('keeps missing shared samples separate from fallback zeros and seeds the first real sample', async () => {
    const wrapper = mount(AnimatedCount, { props: { value: 0, animation: null } });
    wrappers.push(wrapper);
    expect(wrapper.text()).toBe('—');
    vi.advanceTimersByTime(5_000);
    const baseline = observeCountAnimation(undefined, 400_000, Date.now());
    await wrapper.setProps({ value: 400_000, animation: baseline });
    expect(wrapper.text()).toBe('400,000');
    expect(vi.getTimerCount()).toBe(0);

    vi.advanceTimersByTime(8_000);
    await wrapper.setProps({
      value: 440_000,
      animation: observeCountAnimation(baseline, 440_000, Date.now()),
    });
    vi.advanceTimersByTime(4_000);
    await nextTick();
    expect(wrapper.text()).toBe('420,000');

    await wrapper.setProps({ value: 0, animation: null });
    expect(wrapper.text()).toBe('—');
    expect(vi.getTimerCount()).toBe(0);
    vi.advanceTimersByTime(2_000);
    await wrapper.setProps({ value: 500_000, animation: observeCountAnimation(undefined, 500_000, Date.now()) });
    expect(wrapper.text()).toBe('500,000');
    expect(vi.getTimerCount()).toBe(0);
  });

  it('treats a reported zero as a real baseline', async () => {
    const wrapper = counter(0);
    expect(wrapper.text()).toBe('0');
    vi.advanceTimersByTime(8_000);
    await wrapper.setProps({ value: 40_000, sample: {} });
    vi.advanceTimersByTime(4_000);
    await nextTick();
    expect(wrapper.text()).toBe('20,000');
  });

  it('interpolates growth at a fixed delay and stops at the known count', async () => {
    const wrapper = counter();
    expect(wrapper.text()).toBe('100');
    vi.advanceTimersByTime(8_000);
    await wrapper.setProps({ value: 1_100, sample: {} });
    expect(wrapper.text()).toBe('100');
    expect(wrapper.attributes('aria-label')).toBe('1,100');
    vi.advanceTimersByTime(4_000);
    await nextTick();
    expect(wrapper.text()).toBe('600');
    vi.advanceTimersByTime(4_000);
    await nextTick();
    expect(wrapper.text()).toBe('1,100');
    vi.advanceTimersByTime(20_000);
    await nextTick();
    expect(wrapper.text()).toBe('1,100');
  });

  it('rounds down when growth is slower than one item per second', async () => {
    const wrapper = counter();
    vi.advanceTimersByTime(8_000);
    await wrapper.setProps({ value: 101, sample: {} });
    vi.advanceTimersByTime(7_990);
    await nextTick();
    expect(wrapper.text()).toBe('100');
    vi.advanceTimersByTime(10);
    await nextTick();
    expect(wrapper.text()).toBe('101');
  });

  it('includes unchanged samples in the delayed timeline', async () => {
    const wrapper = counter();
    vi.advanceTimersByTime(2_000);
    await wrapper.setProps({ sample: {} });
    vi.advanceTimersByTime(2_000);
    await wrapper.setProps({ value: 200, sample: {} });
    vi.advanceTimersByTime(6_000);
    await nextTick();
    expect(wrapper.text()).toBe('100');
    vi.advanceTimersByTime(800);
    await nextTick();
    expect(wrapper.text()).toBe('140');
  });

  it('retains unchanged observations while a batch enters the delayed timeline', async () => {
    const wrapper = counter(299_901);
    for (let poll = 0; poll < 15; poll++) {
      vi.advanceTimersByTime(2_000);
      await wrapper.setProps({ sample: {} });
    }
    await wrapper.setProps({ value: 329_901, sample: {} });
    expect(wrapper.text()).toBe('299,901');
    for (let poll = 0; poll < 5; poll++) {
      vi.advanceTimersByTime(2_000);
      await wrapper.setProps({ sample: {} });
    }
    expect(wrapper.text()).toBe('329,901');
    expect(wrapper.attributes('aria-label')).toBe('329,901');
    vi.advanceTimersByTime(20_000);
    await nextTick();
    expect(wrapper.text()).toBe('329,901');
    vi.advanceTimersByTime(30_000);
    await nextTick();
    expect(wrapper.text()).toBe('329,901');
  });

  it('evaluates a long gap near the current time and resets a cumulative counter immediately', async () => {
    const wrapper = counter();
    await wrapper.setProps({ resetOnDecrease: true });
    vi.advanceTimersByTime(300_000);
    await wrapper.setProps({ value: 700, sample: {} });
    expect(wrapper.text()).toBe('684');
    vi.advanceTimersByTime(4_000);
    await nextTick();
    expect(wrapper.text()).toBe('692');
    vi.advanceTimersByTime(4_000);
    await nextTick();
    expect(wrapper.text()).toBe('700');
    await wrapper.setProps({ value: 0, sample: {} });
    expect(wrapper.text()).toBe('0');
    expect(vi.getTimerCount()).toBe(0);
  });

  it('keeps earlier segments when a new sample arrives before playback reaches them', async () => {
    const wrapper = counter();
    vi.advanceTimersByTime(8_000);
    await wrapper.setProps({ value: 180, sample: {} });
    vi.advanceTimersByTime(4_000);
    await nextTick();
    expect(wrapper.text()).toBe('140');
    await wrapper.setProps({ value: 220, sample: {} });
    expect(wrapper.text()).toBe('140');
    vi.advanceTimersByTime(4_000);
    await nextTick();
    expect(wrapper.text()).toBe('180');
    vi.advanceTimersByTime(4_000);
    await nextTick();
    expect(wrapper.text()).toBe('220');
  });

  it('animates decreasing pending counts and snaps to a new series', async () => {
    const wrapper = counter();
    vi.advanceTimersByTime(8_000);
    await wrapper.setProps({ value: 50, sample: {} });
    vi.advanceTimersByTime(3_200);
    await nextTick();
    expect(wrapper.text()).toBe('80');
    await wrapper.setProps({ value: 0, resetKey: 'new sync', sample: {} });
    expect(wrapper.text()).toBe('0');
    expect(vi.getTimerCount()).toBe(0);
  });

  it('keeps the counter mounted and its animation state when the bottom sheet closes and reopens', async () => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
    const wrapper = mount(
      defineComponent({
        components: { BottomSheet, AnimatedCount },
        data: () => ({ open: true, count: 100, sample: {} }),
        template:
          '<BottomSheet v-model="open" title="Processing"><AnimatedCount :value="count" :sample="sample" /></BottomSheet>',
      }),
      { attachTo: document.body }
    );
    wrappers.push(wrapper);
    const animated = wrapper.getComponent(AnimatedCount);
    const element = animated.element;
    vi.advanceTimersByTime(8_000);
    await wrapper.setData({ count: 200, sample: {} });
    vi.advanceTimersByTime(800);
    await nextTick();
    expect(animated.text()).toBe('110');

    await wrapper.setData({ open: false });
    vi.advanceTimersByTime(400);
    await nextTick();
    expect(wrapper.getComponent(AnimatedCount).element).toBe(element);
    expect(animated.text()).toBe('115');

    await wrapper.setData({ open: true });
    expect(wrapper.getComponent(AnimatedCount).element).toBe(element);
    expect(animated.text()).toBe('115');
    vi.advanceTimersByTime(6_800);
    await nextTick();
    expect(animated.text()).toBe('200');
  });

  it('updates the visible count only two or three times per second and skips intermediate numbers', async () => {
    const wrapper = counter(0);
    vi.advanceTimersByTime(30_000);
    await wrapper.setProps({ value: 30_000, sample: {} });
    let updates = 0;
    const observer = new MutationObserver(() => updates++);
    observer.observe(wrapper.element, { childList: true, characterData: true, subtree: true });
    await vi.advanceTimersByTimeAsync(1_000);
    expect(updates).toBeGreaterThanOrEqual(2);
    expect(updates).toBeLessThanOrEqual(3);
    expect(Number(wrapper.text().replaceAll(',', ''))).toBeGreaterThan(100);
    observer.disconnect();
  });

  it('does not move backwards when motion is re-enabled during a shared timeline', async () => {
    const media = { matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() };
    vi.stubGlobal('matchMedia', () => media);
    let animation = observeCountAnimation(undefined, 100, Date.now());
    const wrapper = counter();
    await wrapper.setProps({ animation });
    vi.advanceTimersByTime(8_000);
    animation = observeCountAnimation(animation, 200, Date.now());
    await wrapper.setProps({ value: 200, animation });
    vi.advanceTimersByTime(800);
    await nextTick();
    expect(wrapper.text()).toBe('110');

    const motionChanged = media.addEventListener.mock.calls[0]![1] as () => void;
    media.matches = true;
    motionChanged();
    await nextTick();
    expect(wrapper.text()).toBe('200');
    expect(vi.getTimerCount()).toBe(0);
    media.matches = false;
    motionChanged();
    vi.advanceTimersByTime(200);
    animation = observeCountAnimation(animation, 300, Date.now());
    await wrapper.setProps({ value: 300, animation });
    vi.advanceTimersByTime(400);
    await nextTick();
    expect(Number(wrapper.text())).toBeGreaterThanOrEqual(200);
    vi.advanceTimersByTime(8_000);
    await nextTick();
    expect(wrapper.text()).toBe('300');
    wrapper.unmount();
    expect(media.removeEventListener).toHaveBeenCalledWith('change', motionChanged);
  });

  it('plays intermediate observations even when the latest value equals the displayed value', async () => {
    const wrapper = counter();
    vi.advanceTimersByTime(8_000);
    await wrapper.setProps({ value: 200, sample: {} });
    vi.advanceTimersByTime(3_200);
    await nextTick();
    expect(wrapper.text()).toBe('140');
    await wrapper.setProps({ value: 140, sample: {} });
    expect(wrapper.text()).toBe('140');
    expect(vi.getTimerCount()).toBe(1);
    vi.advanceTimersByTime(4_800);
    await nextTick();
    expect(wrapper.text()).toBe('200');
    vi.advanceTimersByTime(3_200);
    await nextTick();
    expect(wrapper.text()).toBe('140');
    expect(vi.getTimerCount()).toBe(0);
  });

  it('keeps settled counts idle through unchanged status samples', async () => {
    const wrapper = counter();
    vi.advanceTimersByTime(8_000);
    await wrapper.setProps({ value: 200, sample: {} });
    vi.advanceTimersByTime(8_000);
    await nextTick();
    expect(wrapper.text()).toBe('200');
    expect(vi.getTimerCount()).toBe(0);
    await wrapper.setProps({ sample: {} });
    expect(vi.getTimerCount()).toBe(0);
  });

  it('shows the reported count immediately with reduced motion and releases pending updates on unmount', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
    const wrapper = counter();
    vi.advanceTimersByTime(2_000);
    await wrapper.setProps({ value: 200, sample: {} });
    expect(wrapper.text()).toBe('200');
    expect(vi.getTimerCount()).toBe(0);

    vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
    const animated = counter();
    vi.advanceTimersByTime(2_000);
    await animated.setProps({ value: 200, sample: {} });
    expect(vi.getTimerCount()).toBe(1);
    animated.unmount();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('stops hidden rendering and evaluates the current simulation time immediately on return', async () => {
    let visibility: DocumentVisibilityState = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility);
    const wrapper = counter();
    vi.advanceTimersByTime(8_000);
    await wrapper.setProps({ value: 180, sample: {} });
    vi.advanceTimersByTime(800);
    await nextTick();
    expect(wrapper.text()).toBe('108');

    visibility = 'hidden';
    document.dispatchEvent(new Event('visibilitychange'));
    expect(vi.getTimerCount()).toBe(0);
    vi.setSystemTime(600_000);
    visibility = 'visible';
    document.dispatchEvent(new Event('visibilitychange'));
    await nextTick();
    expect(wrapper.text()).toBe('180');
    expect(vi.getTimerCount()).toBe(0);
    await wrapper.setProps({ value: 6_100, sample: {} });
    vi.advanceTimersByTime(400);
    await nextTick();
    expect(wrapper.text()).toBe('6,024');
  });
});
