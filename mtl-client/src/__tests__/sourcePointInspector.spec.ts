import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import PrimeVue from 'primevue/config';
import type { TrackFinding } from 'x8ing-mtl-api-typescript-fetch';
import SourceFindings from '@/features/track-review/SourceFindings.vue';
import SourcePointInspector from '@/features/track-review/SourcePointInspector.vue';
import { useLocale } from '@/composables/useLocale';

const api = vi.hoisted(() => ({ getSourceReviewPoints: vi.fn() }));
vi.mock('@/features/track-review/api', () => ({
  reviewApi: () => api,
  reasonLabel: (reason: string) => (reason === 'POSITION_JUMP' ? 'Removed GPS positions' : reason),
  reviewError: async (error: unknown) => String(error),
}));
enableAutoUnmount(afterEach);
beforeEach(() => {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    }
  );
});
afterEach(() => vi.unstubAllGlobals());

async function inspectFinding(finding: TrackFinding) {
  api.getSourceReviewPoints.mockResolvedValue({
    fromPointIndex: 104,
    totalCount: 120,
    points: Array.from({ length: 8 }, (_, index) => ({
      pointIndex: 104 + index,
      sectionIndex: 0,
      timestamp: new Date(Date.UTC(2026, 0, 1, 10, 0, index * 15)),
    })),
  });
  const inspector = mount(SourcePointInspector, {
    attachTo: document.body,
    props: { sourceId: 1, sourceHash: 'synthetic', pointIndex: finding.firstPointIndex!, finding },
    global: { plugins: [PrimeVue], stubs: { Button: { template: '<button />' }, transition: false } },
  });
  await flushPromises();
  return inspector;
}

function highlightedPointNumbers(inspector: Awaited<ReturnType<typeof inspectFinding>>) {
  return inspector.findAll('tr.finding-point--check .point-number').map((cell) => cell.text());
}

it('keeps the selected position finding visible beside the original timestamps', async () => {
  const { formatLocale, setLocale } = useLocale();
  const previousLocale = formatLocale.value;
  setLocale('en-GB');
  api.getSourceReviewPoints.mockResolvedValue({
    fromPointIndex: 106,
    totalPointCount: 120,
    points: [106, 107, 108].map((pointIndex, index) => ({
      pointIndex,
      sectionIndex: 0,
      latitude: 47,
      longitude: 8,
      timestamp: new Date(`2026-01-01T10:00:${String(index * 15).padStart(2, '0')}Z`),
    })),
  });
  const finding: TrackFinding = {
    stage: 'POSITION',
    reason: 'POSITION_JUMP',
    severity: 'WARNING',
    firstPointIndex: 107,
    lastPointIndex: 107,
    message: 'Import removed an unsupported position jump from the canonical path.',
  };
  const findings = mount(SourceFindings, { props: { diagnosis: { findings: [finding] }, inspectable: true } });
  await findings.get('button').trigger('click');
  expect(findings.emitted('select')?.[0]).toEqual([finding]);
  const inspector = mount(SourcePointInspector, {
    props: { sourceId: 1, sourceHash: 'synthetic', pointIndex: 107, finding },
    global: { stubs: { Button: { template: '<button />' } } },
  });
  await flushPromises();
  expect(inspector.get('.selected-finding').text()).toContain('Removed GPS positions');
  expect(inspector.get('.selected-finding').text()).toContain('Import removed');
  expect(inspector.get('tr.selected').text()).toContain('1 Jan 2026');
  expect(inspector.get('tr.selected .point-time').text()).toBe('10:00:15');
  expect(inspector.get('tr.selected .point-time').attributes('title')).toBe('2026-01-01T10:00:15.000Z');
  inspector.unmount();
  setLocale(previousLocale);
});

it.each([
  { reason: 'RECORDING_GAP', before: 108, after: 109 },
  { reason: 'TIME_BACKWARD', before: 107, after: 108 },
  { reason: 'TIME_DUPLICATE', before: 107, after: 108 },
] as const)('marks the two records around the first $reason transition', async ({ reason, before, after }) => {
  const finding: TrackFinding = {
    reason,
    severity: 'WARNING',
    sectionIndex: 0,
    firstPointIndex: 107,
    lastPointIndex: 110,
    affectedCount: 2,
  };
  const inspector = await inspectFinding(finding);
  expect(highlightedPointNumbers(inspector)).toEqual([String(before), String(after)]);
  expect(inspector.findAll('tbody tr')).toHaveLength(8);
  expect(inspector.findAll('.point-finding-link')).toHaveLength(2);
  expect(inspector.get('.finding-context').text()).toContain('first reported transition');
  const findings = mount(SourceFindings, { props: { diagnosis: { findings: [finding] }, inspectable: true } });
  expect(findings.get('button').text()).toBe(`Inspect transition ${before} → ${after}`);
});

it.each(['TIME_MISSING', 'INVALID_VALUE', 'ELEVATION_SPIKE', 'TIME_EXCURSION'] as const)(
  'uses the same marker for a reported %s point and keeps neighboring records neutral',
  async (reason) => {
    const inspector = await inspectFinding({
      reason,
      severity: 'WARNING',
      sectionIndex: 0,
      firstPointIndex: 107,
      lastPointIndex: 110,
      affectedCount: 2,
      message: 'Check the reported original value.',
    });
    expect(highlightedPointNumbers(inspector)).toEqual(['108']);
    expect(inspector.get('tr.finding-point--check .point-finding-link').attributes('aria-label')).toBe(
      `Explain ${reason} at point 108`
    );
    expect(inspector.get('tr.finding-point--check .point-finding-link').attributes('title')).toBe(
      'Check the reported original value.'
    );
    expect(inspector.findAll('tbody tr')).toHaveLength(8);
    expect(inspector.findAll('tbody tr').every((row) => row.findAll('th, td').length === 7)).toBe(true);
    expect(inspector.find('tbody .finding-marker').exists()).toBe(false);
  }
);

it('marks the exact endpoints of one GPS transition but does not infer endpoints for grouped removals', async () => {
  const finding: TrackFinding = {
    reason: 'POSITION_JUMP',
    severity: 'WARNING',
    sectionIndex: 0,
    firstPointIndex: 107,
    lastPointIndex: 110,
    affectedCount: 1,
  };
  const inspector = await inspectFinding(finding);
  expect(highlightedPointNumbers(inspector)).toEqual(['108', '111']);
  expect(inspector.findAll('.point-finding-link')).toHaveLength(2);
  await inspector.setProps({ finding: { ...finding, affectedCount: 2 } });
  expect(highlightedPointNumbers(inspector)).toEqual(['108']);
  const findings = mount(SourceFindings, {
    props: { diagnosis: { findings: [{ ...finding, affectedCount: 2 }] }, inspectable: true },
  });
  expect(findings.get('button').text()).toBe('Inspect point 108');
});

it('does not mark a neighboring section as part of a timing problem', async () => {
  const inspector = await inspectFinding({
    reason: 'TIME_BACKWARD',
    severity: 'REVIEW',
    sectionIndex: 1,
    firstPointIndex: 107,
  });
  api.getSourceReviewPoints.mockResolvedValue({
    fromPointIndex: 106,
    totalCount: 120,
    points: [
      { pointIndex: 106, sectionIndex: 0 },
      { pointIndex: 107, sectionIndex: 1 },
      { pointIndex: 108, sectionIndex: 1 },
    ],
  });
  await inspector.setProps({ sourceHash: 'another-synthetic-revision' });
  await flushPromises();
  expect(highlightedPointNumbers(inspector)).toEqual(['108']);
});

it('keeps informational findings neutral and updates marks when the selected finding changes', async () => {
  const inspector = await inspectFinding({
    reason: 'RECORDING_BREAK',
    severity: 'INFORMATION',
    sectionIndex: 0,
    firstPointIndex: 107,
  });
  expect(highlightedPointNumbers(inspector)).toEqual([]);
  expect(inspector.find('.point-finding-link').exists()).toBe(false);
  await inspector.setProps({
    finding: { reason: 'RECORDING_GAP', severity: 'WARNING', sectionIndex: 0, firstPointIndex: 107 },
  });
  expect(highlightedPointNumbers(inspector)).toEqual(['108', '109']);
});

it('opens the explanation outside the table and preserves every data row and column', async () => {
  const inspector = await inspectFinding({
    reason: 'TIME_BACKWARD',
    severity: 'WARNING',
    sectionIndex: 0,
    firstPointIndex: 107,
    message: 'The timestamp moves backward between these observations.',
  });
  const tableData = inspector.get('tbody').text();
  const trigger = inspector.get('[aria-label="Explain TIME_BACKWARD at point 108"]');
  await trigger.trigger('click');
  await vi.waitFor(() => expect(trigger.attributes('aria-expanded')).toBe('true'));
  const overlay = document.getElementById(trigger.attributes('aria-controls'))!;
  expect(overlay.parentElement).toBe(document.body);
  expect(overlay.getAttribute('role')).toBe('dialog');
  expect(overlay.textContent).toContain('Points 107 → 108');
  expect(overlay.textContent).toContain('The timestamp moves backward between these observations.');
  expect(inspector.get('tbody').text()).toBe(tableData);
  expect(inspector.findAll('tbody tr')).toHaveLength(8);
  expect(inspector.findAll('tbody tr').every((row) => row.findAll('th, td').length === 7)).toBe(true);
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
  await vi.waitFor(() => expect(document.getElementById(overlay.id)).toBeNull());
  expect(trigger.attributes('aria-expanded')).toBe('false');
  expect(document.activeElement).toBe(trigger.element);
});
