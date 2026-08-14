import { describe, expect, it } from 'vitest';
import type { FilterResultGroupKey } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupKey';
import {
  isResultGroupSelected,
  selectAllAvailableResultGroups,
  unavailableSelectedResultGroups,
  updateResultGroupSelection,
} from '@/utils/resultGroupSelection';

const year2024: FilterResultGroupKey = { value: '2024' };
const year2025: FilterResultGroupKey = { value: '2025' };
const year2026: FilterResultGroupKey = { value: '2026' };
const ungrouped: FilterResultGroupKey = { value: null };

describe('result group selection', () => {
  it('turns an all-categories state into an exact whitelist when one group is unchecked', () => {
    const selection = updateResultGroupSelection(undefined, [year2024, year2025, ungrouped], [year2025], false);

    expect(selection?.includedGroups).toEqual([year2024, ungrouped]);
    expect(isResultGroupSelected(year2025, selection)).toBe(false);
  });

  it('normalizes to all categories when a row change selects every available key', () => {
    const selection = updateResultGroupSelection(
      { includedGroups: [year2024] },
      [year2024, year2025],
      [year2025],
      true
    );

    expect(selection).toBeUndefined();
  });

  it('normalizes Select current from an empty whitelist to all categories', () => {
    const selection = selectAllAvailableResultGroups({ includedGroups: [] }, [year2024, year2025]);

    expect(selection).toBeUndefined();
  });

  it('keeps unavailable selected keys and leaves newly discovered keys unchecked', () => {
    const prior = { includedGroups: [year2024, year2026] };

    expect(selectAllAvailableResultGroups(prior, [year2024, year2025])).toEqual({
      includedGroups: [year2024, year2026, year2025],
    });
    expect(unavailableSelectedResultGroups(prior, [year2024, year2025])).toEqual([year2026]);
    expect(isResultGroupSelected(year2025, prior)).toBe(false);
  });

  it('supports an explicit empty whitelist', () => {
    const selection = { includedGroups: [] };

    expect(isResultGroupSelected(year2024, selection)).toBe(false);
    expect(isResultGroupSelected(ungrouped, selection)).toBe(false);
  });
});
