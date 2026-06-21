import { mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import NavigationSheet, { type ToolDef } from '@/components/ui/NavigationSheet.vue';

const originalInnerWidth = window.innerWidth;

const tools: ToolDef[] = [
  { id: 'stats', icon: 'bi bi-graph-up', label: 'Stats' },
  { id: 'filter', icon: 'bi bi-funnel', label: 'Filter' },
  { id: 'planner', icon: 'bi bi-signpost-split', label: 'Planner' },
  { id: 'map-settings', icon: 'bi bi-map', label: 'Map' },
  { id: 'animate', icon: 'bi bi-play', label: 'Animate' },
  { id: 'segments', icon: 'bi bi-diagram-3', label: 'Segments' },
  { id: 'gps', icon: 'bi bi-crosshair', label: 'GPS' },
  { id: 'admin', icon: 'bi bi-shield-lock', label: 'Admin' },
];

function setViewportWidth(width: number) {
  Object.defineProperty(window, 'innerWidth', {
    configurable: true,
    writable: true,
    value: width,
  });
}

function pointerEvent(
  type: string,
  options: { clientX?: number; clientY?: number; pointerType?: string; pointerId?: number } = {}
): PointerEvent {
  const event = new MouseEvent(type, {
    bubbles: true,
    cancelable: true,
    clientX: options.clientX ?? 0,
    clientY: options.clientY ?? 0,
    button: 0,
  }) as PointerEvent;
  Object.defineProperties(event, {
    pointerId: { value: options.pointerId ?? 1 },
    pointerType: { value: options.pointerType ?? 'mouse' },
  });
  return event;
}

function mountNavigationSheet(width = 900) {
  setViewportWidth(width);
  const wrapper = mount(NavigationSheet, {
    attachTo: document.body,
    props: { tools },
    global: {
      stubs: {
        AppBrandButton: { template: '<button type="button" class="app-brand-stub">MTL</button>' },
      },
    },
  });
  return wrapper;
}

function navGrid(): HTMLElement {
  const element = document.body.querySelector<HTMLElement>('.nav-sheet__grid');
  if (!element) throw new Error('Navigation grid not found');
  return element;
}

function handleTapZone(): HTMLElement {
  const element = document.body.querySelector<HTMLElement>('.nav-sheet__handle-tap-zone');
  if (!element) throw new Error('Navigation handle tap zone not found');
  return element;
}

function dragHalo(): HTMLElement {
  const element = document.body.querySelector<HTMLElement>('.nav-sheet__drag-halo');
  if (!element) throw new Error('Navigation drag halo not found');
  return element;
}

function toolButton(label: string): HTMLButtonElement {
  const button = Array.from(document.body.querySelectorAll<HTMLButtonElement>('.nav-sheet__tool')).find(
    (element) => element.textContent?.trim() === label
  );
  if (!button) throw new Error(`Navigation button not found: ${label}`);
  return button;
}

const mountedWrappers: VueWrapper[] = [];

describe('NavigationSheet bottom toolbar gestures', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
  });

  afterEach(() => {
    while (mountedWrappers.length) {
      mountedWrappers.pop()?.unmount();
    }
    document.body.innerHTML = '';
    setViewportWidth(originalInnerWidth);
  });

  it('emits one select for a touch tap and suppresses the duplicate compatibility click', async () => {
    const wrapper = mountNavigationSheet();
    mountedWrappers.push(wrapper);
    await nextTick();

    const mapButton = toolButton('Map');
    const down = pointerEvent('pointerdown', { clientX: 700, clientY: 620, pointerType: 'touch' });
    const up = pointerEvent('pointerup', { clientX: 700, clientY: 620, pointerType: 'touch' });

    mapButton.dispatchEvent(down);
    mapButton.dispatchEvent(up);
    await nextTick();

    expect(down.defaultPrevented).toBe(true);
    expect(up.defaultPrevented).toBe(true);
    expect(wrapper.emitted('select')).toEqual([['map-settings']]);

    const duplicateClick = new MouseEvent('click', {
      bubbles: true,
      cancelable: true,
      clientX: 700,
      clientY: 620,
    });
    const dispatched = mapButton.dispatchEvent(duplicateClick);
    await nextTick();

    expect(dispatched).toBe(false);
    expect(duplicateClick.defaultPrevented).toBe(true);
    expect(wrapper.emitted('select')).toEqual([['map-settings']]);
  });

  it('keeps desktop mouse clicks active when the bottom toolbar is rendered', async () => {
    const wrapper = mountNavigationSheet();
    mountedWrappers.push(wrapper);
    await nextTick();

    const grid = navGrid();
    const mapButton = toolButton('Map');

    grid.dispatchEvent(pointerEvent('pointerdown', { clientX: 700, clientY: 620, pointerType: 'mouse' }));
    grid.dispatchEvent(pointerEvent('pointerup', { clientX: 700, clientY: 620, pointerType: 'mouse' }));
    mapButton.click();
    await nextTick();

    expect(wrapper.emitted('select')).toEqual([['map-settings']]);
  });

  it('owns toolbar touch drags without selecting a tool or leaking a map click', async () => {
    const wrapper = mountNavigationSheet();
    mountedWrappers.push(wrapper);
    await nextTick();

    const mapButton = toolButton('Map');
    const mapSurface = document.createElement('div');
    const leakedMapClick = vi.fn();
    mapSurface.addEventListener('click', leakedMapClick);
    document.body.appendChild(mapSurface);

    const down = pointerEvent('pointerdown', { clientX: 700, clientY: 620, pointerType: 'touch' });
    const move = pointerEvent('pointermove', { clientX: 700, clientY: 700, pointerType: 'touch' });
    const up = pointerEvent('pointerup', { clientX: 700, clientY: 700, pointerType: 'touch' });
    mapButton.dispatchEvent(down);
    mapButton.dispatchEvent(move);
    mapButton.dispatchEvent(up);
    await nextTick();

    expect(down.defaultPrevented).toBe(true);
    expect(move.defaultPrevented).toBe(true);
    expect(up.defaultPrevented).toBe(true);
    expect(wrapper.emitted('select')).toBeUndefined();
    expect(document.body.querySelector('.nav-sheet')?.classList.contains('nav-sheet--collapsed')).toBe(true);

    const leakedClick = new MouseEvent('click', {
      bubbles: true,
      cancelable: true,
      clientX: 700,
      clientY: 700,
    });
    const dispatched = mapSurface.dispatchEvent(leakedClick);

    expect(dispatched).toBe(false);
    expect(leakedClick.defaultPrevented).toBe(true);
    expect(leakedMapClick).not.toHaveBeenCalled();
  });

  it('owns handle touch drags and suppresses the duplicate handle click', async () => {
    const wrapper = mountNavigationSheet();
    mountedWrappers.push(wrapper);
    await nextTick();

    const handle = handleTapZone();
    const down = pointerEvent('pointerdown', { clientX: 420, clientY: 620, pointerType: 'touch' });
    const move = pointerEvent('pointermove', { clientX: 420, clientY: 700, pointerType: 'touch' });
    const up = pointerEvent('pointerup', { clientX: 420, clientY: 700, pointerType: 'touch' });

    handle.dispatchEvent(down);
    handle.dispatchEvent(move);
    handle.dispatchEvent(up);
    await nextTick();

    expect(down.defaultPrevented).toBe(true);
    expect(move.defaultPrevented).toBe(true);
    expect(up.defaultPrevented).toBe(true);
    expect(wrapper.emitted('select')).toBeUndefined();
    expect(document.body.querySelector('.nav-sheet')?.classList.contains('nav-sheet--collapsed')).toBe(true);

    const duplicateClick = new MouseEvent('click', {
      bubbles: true,
      cancelable: true,
      clientX: 420,
      clientY: 700,
    });
    const dispatched = handle.dispatchEvent(duplicateClick);
    await nextTick();

    expect(dispatched).toBe(false);
    expect(duplicateClick.defaultPrevented).toBe(true);
    expect(document.body.querySelector('.nav-sheet')?.classList.contains('nav-sheet--collapsed')).toBe(true);
  });

  it('owns halo touch drags before they can reach the map', async () => {
    const wrapper = mountNavigationSheet();
    mountedWrappers.push(wrapper);
    await nextTick();

    const halo = dragHalo();
    const down = pointerEvent('pointerdown', { clientX: 420, clientY: 610, pointerType: 'touch' });
    const move = pointerEvent('pointermove', { clientX: 420, clientY: 690, pointerType: 'touch' });
    const up = pointerEvent('pointerup', { clientX: 420, clientY: 690, pointerType: 'touch' });

    halo.dispatchEvent(down);
    halo.dispatchEvent(move);
    halo.dispatchEvent(up);
    await nextTick();

    expect(down.defaultPrevented).toBe(true);
    expect(move.defaultPrevented).toBe(true);
    expect(up.defaultPrevented).toBe(true);
    expect(wrapper.emitted('select')).toBeUndefined();
    expect(document.body.querySelector('.nav-sheet')?.classList.contains('nav-sheet--collapsed')).toBe(true);
  });
});
