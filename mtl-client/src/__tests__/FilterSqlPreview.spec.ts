import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import FilterSqlPreview from '@/components/filter/FilterSqlPreview.vue';

const highlightStub = {
  props: ['code', 'language'],
  template: '<pre data-test="sql-code" :data-language="language">{{ code }}</pre>',
};

function mountPreview(viewMode: 'template' | 'resolved' = 'template') {
  const filterInfo = {
    filterConfig: {
      expression: 'SELECT id FROM ([[~/GPS_TRACK/SmartBaseFilter]]) WHERE track_name ILIKE :SEARCH_WORD',
    },
    resolvedSQL: 'SELECT id FROM gps_track WHERE track_name ILIKE :SEARCH_WORD AND start_date >= :DATE_TIME_FROM',
    paramsInSQL: new Set(['SEARCH_WORD', 'DATE_TIME_FROM']),
    paramDefinitions: [
      { name: 'SEARCH_WORD', type: 'STRING', label: 'Search word' },
      { name: 'DATE_TIME_FROM', type: 'DATE_TIME', label: 'From' },
    ],
    effectiveUiMetadata: {
      metadataVersion: 2,
      paramGroups: {
        filter: { label: 'Filter', order: 10 },
        scope: { label: 'Scope', order: 20 },
      },
      params: {
        SEARCH_WORD: { label: 'Keyword', group: 'filter', widget: 'text', optional: true },
        DATE_TIME_FROM: { label: 'From', group: 'scope', widget: 'dateTime', optional: true },
      },
    },
  } as FilterInfo;

  return mount(FilterSqlPreview, {
    props: {
      filterInfo,
      viewMode,
    },
    global: {
      stubs: {
        highlightjs: highlightStub,
      },
    },
  });
}

describe('FilterSqlPreview', () => {
  it('renders template SQL and parameter rows', () => {
    const wrapper = mountPreview('template');

    expect(wrapper.find('[data-test="sql-code"]').text()).toContain('[[~/GPS_TRACK/SmartBaseFilter]]');
    expect(wrapper.find('[data-test="sql-code"]').attributes('data-language')).toBe('mtl-pgsql');
    expect(wrapper.text()).toContain('Keyword');
    expect(wrapper.text()).toContain(':SEARCH_WORD');
    expect(wrapper.text()).toContain('From');
    expect(wrapper.text()).toContain(':DATE_TIME_FROM');
    expect(wrapper.text()).not.toContain('Not set');
  });

  it('emits selected SQL view changes', async () => {
    const wrapper = mountPreview('template');

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Resolved')!
      .trigger('click');

    expect(wrapper.emitted('update:view-mode')).toEqual([['resolved']]);
  });

  it('copies the currently selected SQL', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    });
    const wrapper = mountPreview('resolved');

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Copy SQL')!
      .trigger('click');
    await flushPromises();

    expect(writeText).toHaveBeenCalledWith(
      'SELECT id FROM gps_track WHERE track_name ILIKE :SEARCH_WORD AND start_date >= :DATE_TIME_FROM'
    );
    expect(wrapper.text()).toContain('Copied');
  });
});
