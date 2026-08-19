import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import FilterResultGroupSelector from '@/components/filter/FilterResultGroupSelector.vue';
import { FilterConfigEntityColoringStrategyEnum } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterConfigEntity';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterResultGroupSummary } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupSummary';

function group(value: string | null, count: number): FilterResultGroupSummary {
  return { key: { value }, count };
}

describe('FilterResultGroupSelector', () => {
  it('renders effective counts, raw counts, ungrouped values, and unavailable selections', () => {
    const wrapper = mount(FilterResultGroupSelector, {
      props: {
        availableGroups: [group('2024', 4), group(null, 2)],
        selection: {
          includedGroups: [{ value: '2024' }, { value: '2023' }],
        },
        effectiveCount: 4,
        preSelectionCount: 6,
      },
    });

    expect(wrapper.text()).toContain('4 of 6 matching tracks');
    expect(wrapper.text()).toContain('1 of 2 current selected · 1 unavailable');
    expect(wrapper.text()).toContain('Ungrouped');
    expect(wrapper.text()).toContain('2023');
    expect(wrapper.text()).toContain('No matches with current parameters');
  });

  it('summarizes a selected category when every current category is unavailable', () => {
    const wrapper = mount(FilterResultGroupSelector, {
      props: {
        availableGroups: [],
        selection: { includedGroups: [{ value: 'WALKING' }] },
        effectiveCount: 0,
        preSelectionCount: 0,
      },
    });

    expect(wrapper.text()).toContain('0 of 0 current selected · 1 unavailable');
  });

  it('supports exact row changes and all/none actions', async () => {
    const wrapper = mount(FilterResultGroupSelector, {
      props: {
        availableGroups: [group('WALKING', 3), group('HIKING', 2)],
        effectiveCount: 5,
        preSelectionCount: 5,
      },
    });

    const hikingRow = wrapper.findAll('.result-group-selector__row').find((row) => row.text().includes('HIKING'))!;
    await hikingRow.find('input').setValue(false);
    expect(wrapper.emitted('update:selection')?.at(-1)?.[0]).toEqual({
      includedGroups: [{ value: 'WALKING' }],
    });

    const selectNone = wrapper
      .findAll('.result-group-selector__actions button')
      .find((button) => button.text().includes('Clear selection'))!;
    await selectNone.trigger('click');
    expect(wrapper.emitted('update:selection')?.at(-1)?.[0]).toEqual({ includedGroups: [] });

    await wrapper.setProps({ selection: { includedGroups: [] } });
    const selectCurrent = wrapper
      .findAll('.result-group-selector__actions button')
      .find((button) => button.text().includes('Select current'))!;
    await selectCurrent.trigger('click');
    expect(wrapper.emitted('update:selection')?.at(-1)?.[0]).toBeUndefined();

    await wrapper.find('.result-group-selector__master input').setValue(true);
    expect(wrapper.emitted('update:selection')?.at(-1)?.[0]).toBeUndefined();
  });

  it('shows search for larger catalogs', async () => {
    const wrapper = mount(FilterResultGroupSelector, {
      props: {
        availableGroups: Array.from({ length: 13 }, (_, index) => group(`Year ${index}`, index + 1)),
        effectiveCount: 91,
        preSelectionCount: 91,
      },
    });

    await wrapper.find('input[type="search"]').setValue('Year 12');
    const rows = wrapper.findAll('.result-group-selector__row');
    expect(rows).toHaveLength(1);
    expect(rows[0].text()).toContain('Year 12');
  });

  it('groups numeric buckets into eight bands and marks partial bands', () => {
    const filterInfo = {
      filterConfig: { coloringStrategy: FilterConfigEntityColoringStrategyEnum.SequentialGradient },
      effectiveUiMetadata: { result: { gradient: { bucketCount: 16 } } },
    } as FilterInfo;
    const wrapper = mount(FilterResultGroupSelector, {
      props: {
        availableGroups: Array.from({ length: 16 }, (_, index) => group(String(index).padStart(2, '0'), 1)),
        selection: { includedGroups: [{ value: '00' }] },
        filterInfo,
        effectiveCount: 1,
        preSelectionCount: 16,
      },
    });

    const bands = wrapper.findAll('.result-group-selector__band');
    expect(bands).toHaveLength(8);
    expect((bands[0].find('input').element as HTMLInputElement).indeterminate).toBe(true);
    expect(wrapper.text()).toContain('Individual buckets');
  });
});
