import { afterEach, describe, expect, it } from 'vitest';
import { useLocale } from '@/composables/useLocale';
import { formatDistanceSmart, formatNumber } from '@/utils/Utils';

const { setLocale } = useLocale();

describe('locale-aware formatting', () => {
  afterEach(() => {
    setLocale('');
  });

  it('uses the selected locale for fixed precision numbers', () => {
    setLocale('de-DE');

    expect(formatNumber(94.26, 2)).toBe('94,26');
  });

  it('uses the selected locale for smart distance decimals', () => {
    setLocale('de-DE');

    expect(formatDistanceSmart(3600)).toBe('3,60 km');
  });
});
