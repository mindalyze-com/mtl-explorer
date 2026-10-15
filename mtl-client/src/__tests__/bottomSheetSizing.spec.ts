import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';
import { nextTick } from 'vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import { boundedSheetHeight } from '@/components/ui/bottomSheetSizing';

describe('bounded sheet sizing', () => {
  it.each([
    [600, 600, 0, 800, 600],
    [600, 600, 4, 800, 600],
    [600, 600, 40, 800, 640],
    [600, 600, 64, 800, 664],
    [600, 600, 65, 800, 600],
    [600, 600, 300, 800, 600],
    [600, 600, 80, 1200, 680],
    [600, 600, 81, 1200, 600],
    [600, 640, 30, 800, 640],
    [730, 730, 30, 800, 760],
    [730, 730, 31, 800, 730],
    [780, 780, 5, 800, 780],
  ])('fits baseline %i, current %i, overflow %i, viewport %i → %i', (base, current, overflow, viewport, expected) => {
    expect(boundedSheetHeight(base, current, overflow, viewport)).toBe(expected);
  });
});

class SizeObserver {
  static observers = new Set<SizeObserver>();
  constructor(private callback: ResizeObserverCallback) {
    SizeObserver.observers.add(this);
  }
  observe() {}
  disconnect() {}
  static notify() {
    for (const observer of this.observers) observer.callback([], observer as unknown as ResizeObserver);
  }
}

async function flush() {
  for (let index = 0; index < 6; index += 1) await nextTick();
  await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
  await nextTick();
}

describe('BottomSheet content fitting lifecycle', () => {
  let wrapper: VueWrapper;
  let sheet: HTMLElement;
  let body: HTMLElement;
  let contentHeight: number;
  const originalHeight = window.innerHeight;

  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', SizeObserver);
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 800 });
    contentHeight = 580;
  });
  afterEach(() => {
    wrapper?.unmount();
    SizeObserver.observers.clear();
    document.body.innerHTML = '';
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: originalHeight });
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  function setup(props: Record<string, unknown> = {}, nested = false) {
    wrapper = mount(BottomSheet, {
      attachTo: document.body,
      props: {
        modelValue: false,
        title: 'Sizing',
        detents: [
          { id: 'normal', height: '600px' },
          { id: 'large', height: '760px' },
        ],
        ...props,
      },
      slots: {
        default: nested
          ? '<aside style="overflow-y:auto">Sidebar</aside><div data-sheet-fit-target><article>Content</article></div><div data-sheet-fit-target hidden>Hidden section</div>'
          : '<article>Content</article>',
        footer: '<button>Apply</button>',
      },
    });
    sheet = document.querySelector('.sheet')!;
    body = sheet.querySelector('.sheet-body')!;
    body.style.overflowY = 'auto';
    // The 60px fixed region represents header, footer and padding. Real layout
    // is covered separately in the browser; jsdom has no layout engine.
    const clientHeight = () => Math.max(0, parseFloat(sheet.style.height) - 60);
    Object.defineProperties(body, {
      clientHeight: { configurable: true, get: clientHeight },
      scrollHeight: {
        configurable: true,
        get: () => (nested ? clientHeight() : Math.max(contentHeight, clientHeight())),
      },
    });
    if (nested) {
      const target = body.querySelector<HTMLElement>('[data-sheet-fit-target]')!;
      target.style.overflowY = 'auto';
      Object.defineProperties(target, {
        clientHeight: { get: clientHeight },
        scrollHeight: { get: () => Math.max(contentHeight, clientHeight()) },
      });
      Object.defineProperties(body.querySelector('aside')!, {
        clientHeight: { value: 100 },
        scrollHeight: { value: 900 },
      });
    }
    return wrapper;
  }

  it('measures hidden at the baseline and reveals the final height with the same detent ID', async () => {
    setup();
    const opening = wrapper.setProps({ modelValue: true });
    await nextTick();
    expect(sheet.classList.contains('sheet--preparing')).toBe(true);
    expect(sheet.hasAttribute('inert')).toBe(true);
    expect(wrapper.emitted('layout-change')).toBeUndefined();
    await opening;
    await flush();
    expect(sheet.style.height).toBe('640px');
    expect(sheet.classList.contains('sheet--preparing')).toBe(false);
    expect(wrapper.emitted('detent-change')).toEqual([['normal']]);
  });

  it('uses the visible primary content rather than a sidebar or hidden cached section', async () => {
    setup({}, true);
    await wrapper.setProps({ modelValue: true });
    await flush();
    expect(sheet.style.height).toBe('640px');
    expect(body.querySelector('[data-sheet-fit-target]')?.classList.contains('sheet-scroll-target')).toBe(true);
    expect(sheet.querySelector('.sheet-scroll-hint')).toBeNull();
  });

  it('leaves long content at its normal height', async () => {
    setup();
    contentHeight = 1000;
    await wrapper.setProps({ modelValue: true });
    await flush();
    expect(sheet.style.height).toBe('600px');
    expect(sheet.querySelector('.sheet-scroll-hint')).not.toBeNull();
  });

  it('does not grow cumulatively or shrink for late content in the same section', async () => {
    setup();
    await wrapper.setProps({ modelValue: true });
    await flush();
    contentHeight = 600;
    SizeObserver.notify();
    await flush();
    expect(sheet.style.height).toBe('660px');
    contentHeight = 630;
    for (let index = 0; index < 3; index += 1) {
      SizeObserver.notify();
      await flush();
    }
    expect(sheet.style.height).toBe('660px');
    contentHeight = 200;
    SizeObserver.notify();
    await flush();
    expect(sheet.style.height).toBe('660px');
    await wrapper.setProps({ contentKey: 'next' });
    await flush();
    expect(sheet.style.height).toBe('600px');
  });

  it('recalculates on window resize without a visible intermediate target', async () => {
    setup();
    await wrapper.setProps({ modelValue: true });
    await flush();
    contentHeight = 550;
    window.dispatchEvent(new Event('resize'));
    await flush();
    expect(sheet.style.height).toBe('610px');
    expect(sheet.style.transition).toBe('');
  });

  it('recalculates when an embedded screen changes its content key', async () => {
    setup({}, true);
    await wrapper.setProps({ modelValue: true });
    await flush();
    expect(sheet.style.height).toBe('640px');
    contentHeight = 200;
    body.querySelector('[data-sheet-fit-target]')!.setAttribute('data-sheet-content-key', 'media');
    await flush();
    expect(sheet.style.height).toBe('600px');
  });

  it('restores the fitted height after maximizing and keeps it under manual control', async () => {
    setup();
    await wrapper.setProps({ modelValue: true });
    await flush();
    const maximize = sheet.querySelector<HTMLElement>('.sheet-fullscreen-btn')!;
    maximize.click();
    await flush();
    expect(sheet.classList.contains('sheet--fullscreen')).toBe(true);
    maximize.click();
    await flush();
    expect(sheet.style.height).toBe('640px');
    contentHeight = 600;
    SizeObserver.notify();
    await flush();
    expect(sheet.style.height).toBe('640px');
  });

  it('keeps manual sizing until reopening', async () => {
    setup();
    await wrapper.setProps({ modelValue: true });
    await flush();
    sheet.querySelector<HTMLElement>('.sheet-drag-zone')!.click();
    await flush();
    expect(sheet.style.height).toBe('760px');
    contentHeight = 200;
    await wrapper.setProps({ contentKey: 'next' });
    window.dispatchEvent(new Event('resize'));
    SizeObserver.notify();
    await flush();
    expect(sheet.style.height).toBe('760px');
    await wrapper.setProps({ modelValue: false });
    await wrapper.setProps({ modelValue: true });
    await flush();
    expect(sheet.style.height).toBe('600px');
  });

  it('cancels preparation when closed before measurement finishes', async () => {
    setup();
    void wrapper.setProps({ modelValue: true });
    await nextTick();
    await wrapper.setProps({ modelValue: false });
    await flush();
    expect(sheet.classList.contains('sheet--hidden')).toBe(true);
    expect(wrapper.emitted('detent-change')).toBeUndefined();
    expect(document.querySelector('.sheet-drag-halo')).toBeNull();
    expect(document.body.style.overflow).toBe('');
  });

  it('cancels preparation on unmount without publishing a visible layout', async () => {
    setup();
    void wrapper.setProps({ modelValue: true });
    await nextTick();
    wrapper.unmount();
    await flush();
    expect(document.querySelector('.sheet')).toBeNull();
    expect(wrapper.emitted('detent-change')).toBeUndefined();
    expect(document.body.style.overflow).toBe('');
  });

  it('clamps for a reduced visual viewport and does not expand while the keyboard is open', async () => {
    const viewport = new EventTarget();
    Object.defineProperty(viewport, 'height', { configurable: true, value: 800 });
    vi.stubGlobal('visualViewport', viewport);
    setup();
    await wrapper.setProps({ modelValue: true });
    await flush();
    Object.defineProperty(viewport, 'height', { configurable: true, value: 400 });
    viewport.dispatchEvent(new Event('resize'));
    await flush();
    expect(parseFloat(sheet.style.height)).toBeLessThanOrEqual(392);
    const clamped = sheet.style.height;
    SizeObserver.notify();
    await flush();
    expect(sheet.style.height).toBe(clamped);
  });

  it('starts dragging from a fitted height above the highest configured detent without jumping', async () => {
    setup({ detents: [{ id: 'normal', height: '600px' }] });
    await wrapper.setProps({ modelValue: true });
    await flush();
    const handle = sheet.querySelector<HTMLElement>('.sheet-drag-zone')!;
    const pointer = (type: string, y: number) => {
      const event = new MouseEvent(type, { bubbles: true, cancelable: true, clientY: y, button: 0 });
      Object.defineProperty(event, 'pointerId', { value: 1 });
      return event;
    };
    handle.dispatchEvent(pointer('pointerdown', 100));
    document.dispatchEvent(pointer('pointermove', 108));
    await nextTick();
    expect(sheet.classList.contains('sheet--dragging')).toBe(true);
    expect(sheet.style.height).toBe('632px');
    document.dispatchEvent(pointer('pointerup', 108));
    await flush();
  });

  it('locks the height throughout entry and applies delayed content after the animation', async () => {
    setup();
    contentHeight = 540;
    let finish!: () => void;
    const finished = new Promise<void>((resolve) => {
      finish = resolve;
    });
    const animate = vi.fn(() => ({ finished, cancel: vi.fn() }));
    Object.defineProperty(sheet, 'animate', { value: animate });
    await wrapper.setProps({ modelValue: true });
    await flush();
    expect(sheet.style.height).toBe('600px');
    contentHeight = 580;
    SizeObserver.notify();
    await flush();
    expect(sheet.style.height).toBe('600px');
    expect(animate).toHaveBeenCalledTimes(1);
    finish();
    await flush();
    expect(sheet.style.height).toBe('640px');
  });

  it('reveals the fitted height without animation when reduced motion is requested', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    setup();
    const animate = vi.fn();
    Object.defineProperty(sheet, 'animate', { value: animate });
    await wrapper.setProps({ modelValue: true });
    await flush();
    expect(animate).not.toHaveBeenCalled();
    expect(sheet.style.height).toBe('640px');
    expect(sheet.classList.contains('sheet--preparing')).toBe(false);
  });

  it('respects opt-out and the existing explicit content fitting mode', async () => {
    setup({ fitSmallOverflow: false });
    await wrapper.setProps({ modelValue: true });
    await flush();
    expect(sheet.style.height).toBe('600px');
    await wrapper.setProps({ modelValue: false, fitSmallOverflow: true, fitContentInitial: true });
    contentHeight = 900;
    await wrapper.setProps({ modelValue: true });
    await flush();
    expect(sheet.style.height).toBe('760px');
    expect(wrapper.emitted('detent-change')?.at(-1)).toEqual(['fit-content']);
  });
});
