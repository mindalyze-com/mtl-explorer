import { mount } from '@vue/test-utils';
import { defineComponent, h } from 'vue';
import { afterEach, describe, expect, it } from 'vitest';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';

import FilterDetailPanel from '@/components/filter/FilterDetailPanel.vue';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';

const InputTextStub = defineComponent({
  name: 'InputText',
  props: {
    id: { type: String, default: '' },
    inputmode: { type: String, default: undefined },
    type: { type: String, default: 'text' },
    value: { type: String, default: '' },
  },
  emits: ['input'],
  setup(props, { emit }) {
    return () =>
      h('input', {
        id: props.id,
        inputmode: props.inputmode,
        type: props.type,
        value: props.value,
        onInput: (event: Event) => emit('input', event),
      });
  },
});

function numericFilter(
  name: string,
  controls: Array<{ label: string; name: string; unit: string }>,
  stringParams: Record<string, string> = {}
) {
  return {
    filterInfo: {
      filterConfig: { filterDomain: 'GPS_TRACK', filterName: name },
      paramDefinitions: controls.map((control) => ({
        name: control.name,
        type: 'STRING',
        label: control.name,
      })),
      effectiveUiMetadata: {
        paramGroups: {
          filter: { label: 'Filter', order: 30 },
        },
        params: Object.fromEntries(
          controls.map((control) => [
            control.name,
            {
              label: control.label,
              group: 'filter',
              widget: 'number',
              optional: true,
              unit: control.unit,
            },
          ])
        ),
      },
    } as FilterInfo,
    filterParams: { stringParams },
  };
}

function mountPanel(selectedFilter: ReturnType<typeof numericFilter>) {
  return mount(FilterDetailPanel, {
    props: { selectedFilter },
    global: { stubs: { InputText: InputTextStub } },
  });
}

afterEach(() => {
  useMeasurementSystem().useDefaultMeasurementSystem();
});

describe('FilterDetailPanel numeric parameters', () => {
  it('renders editable metric distance bounds from effective UI metadata', async () => {
    useMeasurementSystem().setMeasurementSystem('METRIC');
    const wrapper = mountPanel(
      numericFilter(
        'TracksByDistanceGradient',
        [
          { name: 'DISTANCE_MIN_KM', label: 'Minimum distance', unit: 'km' },
          { name: 'DISTANCE_MAX_KM', label: 'Maximum distance', unit: 'km' },
        ],
        { DISTANCE_MIN_KM: '10', DISTANCE_MAX_KM: '20' }
      )
    );

    const minimum = wrapper.get<HTMLInputElement>('#DISTANCE_MIN_KM');
    const maximum = wrapper.get<HTMLInputElement>('#DISTANCE_MAX_KM');
    expect(minimum.attributes('type')).toBe('number');
    expect(maximum.attributes('type')).toBe('number');
    expect(minimum.element.value).toBe('10');
    expect(maximum.element.value).toBe('20');
    expect(wrapper.get('label[for="DISTANCE_MIN_KM"]').text()).toBe('Minimum distance');
    expect(wrapper.get('label[for="DISTANCE_MAX_KM"]').text()).toBe('Maximum distance');
    expect(wrapper.findAll('.filter-detail-field__unit').map((unit) => unit.text())).toEqual(['km', 'km']);

    await minimum.setValue('12.5');
    expect(wrapper.emitted('set-string-param')?.at(-1)).toEqual([{ name: 'DISTANCE_MIN_KM', value: '12.5' }]);
  });

  it('renders and converts imperial distance and elevation bounds', async () => {
    useMeasurementSystem().setMeasurementSystem('US_CUSTOMARY');
    const distanceWrapper = mountPanel(
      numericFilter(
        'TracksByDistanceGradient',
        [
          { name: 'DISTANCE_MIN_KM', label: 'Minimum distance', unit: 'km' },
          { name: 'DISTANCE_MAX_KM', label: 'Maximum distance', unit: 'km' },
        ],
        { DISTANCE_MIN_KM: '16.09344', DISTANCE_MAX_KM: '32.18688' }
      )
    );

    const minimumDistance = distanceWrapper.get<HTMLInputElement>('#DISTANCE_MIN_KM');
    expect(Number(minimumDistance.element.value)).toBeCloseTo(10, 10);
    expect(distanceWrapper.findAll('.filter-detail-field__unit').map((unit) => unit.text())).toEqual(['mi', 'mi']);
    await minimumDistance.setValue('2');
    expect(Number(distanceWrapper.emitted('set-string-param')?.at(-1)?.[0].value)).toBeCloseTo(3.218688, 10);

    const elevationWrapper = mountPanel(
      numericFilter(
        'TracksByElevationGainGradient',
        [
          { name: 'ELEVATION_GAIN_MIN_M', label: 'Minimum elevation gain', unit: 'm' },
          { name: 'ELEVATION_GAIN_MAX_M', label: 'Maximum elevation gain', unit: 'm' },
        ],
        { ELEVATION_GAIN_MIN_M: '304.8', ELEVATION_GAIN_MAX_M: '609.6' }
      )
    );

    const minimumElevation = elevationWrapper.get<HTMLInputElement>('#ELEVATION_GAIN_MIN_M');
    expect(Number(minimumElevation.element.value)).toBeCloseTo(1000, 10);
    expect(elevationWrapper.findAll('.filter-detail-field__unit').map((unit) => unit.text())).toEqual(['ft', 'ft']);
    await minimumElevation.setValue('500');
    expect(Number(elevationWrapper.emitted('set-string-param')?.at(-1)?.[0].value)).toBeCloseTo(152.4, 10);
  });
});
