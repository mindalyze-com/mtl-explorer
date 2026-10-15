import { createPinia, setActivePinia } from 'pinia';
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { reactive } from 'vue';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import PrimeVue from 'primevue/config';
import TrackReviewTool from '@/features/track-review/TrackReviewTool.vue';

// These suites exercise normal-installation review actions.
vi.mock('@/stores/demoAccessStore', () => ({
  useDemoAccessStore: () => ({ demoMode: false, writeAllowed: true, load: vi.fn() }),
  SHARED_DEMO_READ_ONLY_MESSAGE: 'Import and review changes are unavailable in this shared demo.',
}));

vi.mock('@/utils/openApiClient', () => ({ getApiConfiguration: () => ({}) }));
vi.mock('@/router', () => ({ default: { push: vi.fn() } }));
const api = vi.hoisted(() => ({
  getTrackReviewQueue: vi.fn(),
  getSourceReview: vi.fn(),
  getSourceReviewPoints: vi.fn(),
}));
vi.mock('@/features/track-review/api', () => ({
  reviewApi: () => api,
  stateLabel: (value: string) => value,
  reasonLabel: (value: string) => value,
  decisionLabel: (value: string) => value,
  reviewError: async (error: unknown) => String(error),
  saveDownload: vi.fn(),
}));
vi.mock('vue-router', () => ({
  useRoute: () => reactive({ name: 'review', params: { sourceId: '1' }, query: {} }),
  useRouter: () => ({ push: vi.fn() }),
  RouterLink: { template: '<a><slot /></a>' },
}));
vi.mock('@/components/ui/BottomSheet.vue', () => ({
  default: {
    props: ['modelValue'],
    emits: ['update:modelValue', 'closed'],
    // BottomSheet retains its slot while closed. Body-appended popovers must close separately.
    template: `<div>
      <button data-test="close-sheet" @click.stop="$emit('update:modelValue', false); $emit('closed')">Close sheet</button>
      <div :hidden="!modelValue"><slot /></div>
    </div>`,
  },
}));
vi.mock('@/features/track-review/SourcePathComparison.vue', () => ({ default: { template: '<div />' } }));

enableAutoUnmount(afterEach);
beforeEach(() => {
  setActivePinia(createPinia());
  vi.resetAllMocks();
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    }
  );
  api.getTrackReviewQueue.mockResolvedValue({ sources: [], needsReviewCount: 1 });
  api.getSourceReview.mockResolvedValue({
    source: { sourceId: 1, sourceHash: 'synthetic', fileName: 'synthetic-jump.gpx', state: 'NEEDS_REVIEW' },
    totalPointCount: 3,
    tracks: [],
    sections: [],
    diagnosis: {
      findings: [
        {
          reason: 'POSITION_JUMP',
          severity: 'WARNING',
          sectionIndex: 0,
          firstPointIndex: 0,
          lastPointIndex: 1,
          affectedCount: 1,
          message: 'Synthetic unsupported GPS transition.',
        },
      ],
    },
  });
  api.getSourceReviewPoints.mockResolvedValue({
    fromPointIndex: 0,
    totalCount: 3,
    points: [0, 1, 2].map((pointIndex) => ({ pointIndex, sectionIndex: 0 })),
  });
});
afterEach(() => vi.unstubAllGlobals());

async function openReview() {
  const wrapper = mount(TrackReviewTool, {
    attachTo: document.body,
    global: { plugins: [PrimeVue], stubs: { transition: false } },
  });
  wrapper.vm.open();
  await flushPromises();
  return wrapper;
}

async function openExplanation(wrapper: Awaited<ReturnType<typeof openReview>>) {
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Inspect transition 1 → 2')!
    .trigger('click');
  await flushPromises();
  const trigger = wrapper.get('[aria-label="Explain POSITION_JUMP at point 1"]');
  await trigger.trigger('click');
  await vi.waitFor(() => expect(trigger.attributes('aria-expanded')).toBe('true'));
  return document.getElementById(trigger.attributes('aria-controls'))!;
}

it.each(['sheet', 'tool'] as const)('closes a finding explanation when the %s closes', async (closePath) => {
  const wrapper = await openReview();
  const overlay = await openExplanation(wrapper);
  expect(overlay.parentElement).toBe(document.body);
  if (closePath === 'sheet') await wrapper.get('[data-test="close-sheet"]').trigger('click');
  else wrapper.vm.close();
  await flushPromises();
  await vi.waitFor(() => expect(document.getElementById(overlay.id)).toBeNull());
  expect(wrapper.get('[aria-label="Explain POSITION_JUMP at point 1"]').attributes('aria-expanded')).toBe('false');
  expect(document.activeElement).not.toBe(wrapper.get('[aria-label="Explain POSITION_JUMP at point 1"]').element);

  wrapper.vm.open();
  await flushPromises();
  expect(wrapper.get('h2').text()).toBe('synthetic-jump.gpx');
  expect(document.querySelector('[aria-label="Finding explanation"]')).toBeNull();
  const reopenedOverlay = await openExplanation(wrapper);
  expect(reopenedOverlay.textContent).toContain('Synthetic unsupported GPS transition.');
  wrapper.vm.close();
  await flushPromises();
});
