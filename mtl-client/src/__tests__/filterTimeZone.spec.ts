import { afterEach, describe, expect, it, vi } from 'vitest';
import { withBrowserTimeZone } from '@/utils/filterTimeZone';

describe('calendar filter timezone context', () => {
  afterEach(() => vi.restoreAllMocks());

  it('uses the current browser timezone instead of a saved timezone without changing criteria', () => {
    vi.spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions').mockReturnValue({
      timeZone: 'Europe/Zurich',
    } as Intl.ResolvedDateTimeFormatOptions);
    const params = { timeZone: 'America/New_York', stringParams: { YEAR_FROM: '2025' } };
    expect(withBrowserTimeZone(params)).toEqual({ timeZone: 'Europe/Zurich', stringParams: { YEAR_FROM: '2025' } });
    expect(params.timeZone).toBe('America/New_York');
  });

  it('provides context for an empty request and defaults to UTC if unavailable', () => {
    vi.spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions').mockReturnValue({
      timeZone: '',
    } as Intl.ResolvedDateTimeFormatOptions);
    expect(withBrowserTimeZone()).toEqual({ timeZone: 'UTC' });
  });
});
