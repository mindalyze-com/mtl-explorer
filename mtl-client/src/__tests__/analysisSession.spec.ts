import { describe, expect, it } from 'vitest';
import { useAnalysisSession, orderedSegments, segmentKey } from '@/components/measure/useAnalysisSession';
import { attempt, result } from './fixtures/segmentAnalysis';

describe('segment analysis session', () => {
  it('starts in speed discovery with no arbitrary selection and preserves multiple visits per track', () => {
    const session = useAnalysisSession(),
      a = attempt(1),
      b = attempt(1, 2);
    session.setResults(result(a, b, attempt(2)));
    expect(session.state.metric).toBe('speed');
    expect(session.selected.value).toEqual([]);
    session.toggleAttempt(b);
    session.toggleAttempt(a);
    expect(session.selected.value.map((a) => a.identity.reference)).toEqual([
      b.identity.reference,
      a.identity.reference,
    ]);
    expect(session.state.references[session.state.segment]).toBe(b.identity.reference);
    session.state.sort = 'metricAsc';
    session.state.metric = 'time';
    session.state.grouping = 'attempts';
    expect(session.selected.value).toHaveLength(2);
    expect(Object.keys(session.state.result!.crossings!)).toHaveLength(2);
  });
  it('orders adjacent forward segments, reverse segments, other transitions, and loops', () => {
    const values = [
      ['C', 'C'],
      ['C', 'A'],
      ['B', 'A'],
      ['B', 'C'],
      ['A', 'B'],
      ['C', 'B'],
    ].map(([from, to], i) => {
      const a = attempt(i + 1);
      a.identity.fromZone = from;
      a.identity.toZone = to;
      return a;
    });
    expect(orderedSegments(result(...values)).map((s) => s.label)).toEqual([
      'A → B',
      'B → C',
      'B → A',
      'C → B',
      'C → A',
      'C → C',
    ]);
  });
  it('keeps separate directed selections, restores fresh references and identifies changed sources', () => {
    const session = useAnalysisSession(),
      a = attempt(1),
      b = attempt(1, 2),
      reverse = attempt(2);
    reverse.identity.fromZone = 'B';
    reverse.identity.toZone = 'A';
    session.setResults(result(a, b, reverse));
    session.toggleAttempt(a);
    session.toggleAttempt(b);
    session.state.segment = segmentKey('B', 'A');
    session.toggleAttempt(reverse);
    session.state.segment = segmentKey('A', 'B');
    expect(session.selected.value).toHaveLength(2);
    const refreshed = structuredClone(a);
    refreshed.identity.reference = 'fresh-reference';
    const changed = structuredClone(b);
    changed.identity.revision = 'changed-source';
    session.setResults(result(refreshed, changed, reverse));
    expect(session.selected.value.map((a) => a.identity.reference)).toEqual(['fresh-reference']);
    expect(session.state.references[session.state.segment]).toBe('fresh-reference');
    expect(session.state.staleSelections).toHaveLength(1);
    session.toggleAttempt(refreshed);
    expect(session.state.references[session.state.segment]).toBeNull();
  });
  it('retains all results and explicitly selects all without a fixed limit', () => {
    const session = useAnalysisSession();
    session.setResults(result(...Array.from({ length: 250 }, (_, i) => attempt(i + 1))));
    expect(session.selected.value).toHaveLength(0);
    session.selectAll();
    expect(session.selected.value).toHaveLength(250);
  });
  it.each(['individual', 'all', 'clear'] as const)(
    'dismisses an obsolete expiry notice when the user makes a new %s selection',
    (action) => {
      const session = useAnalysisSession();
      const old = attempt(1);
      session.setResults(result(old));
      session.toggleAttempt(old);
      const current = structuredClone(old);
      current.identity.revision = 'new-source';
      current.identity.reference = 'current-reference';
      session.setResults(result(current));
      expect(session.selected.value).toHaveLength(0);
      expect(session.state.staleSelections).toHaveLength(1);

      if (action === 'individual') session.toggleAttempt(current);
      else if (action === 'all') session.selectAll();
      else session.clearSelection();

      expect(session.state.staleSelections).toEqual([]);
      expect(session.selected.value).toHaveLength(action === 'clear' ? 0 : 1);
      if (action !== 'clear') expect(session.selected.value[0].identity.reference).toBe('current-reference');
    }
  );
});
