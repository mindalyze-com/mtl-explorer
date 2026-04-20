import { describe, it, expect } from 'vitest';

// Sanity test confirming the Vitest harness is wired up. Real tests are added
// alongside refactored composables/components.
describe('vitest harness', () => {
  it('runs', () => {
    expect(1 + 1).toBe(2);
  });
});
