import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { defineComponent } from 'vue';

function indexSummary(pending: number) {
  return {
    pending,
    failed: 0,
    completed: 1,
    total: pending + 1,
    progressPercent: pending > 0 ? 50 : 100,
  };
}

function jobSummary(pending: number) {
  return {
    pending,
    done: 1,
    total: pending + 1,
    progressPercent: pending > 0 ? 50 : 100,
  };
}

async function mountStatusConsumer(indexPending = 0) {
  const getIndexerStatus = vi.fn().mockResolvedValue([indexSummary(indexPending)]);
  const getJobStatus = vi.fn().mockResolvedValue([jobSummary(0)]);
  const getAdminOperationalTasks = vi.fn().mockResolvedValue([]);

  vi.doMock('@/utils/serverAdminApi', () => ({
    getAdminOperationalTasks,
    getIndexerStatus,
    getJobStatus,
  }));

  const { useIndexerStatus } = await import('@/composables/useIndexerStatus');
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
    {
      global: {
        plugins: [pinia],
      },
    }
  );

  await flushPromises();

  if (!statusApi) {
    throw new Error('Status composable was not initialized.');
  }

  return {
    getAdminOperationalTasks,
    getIndexerStatus,
    getJobStatus,
    statusApi,
    wrapper,
  };
}

describe('useIndexerStatus polling', () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.resetModules();
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.resetModules();
    vi.doUnmock('@/utils/serverAdminApi');
  });

  it('keeps the 60-second idle cadence when no status surface is visible', async () => {
    const setup = await mountStatusConsumer();
    wrapper = setup.wrapper;

    expect(setup.getIndexerStatus).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(59_999);
    expect(setup.getIndexerStatus).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1);
    await flushPromises();
    expect(setup.getIndexerStatus).toHaveBeenCalledTimes(2);
  });

  it('keeps the eight-second cadence while a status surface is visible', async () => {
    const setup = await mountStatusConsumer();
    wrapper = setup.wrapper;

    expect(setup.getIndexerStatus).toHaveBeenCalledTimes(1);

    setup.statusApi.setFastPolling(true);

    await vi.advanceTimersByTimeAsync(7_999);
    expect(setup.getIndexerStatus).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1);
    await flushPromises();
    expect(setup.getIndexerStatus).toHaveBeenCalledTimes(2);

    setup.statusApi.setFastPolling(false);

    await vi.advanceTimersByTimeAsync(59_999);
    expect(setup.getIndexerStatus).toHaveBeenCalledTimes(2);

    await vi.advanceTimersByTimeAsync(1);
    await flushPromises();
    expect(setup.getIndexerStatus).toHaveBeenCalledTimes(3);
  });

  it('polls every eight seconds while operational work is active', async () => {
    const setup = await mountStatusConsumer(1);
    wrapper = setup.wrapper;

    expect(setup.getIndexerStatus).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(7_999);
    expect(setup.getIndexerStatus).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1);
    await flushPromises();
    expect(setup.getIndexerStatus).toHaveBeenCalledTimes(2);
  });
});
