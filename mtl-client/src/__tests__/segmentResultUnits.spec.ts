import { mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import DisplayMeasureResults from '@/components/measure/DisplayMeasureResults.vue';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';

const DataTableStub = defineComponent({
  name: 'DataTable',
  props: {
    value: {
      type: Array,
      default: () => [],
    },
  },
  template: '<div data-test="results-table"><slot /></div>',
});

const ColumnStub = defineComponent({
  name: 'Column',
  props: {
    field: String,
    header: String,
    sortField: String,
  },
  template: '<div></div>',
});

const ToggleSwitchStub = defineComponent({
  name: 'ToggleSwitch',
  props: {
    modelValue: Boolean,
  },
  emits: ['update:modelValue'],
  template: '<button data-test="consolidate" @click="$emit(\'update:modelValue\', !modelValue)"></button>',
});

const SlotStub = defineComponent({
  template: '<div><slot /></div>',
});

const measurementPreference = useMeasurementSystem();

function resultFixture() {
  return {
    crossings: {
      1: {
        gpsTrack: {
          id: 1,
          indexedFile: { name: 'Track 1' },
          startDate: new Date('2026-08-01T10:00:00Z'),
          endDate: new Date('2026-08-01T10:06:00Z'),
        },
        crossings: [
          {
            triggerPoint: { name: 'A' },
            avgSpeedSinceLastTriggerPoint: 0,
            distanceInMeterSinceLastTriggerPoint: 0,
            timeInSecSinceLastTriggerPoint: 0,
          },
          {
            triggerPoint: { name: 'B' },
            avgSpeedSinceLastTriggerPoint: 16.09344,
            distanceInMeterSinceLastTriggerPoint: 1609.344,
            timeInSecSinceLastTriggerPoint: 360,
          },
        ],
      },
    },
    segmentsStats: [{ point1: 'A', point2: 'B', label: 'A - B', count: 1 }],
    tracksPerZone: { A: 1, B: 1 },
  };
}

function mountResults() {
  return mount(DisplayMeasureResults, {
    props: {
      measureServiceResult: resultFixture() as never,
    },
    global: {
      directives: {
        tooltip: {},
      },
      stubs: {
        BottomSheet: SlotStub,
        Column: ColumnStub,
        DataTable: DataTableStub,
        MeasureGraph: true,
        Popover: SlotStub,
        SegmentCompare: true,
        TabPanel: SlotStub,
        TabPanels: SlotStub,
        Tabs: SlotStub,
        ToggleSwitch: ToggleSwitchStub,
        Transition: false,
        VirtualRace: true,
      },
    },
  });
}

function tableRows(wrapper: ReturnType<typeof mountResults>) {
  return wrapper.findComponent(DataTableStub).props('value') as Array<Record<string, unknown>>;
}

async function selectMetric(wrapper: ReturnType<typeof mountResults>, metric: string) {
  const button = wrapper.findAll('.measure-metric-chip').find((candidate) => candidate.text() === metric);
  expect(button).toBeDefined();
  await button!.trigger('click');
}

beforeEach(() => {
  measurementPreference.setMeasurementSystem('US_CUSTOMARY');
});

afterEach(() => {
  measurementPreference.setMeasurementSystem('METRIC');
});

describe('segment result measurement units', () => {
  it('formats consolidated speed and distance in the selected measurement system', async () => {
    const wrapper = mountResults();

    expect(tableRows(wrapper)[0]['A-B']).toBe('10.00 mph');
    expect(tableRows(wrapper)[0]['A-B-sortValue']).toBe(16.09344);

    const segmentColumn = wrapper.findAllComponents(ColumnStub).find((column) => column.props('field') === 'A-B');
    expect(segmentColumn?.props('sortField')).toBe('A-B-sortValue');

    await selectMetric(wrapper, 'distance');
    expect(tableRows(wrapper)[0]['A-B']).toBe('1.00 mi');
    expect(tableRows(wrapper)[0]['A-B-sortValue']).toBe(1609.344);

    measurementPreference.setMeasurementSystem('METRIC');
    await nextTick();
    expect(tableRows(wrapper)[0]['A-B']).toBe('1.61 km');

    wrapper.unmount();
  });

  it('formats individual visit values instead of exposing canonical units', async () => {
    const wrapper = mountResults();
    await wrapper.get('[data-test="consolidate"]').trigger('click');

    expect(tableRows(wrapper)[0]['A1-B1']).toBe('10.00 mph');

    await selectMetric(wrapper, 'distance');
    expect(tableRows(wrapper)[0]['A1-B1']).toBe('1.00 mi');

    await selectMetric(wrapper, 'time');
    expect(tableRows(wrapper)[0]['A1-B1']).toBe('00:06:00');

    wrapper.unmount();
  });
});
