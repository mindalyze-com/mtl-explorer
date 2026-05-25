import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TrackDetailQuality from '@/components/trackdetails/TrackDetailQuality.vue';
import { updateTrackStatisticsExclusion } from '@/utils/ServiceHelper';
import {
  StatisticsExclusionUpdateRequestHighlightExclusionReasonEnum as ExclusionReasonEnum,
  type GpsTrack,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

vi.mock('@/utils/ServiceHelper', () => ({
  updateTrackStatisticsExclusion: vi.fn(),
}));

const updateTrackStatisticsExclusionMock = vi.mocked(updateTrackStatisticsExclusion);

const SelectStub = defineComponent({
  name: 'Select',
  props: {
    modelValue: { type: String, default: null },
    options: { type: Array, default: () => [] },
    optionLabel: { type: String, default: '' },
    optionValue: { type: String, default: '' },
    disabled: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  methods: {
    optionLabelOf(option: Record<string, unknown>) {
      return this.optionLabel ? option[this.optionLabel] : option;
    },
    optionValueOf(option: Record<string, unknown>) {
      return this.optionValue ? option[this.optionValue] : option;
    },
    onChange(event: Event) {
      const value = (event.target as HTMLSelectElement).value;
      this.$emit('update:modelValue', value || null);
    },
  },
  template: `
    <select v-bind="$attrs" :disabled="disabled" :value="modelValue ?? ''" @change="onChange">
      <option
        v-for="option in options"
        :key="String(optionValueOf(option) ?? '')"
        :value="optionValueOf(option) ?? ''"
      >
        {{ optionLabelOf(option) }}
      </option>
    </select>
  `,
});

const flush = async () => {
  await flushPromises();
  await nextTick();
};

function track(overrides: Partial<GpsTrack> = {}): GpsTrack {
  return {
    id: 51,
    trackName: 'Noisy ride',
    loadStatus: 'SUCCESS',
    duplicateStatus: 'UNIQUE',
    activityType: 'BICYCLE',
    numberOfTrackPoints: 120,
    avgDistanceBetweenPoints: 8,
    medianDistanceBetweenPoints: 7,
    maxDistanceBetweenPoints: 40,
    ...overrides,
  } as GpsTrack;
}

function mountQuality(gpsTrack = track()) {
  return mount(TrackDetailQuality, {
    props: { gpsTrack },
    global: {
      directives: { tooltip: {} },
      provide: { toast: { add: vi.fn() } },
      stubs: {
        ActivityTypeBadge: true,
        Select: SelectStub,
      },
    },
  });
}

describe('TrackDetailQuality statistics curation', () => {
  beforeEach(() => {
    updateTrackStatisticsExclusionMock.mockReset();
  });

  it('edits highlight and statistics exclusion reasons immediately', async () => {
    const gpsTrack = track();
    updateTrackStatisticsExclusionMock
      .mockResolvedValueOnce({
        ...gpsTrack,
        highlightExclusionReason: ExclusionReasonEnum.GpsNoise,
      } as GpsTrack)
      .mockResolvedValueOnce({
        ...gpsTrack,
        highlightExclusionReason: ExclusionReasonEnum.GpsNoise,
        statisticsExclusionReason: ExclusionReasonEnum.WrongActivity,
      } as GpsTrack);

    const wrapper = mountQuality(gpsTrack);

    await wrapper.find('[data-test="highlight-exclusion-select"]').setValue(ExclusionReasonEnum.GpsNoise);
    await flush();

    expect(updateTrackStatisticsExclusionMock).toHaveBeenCalledWith(51, {
      highlightExclusionReason: ExclusionReasonEnum.GpsNoise,
      statisticsExclusionReason: undefined,
    });
    expect(wrapper.find('[data-test="curation-note"]').text()).toContain('Excluded from highlights only.');

    await wrapper.find('[data-test="statistics-exclusion-select"]').setValue(ExclusionReasonEnum.WrongActivity);
    await flush();

    expect(updateTrackStatisticsExclusionMock).toHaveBeenLastCalledWith(51, {
      highlightExclusionReason: ExclusionReasonEnum.GpsNoise,
      statisticsExclusionReason: ExclusionReasonEnum.WrongActivity,
    });
    expect(wrapper.find('[data-test="curation-note"]').text()).toContain('Excluded from statistics and highlights.');
    expect(wrapper.emitted('track-updated')).toHaveLength(2);
  });

  it('treats Included as the nullable exclusion state', async () => {
    const gpsTrack = track({
      highlightExclusionReason: ExclusionReasonEnum.GpsNoise,
      statisticsExclusionReason: ExclusionReasonEnum.ImportArtifact,
    });
    updateTrackStatisticsExclusionMock.mockResolvedValueOnce({
      ...gpsTrack,
      highlightExclusionReason: undefined,
    } as GpsTrack);

    const wrapper = mountQuality(gpsTrack);

    expect((wrapper.find('[data-test="highlight-exclusion-select"]').element as HTMLSelectElement).value).toBe(
      ExclusionReasonEnum.GpsNoise
    );

    await wrapper.find('[data-test="highlight-exclusion-select"]').setValue('');
    await flush();

    expect(updateTrackStatisticsExclusionMock).toHaveBeenCalledWith(51, {
      highlightExclusionReason: undefined,
      statisticsExclusionReason: ExclusionReasonEnum.ImportArtifact,
    });
  });
});
