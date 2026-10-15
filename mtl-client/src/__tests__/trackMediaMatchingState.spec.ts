import { describe, expect, it } from 'vitest';
import { trackMediaMatchingState } from '@/components/trackdetails/trackMediaMatchingState';

describe('Track media matching states', () => {
  it('separates missing and failed status from an empty completed result', () => {
    expect(trackMediaMatchingState(null, true)).toBe('checking');
    expect(trackMediaMatchingState(null, false)).toBe('unavailable');
    expect(trackMediaMatchingState({ jobs: [] }, true)).toBe('unavailable');
    expect(trackMediaMatchingState({ jobs: [{ job: 'mediaCorrelation', pending: 0, state: 'COMPLETE' }] }, true)).toBe(
      'complete'
    );
  });
  it.each(['GPS', 'MEDIA'])('waits for %s discovery even before pending files are counted', (index) => {
    expect(
      trackMediaMatchingState(
        {
          indexers: [{ index, discoveryInProgress: true }],
          jobs: [{ job: 'mediaCorrelation', pending: 0, state: 'COMPLETE' }],
        },
        true
      )
    ).toBe('pending');
  });
  it.each(['RUNNING', 'QUEUED', 'RETRYING', 'WAITING'] as const)('keeps %s matching unfinished', (state) => {
    expect(trackMediaMatchingState({ jobs: [{ job: 'mediaCorrelation', pending: 0, state }] }, true)).toBe('pending');
  });
  it('keeps disabled matching distinct from a completed empty result', () => {
    expect(trackMediaMatchingState({ jobs: [{ job: 'mediaCorrelation', state: 'DISABLED' }] }, true)).toBe(
      'unavailable'
    );
  });
});
