import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import BottomSheet from '@/components/ui/BottomSheet.vue';

const initialHeight = window.innerHeight;
const initialWidth = window.innerWidth;
const wrappers: VueWrapper[] = [];

async function settle() {
  await flushPromises();
  await nextTick();
  await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()));
  await flushPromises();
}

function sheet(title: string): HTMLElement {
  return Array.from(document.querySelectorAll<HTMLElement>('.sheet')).find(
    (element) => element.querySelector('.sheet-title')?.textContent?.trim() === title
  )!;
}

function harness(shareLayout = true, responsive = false) {
  const wrapper = mount(
    defineComponent({
      components: { BottomSheet },
      data: () => ({
        open: true,
        childOpen: false,
        childFitContent: false,
        shareLayout,
        content: 'Results',
        detents: responsive
          ? [{ id: 'normal', height: '75vh' }]
          : [
              { id: 'normal', height: '480px' },
              { id: 'expanded', height: '700px' },
            ],
      }),
      template: `
        <BottomSheet v-model="open" title="Dispatcher" :detents="detents"
          :fit-small-overflow="false" :share-layout="shareLayout">
          <div>{{ content }}</div>
          <BottomSheet v-model="childOpen" title="Photo" inherit-parent-layout
            :detents="[{ id: 'viewer', height: '680px' }]" :fit-small-overflow="false"
            :fit-content="childFitContent" :content-key="content"
            desktop-width="compact" viewport-centered native-fullscreen>
            <div>Photo content</div>
          </BottomSheet>
        </BottomSheet>`,
    }),
    { attachTo: document.body }
  );
  wrappers.push(wrapper);
  return wrapper;
}

describe('related bottom sheet layout', () => {
  beforeEach(() => {
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 900 });
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1440 });
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
  });

  afterEach(() => {
    wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
    vi.unstubAllGlobals();
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: initialHeight });
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: initialWidth });
  });

  function stackedHarness(upperHeight = '300px', upperZIndex = 5100, initiallyMaximized = false) {
    const wrapper = mount(
      defineComponent({
        components: { BottomSheet },
        data: () => ({
          lowerOpen: true,
          upperOpen: false,
          upperHeight,
          upperZIndex,
          content: 'Short content',
          fit: false,
          initiallyMaximized,
        }),
        template: `<BottomSheet v-model="lowerOpen" title="Workspace" :detents="[{ id: 'normal', height: '620px' }]"
          :fit-small-overflow="false" :z-index="5000">Workspace</BottomSheet>
        <BottomSheet v-model="upperOpen" title="Foreground" :detents="[{ id: 'normal', height: upperHeight }]"
          :z-index="upperZIndex" :fit-content="fit" :content-key="content"
          :initially-maximized="initiallyMaximized">{{ content }}</BottomSheet>`,
      }),
      { attachTo: document.body }
    );
    wrappers.push(wrapper);
    return wrapper;
  }

  it.each([5000, 5100])(
    'opens a foreground sheet at least as tall as the existing workspace at layer %i',
    async (zIndex) => {
      const wrapper = stackedHarness('300px', zIndex);
      await settle();
      wrapper.vm.upperOpen = true;
      await settle();
      expect(sheet('Foreground').style.height).toBe('620px');
      wrapper.vm.content = 'Different content';
      wrapper.vm.fit = true;
      await settle();
      expect(sheet('Foreground').style.height).toBe('620px');
      expect(wrapper.findAllComponents(BottomSheet)[1].vm.resizeTo(540)).toBe(true);
      await settle();
      expect(sheet('Foreground').style.height).toBe('540px');
      expect(wrapper.findAllComponents(BottomSheet)[1].vm.snapTo('normal')).toBe(true);
      await settle();
      expect(sheet('Foreground').style.height).toBe('300px');
      wrapper.vm.upperOpen = false;
      await settle();
      expect(sheet('Workspace').style.height).toBe('620px');
      wrapper.findAllComponents(BottomSheet)[0].vm.resizeTo(540);
      wrapper.vm.upperOpen = true;
      await settle();
      expect(sheet('Foreground').style.height).toBe('540px');
    }
  );

  it('keeps a larger foreground size and inherits maximization from the underlying workspace', async () => {
    const wrapper = stackedHarness('700px');
    await settle();
    wrapper.vm.upperOpen = true;
    await settle();
    expect(sheet('Foreground').style.height).toBe('700px');
    wrapper.vm.upperOpen = false;
    await settle();
    sheet('Workspace').querySelector<HTMLButtonElement>('.sheet-fullscreen-btn')!.click();
    await settle();
    wrapper.vm.upperOpen = true;
    await settle();
    expect(sheet('Foreground').style.height).toBe('100dvh');
    wrapper.vm.upperOpen = false;
    await settle();
    expect(sheet('Workspace').style.height).toBe('100dvh');
  });

  it.each([390, 1440])('keeps the dispatcher constant under expanded GPS details at width %i', async (width) => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: width });
    const mobile = width <= 768;
    const wrapper = stackedHarness('92vh', 5100, mobile);
    await settle();
    const parent = sheet('Workspace');
    const initialHeight = parent.style.height;
    wrapper.vm.upperOpen = true;
    await settle();
    expect(sheet('Foreground').style.height).toBe(mobile ? '100dvh' : '828px');
    expect(parent.style.height).toBe(initialHeight);
    if (mobile) {
      sheet('Foreground').querySelector<HTMLButtonElement>('.sheet-fullscreen-btn')!.click();
      await settle();
    }
    wrapper.findAllComponents(BottomSheet)[1].vm.resizeTo(540);
    await settle();
    expect(parent.style.height).toBe(initialHeight);
    wrapper.vm.upperOpen = false;
    await settle();
    expect(parent.style.height).toBe(initialHeight);
    wrapper.vm.upperOpen = true;
    await settle();
    expect(sheet('Foreground').style.height).toBe(mobile ? '100dvh' : '828px');
    expect(parent.style.height).toBe(initialHeight);
  });

  it('does not inherit from a sheet above its own layer and clears the floor once the workspace closes', async () => {
    const wrapper = stackedHarness('300px', 4900);
    await settle();
    wrapper.vm.upperOpen = true;
    await settle();
    expect(sheet('Foreground').style.height).toBe('300px');
    wrapper.vm.upperOpen = false;
    wrapper.vm.lowerOpen = false;
    wrapper.vm.upperZIndex = 5100;
    await settle();
    wrapper.vm.upperOpen = true;
    await settle();
    expect(sheet('Foreground').style.height).toBe('300px');
  });

  it('retains a manually chosen height through content changes and inherits it for a child', async () => {
    const wrapper = harness();
    await settle();
    const parent = wrapper.findAllComponents(BottomSheet)[0];
    expect(parent.vm.resizeTo(620)).toBe(true);
    wrapper.vm.content = 'Loading details…';
    await settle();
    wrapper.vm.content = 'Short place details';
    await settle();
    wrapper.vm.content = 'Results';
    await settle();
    expect(sheet('Dispatcher').style.height).toBe('620px');

    wrapper.vm.childOpen = true;
    await settle();
    expect(sheet('Photo').style.height).toBe('620px');
    expect(sheet('Photo').classList.contains('sheet--desktop-compact')).toBe(false);
    expect(sheet('Photo').classList.contains('sheet--viewport-centered')).toBe(false);
    wrapper.vm.childOpen = false;
    await settle();
    expect(sheet('Dispatcher').style.height).toBe('620px');
  });

  it('inherits maximization and restores the inherited height after an explicit viewer restore', async () => {
    const wrapper = harness();
    await settle();
    const parent = wrapper.findAllComponents(BottomSheet)[0];
    parent.vm.resizeTo(620);
    await settle();
    sheet('Dispatcher').querySelector<HTMLButtonElement>('.sheet-fullscreen-btn')!.click();
    await settle();
    wrapper.vm.childOpen = true;
    await settle();
    expect(sheet('Photo').style.height).toBe('100dvh');
    expect(sheet('Photo').classList.contains('sheet--fullscreen')).toBe(true);
    expect(sheet('Photo').classList.contains('sheet--shared-layout')).toBe(true);
    expect(sheet('Dispatcher').classList.contains('sheet--shared-layout')).toBe(true);

    sheet('Photo').querySelector<HTMLButtonElement>('.sheet-fullscreen-btn')!.click();
    await settle();
    expect(sheet('Photo').style.height).toBe('620px');
    wrapper.vm.childOpen = false;
    await settle();
    expect(sheet('Dispatcher').style.height).toBe('100dvh');
  });

  it('restores inherited size after browser fullscreen and takes a fresh layout on reopening', async () => {
    const wrapper = harness();
    await settle();
    const [parent] = wrapper.findAllComponents(BottomSheet);
    parent.vm.resizeTo(620);
    wrapper.vm.childOpen = true;
    await settle();
    const fullscreen = sheet('Photo').querySelector<HTMLButtonElement>('.sheet-native-fullscreen-btn')!;
    fullscreen.click();
    await settle();
    expect(sheet('Photo').style.height).toBe('100dvh');
    fullscreen.click();
    await settle();
    expect(sheet('Photo').style.height).toBe('620px');
    wrapper.vm.childOpen = false;
    await settle();
    parent.vm.resizeTo(540);
    wrapper.vm.childOpen = true;
    await settle();
    expect(sheet('Photo').style.height).toBe('540px');
  });

  it('keeps parent and child aligned when the viewport changes', async () => {
    const wrapper = harness(true, true);
    await settle();
    wrapper.vm.childOpen = true;
    await settle();
    expect(sheet('Photo').style.height).toBe('675px');

    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 600 });
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
    window.dispatchEvent(new Event('resize'));
    await settle();
    expect(sheet('Dispatcher').style.height).toBe('450px');
    expect(sheet('Photo').style.height).toBe('450px');
  });

  it('honors explicit child sizing without fitting late content or resizing its background parent', async () => {
    const wrapper = harness();
    await settle();
    const [parent, child] = wrapper.findAllComponents(BottomSheet);
    parent.vm.resizeTo(620);
    wrapper.vm.childOpen = true;
    await settle();
    expect(child.vm.resizeTo(520)).toBe(true);
    wrapper.vm.childFitContent = true;
    wrapper.vm.content = 'Late metadata';
    await settle();
    expect(sheet('Photo').style.height).toBe('520px');
    expect(sheet('Dispatcher').style.height).toBe('620px');
    wrapper.vm.childOpen = false;
    await settle();
    expect(sheet('Dispatcher').style.height).toBe('620px');
  });

  it('leaves a viewer on its own layout outside a sharing parent', async () => {
    const wrapper = harness(false);
    await settle();
    wrapper.vm.childOpen = true;
    await settle();
    expect(sheet('Photo').style.height).toBe('680px');
    expect(sheet('Photo').classList.contains('sheet--desktop-compact')).toBe(true);
    expect(sheet('Photo').classList.contains('sheet--viewport-centered')).toBe(true);
  });

  it.each([
    [true, false],
    [false, false],
    [true, true],
    [false, true],
  ])('keeps GPS details constant under its media viewer (map: %s, mobile: %s)', async (fromMap, mobile) => {
    if (mobile) Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
    const wrapper = mount(
      defineComponent({
        components: { BottomSheet },
        data: () => ({ childOpen: false, fromMap, mobile, replay: false }),
        template: `<BottomSheet :model-value="true" title="GPS" :detents="[{id:'expanded',height:'92vh'}]"
        initial-detent="expanded" :initially-maximized="mobile" :share-layout="fromMap"
        :background-detent="replay ? {id:'replay-background',height:'76px'} : undefined"
        :fit-small-overflow="false" :z-index="5300">
        <BottomSheet v-model="childOpen" title="Photo" :detents="[{id:'small',height:'40vh'},{id:'large',height:'92vh'}]"
          initial-detent="large" :z-index="5400" viewport-centered inherit-parent-layout>Photo</BottomSheet>
      </BottomSheet>`,
      }),
      { attachTo: document.body }
    );
    wrappers.push(wrapper);
    await settle();
    const parent = sheet('GPS');
    const height = parent.style.height;
    wrapper.vm.childOpen = true;
    await settle();
    expect(parent.style.height).toBe(height);
    expect(sheet('Photo').classList.contains('sheet--viewport-centered')).toBe(!fromMap);
    expect(parent.querySelector('.sheet-body')?.hasAttribute('inert')).toBe(true);
    expect(parent.querySelector('.sheet-stack-scrim')).not.toBeNull();
    if (mobile) {
      sheet('Photo').querySelector<HTMLButtonElement>('.sheet-fullscreen-btn')!.click();
      await settle();
    }
    const child = wrapper.findAllComponents(BottomSheet)[1];
    if (fromMap) expect(child.vm.resizeTo(520)).toBe(true);
    else expect(child.vm.snapTo('small')).toBe(true);
    await settle();
    expect(parent.style.height).toBe(height);
    wrapper.vm.childOpen = false;
    await settle();
    expect(parent.style.height).toBe(height);
  });
});
