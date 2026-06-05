import { mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import SegmentCompare from '@/components/measure/SegmentCompare.vue';
import VirtualRace from '@/components/virtual-race/VirtualRace.vue';
import { fetchTrackSubTrackDetails } from '@/utils/ServiceHelper';

vi.mock('@/utils/ServiceHelper', () => ({
  fetchTrackSubTrackDetails: vi.fn(),
}));

const fetchTrackSubTrackDetailsMock = vi.mocked(fetchTrackSubTrackDetails);

const MiniMapStub = defineComponent({
  name: 'MiniMap',
  methods: {
    invalidateMapSize() {
      return undefined;
    },
  },
  template: '<div data-test="minimap"></div>',
});

const ComparisonChartStub = defineComponent({
  name: 'ComparisonChart',
  template: '<div data-test="comparison-chart"></div>',
});

const RacerCardStub = defineComponent({
  name: 'RacerCard',
  template: '<div data-test="racer-card"></div>',
});

const ButtonStub = defineComponent({
  name: 'Button',
  template: '<button><slot /></button>',
});

const MtlSliderStub = defineComponent({
  name: 'MtlSlider',
  template: '<input />',
});

const segment = { point1: 'A', point2: 'B', consolidated: true };

function measureServiceResult() {
  return {
    crossings: {
      1: {
        gpsTrack: {
          id: 1,
          indexedFile: { name: 'Track 1' },
          startDate: new Date('2026-05-27T10:00:00Z'),
          activityType: 'BICYCLE',
        },
        crossings: [
          {
            gpsTrackDataPoint: {
              id: 101,
              durationSinceStart: 0,
              distanceInMeterSinceStart: 0,
              pointLongLat: { coordinates: [8.5, 47.5] },
            },
            triggerPoint: { name: 'A', coordinate: { x: 8.5, y: 47.5 } },
          },
          {
            gpsTrackDataPoint: {
              id: 102,
              durationSinceStart: 60,
              distanceInMeterSinceStart: 1000,
              pointLongLat: { coordinates: [8.51, 47.51] },
            },
            timeInSecSinceLastTriggerPoint: 60,
            distanceInMeterSinceLastTriggerPoint: 1000,
            avgSpeedSinceLastTriggerPoint: 60,
            triggerPoint: { name: 'B', coordinate: { x: 8.51, y: 47.51 } },
          },
        ],
      },
    },
    segmentsStats: [{ point1: 'A', point2: 'B', label: 'A - B' }],
  };
}

function degenerateMeasureServiceResult() {
  const samePoint = {
    id: 101,
    durationSinceStart: 0,
    distanceInMeterSinceStart: 0,
    pointLongLat: { coordinates: [8.5, 47.5] },
  };
  return {
    crossings: {
      1: {
        gpsTrack: {
          id: 1,
          indexedFile: { name: 'Track 1' },
          startDate: new Date('2026-05-27T10:00:00Z'),
          activityType: 'BICYCLE',
        },
        crossings: [
          {
            gpsTrackDataPoint: samePoint,
            triggerPoint: { name: 'A', coordinate: { x: 8.5, y: 47.5 } },
          },
          {
            gpsTrackDataPoint: samePoint,
            timeInSecSinceLastTriggerPoint: 0,
            distanceInMeterSinceLastTriggerPoint: 0,
            avgSpeedSinceLastTriggerPoint: 0,
            triggerPoint: { name: 'B', coordinate: { x: 8.51, y: 47.51 } },
          },
        ],
      },
    },
    segmentsStats: [{ point1: 'A', point2: 'B', label: 'A - B' }],
  };
}

function generatedJtsPointShapeMeasureServiceResult() {
  const generatedJtsCoordinates = () => [{ x: undefined }, { y: undefined }] as unknown as number[];
  return {
    crossings: {
      1: {
        gpsTrack: {
          id: 1,
          indexedFile: { name: 'Track 1' },
          startDate: new Date('2026-05-27T10:00:00Z'),
          activityType: 'BICYCLE',
        },
        crossings: [
          {
            gpsTrackDataPoint: {
              id: 101,
              durationSinceStart: 0,
              distanceInMeterSinceStart: 0,
              pointLongLat: { coordinates: generatedJtsCoordinates() },
            },
            triggerPoint: { name: 'A', coordinate: { x: 8.5, y: 47.5 } },
          },
          {
            gpsTrackDataPoint: {
              id: 102,
              durationSinceStart: 60,
              distanceInMeterSinceStart: 1000,
              pointLongLat: { coordinates: generatedJtsCoordinates() },
            },
            timeInSecSinceLastTriggerPoint: 60,
            distanceInMeterSinceLastTriggerPoint: 1000,
            avgSpeedSinceLastTriggerPoint: 60,
            triggerPoint: { name: 'B', coordinate: { x: 8.51, y: 47.51 } },
          },
        ],
      },
    },
    segmentsStats: [{ point1: 'A', point2: 'B', label: 'A - B' }],
  };
}

function segmentPoints() {
  return [
    {
      durationSinceStart: 0,
      distanceInMeterSinceStart: 0,
      pointLongLat: { coordinates: [8.5, 47.5] },
      speedInKmhMovingWindow: 20,
    },
    {
      durationSinceStart: 60,
      distanceInMeterSinceStart: 1000,
      pointLongLat: { coordinates: [8.51, 47.51] },
      speedInKmhMovingWindow: 30,
    },
  ];
}

function degenerateSegmentPoints() {
  return [
    {
      id: 101,
      durationSinceStart: 0,
      distanceInMeterSinceStart: 0,
      pointLongLat: { coordinates: [8.5, 47.5] },
    },
  ];
}

function generatedJtsPointShapeSegmentPoints() {
  const generatedJtsCoordinates = () => [{ x: undefined }, { y: undefined }] as unknown as number[];
  return [
    {
      id: 101,
      durationSinceStart: 0,
      distanceInMeterSinceStart: 0,
      pointLongLat: { coordinates: generatedJtsCoordinates() },
    },
    {
      id: 102,
      durationSinceStart: 60,
      distanceInMeterSinceStart: 1000,
      pointLongLat: { coordinates: generatedJtsCoordinates() },
    },
  ];
}

function loopMeasureServiceResult() {
  return {
    crossings: {
      1: {
        gpsTrack: {
          id: 1,
          indexedFile: { name: 'Track 1' },
          startDate: new Date('2026-05-27T10:00:00Z'),
          activityType: 'BICYCLE',
        },
        crossings: [
          {
            gpsTrackDataPoint: {
              id: 101,
              durationSinceStart: 0,
              distanceInMeterSinceStart: 0,
              pointLongLat: { coordinates: [8.5, 47.5] },
            },
            triggerPoint: { name: 'A', coordinate: { x: 8.5, y: 47.5 } },
          },
          {
            gpsTrackDataPoint: {
              id: 102,
              durationSinceStart: 60,
              distanceInMeterSinceStart: 500,
              pointLongLat: { coordinates: [8.51, 47.51] },
            },
            timeInSecSinceLastTriggerPoint: 60,
            distanceInMeterSinceLastTriggerPoint: 500,
            triggerPoint: { name: 'A', coordinate: { x: 8.5, y: 47.5 } },
          },
          {
            gpsTrackDataPoint: {
              id: 103,
              durationSinceStart: 120,
              distanceInMeterSinceStart: 1000,
              pointLongLat: { coordinates: [8.52, 47.52] },
            },
            timeInSecSinceLastTriggerPoint: 60,
            distanceInMeterSinceLastTriggerPoint: 500,
            triggerPoint: { name: 'B', coordinate: { x: 8.52, y: 47.52 } },
          },
        ],
      },
    },
    segmentsStats: [
      { point1: 'A', point2: 'A', label: 'A - A' },
      { point1: 'A', point2: 'B', label: 'A - B' },
    ],
  };
}

async function flush() {
  await nextTick();
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

describe('measure preview stale responses', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    fetchTrackSubTrackDetailsMock.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('does not let a stale segment comparison load repopulate a newer empty selection', async () => {
    let resolveFirst!: (value: unknown) => void;
    fetchTrackSubTrackDetailsMock.mockReturnValueOnce(new Promise((resolve) => (resolveFirst = resolve)));

    const wrapper = mount(SegmentCompare, {
      props: {
        measureServiceResult: measureServiceResult(),
        selectedTrackIds: new Set([1]),
        selectedSegment: segment,
      },
      global: {
        stubs: {
          MiniMap: MiniMapStub,
          ComparisonChart: ComparisonChartStub,
          RacerCard: RacerCardStub,
        },
      },
    });

    await vi.runOnlyPendingTimersAsync();
    expect(fetchTrackSubTrackDetailsMock).toHaveBeenCalledOnce();

    await wrapper.setProps({ selectedTrackIds: new Set() });
    resolveFirst(segmentPoints());
    await flush();

    expect((wrapper.vm as unknown as { loadedData: unknown[] }).loadedData).toEqual([]);
    expect(wrapper.find('[data-test="racer-card"]').exists()).toBe(false);
  });

  it('does not let a stale virtual race preview repopulate a newer empty selection', async () => {
    let resolveFirst!: (value: unknown) => void;
    fetchTrackSubTrackDetailsMock.mockReturnValueOnce(new Promise((resolve) => (resolveFirst = resolve)));

    const wrapper = mount(VirtualRace, {
      props: {
        measureServiceResult: measureServiceResult(),
        selectedTrackIds: new Set([1]),
        initialSegment: segment,
      },
      global: {
        stubs: {
          MiniMap: MiniMapStub,
          RacerCard: RacerCardStub,
          Button: ButtonStub,
          MtlSlider: MtlSliderStub,
        },
      },
    });
    await flush();
    expect(fetchTrackSubTrackDetailsMock).toHaveBeenCalledOnce();

    await wrapper.setProps({ selectedTrackIds: new Set() });
    resolveFirst(segmentPoints());
    await flush();

    const vm = wrapper.vm as unknown as { matchingCrossings: unknown; raceGeoJson: unknown };
    expect(vm.matchingCrossings).toBeNull();
    expect(vm.raceGeoJson).toBeNull();
    expect(wrapper.find('[data-test="racer-card"]').exists()).toBe(false);
  });

  it('shows a comparison empty state when every sub-track slice is degenerate', async () => {
    fetchTrackSubTrackDetailsMock.mockResolvedValueOnce(degenerateSegmentPoints());

    const wrapper = mount(SegmentCompare, {
      props: {
        measureServiceResult: degenerateMeasureServiceResult(),
        selectedTrackIds: new Set([1]),
        selectedSegment: segment,
      },
      global: {
        stubs: {
          MiniMap: MiniMapStub,
          ComparisonChart: ComparisonChartStub,
          RacerCard: RacerCardStub,
        },
      },
    });

    await vi.runOnlyPendingTimersAsync();
    await flush();

    expect(wrapper.text()).toContain('Selected tracks do not contain enough segment data to compare.');
    expect(wrapper.text()).not.toContain('Preparing comparison');
    expect(wrapper.find('[data-test="racer-card"]').exists()).toBe(false);
  });

  it('does not build a segment comparison from generated JTS coordinate object shapes', async () => {
    fetchTrackSubTrackDetailsMock.mockResolvedValueOnce(generatedJtsPointShapeSegmentPoints());

    const wrapper = mount(SegmentCompare, {
      props: {
        measureServiceResult: generatedJtsPointShapeMeasureServiceResult(),
        selectedTrackIds: new Set([1]),
        selectedSegment: segment,
      },
      global: {
        stubs: {
          MiniMap: MiniMapStub,
          ComparisonChart: ComparisonChartStub,
          RacerCard: RacerCardStub,
        },
      },
    });

    await vi.runOnlyPendingTimersAsync();
    await flush();

    expect((wrapper.vm as unknown as { loadedData: unknown[] }).loadedData).toEqual([]);
    expect(wrapper.text()).toContain('Selected tracks do not contain enough segment data to compare.');
    expect(wrapper.find('[data-test="racer-card"]').exists()).toBe(false);
  });

  it('shows a race empty state when every racer slice is degenerate', async () => {
    fetchTrackSubTrackDetailsMock.mockResolvedValueOnce(degenerateSegmentPoints());

    const wrapper = mount(VirtualRace, {
      props: {
        measureServiceResult: degenerateMeasureServiceResult(),
        selectedTrackIds: new Set([1]),
        initialSegment: segment,
      },
      global: {
        stubs: {
          MiniMap: MiniMapStub,
          RacerCard: RacerCardStub,
          Button: ButtonStub,
          MtlSlider: MtlSliderStub,
        },
      },
    });

    await flush();

    expect(wrapper.text()).toContain('Selected tracks do not contain enough segment data to race.');
    expect(wrapper.find('[data-test="racer-card"]').exists()).toBe(false);
  });

  it('does not build a race preview from generated JTS coordinate object shapes', async () => {
    fetchTrackSubTrackDetailsMock.mockResolvedValueOnce(generatedJtsPointShapeSegmentPoints());

    const wrapper = mount(VirtualRace, {
      props: {
        measureServiceResult: generatedJtsPointShapeMeasureServiceResult(),
        selectedTrackIds: new Set([1]),
        initialSegment: segment,
      },
      global: {
        stubs: {
          MiniMap: MiniMapStub,
          RacerCard: RacerCardStub,
          Button: ButtonStub,
          MtlSlider: MtlSliderStub,
        },
      },
    });

    await flush();

    const vm = wrapper.vm as unknown as { raceGeoJson: unknown; matchingCrossings: unknown };
    expect(vm.raceGeoJson).toBeNull();
    expect(vm.matchingCrossings).toBeNull();
    expect(wrapper.text()).toContain('Selected tracks do not contain enough segment data to race.');
    expect(wrapper.find('[data-test="racer-card"]').exists()).toBe(false);
  });

  it('labels consecutive visits as A1 to A2 instead of A2 to A2', async () => {
    fetchTrackSubTrackDetailsMock.mockResolvedValue(segmentPoints());

    const wrapper = mount(VirtualRace, {
      props: {
        measureServiceResult: loopMeasureServiceResult(),
        consolidateVisits: false,
        selectedTrackIds: new Set([1]),
      },
      global: {
        stubs: {
          MiniMap: MiniMapStub,
          RacerCard: RacerCardStub,
          Button: ButtonStub,
          MtlSlider: MtlSliderStub,
        },
      },
    });

    await flush();

    expect(wrapper.text()).toContain('A1 - A2');
    expect(wrapper.text()).not.toContain('A2 - A2');
  });
});
