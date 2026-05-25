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

function mountStackHarness(options: {
  lowerOpen?: boolean;
  upperOpen?: boolean;
  lowerZIndex?: number;
  upperZIndex?: number;
  lowerNoBackdrop?: boolean;
  upperNoBackdrop?: boolean;
} = {}) {
  const wrapper = mount(
    defineComponent({
      components: { BottomSheet },
      data() {
        return {
          lowerOpen: options.lowerOpen ?? true,
          upperOpen: options.upperOpen ?? false,
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

const mountedWrappers: VueWrapper[] = [];

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
    const wrapper = mountStackHarness({
      lowerNoBackdrop: false,
      upperNoBackdrop: true,
      upperOpen: true,
    });
    await flushSheetUpdates();

    expect(document.body.querySelectorAll('.sheet-backdrop')).toHaveLength(1);
  });
});
