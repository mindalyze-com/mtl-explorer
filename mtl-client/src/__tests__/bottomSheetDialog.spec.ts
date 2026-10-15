import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, nextTick } from 'vue';
import PrimeVue from 'primevue/config';
import Dialog from 'primevue/dialog';
import AllVersionsReport from '@/components/admin/AllVersionsReport.vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';

const wrappers: VueWrapper[] = [];
const originalWidth = window.innerWidth;
const dialogSelector = '[data-pc-name="dialog"][role="dialog"]';

async function settle() {
  await nextTick();
  await flushPromises();
  await nextTick();
}

function escape(target: Element | Document = document, extra: KeyboardEventInit = {}) {
  const event = new KeyboardEvent('keydown', {
    key: 'Escape',
    code: 'Escape',
    bubbles: true,
    cancelable: true,
    ...extra,
  });
  target.dispatchEvent(event);
  return event;
}

function mountHarness(options: { closeOnEscape?: boolean; backLabel?: string; maximized?: boolean } = {}) {
  const wrapper = mount(
    defineComponent({
      components: { BottomSheet, AllVersionsReport, Dialog },
      data: () => ({
        open: true,
        dialog: false,
        backCount: 0,
        closeCount: 0,
        closeOnEscape: options.closeOnEscape ?? true,
        backLabel: options.backLabel ?? '',
        maximized: options.maximized ?? false,
      }),
      template: `
        <BottomSheet v-model="open" title="Settings" :detents="[{ height: '500px' }]"
          :back-label="backLabel" :initially-maximized="maximized" @back="backCount++" @closed="closeCount++">
          <AllVersionsReport report="MTL Explorer — all versions" />
          <button class="open-dialog" @click="dialog = true">Open editor</button>
          <Dialog v-model:visible="dialog" modal header="Editor" :close-on-escape="closeOnEscape">
            <textarea>Unchanged draft</textarea>
            <button class="close-dialog" @click="dialog = false">Close editor</button>
          </Dialog>
        </BottomSheet>`,
    }),
    { attachTo: document.body, global: { plugins: [PrimeVue], stubs: { transition: false } } }
  );
  wrappers.push(wrapper);
  return wrapper;
}

function state(wrapper: VueWrapper) {
  return wrapper.vm as unknown as { open: boolean; dialog: boolean; backCount: number; closeCount: number };
}

async function openDialog(triggerSelector: string) {
  await settle();
  const trigger = document.querySelector<HTMLButtonElement>(triggerSelector)!;
  trigger.focus();
  trigger.click();
  await settle();
  await vi.waitFor(() => expect(document.querySelector(dialogSelector)).not.toBeNull());
  return trigger;
}

async function dialogGone() {
  await settle();
  await vi.waitFor(() => expect(document.querySelector(dialogSelector)).toBeNull());
}

beforeEach(() => {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
  );
});

afterEach(async () => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
  await settle();
  document.body.innerHTML = '';
  document.body.style.overflow = '';
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalWidth });
  vi.unstubAllGlobals();
});

describe('BottomSheet modal Escape ownership', () => {
  it.each([1440, 390])('dismisses only All versions at %i px, then Settings on the next Escape', async (width) => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: width });
    const wrapper = mountHarness();
    const trigger = await openDialog('.all-versions-action button');
    const field = document.querySelector<HTMLTextAreaElement>(`${dialogSelector} textarea`)!;
    field.focus();

    expect(escape(field).defaultPrevented).toBe(false);
    await dialogGone();
    expect(state(wrapper).open).toBe(true);
    expect(state(wrapper).closeCount).toBe(0);
    expect(document.activeElement).toBe(trigger);
    expect(document.body.style.overflow).toBe('hidden');

    escape(trigger);
    await settle();
    expect(state(wrapper).open).toBe(false);
    expect(state(wrapper).closeCount).toBe(1);
    expect(document.body.style.overflow).toBe('');
  });

  it('keeps Settings open after the versions Close button and releases Escape ownership', async () => {
    const wrapper = mountHarness();
    const trigger = await openDialog('.all-versions-action button');
    const close = Array.from(document.querySelectorAll<HTMLButtonElement>(`${dialogSelector} button`)).find(
      (button) => button.textContent === 'Close'
    )!;
    close.click();
    await dialogGone();
    expect(state(wrapper).open).toBe(true);
    expect(document.activeElement).toBe(trigger);

    escape();
    await settle();
    expect(state(wrapper).open).toBe(false);
  });

  it('keeps both layers open when the modal disables Escape', async () => {
    const wrapper = mountHarness({ closeOnEscape: false });
    await openDialog('.open-dialog');
    expect(escape().defaultPrevented).toBe(false);
    await settle();
    expect(state(wrapper).dialog).toBe(true);
    expect(state(wrapper).open).toBe(true);

    document.querySelector<HTMLButtonElement>('.close-dialog')!.click();
    await dialogGone();
    escape();
    await settle();
    expect(state(wrapper).open).toBe(false);
  });

  it('preserves the dialog and parent during input composition', async () => {
    const wrapper = mountHarness();
    await openDialog('.open-dialog');
    escape(document.querySelector(`${dialogSelector} textarea`)!, { isComposing: true });
    await settle();
    expect(state(wrapper).dialog).toBe(true);
    expect(state(wrapper).open).toBe(true);
  });

  it('preserves Back context until a following Escape', async () => {
    const wrapper = mountHarness({ backLabel: 'Back to Settings' });
    await openDialog('.all-versions-action button');
    escape();
    await dialogGone();
    expect(state(wrapper).backCount).toBe(0);
    expect(state(wrapper).open).toBe(true);

    escape();
    await settle();
    expect(state(wrapper).backCount).toBe(1);
    expect(state(wrapper).open).toBe(true);
  });

  it('preserves a maximized panel until a following Escape restores its size', async () => {
    const wrapper = mountHarness({ maximized: true });
    await openDialog('.all-versions-action button');
    escape();
    await dialogGone();
    expect(document.querySelector('.sheet--fullscreen')).not.toBeNull();
    expect(state(wrapper).open).toBe(true);

    escape();
    await settle();
    expect(document.querySelector('.sheet--fullscreen')).toBeNull();
    expect(state(wrapper).open).toBe(true);

    escape();
    await settle();
    expect(state(wrapper).open).toBe(false);
  });

  it.each(['display', 'aria-hidden'])('ignores a hidden modal (%s)', async (hiddenBy) => {
    const wrapper = mountHarness();
    await settle();
    const hiddenDialog = document.createElement('div');
    hiddenDialog.setAttribute('data-pc-name', 'dialog');
    hiddenDialog.setAttribute('role', 'dialog');
    hiddenDialog.setAttribute('aria-modal', 'true');
    if (hiddenBy === 'display') hiddenDialog.style.display = 'none';
    else hiddenDialog.setAttribute('aria-hidden', 'true');
    document.body.append(hiddenDialog);

    escape();
    await settle();
    expect(state(wrapper).open).toBe(false);
  });
});
