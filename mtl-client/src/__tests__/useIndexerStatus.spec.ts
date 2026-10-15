import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { defineComponent } from 'vue';
import { APPLICATION_STATUS_SECTION, type ApplicationStatusSection } from '@/stores/applicationStatusStore';
import { useIndexerStatus } from '@/composables/useIndexerStatus';

const getApplicationStatusMock = vi.hoisted(() => vi.fn());
vi.mock('x8ing-mtl-api-typescript-fetch', async () => {
  const actual = await vi.importActual<typeof import('x8ing-mtl-api-typescript-fetch')>(
    'x8ing-mtl-api-typescript-fetch'
  );
  return {
    ...actual,
    ApplicationStatusControllerApi: class {
      getApplicationStatus = getApplicationStatusMock;
    },
  };
});

function aggregateResponse(include: ApplicationStatusSection[], indexPending: number) {
  return {
    generatedAt: new Date(),
    included: new Set(include),
    processing: include.includes(APPLICATION_STATUS_SECTION.PROCESSING)
      ? {
          indexers: [{ index: 'GPS', pending: indexPending, total: indexPending, progressPercent: 0 }],
          jobs: [],
        }
      : undefined,
    services: include.includes(APPLICATION_STATUS_SECTION.SERVICES)
      ? { tileMode: 'local', plannerEnabled: false, map: { ready: true }, locationSearch: { ready: true } }
      : undefined,
  };
}

async function mountStatusConsumer(indexPending = 0) {
  getApplicationStatusMock.mockImplementation(({ include }: { include: ApplicationStatusSection[] }) =>
    Promise.resolve(aggregateResponse(include, indexPending))
  );
  const pinia = createPinia();
  setActivePinia(pinia);

  let statusApi: { setFastPolling: (enabled: boolean) => void } | null = null;
  const wrapper = mount(
    defineComponent({
      setup() {
        statusApi = useIndexerStatus();
        return () => null;
      },
    }),
    { global: { plugins: [pinia] } }
  );

  await vi.advanceTimersByTimeAsync(0);
  await flushPromises();
  if (!statusApi) throw new Error('Status composable was not initialized.');
  return { statusApi, wrapper };
}

describe('useIndexerStatus polling', () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    vi.useFakeTimers();
    getApplicationStatusMock.mockReset();
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('keeps the 60-second idle cadence when no status surface is visible', async () => {
    const setup = await mountStatusConsumer();
    wrapper = setup.wrapper;

    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(59_999);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
  });

  it('keeps the eight-second cadence while a status surface is visible', async () => {
    const setup = await mountStatusConsumer();
    wrapper = setup.wrapper;
    setup.statusApi.setFastPolling(true);
    await vi.advanceTimersByTimeAsync(0);

    await vi.advanceTimersByTimeAsync(7_999);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);

    setup.statusApi.setFastPolling(false);
    await vi.advanceTimersByTimeAsync(59_999);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(3);
  });

  it('polls every eight seconds while processing work is active', async () => {
    const setup = await mountStatusConsumer(1);
    wrapper = setup.wrapper;

    await vi.advanceTimersByTimeAsync(7_999);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
  });
});
