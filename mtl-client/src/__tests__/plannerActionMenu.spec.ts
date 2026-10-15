import { DOMWrapper, enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PrimeVue from 'primevue/config';
import PlannerActionMenu from '@/planner/components/PlannerActionMenu.vue';

enableAutoUnmount(afterEach);
describe('planner action menu', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function () {
      const width = this.classList.contains('planner-action-menu') ? 240 : 0;
      const height = this.classList.contains('planner-action-menu') ? 220 : 0;
      return { x: 0, y: 0, top: 0, left: 0, right: width, bottom: height, width, height, toJSON() {} };
    });
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });
  function mountMenu(overrides = {}) {
    return mount(PlannerActionMenu, {
      attachTo: document.body,
      props: {
        title: '2 · Waypoint',
        anchor: { x: 390, y: 390 },
        bounds: { left: 0, top: 0, right: 400, bottom: 400 },
        actions: [
          { id: 'move', label: 'Move to…' },
          { id: 'remove', label: 'Remove point', destructive: true },
        ],
        ...overrides,
      },
      global: { plugins: [PrimeVue] },
    });
  }
  it('flips beside its point and keeps the popup inside the available map area', async () => {
    const wrapper = mountMenu();
    await flushPromises();
    const menu = document.querySelector<HTMLElement>('.planner-action-menu')!;
    expect(menu.style.left).toBe('138px');
    expect(menu.style.top).toBe('158px');
    await wrapper.setProps({ bounds: { left: 0, top: 0, right: 320, bottom: 200 } });
    expect(menu.style.maxWidth).toBe('304px');
    expect(menu.style.maxHeight).toBe('184px');
    expect(menu.style.top).toBe('8px');
  });
  it('uses the framework menu keyboard navigation and invokes the focused action', async () => {
    const wrapper = mountMenu();
    await flushPromises();
    const list = new DOMWrapper(document.querySelector<HTMLElement>('.planner-action-menu [role="menu"]')!);
    expect(document.activeElement).toBe(list.element);
    expect(list.attributes('aria-activedescendant')).toMatch(/_0$/);
    await list.trigger('keydown', { key: 'ArrowDown', code: 'ArrowDown' });
    expect(list.attributes('aria-activedescendant')).toMatch(/_1$/);
    await list.trigger('keydown', { key: 'Enter', code: 'Enter' });
    expect(wrapper.emitted('action')).toEqual([['remove']]);
  });
  it('keeps the point reachable on a narrow map and limits scrolling to the available space', async () => {
    mountMenu({
      hint: 'Drag this point to move it.',
      keepAnchorVisible: true,
      anchor: { x: 195, y: 170 },
      bounds: { left: 0, top: 0, right: 390, bottom: 390 },
    });
    await flushPromises();
    const menu = document.querySelector<HTMLElement>('.planner-action-menu')!;
    expect(menu.style.left).toBe('75px');
    expect(menu.style.top).toBe('194px');
    expect(menu.style.maxHeight).toBe('188px');
    expect(Number.parseFloat(menu.style.top)).toBeGreaterThan(170);
  });
  it('uses spare screen space when a narrow map cannot fit the actions beside the point', async () => {
    mountMenu({
      hint: 'Drag this point to move it.',
      keepAnchorVisible: true,
      anchor: { x: 195, y: 170 },
      bounds: { left: 0, top: 0, right: 390, bottom: 390 },
      fallbackBounds: { left: 0, top: 0, right: 390, bottom: 844 },
    });
    await flushPromises();
    const menu = document.querySelector<HTMLElement>('.planner-action-menu')!;
    expect(menu.style.left).toBe('75px');
    expect(menu.style.top).toBe('194px');
    expect(menu.style.maxHeight).toBe('642px');
  });
  it('contains Escape so it cannot close the planner sheet', async () => {
    const wrapper = mountMenu();
    await flushPromises();
    const escaped = vi.fn();
    document.addEventListener('keydown', escaped);
    await new DOMWrapper(document.querySelector<HTMLElement>('.planner-action-menu [role="menu"]')!).trigger(
      'keydown',
      { key: 'Escape', code: 'Escape' }
    );
    expect(wrapper.emitted('dismiss')).toEqual([[{ restoreFocus: true }]]);
    expect(escaped).not.toHaveBeenCalled();
    document.removeEventListener('keydown', escaped);
  });
  it('describes the menu with the drag hint and dismisses only the hint', async () => {
    const wrapper = mountMenu({ hint: 'Drag this point to move it.' });
    await flushPromises();
    const list = document.querySelector<HTMLElement>('.planner-action-menu [role="menu"]')!;
    const hint = document.querySelector<HTMLElement>('.planner-action-menu__hint p')!;
    expect(list.getAttribute('aria-describedby')).toBe(hint.id);
    const button = new DOMWrapper(document.querySelector<HTMLButtonElement>('[aria-label="Dismiss drag hint"]')!);
    button.element.focus();
    await button.trigger('click');
    expect(wrapper.emitted('dismiss-hint')).toEqual([[]]);
    expect(wrapper.emitted('action')).toBeUndefined();
    expect(wrapper.emitted('dismiss')).toBeUndefined();
    expect(document.activeElement).toBe(list);
    await wrapper.setProps({ hint: undefined });
    expect(list.hasAttribute('aria-describedby')).toBe(false);
  });
  it('lets keyboard users reach the hint dismissal and closes when Tab leaves the menu', async () => {
    const wrapper = mountMenu({ hint: 'Drag this point to move it.' });
    await flushPromises();
    const list = new DOMWrapper(document.querySelector<HTMLElement>('.planner-action-menu [role="menu"]')!);
    await list.trigger('keydown', { key: 'Tab', shiftKey: true });
    expect(wrapper.emitted('dismiss')).toBeUndefined();
    const button = new DOMWrapper(document.querySelector<HTMLButtonElement>('[aria-label="Dismiss drag hint"]')!);
    await button.trigger('keydown', { key: 'Tab' });
    expect(wrapper.emitted('dismiss')).toBeUndefined();
    await list.trigger('keydown', { key: 'Tab' });
    expect(wrapper.emitted('dismiss')).toEqual([[{ restoreFocus: true }]]);
  });
  it('replaces the drag tip with the active move instruction', async () => {
    const wrapper = mountMenu({ hint: 'Drag this point to move it.', message: 'Tap the map at the new position.' });
    await flushPromises();
    expect(document.querySelector('.planner-action-menu__hint')).toBeNull();
    expect(document.querySelector('.planner-action-menu__message')?.textContent).toBe(
      'Tap the map at the new position.'
    );
    expect(document.querySelector('[role="menu"]')?.hasAttribute('aria-describedby')).toBe(false);
    expect(wrapper.emitted('dismiss-hint')).toBeUndefined();
  });
  it('dismisses outside taps, excludes its trigger, and removes listeners on unmount', async () => {
    const trigger = document.createElement('button');
    document.body.appendChild(trigger);
    const dismissed = vi.fn();
    const wrapper = mountMenu({ trigger, onDismiss: dismissed });
    await flushPromises();
    trigger.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    expect(wrapper.emitted('dismiss')).toBeUndefined();
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    expect(wrapper.emitted('dismiss')).toHaveLength(1);
    wrapper.unmount();
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    expect(dismissed).toHaveBeenCalledTimes(1);
    trigger.remove();
  });
});
