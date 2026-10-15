import { flushPromises, mount } from '@vue/test-utils';
import type { VueWrapper } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import MtlSlider from '@/components/ui/MtlSlider.vue';
import Popover from 'primevue/popover';
import PrimeVue from 'primevue/config';

class TestResizeObserver {
  static instances: TestResizeObserver[] = [];
  targets = new Set<Element>();
  constructor(private callback: ResizeObserverCallback) {
    TestResizeObserver.instances.push(this);
  }
  observe(target: Element) {
    this.targets.add(target);
  }
  disconnect() {
    this.targets.clear();
  }
  static notify(target: Element) {
    for (const observer of this.instances) {
      if (observer.targets.has(target))
        observer.callback(
          [{ target, contentRect: target.getBoundingClientRect() } as ResizeObserverEntry],
          observer as unknown as ResizeObserver
        );
    }
  }
}

function findSheet(label: string): HTMLElement {
  const sheet = Array.from(document.body.querySelectorAll<HTMLElement>('.sheet')).find((element) =>
    element.textContent?.includes(label)
  );
  if (!sheet) throw new Error(`Sheet not found: ${label}`);
  return sheet;
}

async function flushSheetUpdates() {
  await nextTick();
  await flushPromises();
  await nextTick();
}

function pointerEvent(type: string, clientX: number, clientY: number, pointerId = 1): PointerEvent {
  const event = new MouseEvent(type, {
    bubbles: true,
    cancelable: true,
    button: 0,
    clientX,
    clientY,
  }) as PointerEvent;
  Object.defineProperty(event, 'pointerId', { value: pointerId });
  return event;
}

function touchEvent(type: string, clientX: number, clientY: number, touchCount = 1): TouchEvent {
  const event = new Event(type, { bubbles: true, cancelable: true }) as TouchEvent;
  Object.defineProperty(event, 'touches', {
    value: Array.from({ length: touchCount }, (_, identifier) => ({ identifier, clientX, clientY })),
  });
  return event;
}

function mountStackHarness(
  options: {
    lowerOpen?: boolean;
    upperOpen?: boolean;
    lowerZIndex?: number;
    upperZIndex?: number;
    lowerBackgroundDetent?: unknown;
    lowerNoBackdrop?: boolean;
    upperNoBackdrop?: boolean;
    upperCoverPreviousSheet?: boolean;
    lowerHeight?: string;
    upperHeight?: string;
    useDefaultZIndex?: boolean;
  } = {}
) {
  const wrapper = mount(
    defineComponent({
      components: { BottomSheet },
      data() {
        return {
          lowerOpen: options.lowerOpen ?? true,
          upperOpen: options.upperOpen ?? false,
          lowerBackgroundDetent: options.lowerBackgroundDetent,
          lowerZIndex: options.useDefaultZIndex ? undefined : (options.lowerZIndex ?? 5000),
          upperZIndex: options.useDefaultZIndex ? undefined : (options.upperZIndex ?? 5100),
          lowerNoBackdrop: options.lowerNoBackdrop ?? true,
          upperNoBackdrop: options.upperNoBackdrop ?? true,
          upperCoverPreviousSheet: options.upperCoverPreviousSheet ?? true,
          lowerHeight: options.lowerHeight ?? '50vh',
          upperHeight: options.upperHeight ?? '50vh',
        };
      },
      template: `
        <BottomSheet
          v-model="lowerOpen"
          title="Lower Sheet"
          :detents="[{ height: lowerHeight }]"
          :background-detent="lowerBackgroundDetent"
          :z-index="lowerZIndex"
          :no-backdrop="lowerNoBackdrop"
        >
          <button type="button">Lower Action</button>
        </BottomSheet>
        <BottomSheet
          v-model="upperOpen"
          title="Upper Sheet"
          :detents="[{ height: upperHeight }]"
          :cover-previous-sheet="upperCoverPreviousSheet"
          :z-index="upperZIndex"
          :no-backdrop="upperNoBackdrop"
        >
          <button type="button">Upper Action</button>
        </BottomSheet>
      `,
    }),
    { attachTo: document.body }
  );
  mountedWrappers.push(wrapper);
  return wrapper;
}

function mountInteractionHarness(nativeFullscreen = false) {
  const wrapper = mount(
    defineComponent({
      components: { BottomSheet },
      data() {
        return {
          open: true,
          actionClicks: 0,
          nativeFullscreen,
        };
      },
      template: `
        <BottomSheet
          v-model="open"
          title="Interactive Sheet"
          :detents="[
            { id: 'low', height: '200px' },
            { id: 'high', height: '400px' },
          ]"
          initial-detent="low"
          :native-fullscreen="nativeFullscreen"
        >
          <template #title>
            <span>Interactive Sheet</span>
            <span class="selectable-track-id" title="Select TrackID to copy" @click.stop>#100018</span>
          </template>
          <template #header-actions>
            <button class="header-action" type="button" @click="actionClicks++">Action</button>
          </template>
          <div>Body</div>
        </BottomSheet>
      `,
    }),
    { attachTo: document.body }
  );
  mountedWrappers.push(wrapper);
  return wrapper;
}

function mountFooterHarness() {
  const wrapper = mount(
    defineComponent({
      components: { BottomSheet },
      data() {
        return { open: true };
      },
      template: `
        <BottomSheet v-model="open" title="Footer Sheet" :detents="[{ height: '300px' }]">
          <div class="scroll-content">Scrollable content</div>
          <template #footer>
            <button class="footer-action" type="button">Save</button>
          </template>
        </BottomSheet>
      `,
    }),
    { attachTo: document.body }
  );
  mountedWrappers.push(wrapper);
  return wrapper;
}

function mountResizeHarness() {
  const wrapper = mount(
    defineComponent({
      components: { BottomSheet },
      data() {
        return { open: true };
      },
      template: `
        <BottomSheet v-model="open" title="Responsive Sheet" :detents="[{ height: 0.75 }]">
          <div>Responsive content</div>
        </BottomSheet>
      `,
    }),
    { attachTo: document.body }
  );
  mountedWrappers.push(wrapper);
  return wrapper;
}

function mountScrollHarness() {
  const wrapper = mount(
    defineComponent({
      components: { BottomSheet },
      data() {
        return { open: true };
      },
      template: `
        <BottomSheet
          v-model="open"
          title="Scroll Sheet"
          scroll-hint-label="More views"
          :detents="[{ height: '300px' }]"
        >
          <div class="test-scroll-target" style="overflow-y: auto">Scrollable content</div>
        </BottomSheet>
      `,
    }),
    { attachTo: document.body }
  );
  mountedWrappers.push(wrapper);
  return wrapper;
}

const mountedWrappers: VueWrapper[] = [];
const initialWindowHeight = window.innerHeight;

describe('BottomSheet stacking', () => {
  let consoleLogSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestResizeObserver.instances = [];
    vi.stubGlobal('ResizeObserver', TestResizeObserver);
    document.body.innerHTML = '';
    consoleLogSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
  });

  afterEach(() => {
    while (mountedWrappers.length) {
      mountedWrappers.pop()?.unmount();
    }
    consoleLogSpy.mockRestore();
    document.body.innerHTML = '';
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: initialWindowHeight });
  });

  it.each(['button', 'backdrop'])('preserves a deferred sheet until its caller accepts a %s close', async (source) => {
    const component = mount(BottomSheet, {
      props: {
        modelValue: true,
        title: 'Deferred Sheet',
        deferClose: true,
        noBackdrop: false,
        detents: [{ id: 'normal', height: '300px' }],
      },
      attachTo: document.body,
    });
    mountedWrappers.push(component);
    await flushSheetUpdates();
    const sheet = findSheet('Deferred Sheet');
    sheet.querySelector<HTMLButtonElement>('.sheet-fullscreen-btn')!.click();
    await flushSheetUpdates();
    const closeTarget =
      source === 'button'
        ? sheet.querySelector<HTMLButtonElement>('.sheet-close-btn')!
        : document.querySelector<HTMLElement>('.sheet-backdrop')!;
    closeTarget.click();
    await flushSheetUpdates();
    expect(component.props('modelValue')).toBe(true);
    expect(sheet.style.height).toBe('100dvh');
    expect(component.emitted('close-request')).toHaveLength(1);
    expect(component.emitted('update:modelValue')).toBeUndefined();
    expect(component.emitted('closed')).toBeUndefined();
    await component.setProps({ modelValue: false });
    await flushSheetUpdates();
    expect(sheet.classList.contains('sheet--open')).toBe(false);
    expect(sheet.style.height).toBe('0px');
  });

  describe('content fitting', () => {
    let naturalHeight = 100;
    const chromeHeight = 40;

    beforeEach(() => {
      naturalHeight = 100;
      vi.spyOn(Element.prototype, 'scrollHeight', 'get').mockImplementation(function (this: Element) {
        if (!this.classList.contains('sheet-body')) return 0;
        const sheet = this.closest<HTMLElement>('.sheet');
        const viewportHeight = Math.max(0, Number.parseFloat(sheet?.style.height || '0') - chromeHeight);
        return Math.max(naturalHeight, viewportHeight);
      });
      vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockImplementation(function (this: HTMLElement) {
        return this.classList.contains('sheet-drag-zone') ? chromeHeight : 0;
      });
    });

    afterEach(() => vi.restoreAllMocks());

    function mountContentSheet(fitContent = true) {
      const wrapper = mount(BottomSheet, {
        props: {
          modelValue: true,
          title: 'Content Sheet',
          fitContent,
          detents: [
            { id: 'low', height: '180px' },
            { id: 'medium', height: '500px' },
            { id: 'high', height: '600px' },
          ],
          initialDetent: 'medium',
        },
        slots: { default: '<div class="natural-content">Results</div>' },
        attachTo: document.body,
      });
      mountedWrappers.push(wrapper);
      return wrapper;
    }

    async function settleFit() {
      await flushSheetUpdates();
      await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()));
      await flushSheetUpdates();
    }

    it('opens short content at its minimum instead of falling back to a large preset', async () => {
      mountContentSheet();
      await settleFit();
      expect(findSheet('Content Sheet').style.height).toBe('180px');
    });

    it('grows for a result list and shrinks for the overview without retaining the old viewport height', async () => {
      const wrapper = mountContentSheet();
      await settleFit();
      naturalHeight = 380;
      await wrapper.setProps({ contentKey: 'results' });
      await settleFit();
      expect(findSheet('Content Sheet').style.height).toBe('420px');
      naturalHeight = 100;
      await wrapper.setProps({ contentKey: 'overview' });
      await settleFit();
      expect(findSheet('Content Sheet').style.height).toBe('180px');
    });

    it('bounds long content and retains a manual height through subsequent results', async () => {
      const wrapper = mountContentSheet();
      await settleFit();
      const sheet = findSheet('Content Sheet');
      const header = sheet.querySelector<HTMLElement>('.sheet-title')!;
      let now = 1_000;
      const clock = vi.spyOn(Date, 'now').mockImplementation(() => now);
      header.dispatchEvent(pointerEvent('pointerdown', 100, 300));
      window.dispatchEvent(pointerEvent('pointermove', 100, 280));
      now += 1_000;
      window.dispatchEvent(pointerEvent('pointermove', 100, 220));
      window.dispatchEvent(pointerEvent('pointerup', 100, 220));
      clock.mockRestore();
      await settleFit();
      expect(sheet.style.height).toBe('260px');
      naturalHeight = 1_000;
      await wrapper.setProps({ contentKey: 'more-results' });
      await settleFit();
      expect(sheet.style.height).toBe('260px');
      await wrapper.setProps({ modelValue: false });
      await settleFit();
      await wrapper.setProps({ modelValue: true });
      await settleFit();
      expect(sheet.style.height).toBe('600px');
    });

    it('leaves ordinary preset panels unchanged when fitting is not requested', async () => {
      mountContentSheet(false);
      await settleFit();
      expect(findSheet('Content Sheet').style.height).toBe('500px');
    });
  });

  it.each([false, true])(
    'lets a compact map tool ignore the previous sheet size (maximized: %s)',
    async (maximized) => {
      const wrapper = mountStackHarness({
        lowerHeight: '500px',
        upperHeight: '180px',
        upperCoverPreviousSheet: false,
      });
      await flushSheetUpdates();
      if (maximized) {
        findSheet('Lower Sheet').querySelector<HTMLButtonElement>('.sheet-fullscreen-btn')?.click();
        await flushSheetUpdates();
      }
      (wrapper.vm as unknown as { upperOpen: boolean }).upperOpen = true;
      await flushSheetUpdates();
      const tool = findSheet('Upper Sheet');
      expect(tool.style.height).toBe('180px');
      expect(tool.classList.contains('sheet--fullscreen')).toBe(false);
      tool.querySelector<HTMLButtonElement>('.sheet-close-btn')?.click();
      await flushSheetUpdates();
      expect((wrapper.vm as unknown as { lowerOpen: boolean }).lowerOpen).toBe(true);
    }
  );

  it('dims only the lower sheet when a higher stacked sheet opens', async () => {
    const wrapper = mountStackHarness();
    await flushSheetUpdates();

    expect(findSheet('Lower Sheet').classList.contains('sheet--backgrounded')).toBe(false);

    (wrapper.vm as unknown as { upperOpen: boolean }).upperOpen = true;
    await flushSheetUpdates();

    const lowerSheet = findSheet('Lower Sheet');
    const upperSheet = findSheet('Upper Sheet');

    expect(lowerSheet.classList.contains('sheet--backgrounded')).toBe(true);
    expect(lowerSheet.querySelector('.sheet-stack-scrim')).not.toBeNull();
    expect(upperSheet.classList.contains('sheet--backgrounded')).toBe(false);
    expect(upperSheet.querySelector('.sheet-stack-scrim')).toBeNull();
  });

  it('removes lower sheet dimming after the upper sheet closes', async () => {
    const wrapper = mountStackHarness({ upperOpen: true });
    await flushSheetUpdates();

    expect(findSheet('Lower Sheet').classList.contains('sheet--backgrounded')).toBe(true);

    (wrapper.vm as unknown as { upperOpen: boolean }).upperOpen = false;
    await flushSheetUpdates();

    const lowerSheet = findSheet('Lower Sheet');
    expect(lowerSheet.classList.contains('sheet--backgrounded')).toBe(false);
    expect(lowerSheet.querySelector('.sheet-stack-scrim')).toBeNull();
  });

  it('closes only the scoped foreground sheet when stacked sheets expose multiple Close buttons', async () => {
    const wrapper = mountStackHarness({ upperOpen: true });
    await flushSheetUpdates();

    const lowerSheet = findSheet('Lower Sheet');
    const upperSheet = findSheet('Upper Sheet');
    const closeButtons = document.body.querySelectorAll<HTMLButtonElement>('.sheet-close-btn[aria-label="Close"]');
    const foregroundSheets = document.body.querySelectorAll<HTMLElement>(
      '.sheet.sheet--open:not(.sheet--backgrounded)'
    );

    expect(closeButtons).toHaveLength(2);
    expect(lowerSheet.classList.contains('sheet--backgrounded')).toBe(true);
    expect(foregroundSheets).toHaveLength(1);
    expect(foregroundSheets[0]).toBe(upperSheet);

    const foregroundClose = foregroundSheets[0]?.querySelector<HTMLButtonElement>('.sheet-close-btn');
    expect(foregroundClose).not.toBeNull();
    foregroundClose?.click();
    await flushSheetUpdates();

    expect((wrapper.vm as unknown as { lowerOpen: boolean; upperOpen: boolean }).upperOpen).toBe(false);
    expect((wrapper.vm as unknown as { lowerOpen: boolean; upperOpen: boolean }).lowerOpen).toBe(true);
    expect(lowerSheet.classList.contains('sheet--backgrounded')).toBe(false);
    expect(lowerSheet.querySelector('.sheet-stack-scrim')).toBeNull();
  });

  it('uses open order as the tie-breaker for equal z-index sheets', async () => {
    const wrapper = mountStackHarness({ lowerZIndex: 5200, upperZIndex: 5200 });
    await flushSheetUpdates();

    (wrapper.vm as unknown as { upperOpen: boolean }).upperOpen = true;
    await flushSheetUpdates();

    expect(findSheet('Lower Sheet').classList.contains('sheet--backgrounded')).toBe(true);
    expect(findSheet('Upper Sheet').classList.contains('sheet--backgrounded')).toBe(false);
  });

  it('places a newly opened default-layer sheet above the existing sheet', async () => {
    const wrapper = mountStackHarness({ useDefaultZIndex: true });
    await flushSheetUpdates();

    (wrapper.vm as unknown as { upperOpen: boolean }).upperOpen = true;
    await flushSheetUpdates();

    const lowerSheet = findSheet('Lower Sheet');
    const upperSheet = findSheet('Upper Sheet');

    expect(lowerSheet.style.zIndex).toBe('5001');
    expect(upperSheet.style.zIndex).toBe('5003');
  });

  it('does not render an extra viewport backdrop for an explicit stacked child noBackdrop sheet', async () => {
    mountStackHarness({
      lowerNoBackdrop: false,
      upperNoBackdrop: true,
      upperOpen: true,
    });
    await flushSheetUpdates();

    expect(document.body.querySelectorAll('.sheet-backdrop')).toHaveLength(1);
  });

  it('collapses to a configured background detent and restores when foregrounded', async () => {
    const wrapper = mountStackHarness({ lowerBackgroundDetent: { id: 'peek', height: '80px' } });
    await flushSheetUpdates();

    const lowerSheet = findSheet('Lower Sheet');
    const originalHeight = lowerSheet.style.height;

    (wrapper.vm as unknown as { upperOpen: boolean }).upperOpen = true;
    await flushSheetUpdates();

    expect(lowerSheet.style.height).toBe('80px');

    (wrapper.vm as unknown as { upperOpen: boolean }).upperOpen = false;
    await flushSheetUpdates();

    expect(lowerSheet.style.height).toBe(originalHeight);
  });

  it('snaps to the adjacent detent when the drag zone is tapped', async () => {
    mountInteractionHarness();
    await flushSheetUpdates();

    const sheet = findSheet('Interactive Sheet');
    const dragZone = sheet.querySelector('.sheet-drag-zone');
    if (!dragZone) throw new Error('Drag zone not found');

    expect(sheet.style.height).toBe('200px');

    dragZone.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await flushSheetUpdates();

    expect(sheet.style.height).toBe('400px');

    dragZone.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await flushSheetUpdates();

    expect(sheet.style.height).toBe('200px');
  });

  it('does not snap when header actions are tapped', async () => {
    const wrapper = mountInteractionHarness();
    await flushSheetUpdates();

    const sheet = findSheet('Interactive Sheet');
    const action = sheet.querySelector('.header-action');
    if (!action) throw new Error('Header action not found');

    action.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await flushSheetUpdates();

    expect(sheet.style.height).toBe('200px');
    expect((wrapper.vm as unknown as { actionClicks: number }).actionClicks).toBe(1);
  });

  it('leaves horizontal movement to selectable header text while accepting vertical sheet drags', async () => {
    mountInteractionHarness();
    await flushSheetUpdates();

    const sheet = findSheet('Interactive Sheet');
    const trackId = sheet.querySelector<HTMLElement>('.selectable-track-id');
    if (!trackId) throw new Error('Selectable TrackID not found');

    trackId.dispatchEvent(pointerEvent('pointerdown', 100, 100));
    trackId.dispatchEvent(pointerEvent('pointermove', 130, 102));
    trackId.dispatchEvent(pointerEvent('pointerup', 130, 102));
    trackId.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await flushSheetUpdates();

    expect(sheet.style.height).toBe('200px');

    trackId.dispatchEvent(pointerEvent('pointerdown', 100, 100, 2));
    trackId.dispatchEvent(pointerEvent('pointermove', 102, 40, 2));
    await nextTick();

    expect(sheet.style.height).toBe('260px');

    trackId.dispatchEvent(pointerEvent('pointerup', 102, 40, 2));
  });

  it('restores a maximized panel on the first Escape press and closes on the second', async () => {
    const wrapper = mountInteractionHarness();
    await flushSheetUpdates();

    const sheet = findSheet('Interactive Sheet');
    const fullscreenButton = sheet.querySelector<HTMLButtonElement>('.sheet-fullscreen-btn');
    if (!fullscreenButton) throw new Error('Fullscreen button not found');
    expect(fullscreenButton.getAttribute('aria-label')).toBe('Maximize panel');

    fullscreenButton.click();
    await flushSheetUpdates();
    expect(sheet.classList.contains('sheet--fullscreen')).toBe(true);
    expect(fullscreenButton.getAttribute('aria-label')).toBe('Restore panel size');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await flushSheetUpdates();
    expect(sheet.classList.contains('sheet--fullscreen')).toBe(false);
    expect((wrapper.vm as unknown as { open: boolean }).open).toBe(true);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await flushSheetUpdates();
    expect((wrapper.vm as unknown as { open: boolean }).open).toBe(false);
  });

  it('keeps resize subscriptions stable while chart styles change', async () => {
    const observe = vi.fn();
    vi.stubGlobal(
      'ResizeObserver',
      class extends TestResizeObserver {
        observe = observe;
      }
    );
    const wrapper = mount(BottomSheet, {
      props: { modelValue: true, title: 'Chart Sheet', fitSmallOverflow: true, detents: [{ height: '300px' }] },
      slots: { default: '<div class="fake-chart">Chart</div>' },
      attachTo: document.body,
    });
    mountedWrappers.push(wrapper);
    await flushSheetUpdates();
    const subscriptions = observe.mock.calls.length;
    expect(subscriptions).toBeGreaterThan(0);
    findSheet('Chart Sheet').querySelector<HTMLElement>('.fake-chart')!.style.width = '390px';
    await flushSheetUpdates();
    expect(observe.mock.calls).toHaveLength(subscriptions);
  });

  it('dismisses a PrimeVue info popover before closing its sheet', async () => {
    const wrapper = mount(
      defineComponent({
        components: { BottomSheet, Popover },
        data: () => ({ open: true }),
        template: `
        <BottomSheet v-model="open" title="Popover Sheet" :detents="[{ height: '300px' }]">
          <button class="info-trigger" @click="$refs.info.toggle($event)">About duration</button>
          <Popover ref="info"><p>Moving time explanation</p></Popover>
        </BottomSheet>`,
      }),
      { attachTo: document.body, global: { plugins: [PrimeVue], stubs: { transition: false } } }
    );
    mountedWrappers.push(wrapper);
    await flushSheetUpdates();
    findSheet('Popover Sheet').querySelector<HTMLButtonElement>('.info-trigger')!.click();
    await flushSheetUpdates();
    expect(document.querySelector('[data-pc-name="popover"][role="dialog"]')).not.toBeNull();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
    await flushSheetUpdates();
    expect((wrapper.vm as unknown as { open: boolean }).open).toBe(true);
    await vi.waitFor(() => expect(document.querySelector('[data-pc-name="popover"][role="dialog"]')).toBeNull());

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
    await flushSheetUpdates();
    expect((wrapper.vm as unknown as { open: boolean }).open).toBe(false);
  });

  it('keeps maximize and browser fullscreen as exclusive window modes', async () => {
    mountInteractionHarness(true);
    await flushSheetUpdates();

    const sheet = findSheet('Interactive Sheet');
    const requestFullscreen = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(sheet, 'requestFullscreen', { configurable: true, value: requestFullscreen });
    Object.defineProperty(document, 'fullscreenElement', { configurable: true, value: null });

    const maximizeButton = sheet.querySelector<HTMLButtonElement>('.sheet-fullscreen-btn');
    const nativeFullscreenButton = sheet.querySelector<HTMLButtonElement>('.sheet-native-fullscreen-btn');
    if (!maximizeButton || !nativeFullscreenButton) throw new Error('Fullscreen controls not found');

    maximizeButton.click();
    await flushSheetUpdates();
    expect(sheet.classList.contains('sheet--fullscreen')).toBe(true);

    nativeFullscreenButton.click();
    await flushSheetUpdates();

    expect(requestFullscreen).toHaveBeenCalledOnce();
    expect(sheet.classList.contains('sheet--fullscreen')).toBe(false);
    expect(sheet.classList.contains('sheet--native-fullscreen')).toBe(true);
    expect(nativeFullscreenButton.getAttribute('aria-label')).toBe('Exit fullscreen');

    document.dispatchEvent(new Event('fullscreenchange'));
    await flushSheetUpdates();
    expect(sheet.classList.contains('sheet--native-fullscreen')).toBe(false);
    expect(sheet.classList.contains('sheet--fullscreen')).toBe(false);
    expect(nativeFullscreenButton.getAttribute('aria-label')).toBe('Enter fullscreen');

    delete (document as Document & { fullscreenElement?: Element | null }).fullscreenElement;
  });

  it('returns from browser fullscreen to the normal panel before Escape closes it', async () => {
    const wrapper = mountInteractionHarness(true);
    await flushSheetUpdates();

    const sheet = findSheet('Interactive Sheet');
    Object.defineProperty(sheet, 'requestFullscreen', {
      configurable: true,
      value: vi.fn().mockResolvedValue(undefined),
    });
    Object.defineProperty(document, 'fullscreenElement', { configurable: true, value: null });

    sheet.querySelector<HTMLButtonElement>('.sheet-fullscreen-btn')?.click();
    sheet.querySelector<HTMLButtonElement>('.sheet-native-fullscreen-btn')?.click();
    await flushSheetUpdates();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await flushSheetUpdates();
    expect(sheet.classList.contains('sheet--native-fullscreen')).toBe(false);
    expect(sheet.classList.contains('sheet--fullscreen')).toBe(false);
    expect((wrapper.vm as unknown as { open: boolean }).open).toBe(true);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await flushSheetUpdates();
    expect((wrapper.vm as unknown as { open: boolean }).open).toBe(false);

    delete (document as Document & { fullscreenElement?: Element | null }).fullscreenElement;
  });

  it('unmounts without a fullscreen API on browsers that expose a null fullscreen element', async () => {
    const fullscreenDescriptor = Object.getOwnPropertyDescriptor(document, 'fullscreenElement');
    const exitDescriptor = Object.getOwnPropertyDescriptor(document, 'exitFullscreen');
    Object.defineProperty(document, 'fullscreenElement', { configurable: true, value: null });
    Object.defineProperty(document, 'exitFullscreen', { configurable: true, value: undefined });
    try {
      const wrapper = mountInteractionHarness();
      await flushSheetUpdates();
      expect(() => wrapper.unmount()).not.toThrow();
      mountedWrappers.splice(mountedWrappers.indexOf(wrapper), 1);
    } finally {
      if (fullscreenDescriptor) Object.defineProperty(document, 'fullscreenElement', fullscreenDescriptor);
      else delete (document as Document & { fullscreenElement?: Element | null }).fullscreenElement;
      if (exitDescriptor) Object.defineProperty(document, 'exitFullscreen', exitDescriptor);
      else delete (document as { exitFullscreen?: unknown }).exitFullscreen;
    }
  });

  it('exits owned fullscreen before the element ref is cleared on unmount', async () => {
    const fullscreenDescriptor = Object.getOwnPropertyDescriptor(document, 'fullscreenElement');
    const exitDescriptor = Object.getOwnPropertyDescriptor(document, 'exitFullscreen');
    const exit = vi.fn().mockResolvedValue(undefined);
    const wrapper = mountInteractionHarness(true);
    await flushSheetUpdates();
    Object.defineProperty(document, 'fullscreenElement', { configurable: true, value: findSheet('Interactive Sheet') });
    Object.defineProperty(document, 'exitFullscreen', { configurable: true, value: exit });
    try {
      wrapper.unmount();
      mountedWrappers.splice(mountedWrappers.indexOf(wrapper), 1);
      expect(exit).toHaveBeenCalledOnce();
    } finally {
      if (fullscreenDescriptor) Object.defineProperty(document, 'fullscreenElement', fullscreenDescriptor);
      else delete (document as Document & { fullscreenElement?: Element | null }).fullscreenElement;
      if (exitDescriptor) Object.defineProperty(document, 'exitFullscreen', exitDescriptor);
      else delete (document as { exitFullscreen?: unknown }).exitFullscreen;
    }
  });

  it('closes the native fullscreen fallback when exitFullscreen is unavailable', async () => {
    const fullscreenDescriptor = Object.getOwnPropertyDescriptor(document, 'fullscreenElement');
    const exitDescriptor = Object.getOwnPropertyDescriptor(document, 'exitFullscreen');
    Object.defineProperty(document, 'fullscreenElement', { configurable: true, value: null });
    Object.defineProperty(document, 'exitFullscreen', { configurable: true, value: undefined });
    try {
      const wrapper = mountInteractionHarness(true);
      await flushSheetUpdates();
      findSheet('Interactive Sheet').querySelector<HTMLButtonElement>('.sheet-native-fullscreen-btn')!.click();
      await flushSheetUpdates();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      await flushSheetUpdates();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      await flushSheetUpdates();
      expect((wrapper.vm as unknown as { open: boolean }).open).toBe(false);
      expect(() => wrapper.unmount()).not.toThrow();
      mountedWrappers.splice(mountedWrappers.indexOf(wrapper), 1);
    } finally {
      if (fullscreenDescriptor) Object.defineProperty(document, 'fullscreenElement', fullscreenDescriptor);
      else delete (document as Document & { fullscreenElement?: Element | null }).fullscreenElement;
      if (exitDescriptor) Object.defineProperty(document, 'exitFullscreen', exitDescriptor);
      else delete (document as { exitFullscreen?: unknown }).exitFullscreen;
    }
  });

  it('keeps footer actions outside the scrollable and faded content region', async () => {
    mountFooterHarness();
    await flushSheetUpdates();

    const sheet = findSheet('Footer Sheet');
    const bodyFrame = sheet.querySelector('.sheet-body-frame');
    const body = sheet.querySelector('.sheet-body');
    const footer = sheet.querySelector('.sheet-footer');
    const footerAction = sheet.querySelector('.footer-action');

    expect(bodyFrame).not.toBeNull();
    expect(footer).not.toBeNull();
    expect(body?.contains(footerAction)).toBe(false);
    expect(bodyFrame?.contains(footer)).toBe(false);
  });

  it.each([8, 34])('fits content, fixed chrome and %s px of bottom padding', async (bottomPadding) => {
    const wrapper = mount(
      defineComponent({
        components: { BottomSheet },
        data: () => ({ open: false }),
        template: `
          <BottomSheet v-model="open" title="Inset Sheet"
            :detents="[{ height: '180px' }, { height: '90vh' }]" fit-content-initial>
            <div>Menu content</div>
            <template #footer><button type="button">Apply</button></template>
          </BottomSheet>
        `,
      }),
      { attachTo: document.body }
    );
    mountedWrappers.push(wrapper);
    await flushSheetUpdates();
    const sheet = findSheet('Inset Sheet');
    sheet.style.paddingTop = '6px';
    sheet.style.paddingBottom = `${bottomPadding}px`;
    sheet.style.borderTop = '2px solid';
    sheet.style.borderBottom = '2px solid';
    Object.defineProperty(sheet.querySelector('.sheet-drag-zone'), 'offsetHeight', { value: 48 });
    Object.defineProperty(sheet.querySelector('.sheet-body'), 'scrollHeight', { value: 360 });
    Object.defineProperty(sheet.querySelector('.sheet-footer'), 'offsetHeight', { value: 40 });
    (wrapper.vm as unknown as { open: boolean }).open = true;
    await flushSheetUpdates();

    const contentSpace = Number.parseFloat(sheet.style.height) - 6 - bottomPadding - 4 - 48 - 40;
    expect(contentSpace).toBeGreaterThanOrEqual(360);
  });

  it('applies the shared mobile scroll contract to the detected scroll target', async () => {
    const wrapper = mountScrollHarness();
    await flushSheetUpdates();

    const sheet = findSheet('Scroll Sheet');
    const scrollTarget = sheet.querySelector<HTMLElement>('.test-scroll-target');
    if (!scrollTarget) throw new Error('Scroll target not found');

    Object.defineProperties(scrollTarget, {
      clientHeight: { configurable: true, value: 100 },
      scrollHeight: { configurable: true, value: 300 },
      scrollTop: { configurable: true, value: 0, writable: true },
    });
    const scrollBy = vi.fn();
    Object.defineProperty(scrollTarget, 'scrollBy', { configurable: true, value: scrollBy });

    scrollTarget.append(document.createElement('span'));
    await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
    await flushSheetUpdates();

    expect(scrollTarget.classList.contains('sheet-scroll-target')).toBe(true);
    const scrollButton = sheet.querySelector<HTMLButtonElement>('.sheet-scroll-hint__button');
    expect(scrollButton?.getAttribute('aria-label')).toBe('Scroll down: More views');
    expect(scrollButton?.querySelector('.sheet-scroll-hint__label')?.textContent).toBe('More views');

    scrollButton?.click();
    expect(scrollBy).toHaveBeenCalledWith({ top: 80, behavior: 'smooth' });

    (wrapper.vm as unknown as { open: boolean }).open = false;
    await flushSheetUpdates();
    expect(scrollTarget.classList.contains('sheet-scroll-target')).toBe(false);
  });

  it('does not render a scroll hint when the content fits the viewport', async () => {
    mountScrollHarness();
    await flushSheetUpdates();

    const sheet = findSheet('Scroll Sheet');
    const scrollTarget = sheet.querySelector<HTMLElement>('.test-scroll-target');
    if (!scrollTarget) throw new Error('Scroll target not found');

    Object.defineProperties(scrollTarget, {
      clientHeight: { configurable: true, value: 300 },
      scrollHeight: { configurable: true, value: 300 },
      scrollTop: { configurable: true, value: 0, writable: true },
    });
    scrollTarget.append(document.createElement('span'));
    await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
    await flushSheetUpdates();

    expect(sheet.querySelector('.sheet-scroll-hint')).toBeNull();
  });

  it.each([false, true])('allows native content movement only when it can scroll: %s', async (canScroll) => {
    mountScrollHarness();
    await flushSheetUpdates();
    const sheet = findSheet('Scroll Sheet');
    const target = sheet.querySelector<HTMLElement>('.test-scroll-target')!;
    Object.defineProperties(target, {
      clientHeight: { configurable: true, value: 100 },
      scrollHeight: { configurable: true, value: canScroll ? 300 : 100 },
    });
    target.append(document.createElement('span'));
    await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
    await flushSheetUpdates();
    const height = sheet.style.height;
    target.dispatchEvent(touchEvent('touchstart', 100, 200));
    const move = touchEvent('touchmove', 100, 160);
    expect(target.dispatchEvent(move)).toBe(canScroll);
    expect(move.defaultPrevented).toBe(!canScroll);
    expect(sheet.style.height).toBe(height);
  });

  it.each([false, true])(
    'allows horizontal tabs outside the primary scroll viewport (vertical overflow: %s)',
    async (verticalOverflow) => {
      const wrapper = mount(
        defineComponent({
          components: { BottomSheet },
          data: () => ({ open: true }),
          template: `<BottomSheet v-model="open" title="Tabs Sheet" :detents="[{ height: '300px' }]">
        <div class="test-tabs" style="overflow-x: auto; touch-action: pan-x"><span>Tabs</span></div>
        <div data-sheet-fit-target class="test-panels" style="overflow-y: auto">Panel</div>
      </BottomSheet>`,
        }),
        { attachTo: document.body }
      );
      mountedWrappers.push(wrapper);
      await flushSheetUpdates();
      const sheet = findSheet('Tabs Sheet');
      const tabs = sheet.querySelector<HTMLElement>('.test-tabs')!;
      const panels = sheet.querySelector<HTMLElement>('.test-panels')!;
      Object.defineProperties(tabs, {
        clientWidth: { configurable: true, value: 200 },
        scrollWidth: { configurable: true, value: 500 },
        scrollLeft: { configurable: true, value: 0, writable: true },
      });
      Object.defineProperties(panels, {
        clientHeight: { configurable: true, value: 100 },
        scrollHeight: { configurable: true, value: verticalOverflow ? 300 : 100 },
      });
      panels.append(document.createElement('span'));
      await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
      await flushSheetUpdates();
      const target = tabs.querySelector('span')!;
      target.dispatchEvent(touchEvent('touchstart', 100, 200));
      const move = touchEvent('touchmove', 60, 200);
      target.dispatchEvent(move);
      expect(move.defaultPrevented).toBe(false);
    }
  );

  it('allows vertical scrolling when the sheet body itself overflows', async () => {
    mountInteractionHarness();
    await flushSheetUpdates();
    const body = findSheet('Interactive Sheet').querySelector<HTMLElement>('.sheet-body')!;
    body.style.overflowY = 'auto';
    Object.defineProperties(body, {
      clientHeight: { configurable: true, value: 100 },
      scrollHeight: { configurable: true, value: 300 },
      scrollTop: { configurable: true, value: 0, writable: true },
    });
    const target = body.querySelector('div')!;
    target.dispatchEvent(touchEvent('touchstart', 100, 200));
    const move = touchEvent('touchmove', 100, 160);
    target.dispatchEvent(move);
    expect(move.defaultPrevented).toBe(false);
  });

  it.each([
    { axis: 'y', offset: 0, dx: 0, dy: 40, allowed: false },
    { axis: 'y', offset: 0, dx: 0, dy: -40, allowed: true },
    { axis: 'y', offset: 0.5, dx: 0, dy: 40, allowed: false },
    { axis: 'y', offset: 2, dx: 0, dy: 40, allowed: true },
    { axis: 'y', offset: 198, dx: 0, dy: -40, allowed: true },
    { axis: 'y', offset: 199.5, dx: 0, dy: -40, allowed: false },
    { axis: 'y', offset: 200, dx: 0, dy: -40, allowed: false },
    { axis: 'y', offset: 200, dx: 0, dy: 40, allowed: true },
    { axis: 'y', offset: 100, dx: 0, dy: 40, allowed: true },
    { axis: 'x', offset: 0, dx: 40, dy: 0, allowed: false },
    { axis: 'x', offset: 0, dx: -40, dy: 0, allowed: true },
    { axis: 'x', offset: 0.5, dx: 40, dy: 0, allowed: false },
    { axis: 'x', offset: 2, dx: 40, dy: 0, allowed: true },
    { axis: 'x', offset: 198, dx: -40, dy: 0, allowed: true },
    { axis: 'x', offset: 199.5, dx: -40, dy: 0, allowed: false },
    { axis: 'x', offset: 200, dx: -40, dy: 0, allowed: false },
    { axis: 'x', offset: 200, dx: 40, dy: 0, allowed: true },
    { axis: 'x', offset: 100, dx: -40, dy: 0, allowed: true },
    { axis: 'x', offset: 100, dx: 0, dy: -40, allowed: false },
  ])('contains movement at scroll boundaries: $axis, $offset, $dx, $dy', async ({ axis, offset, dx, dy, allowed }) => {
    mountScrollHarness();
    await flushSheetUpdates();
    const target = findSheet('Scroll Sheet').querySelector<HTMLElement>('.test-scroll-target')!;
    target.style.overflowX = axis === 'x' ? 'auto' : 'hidden';
    target.style.overflowY = axis === 'y' ? 'auto' : 'hidden';
    Object.defineProperties(target, {
      clientWidth: { configurable: true, value: 100 },
      scrollWidth: { configurable: true, value: axis === 'x' ? 300 : 100 },
      scrollLeft: { configurable: true, value: axis === 'x' ? offset : 0, writable: true },
      clientHeight: { configurable: true, value: 100 },
      scrollHeight: { configurable: true, value: axis === 'y' ? 300 : 100 },
      scrollTop: { configurable: true, value: axis === 'y' ? offset : 0, writable: true },
    });
    target.dispatchEvent(touchEvent('touchstart', 100, 200));
    const move = touchEvent('touchmove', 100 + dx, 200 + dy);
    target.dispatchEvent(move);
    expect(move.defaultPrevented).toBe(!allowed);
  });

  it.each([
    { offset: 0, dx: -40, allowed: false },
    { offset: -0.5, dx: -40, allowed: false },
    { offset: -2, dx: -40, allowed: true },
    { offset: -198, dx: 40, allowed: true },
    { offset: -199.5, dx: 40, allowed: false },
    { offset: -200, dx: 40, allowed: false },
    { offset: -100, dx: 40, allowed: true },
  ])('contains RTL scroll edges: $offset, $dx', async ({ offset, dx, allowed }) => {
    mountScrollHarness();
    await flushSheetUpdates();
    const target = findSheet('Scroll Sheet').querySelector<HTMLElement>('.test-scroll-target')!;
    target.style.overflowX = 'auto';
    target.style.overflowY = 'hidden';
    target.style.direction = 'rtl';
    Object.defineProperties(target, {
      clientWidth: { configurable: true, value: 100 },
      scrollWidth: { configurable: true, value: 300 },
      scrollLeft: { configurable: true, value: offset, writable: true },
    });
    target.dispatchEvent(touchEvent('touchstart', 100, 200));
    const move = touchEvent('touchmove', 100 + dx, 200);
    target.dispatchEvent(move);
    expect(move.defaultPrevented).toBe(!allowed);
  });

  it.each(['x', 'y'])('keeps the overflow tolerance separate from %s edge detection', async (axis) => {
    mountScrollHarness();
    await flushSheetUpdates();
    const target = findSheet('Scroll Sheet').querySelector<HTMLElement>('.test-scroll-target')!;
    target.style.overflowX = axis === 'x' ? 'auto' : 'hidden';
    target.style.overflowY = axis === 'y' ? 'auto' : 'hidden';
    Object.defineProperties(target, {
      clientWidth: { configurable: true, value: 100 },
      scrollWidth: { configurable: true, value: axis === 'x' ? 104 : 100 },
      scrollLeft: { configurable: true, value: axis === 'x' ? 2 : 0 },
      clientHeight: { configurable: true, value: 100 },
      scrollHeight: { configurable: true, value: axis === 'y' ? 104 : 100 },
      scrollTop: { configurable: true, value: axis === 'y' ? 2 : 0 },
    });
    target.dispatchEvent(touchEvent('touchstart', 100, 200));
    const move = touchEvent('touchmove', axis === 'x' ? 60 : 100, axis === 'y' ? 160 : 200);
    target.dispatchEvent(move);
    expect(move.defaultPrevented).toBe(true);
  });

  it.each([false, true])('preserves native range drags (vertical overflow: %s)', async (verticalOverflow) => {
    mountScrollHarness();
    await flushSheetUpdates();
    const sheet = findSheet('Scroll Sheet');
    const height = sheet.style.height;
    const target = sheet.querySelector<HTMLElement>('.test-scroll-target')!;
    Object.defineProperties(target, {
      clientHeight: { configurable: true, value: 100 },
      scrollHeight: { configurable: true, value: verticalOverflow ? 300 : 100 },
      scrollTop: { configurable: true, value: 0, writable: true },
    });
    const range = document.createElement('input');
    range.type = 'range';
    target.append(range);
    range.dispatchEvent(touchEvent('touchstart', 100, 200));
    for (const x of [101, 140, 60]) {
      const move = touchEvent('touchmove', x, 200);
      range.dispatchEvent(move);
      expect(move.defaultPrevented).toBe(false);
    }
    range.dispatchEvent(touchEvent('touchend', 60, 200, 0));
    // Missing gesture state must not cancel a native control's next movement.
    const moveAfterEnd = touchEvent('touchmove', 120, 200);
    range.dispatchEvent(moveAfterEnd);
    expect(moveAfterEnd.defaultPrevented).toBe(false);
    expect(sheet.style.height).toBe(height);

    target.dispatchEvent(touchEvent('touchstart', 100, 200));
    const contentMove = touchEvent('touchmove', 100, 160);
    target.dispatchEvent(contentMove);
    expect(contentMove.defaultPrevented).toBe(!verticalOverflow);
    // Horizontal gestures on ordinary content remain contained.
    target.dispatchEvent(touchEvent('touchstart', 100, 200));
    const horizontalMove = touchEvent('touchmove', 140, 200);
    target.dispatchEvent(horizontalMove);
    expect(horizontalMove.defaultPrevented).toBe(true);
  });

  it.each([
    { type: 'range', disabled: true },
    { type: 'range', disabled: false, fieldsetDisabled: true },
    { type: 'text', disabled: false },
  ])('contains gestures on controls without native dragging: $type, $disabled, $fieldsetDisabled', async (control) => {
    mountScrollHarness();
    await flushSheetUpdates();
    const target = findSheet('Scroll Sheet').querySelector<HTMLElement>('.test-scroll-target')!;
    const fieldset = document.createElement('fieldset');
    fieldset.disabled = control.fieldsetDisabled ?? false;
    const input = document.createElement('input');
    input.type = control.type;
    input.disabled = control.disabled;
    fieldset.append(input);
    target.append(fieldset);
    input.dispatchEvent(touchEvent('touchstart', 100, 200));
    const move = touchEvent('touchmove', 140, 200);
    input.dispatchEvent(move);
    expect(move.defaultPrevented).toBe(true);
  });

  it('preserves custom slider pointer updates inside a sheet', async () => {
    const wrapper = mount(
      defineComponent({
        components: { BottomSheet, MtlSlider },
        data: () => ({ open: true, value: 20 }),
        template: `<BottomSheet v-model="open" title="Slider Sheet" :detents="[{ height: '300px' }]">
          <MtlSlider v-model="value" aria-label="Test value" />
        </BottomSheet>`,
      }),
      { attachTo: document.body }
    );
    mountedWrappers.push(wrapper);
    await flushSheetUpdates();
    const sheet = findSheet('Slider Sheet');
    const slider = sheet.querySelector<HTMLElement>('.mtl-slider')!;
    vi.spyOn(slider.querySelector('.mtl-slider__track')!, 'getBoundingClientRect').mockReturnValue({
      left: 0,
      width: 100,
    } as DOMRect);
    slider.dispatchEvent(pointerEvent('pointerdown', 20, 200));
    slider.dispatchEvent(touchEvent('touchstart', 20, 200));
    slider.dispatchEvent(touchEvent('touchmove', 80, 200));
    window.dispatchEvent(pointerEvent('pointermove', 80, 200));
    window.dispatchEvent(pointerEvent('pointerup', 80, 200));
    await flushSheetUpdates();
    expect((wrapper.vm as unknown as { value: number }).value).toBe(80);
    expect(wrapper.findComponent(MtlSlider).emitted('slideend')?.[0][0]).toMatchObject({ value: 80 });
  });

  it('preserves multi-touch gestures in fitted content', async () => {
    mountInteractionHarness();
    await flushSheetUpdates();
    const target = findSheet('Interactive Sheet').querySelector<HTMLElement>('.sheet-body')!;
    target.dispatchEvent(touchEvent('touchstart', 100, 200, 2));
    const move = touchEvent('touchmove', 100, 160, 2);
    target.dispatchEvent(move);
    expect(move.defaultPrevented).toBe(false);
  });

  it('bounds the fade to the nested scroll viewport and follows responsive layout changes', async () => {
    mountScrollHarness();
    await flushSheetUpdates();

    const sheet = findSheet('Scroll Sheet');
    const frame = sheet.querySelector<HTMLElement>('.sheet-body-frame');
    const target = sheet.querySelector<HTMLElement>('.test-scroll-target');
    if (!frame || !target) throw new Error('Scroll frame or target not found');

    Object.defineProperties(target, {
      clientHeight: { configurable: true, value: 550 },
      scrollHeight: { configurable: true, value: 1200 },
      scrollTop: { configurable: true, value: 0 },
    });
    vi.spyOn(frame, 'getBoundingClientRect').mockReturnValue(new DOMRect(100, 200, 900, 600));
    const targetRect = vi.spyOn(target, 'getBoundingClientRect');
    targetRect.mockReturnValue(new DOMRect(340, 240, 650, 550));

    target.append(document.createElement('span'));
    await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
    await flushSheetUpdates();

    const hint = sheet.querySelector<HTMLElement>('.sheet-scroll-hint');
    expect(hint?.style.left).toBe('240px');
    expect(hint?.style.right).toBe('10px');
    expect(hint?.style.bottom).toBe('10px');
    expect(hint?.style.maxHeight).toBe('550px');

    // Mobile hides the sidebar and lets the content viewport span the frame.
    targetRect.mockReturnValue(new DOMRect(100, 240, 900, 560));
    window.dispatchEvent(new Event('resize'));
    await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()));
    await flushSheetUpdates();

    expect(hint?.style.left).toBe('0px');
    expect(hint?.style.right).toBe('0px');
    expect(hint?.style.bottom).toBe('0px');
    expect(hint?.style.maxHeight).toBe('560px');
  });

  it('updates scroll-hint placement after resize delivery and cancels pending reads on removal', async () => {
    const wrapper = mountScrollHarness();
    await flushSheetUpdates();
    const sheet = findSheet('Scroll Sheet');
    const frame = sheet.querySelector<HTMLElement>('.sheet-body-frame')!;
    const target = sheet.querySelector<HTMLElement>('.test-scroll-target')!;
    Object.defineProperties(target, {
      clientHeight: { configurable: true, value: 100 },
      scrollHeight: { configurable: true, value: 300 },
    });
    const frameRect = vi.spyOn(frame, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 400, 300));
    const targetRect = vi.spyOn(target, 'getBoundingClientRect').mockReturnValue(new DOMRect(10, 0, 390, 100));
    target.append(document.createElement('span'));
    await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
    await flushSheetUpdates();
    const hint = sheet.querySelector<HTMLElement>('.sheet-scroll-hint')!;
    expect(hint.style.left).toBe('10px');
    vi.useFakeTimers();
    try {
      targetRect.mockReturnValue(new DOMRect(0, 0, 400, 100));
      frameRect.mockClear();
      TestResizeObserver.notify(target);
      TestResizeObserver.notify(target);
      await nextTick();
      expect(frameRect).not.toHaveBeenCalled();
      expect(hint.style.left).toBe('10px');
      vi.advanceTimersToNextFrame();
      await nextTick();
      expect(hint.style.left).toBe('0px');
      frameRect.mockClear();
      TestResizeObserver.notify(target);
      wrapper.unmount();
      vi.advanceTimersToNextFrame();
      expect(frameRect).not.toHaveBeenCalled();
    } finally {
      vi.useRealTimers();
      frameRect.mockRestore();
      targetRect.mockRestore();
    }
  });

  it('keeps the scroll-hint overlay mounted when it hides at the bottom', async () => {
    mountScrollHarness();
    await flushSheetUpdates();

    const sheet = findSheet('Scroll Sheet');
    const scrollTarget = sheet.querySelector<HTMLElement>('.test-scroll-target');
    if (!scrollTarget) throw new Error('Scroll target not found');

    Object.defineProperties(scrollTarget, {
      clientHeight: { configurable: true, value: 100 },
      scrollHeight: { configurable: true, value: 300 },
      scrollTop: { configurable: true, value: 0, writable: true },
    });
    scrollTarget.append(document.createElement('span'));
    await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
    await flushSheetUpdates();

    const scrollHint = sheet.querySelector<HTMLElement>('.sheet-scroll-hint');
    if (!scrollHint) throw new Error('Scroll hint not found');

    scrollTarget.scrollTop = 200;
    scrollTarget.dispatchEvent(new Event('scroll'));
    await flushSheetUpdates();

    expect(sheet.querySelector('.sheet-scroll-hint')).toBe(scrollHint);
    expect(scrollHint.style.display).toBe('none');

    scrollTarget.scrollTop = 50;
    scrollTarget.dispatchEvent(new Event('scroll'));
    await flushSheetUpdates();

    expect(sheet.querySelector('.sheet-scroll-hint')).toBe(scrollHint);
    expect(scrollHint.style.display).not.toBe('none');
  });

  it('returns to a hub with Back or Escape, then closes from the hub', async () => {
    const wrapper = mount(BottomSheet, {
      props: { modelValue: true, title: 'Section', backLabel: 'Back to hub', detents: [{ height: '400px' }] },
      attachTo: document.body,
    });
    mountedWrappers.push(wrapper);
    await flushSheetUpdates();

    const sheet = findSheet('Section');
    sheet.querySelector<HTMLButtonElement>('[aria-label="Back to hub"]')?.click();
    await flushSheetUpdates();
    expect(wrapper.emitted('back')).toHaveLength(1);
    expect(wrapper.emitted('update:modelValue')).toBeUndefined();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await flushSheetUpdates();
    expect(wrapper.emitted('back')).toHaveLength(2);
    expect(wrapper.emitted('update:modelValue')).toBeUndefined();

    await wrapper.setProps({ backLabel: '' });
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await flushSheetUpdates();
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]]);
  });

  it('uses the sheet body as a fallback scroll target for unconstrained content', async () => {
    mountFooterHarness();
    await flushSheetUpdates();

    const sheet = findSheet('Footer Sheet');
    const body = sheet.querySelector<HTMLElement>('.sheet-body');
    if (!body) throw new Error('Sheet body not found');
    // SFC styles are not injected by this unit-test transform. Mirror the
    // component's fallback overflow so scroll-target detection can run.
    body.style.overflowY = 'auto';

    Object.defineProperties(body, {
      clientHeight: { configurable: true, value: 100 },
      scrollHeight: { configurable: true, value: 300 },
      scrollTop: { configurable: true, value: 0, writable: true },
    });
    body.append(document.createElement('span'));
    await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
    await flushSheetUpdates();

    expect(body.classList.contains('sheet-scroll-target')).toBe(true);
    expect(sheet.querySelector('.sheet-scroll-hint__button')).not.toBeNull();
  });

  it('recomputes the active detent when the viewport height changes', async () => {
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 800 });
    mountResizeHarness();
    await flushSheetUpdates();

    const sheet = findSheet('Responsive Sheet');
    expect(sheet.style.height).toBe('600px');

    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 400 });
    window.dispatchEvent(new Event('resize'));
    await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()));
    await flushSheetUpdates();

    expect(sheet.style.height).toBe('300px');
  });

  it('restores a resized detent after a backgrounded sheet returns', async () => {
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 800 });
    const wrapper = mountStackHarness({ lowerBackgroundDetent: { id: 'peek', height: '80px' } });
    await flushSheetUpdates();

    const lowerSheet = findSheet('Lower Sheet');
    expect(lowerSheet.style.height).toBe('400px');

    (wrapper.vm as unknown as { upperOpen: boolean }).upperOpen = true;
    await flushSheetUpdates();
    expect(lowerSheet.style.height).toBe('80px');

    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 400 });
    window.dispatchEvent(new Event('resize'));
    await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()));
    await flushSheetUpdates();

    (wrapper.vm as unknown as { upperOpen: boolean }).upperOpen = false;
    await flushSheetUpdates();
    expect(lowerSheet.style.height).toBe('200px');
  });

  describe('adaptive panels', () => {
    function mountAdaptiveHarness(options: {
      dragToClose?: boolean;
      deferClose?: boolean;
      snapOnRelease?: boolean;
      freeResize?: boolean;
      sizeAction?: boolean;
    }) {
      const wrapper = mount(
        defineComponent({
          components: { BottomSheet },
          data() {
            return {
              open: true,
              detentChanges: [] as string[],
              dragToClose: options.dragToClose ?? true,
              deferClose: options.deferClose ?? false,
              snapOnRelease: options.snapOnRelease ?? false,
              freeResize: options.freeResize ?? false,
              resizedHeights: [] as number[],
              sizeAction: options.sizeAction ?? false,
            };
          },
          template: `
            <BottomSheet
              ref="sheet"
              v-model="open"
              title="Adaptive Sheet"
              :detents="[
                { id: 'low', height: '200px' },
                { id: 'high', height: '400px' },
              ]"
              initial-detent="low"
              :fit-small-overflow="false"
              :drag-to-close="dragToClose"
              :defer-close="deferClose"
              :snap-on-release="snapOnRelease"
              :free-resize="freeResize"
              @detent-change="detentChanges.push($event)"
              @resize-end="resizedHeights.push($event)"
            >
              <template v-if="sizeAction" #size-action>
                <button class="custom-size" type="button">More map</button>
              </template>
              <template #pinned><div class="pinned-summary">Totals</div></template>
              <div class="body-content">Tools <span class="native-chart" data-sheet-native-gesture>Chart</span></div>
            </BottomSheet>
          `,
        }),
        { attachTo: document.body }
      );
      mountedWrappers.push(wrapper);
      return wrapper as VueWrapper<
        { open: boolean; detentChanges: string[]; resizedHeights: number[] } & {
          $refs: { sheet: { snapTo(id: string): boolean; resizeTo(heightPx: number): boolean } };
        }
      >;
    }

    async function dragHeader(sheet: HTMLElement, fromY: number, toY: number, durationMs: number) {
      const target = sheet.querySelector<HTMLElement>('.sheet-title');
      if (!target) throw new Error('Sheet title not found');
      let now = 1_000;
      const clock = vi.spyOn(Date, 'now').mockImplementation(() => now);
      target.dispatchEvent(pointerEvent('pointerdown', 100, fromY, 7));
      window.dispatchEvent(pointerEvent('pointermove', 100, fromY + Math.sign(toY - fromY) * 20, 7));
      now += durationMs;
      window.dispatchEvent(pointerEvent('pointermove', 100, toY, 7));
      window.dispatchEvent(pointerEvent('pointerup', 100, toY, 7));
      clock.mockRestore();
      await flushSheetUpdates();
    }

    it('repeats a size command even when the same detent was requested before', async () => {
      const wrapper = mountAdaptiveHarness({});
      await flushSheetUpdates();
      const sheet = findSheet('Adaptive Sheet');
      const handle = wrapper.vm.$refs.sheet;

      expect(handle.snapTo('high')).toBe(true);
      await flushSheetUpdates();
      expect(sheet.style.height).toBe('400px');

      expect(handle.snapTo('low')).toBe(true);
      await flushSheetUpdates();
      await dragHeader(sheet, 300, 220, 1_000);
      expect(sheet.style.height).toBe('280px');

      expect(handle.snapTo('low')).toBe(true);
      await flushSheetUpdates();
      expect(sheet.style.height).toBe('200px');
      expect(wrapper.vm.detentChanges.slice(-3)).toEqual(['high', 'low', 'low']);
      expect(handle.snapTo('missing')).toBe(false);
    });

    it('ignores size commands while maximized until the size action restores the panel', async () => {
      const wrapper = mount(
        defineComponent({
          components: { BottomSheet },
          data() {
            return { open: true };
          },
          template: `
            <BottomSheet
              ref="sheet"
              v-model="open"
              title="Maximized Sheet"
              :detents="[{ id: 'low', height: '200px' }, { id: 'high', height: '400px' }]"
              initial-detent="low"
              :fit-small-overflow="false"
              initially-maximized
            >
              <template #size-action="{ maximized, restore }">
                <button class="custom-size" type="button" @click="maximized && restore(); $refs.sheet.snapTo('high')">
                  {{ maximized ? 'Maximized' : 'Normal' }}
                </button>
              </template>
            </BottomSheet>
          `,
        }),
        { attachTo: document.body }
      );
      mountedWrappers.push(wrapper);
      await flushSheetUpdates();
      const sheet = findSheet('Maximized Sheet');
      const handle = (wrapper.vm.$refs as { sheet: { snapTo(id: string): boolean } }).sheet;

      expect(sheet.classList.contains('sheet--fullscreen')).toBe(true);
      expect(handle.snapTo('high')).toBe(false);
      expect(sheet.querySelector('.custom-size')?.textContent?.trim()).toBe('Maximized');

      sheet.querySelector<HTMLElement>('.custom-size')!.click();
      await flushSheetUpdates();
      expect(sheet.classList.contains('sheet--fullscreen')).toBe(false);
      expect(sheet.style.height).toBe('400px');
    });

    it.each([
      { dragToClose: true, open: false },
      { dragToClose: false, open: true },
    ])('handles a fast downward flick with dragToClose=$dragToClose', async ({ dragToClose, open }) => {
      const wrapper = mountAdaptiveHarness({ dragToClose });
      await flushSheetUpdates();
      const sheet = findSheet('Adaptive Sheet');

      await dragHeader(sheet, 300, 600, 10);

      expect(wrapper.vm.open).toBe(open);
      if (open) expect(sheet.style.height).toBe('200px');
    });

    it('restores the pre-drag size while a drag-to-close request is deferred', async () => {
      const wrapper = mountAdaptiveHarness({ dragToClose: true, deferClose: true });
      const component = wrapper.getComponent(BottomSheet);
      await flushSheetUpdates();
      const sheet = findSheet('Adaptive Sheet');
      await dragHeader(sheet, 300, 600, 10);
      expect(wrapper.vm.open).toBe(true);
      expect(sheet.style.height).toBe('200px');
      expect(component.emitted('close-request')).toHaveLength(1);
      expect(component.emitted('update:modelValue')).toBeUndefined();
      expect(component.emitted('closed')).toBeUndefined();
    });

    it('never drags below the lowest detent when dragToClose is false', async () => {
      mountAdaptiveHarness({ dragToClose: false });
      await flushSheetUpdates();
      const sheet = findSheet('Adaptive Sheet');
      const target = sheet.querySelector<HTMLElement>('.sheet-title')!;

      target.dispatchEvent(pointerEvent('pointerdown', 100, 300, 8));
      window.dispatchEvent(pointerEvent('pointermove', 100, 320, 8));
      window.dispatchEvent(pointerEvent('pointermove', 100, 500, 8));
      await nextTick();
      expect(sheet.style.height).toBe('200px');
      window.dispatchEvent(pointerEvent('pointerup', 100, 500, 8));
    });

    it.each([
      { snapOnRelease: false, height: '320px' },
      { snapOnRelease: true, height: '400px' },
    ])('settles a slow release with snapOnRelease=$snapOnRelease', async ({ snapOnRelease, height }) => {
      const wrapper = mountAdaptiveHarness({ snapOnRelease });
      await flushSheetUpdates();
      const sheet = findSheet('Adaptive Sheet');

      await dragHeader(sheet, 300, 180, 1_000);

      expect(sheet.style.height).toBe(height);
      expect(wrapper.vm.detentChanges.includes('high')).toBe(snapOnRelease);
    });

    it.each([10, 1_000])('preserves the released height with free resizing (%s ms drag)', async (duration) => {
      const wrapper = mountAdaptiveHarness({ dragToClose: false, freeResize: true, snapOnRelease: true });
      await flushSheetUpdates();
      const sheet = findSheet('Adaptive Sheet');
      await dragHeader(sheet, 300, 180, duration);
      expect(sheet.style.height).toBe('320px');
      expect(wrapper.vm.resizedHeights).toEqual([320]);
      expect(wrapper.vm.detentChanges).toEqual(['low']);
      expect(wrapper.vm.open).toBe(true);
      await dragHeader(sheet, 180, 240, duration);
      expect(sheet.style.height).toBe('260px');
      expect(wrapper.vm.resizedHeights).toEqual([320, 260]);
    });

    it('restores custom heights within drag bounds and rejects invalid or closed commands', async () => {
      const wrapper = mountAdaptiveHarness({ dragToClose: false, freeResize: true });
      await flushSheetUpdates();
      const sheet = findSheet('Adaptive Sheet');
      const handle = wrapper.vm.$refs.sheet;
      expect(handle.resizeTo(317)).toBe(true);
      await flushSheetUpdates();
      expect(sheet.style.height).toBe('317px');
      expect(handle.resizeTo(900)).toBe(true);
      await flushSheetUpdates();
      expect(sheet.style.height).toBe('400px');
      expect(handle.resizeTo(1)).toBe(true);
      await flushSheetUpdates();
      expect(sheet.style.height).toBe('200px');
      expect(handle.resizeTo(NaN)).toBe(false);
      expect(handle.resizeTo(Infinity)).toBe(false);
      wrapper.vm.open = false;
      await flushSheetUpdates();
      expect(handle.resizeTo(317)).toBe(false);
    });

    it('keeps pinned content between the header and scrolling body without making it a drag source', async () => {
      mountAdaptiveHarness({});
      await flushSheetUpdates();
      const sheet = findSheet('Adaptive Sheet');
      const pinned = sheet.querySelector<HTMLElement>('.sheet-pinned');
      if (!pinned) throw new Error('Pinned region not found');

      expect(pinned.previousElementSibling?.classList.contains('sheet-drag-zone')).toBe(true);
      expect(pinned.nextElementSibling?.classList.contains('sheet-body-frame')).toBe(true);
      expect(sheet.querySelector('.sheet-body')?.contains(pinned)).toBe(false);

      const summary = pinned.querySelector<HTMLElement>('.pinned-summary')!;
      summary.dispatchEvent(pointerEvent('pointerdown', 100, 300, 9));
      window.dispatchEvent(pointerEvent('pointermove', 100, 200, 9));
      window.dispatchEvent(pointerEvent('pointerup', 100, 200, 9));
      await flushSheetUpdates();
      expect(sheet.style.height).toBe('200px');
    });

    it('lets embedded charts own touches without resizing or canceling their gestures', async () => {
      mountAdaptiveHarness({});
      await flushSheetUpdates();
      const sheet = findSheet('Adaptive Sheet');
      const chart = sheet.querySelector<HTMLElement>('.native-chart')!;
      chart.dispatchEvent(touchEvent('touchstart', 100, 300));
      const move = touchEvent('touchmove', 100, 220);
      chart.dispatchEvent(move);
      chart.dispatchEvent(touchEvent('touchend', 100, 220, 0));
      await flushSheetUpdates();
      expect(move.defaultPrevented).toBe(false);
      expect(sheet.style.height).toBe('200px');
    });

    it('replaces the maximize control with a scoped size action', async () => {
      mountAdaptiveHarness({ sizeAction: true });
      mountInteractionHarness();
      await flushSheetUpdates();

      const adaptive = findSheet('Adaptive Sheet');
      expect(adaptive.querySelector('.sheet-fullscreen-btn')).toBeNull();
      expect(adaptive.querySelector('.sheet-window-actions .custom-size')?.textContent).toBe('More map');
      expect(findSheet('Interactive Sheet').querySelector('.sheet-fullscreen-btn')).not.toBeNull();

      adaptive.querySelector<HTMLElement>('.custom-size')!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      await flushSheetUpdates();
      expect(adaptive.style.height).toBe('200px');
    });
  });
});
