import { mount, type VueWrapper } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { defineComponent, h } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminProcessingSection from '@/components/admin/AdminProcessingSection.vue';
import {
  APPLICATION_STATUS_SECTION,
  useApplicationStatusStore,
  type ApplicationStatusSection,
} from '@/stores/applicationStatusStore';
import { GOOGLE_HEALTH_COUNT_KEY } from '@/utils/countAnimation';
import type { ProcessingStatusDto } from 'x8ing-mtl-api-typescript-fetch';

const getApplicationStatusMock = vi.hoisted(() => vi.fn());
vi.mock('x8ing-mtl-api-typescript-fetch', async (importOriginal) => {
  const actual = await importOriginal<typeof import('x8ing-mtl-api-typescript-fetch')>();
  return {
    ...actual,
    ApplicationStatusControllerApi: class {
      getApplicationStatus = getApplicationStatusMock;
    },
  };
});

const sections = [
  APPLICATION_STATUS_SECTION.PROCESSING,
  APPLICATION_STATUS_SECTION.SERVICES,
  APPLICATION_STATUS_SECTION.GOOGLE_HEALTH,
];
let loop = 0;
let processingOverride: ProcessingStatusDto | undefined;
let view: VueWrapper | undefined;

function snapshot(include: ApplicationStatusSection[]) {
  return {
    generatedAt: new Date(),
    included: new Set(include),
    processing: processingOverride ?? {
      indexers: ['GPS', 'MEDIA'].map((index) => ({
        index,
        completed: 100 + loop * 80,
        total: 1_000,
        pending: 900 - loop * 80,
        failed: 0,
        progressPercent: 10 + loop * 8,
      })),
      jobs: ['duplicate', 'activityType', 'energy', 'exploration', 'mediaCorrelation'].map((job) => ({
        job,
        done: 100 + loop * 80,
        total: 1_000,
        pending: 900 - loop * 80,
        failed: 0,
        unavailable: 1,
        progressPercent: 10 + loop * 8,
      })),
    },
    services: {
      tileMode: 'local',
      plannerEnabled: true,
      map: {
        ready: false,
        phase: 'downloading',
        downloadBytes: 100 + loop * 80,
        downloadTotal: 1_000,
        downloadPct: 10 + loop * 8,
      },
      locationSearch: { ready: false, phase: 'importing', rowCount: 100 + loop * 80 },
      planner: { available: true, brouterRunning: true, segmentsQueued: 900 - loop * 80 },
    },
    googleHealth: {
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      mirrorSummary: { recordCount: 10_000 },
      job: { state: 'RUNNING', syncId: 'cycle', recordsProcessed: 100 + loop * 80 },
    },
  };
}

async function refresh() {
  const request = useApplicationStatusStore().refresh(sections, { immediate: true });
  await vi.advanceTimersByTimeAsync(0);
  await request;
}

function mountMonitor() {
  const store = useApplicationStatusStore();
  return mount(
    defineComponent({
      setup: () => () =>
        h(AdminProcessingSection, {
          demoMode: false,
          googleHealthStatus: store.googleHealth,
          googleHealthCountAnimation: store.countAnimations.get(GOOGLE_HEALTH_COUNT_KEY) ?? null,
        }),
    }),
    { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } }
  );
}

function captionCounts() {
  return view!.findAll('.processing-task__caption').map((caption) => {
    const animated = caption.find('.animated-count');
    return animated.exists()
      ? animated.text()
      : caption
          .text()
          .trim()
          .match(/^[\d,]+/)?.[0];
  });
}

function expectedCounts(completed: number, queued: number) {
  return [...Array<string>(8).fill(String(completed)), `${completed} B`, String(completed), String(queued)];
}

describe('visible progress monitor counts', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(0);
    vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
    setActivePinia(createPinia());
    loop = 0;
    processingOverride = undefined;
    getApplicationStatusMock.mockImplementation(({ include }: { include: ApplicationStatusSection[] }) =>
      Promise.resolve(snapshot(include))
    );
  });

  afterEach(() => {
    view?.unmount();
    view = undefined;
    useApplicationStatusStore().$dispose();
    vi.clearAllTimers();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('animates every task between polls and resumes after reopening with its counts in step', async () => {
    await refresh();
    view = mountMonitor();
    expect(captionCounts()).toEqual(expectedCounts(100, 900));
    expect(view.findAll('.processing-task__details').every((details) => !details.isVisible())).toBe(true);
    expect(view.findAll('.processing-task__caption .animated-count').every((count) => count.isVisible())).toBe(true);
    expect(view.get('[data-testid="google-health-sync-progress"] .processing-task__caption').text()).toContain(
      '100 records processed this cycle'
    );
    expect(view.findAll('.processing-task__caption')[3]!.text().replace(/\s+/g, ' ')).toContain(
      '100 of 1,000 tracks processed · 1 unavailable'
    );

    await vi.advanceTimersByTimeAsync(8_000);
    loop = 1;
    await refresh();
    expect(captionCounts()).toEqual(expectedCounts(100, 900));
    await vi.advanceTimersByTimeAsync(400);
    expect(captionCounts()).toEqual(expectedCounts(104, 896));
    await vi.advanceTimersByTimeAsync(400);
    expect(captionCounts()).toEqual(expectedCounts(108, 892));
    await view.findAll('.processing-task__toggle')[1]!.trigger('click');
    expect(view.findAll('.processing-facts')[1]!.text()).toContain('Completed108');
    expect(view.findAll('.processing-facts')[1]!.text()).toContain('Pending892');

    view.unmount();
    view = undefined;
    await vi.advanceTimersByTimeAsync(3_200);
    view = mountMonitor();
    expect(captionCounts()).toEqual(expectedCounts(140, 860));
    await vi.advanceTimersByTimeAsync(4_000);
    expect(captionCounts()).toEqual(expectedCounts(180, 820));
    await vi.advanceTimersByTimeAsync(8_000);
    expect(captionCounts()).toEqual(expectedCounts(180, 820));
  });

  it('shows the latest reported counts immediately when reduced motion is requested', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
    await refresh();
    view = mountMonitor();
    await vi.advanceTimersByTimeAsync(8_000);
    loop = 1;
    await refresh();
    expect(captionCounts()).toEqual(expectedCounts(180, 820));
  });

  it('follows motion changes without replaying counts that were already shown', async () => {
    const motion = { matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() };
    vi.stubGlobal('matchMedia', () => motion);
    await refresh();
    view = mountMonitor();
    await vi.advanceTimersByTimeAsync(8_000);
    loop = 1;
    await refresh();
    await vi.advanceTimersByTimeAsync(400);
    expect(captionCounts()).toEqual(expectedCounts(104, 896));
    for (const reduced of [true, false]) {
      motion.matches = reduced;
      for (const [, listener] of motion.addEventListener.mock.calls) listener(new Event('change'));
      await vi.advanceTimersByTimeAsync(400);
      expect(captionCounts()).toEqual(expectedCounts(180, 820));
    }
  });

  it('pauses hidden playback and resumes at the current point in the shared timeline', async () => {
    let visibility: DocumentVisibilityState = 'visible';
    const visibilitySpy = vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility);
    try {
      await refresh();
      view = mountMonitor();
      await vi.advanceTimersByTimeAsync(8_000);
      loop = 1;
      await refresh();
      await vi.advanceTimersByTimeAsync(400);
      expect(captionCounts()).toEqual(expectedCounts(104, 896));
      visibility = 'hidden';
      document.dispatchEvent(new Event('visibilitychange'));
      await vi.advanceTimersByTimeAsync(3_600);
      expect(captionCounts()).toEqual(expectedCounts(104, 896));
      visibility = 'visible';
      document.dispatchEvent(new Event('visibilitychange'));
      await vi.advanceTimersByTimeAsync(0);
      expect(captionCounts()).toEqual(expectedCounts(140, 860));
    } finally {
      visibilitySpy.mockRestore();
    }
  });

  it('keeps odd-sized progress, pending counts, outcomes and segmented meters consistent at every frame', async () => {
    const batch = (completed: number): ProcessingStatusDto => ({
      indexers: [
        {
          index: 'GPS',
          completed,
          pending: 993 - completed,
          total: 1_000,
          failed: 3,
          skipped: 2,
          removed: 1,
          excluded: 1,
          progressPercent: (completed + 7) / 10,
        },
      ],
      jobs: [
        {
          job: 'energy',
          state: 'RUNNING',
          done: completed,
          pending: 996 - completed,
          total: 1_000,
          failed: 2,
          unavailable: 2,
          progressPercent: (completed + 4) / 10,
          work: { ready: 896 - completed, waiting: 100, retrying: 0 },
          blockedBy: [{ kind: 'JOB', id: 'activityType', scope: 'ITEMS', resolution: 'AUTOMATIC' }],
        },
      ],
    });
    processingOverride = batch(100);
    await refresh();
    view = mountMonitor();
    await vi.advanceTimersByTimeAsync(8_000);
    processingOverride = batch(181);
    await refresh();
    for (let elapsed = 0; elapsed <= 8_000; elapsed += 400) {
      const completed = 100 + Math.floor((81 * elapsed) / 8_000);
      const index = view.get('#processing-INDEX-GPS');
      const job = view.get('#processing-JOB-energy');
      const facts = Object.fromEntries(
        index
          .findAll('.processing-facts > div')
          .map((fact) => [fact.get('dt').text(), Number(fact.get('dd').text().replaceAll(',', ''))])
      );
      expect(facts.Completed).toBe(completed);
      expect(facts.Pending).toBe(993 - completed);
      expect(facts.Completed + facts.Pending + facts.Failed + facts.Skipped + facts.Removed + facts.Excluded).toBe(
        facts['Total files']
      );
      expect(index.get('.processing-task__caption').text().replace(/\s+/g, ' ')).toContain(`${completed} of 1,000`);
      expect(job.get('.processing-task__caption').text().replace(/\s+/g, ' ')).toContain(`${completed} of 1,000`);
      expect(job.get('.processing-facts').text()).toContain(`Completed${completed}`);
      expect(job.get('.processing-facts').text()).toContain(`Pending${996 - completed}`);
      expect(job.get('.processing-task__notice').text()).toContain('100 tracks wait for');
      expect(job.get('[role="progressbar"]').attributes('aria-valuenow')).toBe(String((completed + 4) / 10));
      expect(job.get('.processing-segment--ready').attributes('style')).toContain(`${(896 - completed) / 10}%`);
      if (elapsed < 8_000) await vi.advanceTimersByTimeAsync(400);
    }
  });

  it('snaps changed totals, outcomes, resets and completed tasks to one current snapshot', async () => {
    const batch = (completed: number, total = 1_000, failed = 0): ProcessingStatusDto => ({
      indexers: [{ index: 'GPS', completed, total, failed, pending: total - completed - failed, progressPercent: 20 }],
      jobs: [
        {
          job: 'energy',
          state: 'RUNNING',
          done: completed,
          total,
          failed,
          pending: total - completed - failed,
          progressPercent: 20,
        },
      ],
    });
    processingOverride = batch(100);
    await refresh();
    view = mountMonitor();
    await vi.advanceTimersByTimeAsync(8_000);
    processingOverride = batch(180);
    await refresh();
    await vi.advanceTimersByTimeAsync(400);
    expect(view.get('#processing-INDEX-GPS .processing-facts').text()).toContain('Completed104');
    for (const current of [batch(200, 1_100), batch(220, 1_100, 3), batch(10, 1_100, 3), batch(1_097, 1_100, 3)]) {
      processingOverride = current;
      await refresh();
      const completed = current.indexers![0]!.completed!;
      const pending = current.indexers![0]!.pending!;
      for (const id of ['INDEX-GPS', 'JOB-energy']) {
        const row = view.get(`#processing-${id}`);
        expect(row.get('.processing-task__caption').text().replace(/\s+/g, ' ')).toContain(
          `${completed.toLocaleString('en-US')} of 1,100`
        );
        expect(row.get('.processing-facts').text()).toContain(`Completed${completed.toLocaleString('en-US')}`);
        expect(row.get('.processing-facts').text()).toContain(`Pending${pending.toLocaleString('en-US')}`);
      }
      await vi.advanceTimersByTimeAsync(8_000);
    }
  });
});
