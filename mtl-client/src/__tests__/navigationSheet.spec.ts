import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import NavigationSheet, { type ToolDef } from '@/components/ui/NavigationSheet.vue';

const originalInnerWidth = window.innerWidth;

const tools: ToolDef[] = [
  {
    id: 'stats',
    icon: 'bi bi-graph-up',
    label: 'Stats',
    moreLabel: 'Statistics',
    description: 'Activity history, trends, and milestones.',
  },
  { id: 'filter', icon: 'bi bi-funnel', label: 'Filter' },
  { id: 'planner', icon: 'bi bi-signpost-split', label: 'Planner' },
  { id: 'map-settings', icon: 'bi bi-map', label: 'Map' },
  { id: 'animate', icon: 'bi bi-play', label: 'Animate', description: 'Replay your tracks over time.' },
  {
    id: 'segments',
    icon: 'bi bi-diagram-3',
    label: 'Segments',
    description: 'Explore and compare route sections.',
  },
  { id: 'gps', icon: 'bi bi-crosshair', label: 'GPS' },
  { id: 'health', icon: 'bi bi-heart-pulse', label: 'Health', description: 'View health and activity data.' },
  { id: 'admin', icon: 'bi bi-gear', label: 'Settings', description: 'Configure MTL Explorer.' },
];

const primaryIds = ['gps', 'map-settings', 'filter', 'planner'];

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

function nativeTouchEvent(type: string, positions: Array<{ clientX: number; clientY: number }> = []): TouchEvent {
  const event = new Event(type, { bubbles: true, cancelable: true }) as TouchEvent;
  Object.defineProperty(event, 'touches', {
    value: positions.map((position, identifier) => ({ ...position, identifier })),
  });
  return event;
}

function mountNavigationSheet(width = 900) {
  setViewportWidth(width);
  const wrapper = mount(NavigationSheet, {
    attachTo: document.body,
    props: { tools, primaryIds, utilityId: 'admin' },
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

function moreButton(): HTMLButtonElement {
  const button = document.body.querySelector<HTMLButtonElement>('.nav-sheet__tool--more');
  if (!button) throw new Error('More button not found');
  return button;
}

function overflowButton(label: string): HTMLButtonElement {
  const button = Array.from(document.body.querySelectorAll<HTMLButtonElement>('.nav-sheet__overflow-tool')).find(
    (element) => element.querySelector('.nav-sheet__overflow-label')?.textContent?.trim() === label
  );
  if (!button) throw new Error(`Overflow button not found: ${label}`);
  return button;
}

const mountedWrappers: VueWrapper[] = [];

describe('NavigationSheet bottom toolbar gestures', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      }
    );
  });

  afterEach(() => {
    while (mountedWrappers.length) {
      mountedWrappers.pop()?.unmount();
    }
    document.body.innerHTML = '';
    setViewportWidth(originalInnerWidth);
    vi.unstubAllGlobals();
  });

  it('renders the mobile field tools in one five-item primary row', async () => {
    const wrapper = mountNavigationSheet();
    mountedWrappers.push(wrapper);
    await nextTick();

    const labels = Array.from(
      document.body.querySelectorAll<HTMLElement>('.nav-sheet__row--primary .nav-sheet__tool-label')
    ).map((element) => element.textContent?.trim());

    expect(labels).toEqual(['GPS', 'Map', 'Filter', 'Planner', 'More']);
    expect(document.body.querySelector('.nav-sheet__overflow-panel')).toBeNull();
    expect(moreButton().getAttribute('aria-expanded')).toBe('false');
  });

  it.each(['icon', 'label', 'padding'])(
    'blocks native toolbar touch movement from its %s without moving the page',
    async (targetKind) => {
      const wrapper = mountNavigationSheet(390);
      mountedWrappers.push(wrapper);
      await nextTick();
      const toolbar = document.body.querySelector<HTMLElement>('.nav-sheet')!;
      const button = toolButton('Filter');
      const target =
        targetKind === 'padding'
          ? toolbar
          : button.querySelector<HTMLElement>(targetKind === 'icon' ? 'i' : '.nav-sheet__tool-label')!;
      const initialHeight = toolbar.style.getPropertyValue('--nav-body-h');
      const initialMapOffset = document.documentElement.style.getPropertyValue('--nav-sheet-h');
      const documentTouch = vi.fn();
      document.addEventListener('touchmove', documentTouch);
      try {
        const start = nativeTouchEvent('touchstart', [{ clientX: 195, clientY: 790 }]);
        target.dispatchEvent(start);
        expect(start.defaultPrevented).toBe(false);
        for (const clientY of [760, 720, 650, 750, 790]) {
          const move = nativeTouchEvent('touchmove', [{ clientX: 195, clientY }]);
          expect(target.dispatchEvent(move)).toBe(false);
          expect(move.defaultPrevented).toBe(true);
        }
        const end = nativeTouchEvent('touchend');
        target.dispatchEvent(end);
        expect(end.defaultPrevented).toBe(false);
        expect(documentTouch).not.toHaveBeenCalled();
      } finally {
        document.removeEventListener('touchmove', documentTouch);
      }
      await nextTick();
      expect(toolbar.style.getPropertyValue('--nav-body-h')).toBe(initialHeight);
      expect(document.documentElement.style.getPropertyValue('--nav-sheet-h')).toBe(initialMapOffset);
      expect(wrapper.emitted('select')).toBeUndefined();
      expect(moreButton().getAttribute('aria-expanded')).toBe('false');
    }
  );

  it('renders update and tool status through AppIcon on desktop and clears them independently', async () => {
    const wrapper = mountNavigationSheet(1280);
    mountedWrappers.push(wrapper);
    await wrapper.setProps({ utilityBadge: 'Update', alertToolIds: ['filter'], driftedToolIds: ['gps'] });
    const utility = document.body.querySelector('.nav-panel__utility')!;
    expect(utility.getAttribute('aria-label')).toBe('Settings, update available');
    expect(utility.querySelector('.app-icon__dot--notice.app-icon__dot--pulse')).not.toBeNull();
    expect(document.body.querySelectorAll('.app-icon__dot')).toHaveLength(3);
    expect(document.body.querySelector('.app-icon__dot--warning')).not.toBeNull();
    await wrapper.setProps({ utilityBadge: undefined });
    expect(utility.querySelector('.app-icon__dot')).toBeNull();
    expect(document.body.querySelectorAll('.app-icon__dot')).toHaveLength(2);
  });

  it('keeps mobile updates and review counts announced together with an inline Settings label', async () => {
    const wrapper = mountNavigationSheet();
    mountedWrappers.push(wrapper);
    await wrapper.setProps({ utilityBadge: 'Update', moreBadge: '2', moreBadgeLabel: '2 sources need review' });
    expect(moreButton().getAttribute('aria-label')).toBe('More, update available, 2 sources need review');
    expect(moreButton().querySelector('.app-icon__dot--notice')).not.toBeNull();
    moreButton().click();
    await nextTick();
    expect(document.body.querySelector('.nav-sheet__utility-label')?.textContent).toContain('Update');
    await wrapper.setProps({ utilityBadge: undefined });
    expect(moreButton().querySelector('.app-icon__dot')).toBeNull();
    expect(moreButton().getAttribute('aria-label')).toBe('More, 2 sources need review');
  });

  it('keeps a pending review count visible while More is collapsed and clears it when resolved', async () => {
    const wrapper = mountNavigationSheet();
    mountedWrappers.push(wrapper);
    await wrapper.setProps({ moreBadge: '2', moreBadgeLabel: '2 sources need review' });
    expect(document.body.querySelector('.nav-sheet__overflow-panel')).toBeNull();
    expect(moreButton().textContent).toContain('More (2)');
    expect(moreButton().getAttribute('aria-label')).toBe('More, 2 sources need review');
    await wrapper.setProps({ moreBadge: undefined, moreBadgeLabel: undefined });
    expect(moreButton().textContent?.trim()).toBe('More');
    expect(moreButton().hasAttribute('aria-label')).toBe(false);
  });

  it('opens a balanced More panel and keeps Settings separate from map tools', async () => {
    const wrapper = mountNavigationSheet();
    mountedWrappers.push(wrapper);
    await nextTick();

    moreButton().click();
    await nextTick();

    const overflowLabels = Array.from(document.body.querySelectorAll<HTMLElement>('.nav-sheet__overflow-label')).map(
      (element) => element.textContent?.trim()
    );

    expect(overflowLabels).toEqual(['User guide', 'Statistics', 'Animate', 'Segments', 'Health']);
    expect(document.body.querySelector<HTMLAnchorElement>('a.nav-sheet__overflow-tool')?.getAttribute('href')).toMatch(
      /\/guide\/index\.html$/
    );
    expect(document.body.querySelector('a.nav-sheet__overflow-tool')?.getAttribute('rel')).toBe('opener');
    expect(document.body.querySelector('.nav-sheet__utility-label')?.textContent?.trim()).toBe('Settings');
    expect(document.body.querySelector('.nav-sheet__more-intro')?.textContent).toContain('Open another area');
    expect(
      Array.from(document.body.querySelectorAll<HTMLElement>('.nav-sheet__overflow-description')).map((element) =>
        element.textContent?.trim()
      )
    ).toEqual([
      'Help for this installation.',
      'Activity history, trends, and milestones.',
      'Replay your tracks over time.',
      'Explore and compare route sections.',
      'View health and activity data.',
      'Configure MTL Explorer.',
    ]);
    expect(document.body.querySelector('.nav-sheet')?.classList.contains('nav-sheet--overflow')).toBe(true);
    expect(moreButton().getAttribute('aria-expanded')).toBe('true');
  });

  it('uses each tool icon in the More panel without substituting another glyph', async () => {
    const wrapper = mountNavigationSheet();
    mountedWrappers.push(wrapper);
    await nextTick();

    moreButton().click();
    await nextTick();

    for (const tool of tools.filter((candidate) => ['stats', 'animate', 'segments', 'health'].includes(candidate.id))) {
      const icon = document.body.querySelector<HTMLElement>(
        `.nav-sheet__overflow-tool[data-tool-id="${tool.id}"] .nav-sheet__overflow-icon i`
      );
      expect(Array.from(icon?.classList ?? [])).toEqual(expect.arrayContaining(tool.icon.split(' ')));
    }
  });

  it('closes More after selecting a secondary tool and reflects its active state on More', async () => {
    const wrapper = mountNavigationSheet();
    mountedWrappers.push(wrapper);
    await nextTick();

    moreButton().click();
    await nextTick();
    overflowButton('Health').click();
    await nextTick();

    expect(wrapper.emitted('select')).toEqual([['health']]);
    expect(document.body.querySelector('.nav-sheet__overflow-panel')).toBeNull();

    await wrapper.setProps({ activeTool: 'health' });
    await nextTick();
    expect(moreButton().classList.contains('nav-sheet__tool--active')).toBe(true);
  });

  it('opens Settings from the More utility row', async () => {
    const wrapper = mountNavigationSheet();
    mountedWrappers.push(wrapper);
    await nextTick();

    moreButton().click();
    await nextTick();
    document.body.querySelector<HTMLButtonElement>('.nav-sheet__utility')?.click();
    await nextTick();

    expect(wrapper.emitted('select')).toEqual([['admin']]);
    expect(document.body.querySelector('.nav-sheet__overflow-panel')).toBeNull();
  });

  it('keeps More pointer gestures out of sheet dragging and opens its tools through one click', async () => {
    const wrapper = mountNavigationSheet(390);
    mountedWrappers.push(wrapper);
    moreButton().click();
    await nextTick();

    const healthButton = overflowButton('Health');
    const down = pointerEvent('pointerdown', { clientX: 180, clientY: 420, pointerType: 'touch' });
    const move = pointerEvent('pointermove', { clientX: 180, clientY: 380, pointerType: 'touch' });
    const up = pointerEvent('pointerup', { clientX: 180, clientY: 380, pointerType: 'touch' });
    healthButton.dispatchEvent(down);
    healthButton.dispatchEvent(move);
    healthButton.dispatchEvent(up);
    await nextTick();

    expect(down.defaultPrevented).toBe(false);
    expect(move.defaultPrevented).toBe(false);
    expect(up.defaultPrevented).toBe(false);
    expect(document.body.querySelector('.nav-sheet')?.classList.contains('nav-sheet--dragging')).toBe(false);
    expect(wrapper.emitted('select')).toBeUndefined();

    healthButton.click();
    await nextTick();
    expect(wrapper.emitted('select')).toEqual([['health']]);
    expect(document.body.querySelector('.nav-sheet__overflow-panel')).toBeNull();
  });

  it.each(['Segments', 'Health'])(
    'keeps %s touches inside navigation without cancelling available native scrolling',
    async (label) => {
      const wrapper = mountNavigationSheet(390);
      mountedWrappers.push(wrapper);
      moreButton().click();
      await nextTick();
      const body = document.body.querySelector<HTMLElement>('.nav-more-sheet .sheet-body')!;
      body.style.overflowY = 'auto';
      Object.defineProperties(body, {
        clientHeight: { configurable: true, value: 100 },
        scrollHeight: { configurable: true, value: 800 },
      });
      body.append(document.createElement('span'));
      await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
      await flushPromises();
      const target = overflowButton(label).querySelector<HTMLElement>('.nav-sheet__overflow-label')!;
      const sheet = document.body.querySelector<HTMLElement>('.nav-sheet')!;
      const initialHeight = sheet.style.getPropertyValue('--nav-body-h');
      // The application's document-level zoom handlers must not consume menu taps or swipes.
      const documentTouch = vi.fn((event: Event) => event.preventDefault());
      const eventTypes = ['touchstart', 'touchmove', 'touchend', 'touchcancel'];
      eventTypes.forEach((type) => document.addEventListener(type, documentTouch));
      try {
        for (const type of eventTypes) {
          const event = nativeTouchEvent(type, [{ clientX: 180, clientY: 420 }]);
          target.dispatchEvent(event);
          expect(event.defaultPrevented).toBe(false);
        }
        expect(documentTouch).not.toHaveBeenCalled();
      } finally {
        eventTypes.forEach((type) => document.removeEventListener(type, documentTouch));
      }
      expect(sheet.style.getPropertyValue('--nav-body-h')).toBe(initialHeight);
      expect(sheet.classList.contains('nav-sheet--dragging')).toBe(false);
      expect(wrapper.emitted('select')).toBeUndefined();
      overflowButton(label).click();
      await nextTick();
      expect(wrapper.emitted('select')).toEqual([[label.toLowerCase()]]);
    }
  );

  it('cancels native movement in a fitted More panel before its content stops bubbling', async () => {
    const wrapper = mountNavigationSheet(390);
    mountedWrappers.push(wrapper);
    moreButton().click();
    await flushPromises();
    const more = document.body.querySelector<HTMLElement>('.nav-more-sheet')!;
    const initialHeight = more.style.height;
    const target = overflowButton('Health').querySelector<HTMLElement>('.nav-sheet__overflow-label')!;
    const move = nativeTouchEvent('touchmove', [{ clientX: 180, clientY: 400 }]);
    expect(target.dispatchEvent(move)).toBe(false);
    expect(move.defaultPrevented).toBe(true);
    await nextTick();
    expect(more.style.height).toBe(initialHeight);
    expect(moreButton().getAttribute('aria-expanded')).toBe('true');
    expect(wrapper.emitted('select')).toBeUndefined();
  });

  it('uses the shared sheet while keeping toolbar and map offsets unchanged', async () => {
    const wrapper = mountNavigationSheet(390);
    mountedWrappers.push(wrapper);
    await nextTick();
    const toolbar = document.body.querySelector<HTMLElement>('.nav-sheet')!;
    const initialHeight = toolbar.style.getPropertyValue('--nav-body-h');
    const mapOffset = document.documentElement.style.getPropertyValue('--nav-sheet-h');

    moreButton().click();
    await flushPromises();
    const more = document.body.querySelector<HTMLElement>('.nav-more-sheet')!;
    expect(more.classList.contains('sheet--open')).toBe(true);
    expect(toolbar.style.getPropertyValue('--nav-body-h')).toBe(initialHeight);
    expect(document.documentElement.style.getPropertyValue('--nav-sheet-h')).toBe(mapOffset);

    more.querySelector<HTMLButtonElement>('.sheet-fullscreen-btn')!.click();
    await nextTick();
    expect(more.classList.contains('sheet--fullscreen')).toBe(true);
    expect(document.documentElement.style.getPropertyValue('--nav-sheet-h')).toBe(mapOffset);
    more.querySelector<HTMLButtonElement>('.sheet-close-btn')!.click();
    await flushPromises();
    expect(moreButton().getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(moreButton());
    expect(document.documentElement.style.getPropertyValue('--nav-sheet-h')).toBe(mapOffset);

    moreButton().click();
    await flushPromises();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    await flushPromises();
    expect(moreButton().getAttribute('aria-expanded')).toBe('false');
    expect(document.documentElement.style.getPropertyValue('--nav-sheet-h')).toBe(mapOffset);
  });

  it.each([-80, 80])('keeps More open when dragging its side margin by %s pixels', async (deltaY) => {
    const wrapper = mountNavigationSheet(390);
    mountedWrappers.push(wrapper);
    moreButton().click();
    await nextTick();

    const grid = document.body.querySelector<HTMLElement>('.nav-sheet__overflow-panel')!;
    const sheet = document.body.querySelector<HTMLElement>('.nav-sheet')!;
    const initialHeight = sheet.style.getPropertyValue('--nav-body-h');
    const capture = vi.fn();
    Object.defineProperty(grid, 'setPointerCapture', { value: capture });
    grid.dispatchEvent(pointerEvent('pointerdown', { clientX: 8, clientY: 420, pointerType: 'touch' }));
    grid.dispatchEvent(pointerEvent('pointermove', { clientX: 8, clientY: 420 + deltaY, pointerType: 'touch' }));
    await nextTick();

    expect(sheet.classList.contains('nav-sheet--dragging')).toBe(false);
    expect(sheet.style.getPropertyValue('--nav-body-h')).toBe(initialHeight);
    expect(capture).not.toHaveBeenCalled();
    grid.dispatchEvent(pointerEvent('pointerup', { clientX: 8, clientY: 420 + deltaY, pointerType: 'touch' }));
    await nextTick();
    expect(moreButton().getAttribute('aria-expanded')).toBe('true');
    expect(wrapper.emitted('select')).toBeUndefined();

    document.body.querySelector<HTMLButtonElement>('.nav-sheet__utility')?.click();
    await nextTick();
    expect(wrapper.emitted('select')).toEqual([['admin']]);
  });

  it.each([-200, 200])('keeps More open when a primary button is swiped by %s pixels', async (deltaY) => {
    const wrapper = mountNavigationSheet(390);
    mountedWrappers.push(wrapper);
    moreButton().click();
    await nextTick();

    const button = toolButton('Map');
    button.dispatchEvent(pointerEvent('pointerdown', { clientX: 110, clientY: 500, pointerType: 'touch' }));
    button.dispatchEvent(pointerEvent('pointermove', { clientX: 110, clientY: 500 + deltaY, pointerType: 'touch' }));
    await nextTick();
    expect(document.body.querySelector('.nav-sheet')?.classList.contains('nav-sheet--dragging')).toBe(false);
    button.dispatchEvent(pointerEvent('pointerup', { clientX: 110, clientY: 500 + deltaY, pointerType: 'touch' }));
    await nextTick();

    expect(moreButton().getAttribute('aria-expanded')).toBe('true');
    expect(document.body.querySelector('.nav-sheet')?.classList.contains('nav-sheet--collapsed')).toBe(false);
    expect(wrapper.emitted('select')).toBeUndefined();
  });

  it('keeps desktop tools direct and pins Settings in the rail footer', async () => {
    const wrapper = mountNavigationSheet(1200);
    mountedWrappers.push(wrapper);
    await nextTick();

    const desktopLabels = Array.from(
      document.body.querySelectorAll<HTMLElement>('.nav-panel__grid .nav-panel__tool-label')
    ).map((element) => element.textContent?.trim());

    expect(desktopLabels).toEqual(['Stats', 'Filter', 'Planner', 'Map', 'Animate', 'Segments', 'GPS', 'Health']);
    expect(document.body.querySelector('.nav-panel__guide')?.getAttribute('rel')).toBe('opener');
    expect(document.body.querySelector('.nav-panel__utility .nav-panel__tool-label')?.textContent?.trim()).toBe(
      'Settings'
    );
    expect(document.body.querySelector('.nav-sheet__tool--more')).toBeNull();
  });

  it('emits one select for a touch tap and suppresses the duplicate compatibility click', async () => {
    const wrapper = mountNavigationSheet();
    mountedWrappers.push(wrapper);
    await nextTick();

    const mapButton = toolButton('Map');
    vi.spyOn(mapButton, 'getBoundingClientRect').mockReturnValue(new DOMRect(680, 600, 80, 50));
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

  it.each(['GPS', 'Map', 'Filter', 'Planner', 'More'])(
    'cancels an upward swipe starting on %s without opening More',
    async (label) => {
      const wrapper = mountNavigationSheet(390);
      mountedWrappers.push(wrapper);
      const button = toolButton(label);
      const target = button.querySelector('i')!;
      target.dispatchEvent(pointerEvent('pointerdown', { clientX: 195, clientY: 700, pointerType: 'touch' }));
      // Pointer capture delivers the subsequent events to the grid, outside the original button.
      navGrid().dispatchEvent(pointerEvent('pointermove', { clientX: 195, clientY: 350, pointerType: 'touch' }));
      navGrid().dispatchEvent(pointerEvent('pointerup', { clientX: 195, clientY: 350, pointerType: 'touch' }));
      await nextTick();
      button.click();
      await nextTick();

      expect(wrapper.emitted('select')).toBeUndefined();
      expect(moreButton().getAttribute('aria-expanded')).toBe('false');
      expect(document.body.querySelector('.nav-sheet__overflow-panel')).toBeNull();
    }
  );

  it('cancels a release outside the original button even within the movement tolerance', async () => {
    const wrapper = mountNavigationSheet(390);
    mountedWrappers.push(wrapper);
    const button = toolButton('Filter');
    vi.spyOn(button, 'getBoundingClientRect').mockReturnValue(new DOMRect(100, 100, 70, 50));
    button.dispatchEvent(pointerEvent('pointerdown', { clientX: 135, clientY: 102, pointerType: 'touch' }));
    // A release can arrive without an intervening pointermove.
    navGrid().dispatchEvent(pointerEvent('pointerup', { clientX: 135, clientY: 97, pointerType: 'touch' }));
    await nextTick();
    const click = new MouseEvent('click', { bubbles: true, cancelable: true, clientX: 135, clientY: 97 });
    expect(button.dispatchEvent(click)).toBe(false);
    expect(wrapper.emitted('select')).toBeUndefined();
    expect(moreButton().getAttribute('aria-expanded')).toBe('false');
  });

  it.each(['pointerup', 'pointercancel'])(
    'activates a small movement inside Filter only on pointerup, ending with %s',
    async (endType) => {
      const wrapper = mountNavigationSheet(390);
      mountedWrappers.push(wrapper);
      const button = toolButton('Filter');
      vi.spyOn(button, 'getBoundingClientRect').mockReturnValue(new DOMRect(100, 100, 70, 50));
      button.dispatchEvent(pointerEvent('pointerdown', { clientX: 135, clientY: 125, pointerType: 'touch' }));
      navGrid().dispatchEvent(pointerEvent('pointermove', { clientX: 138, clientY: 128, pointerType: 'touch' }));
      navGrid().dispatchEvent(pointerEvent(endType, { clientX: 138, clientY: 128, pointerType: 'touch' }));
      await nextTick();
      button.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, clientX: 138, clientY: 128 }));
      await nextTick();
      expect(wrapper.emitted('select')).toEqual(endType === 'pointerup' ? [['filter']] : undefined);
      expect(moreButton().getAttribute('aria-expanded')).toBe('false');
    }
  );

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

  it('consumes toolbar touch drags without hiding it, selecting a tool, or leaking a map click', async () => {
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
    expect(document.body.querySelector('.nav-sheet')?.classList.contains('nav-sheet--collapsed')).toBe(false);

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

  it('keeps the toolbar visible after collapse requests and former handle gestures', async () => {
    const wrapper = mountNavigationSheet(390);
    mountedWrappers.push(wrapper);
    await nextTick();
    const sheet = document.body.querySelector<HTMLElement>('.nav-sheet')!;
    const initialHeight = sheet.style.getPropertyValue('--nav-body-h');
    const initialMapOffset = document.documentElement.style.getPropertyValue('--nav-sheet-h');
    const api = wrapper.vm as unknown as { snapTo: (snap: string) => void };
    api.snapTo('collapsed');
    await nextTick();
    const spacer = document.body.querySelector<HTMLElement>('.nav-sheet__handle-zone')!;
    spacer.click();
    spacer.dispatchEvent(pointerEvent('pointerdown', { clientX: 195, clientY: 780, pointerType: 'touch' }));
    spacer.dispatchEvent(pointerEvent('pointermove', { clientX: 195, clientY: 840, pointerType: 'touch' }));
    spacer.dispatchEvent(pointerEvent('pointerup', { clientX: 195, clientY: 840, pointerType: 'touch' }));
    await nextTick();
    expect(sheet.classList.contains('nav-sheet--collapsed')).toBe(false);
    expect(sheet.style.getPropertyValue('--nav-body-h')).toBe(initialHeight);
    expect(document.documentElement.style.getPropertyValue('--nav-sheet-h')).toBe(initialMapOffset);
    expect(document.body.querySelector('.nav-sheet__handle')).toBeNull();
    expect(document.body.querySelector('.nav-sheet__handle-hit-zone')).toBeNull();
    expect(document.body.querySelector('.nav-sheet__handle-tap-zone')).toBeNull();
    expect(document.body.querySelector('.nav-sheet__drag-halo')).toBeNull();
    moreButton().click();
    await flushPromises();
    expect(moreButton().getAttribute('aria-expanded')).toBe('true');
  });

  it.skip('owns handle touch drags and suppresses the duplicate handle click', async () => {
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

  it.each([
    { clientX: 195, clientY: 700, position: 'the swipe start' },
    { clientX: 195, clientY: 350, position: 'the swipe end' },
    { clientX: 0, clientY: 0, position: 'missing click coordinates' },
  ])('cancels an upward swipe and blocks delayed clicks at $position', async (clickPosition) => {
    vi.useFakeTimers();
    try {
      const wrapper = mountNavigationSheet(390);
      mountedWrappers.push(wrapper);
      const button = moreButton();
      button.dispatchEvent(pointerEvent('pointerdown', { clientX: 195, clientY: 700, pointerType: 'touch' }));
      button.dispatchEvent(pointerEvent('pointermove', { clientX: 195, clientY: 350, pointerType: 'touch' }));
      button.dispatchEvent(pointerEvent('pointerup', { clientX: 195, clientY: 350, pointerType: 'touch' }));
      await nextTick();

      expect(moreButton().getAttribute('aria-expanded')).toBe('false');
      expect(wrapper.emitted('select')).toBeUndefined();
      vi.advanceTimersByTime(650);
      for (let index = 0; index < 2; index++) {
        const ghostClick = new MouseEvent('click', { bubbles: true, cancelable: true, ...clickPosition });
        expect(button.dispatchEvent(ghostClick)).toBe(false);
        expect(ghostClick.defaultPrevented).toBe(true);
      }
      await nextTick();
      expect(moreButton().getAttribute('aria-expanded')).toBe('false');
      expect(wrapper.emitted('select')).toBeUndefined();

      // A fresh interaction can still open More and select Settings.
      button.dispatchEvent(pointerEvent('pointerdown', { pointerType: 'mouse' }));
      button.click();
      await nextTick();
      const settings = document.body.querySelector<HTMLButtonElement>('.nav-sheet__utility')!;
      settings.dispatchEvent(pointerEvent('pointerdown', { clientX: 195, clientY: 600, pointerType: 'touch' }));
      settings.dispatchEvent(pointerEvent('pointerup', { clientX: 195, clientY: 600, pointerType: 'touch' }));
      settings.click();
      await nextTick();
      expect(wrapper.emitted('select')).toEqual([['admin']]);
      expect(document.body.querySelector('.nav-sheet__overflow-panel')).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('allows keyboard selection after a swipe', async () => {
    const wrapper = mountNavigationSheet(390);
    mountedWrappers.push(wrapper);
    const button = moreButton();
    button.dispatchEvent(pointerEvent('pointerdown', { clientX: 195, clientY: 700, pointerType: 'touch' }));
    button.dispatchEvent(pointerEvent('pointermove', { clientX: 195, clientY: 350, pointerType: 'touch' }));
    button.dispatchEvent(pointerEvent('pointerup', { clientX: 195, clientY: 350, pointerType: 'touch' }));
    await nextTick();

    button.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    button.click();
    await nextTick();
    expect(moreButton().getAttribute('aria-expanded')).toBe('true');
    const settings = document.body.querySelector<HTMLButtonElement>('.nav-sheet__utility')!;
    settings.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    settings.click();
    await nextTick();
    expect(wrapper.emitted('select')).toEqual([['admin']]);
  });

  it('does not turn a movement that returns to its starting point into a tool tap', async () => {
    const wrapper = mountNavigationSheet(390);
    mountedWrappers.push(wrapper);
    const button = toolButton('Map');
    button.dispatchEvent(pointerEvent('pointerdown', { clientX: 195, clientY: 700, pointerType: 'touch' }));
    button.dispatchEvent(pointerEvent('pointermove', { clientX: 195, clientY: 688, pointerType: 'touch' }));
    button.dispatchEvent(pointerEvent('pointerup', { clientX: 195, clientY: 700, pointerType: 'touch' }));
    await nextTick();
    button.click();
    expect(wrapper.emitted('select')).toBeUndefined();
    expect(moreButton().getAttribute('aria-expanded')).toBe('false');
  });

  // Retained tests for the disabled toolbar collapse controls.
  it.skip.each(
    ['mouse', 'pen'].flatMap((pointerType) =>
      ['.nav-sheet__handle-tap-zone', '.nav-sheet__handle-hit-zone', '.nav-sheet__drag-halo'].map((zone) => ({
        pointerType,
        zone,
      }))
    )
  )('moves and snaps $zone with $pointerType input without activating a tool', async ({ pointerType, zone }) => {
    const wrapper = mountNavigationSheet(390);
    mountedWrappers.push(wrapper);
    await nextTick();
    moreButton().click();
    await nextTick();

    const sheet = document.body.querySelector<HTMLElement>('.nav-sheet')!;
    const handle = document.body.querySelector<HTMLElement>(zone)!;
    const expandedHeight = Number.parseFloat(sheet.style.getPropertyValue('--nav-body-h'));
    expect(moreButton().getAttribute('aria-expanded')).toBe('true');

    handle.dispatchEvent(pointerEvent('pointerdown', { clientX: 195, clientY: 333, pointerType }));
    handle.dispatchEvent(pointerEvent('pointermove', { clientX: 195, clientY: 500, pointerType }));
    await nextTick();
    expect(sheet.classList.contains('nav-sheet--dragging')).toBe(true);
    expect(Number.parseFloat(sheet.style.getPropertyValue('--nav-body-h'))).toBeLessThan(expandedHeight);
    handle.dispatchEvent(pointerEvent('pointermove', { clientX: 195, clientY: 685, pointerType }));
    handle.dispatchEvent(pointerEvent('pointerup', { clientX: 195, clientY: 685, pointerType }));
    await nextTick();
    expect(moreButton().getAttribute('aria-expanded')).toBe('false');
    expect(sheet.classList.contains('nav-sheet--collapsed')).toBe(false);

    const duplicateClick = new MouseEvent('click', {
      bubbles: true,
      cancelable: true,
      clientX: 195,
      clientY: 685,
    });
    expect(handle.dispatchEvent(duplicateClick)).toBe(false);
    await nextTick();
    expect(sheet.classList.contains('nav-sheet--collapsed')).toBe(false);

    handle.dispatchEvent(pointerEvent('pointerdown', { clientX: 195, clientY: 680, pointerType }));
    handle.dispatchEvent(pointerEvent('pointermove', { clientX: 195, clientY: 330, pointerType }));
    handle.dispatchEvent(pointerEvent('pointerup', { clientX: 195, clientY: 330, pointerType }));
    await nextTick();
    expect(moreButton().getAttribute('aria-expanded')).toBe('true');
    expect(Number.parseFloat(sheet.style.getPropertyValue('--nav-body-h'))).toBe(expandedHeight);
    expect(wrapper.emitted('select')).toBeUndefined();
  });

  it.skip('owns halo touch drags before they can reach the map', async () => {
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
