import { flushPromises, mount } from '@vue/test-utils';
import type { VueWrapper } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';

class TestResizeObserver {
  observe() {}
  disconnect() {}
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

function mountStackHarness(
  options: {
    lowerOpen?: boolean;
    upperOpen?: boolean;
    lowerZIndex?: number;
    upperZIndex?: number;
    lowerBackgroundDetent?: unknown;
    lowerNoBackdrop?: boolean;
    upperNoBackdrop?: boolean;
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
          lowerZIndex: options.lowerZIndex ?? 5000,
          upperZIndex: options.upperZIndex ?? 5100,
          lowerNoBackdrop: options.lowerNoBackdrop ?? true,
          upperNoBackdrop: options.upperNoBackdrop ?? true,
        };
      },
      template: `
        <BottomSheet
          v-model="lowerOpen"
          title="Lower Sheet"
          :detents="[{ height: 0.5 }]"
          :background-detent="lowerBackgroundDetent"
          :z-index="lowerZIndex"
          :no-backdrop="lowerNoBackdrop"
        >
          <button type="button">Lower Action</button>
        </BottomSheet>
        <BottomSheet
          v-model="upperOpen"
          title="Upper Sheet"
          :detents="[{ height: 0.5 }]"
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

function mountInteractionHarness() {
  const wrapper = mount(
    defineComponent({
      components: { BottomSheet },
      data() {
        return {
          open: true,
          actionClicks: 0,
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
        >
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

  beforeAll(() => {
    vi.stubGlobal('ResizeObserver', TestResizeObserver);
  });

  beforeEach(() => {
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

  it('uses open order as the tie-breaker for equal z-index sheets', async () => {
    const wrapper = mountStackHarness({ lowerZIndex: 5200, upperZIndex: 5200 });
    await flushSheetUpdates();

    (wrapper.vm as unknown as { upperOpen: boolean }).upperOpen = true;
    await flushSheetUpdates();

    expect(findSheet('Lower Sheet').classList.contains('sheet--backgrounded')).toBe(true);
    expect(findSheet('Upper Sheet').classList.contains('sheet--backgrounded')).toBe(false);
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
});
