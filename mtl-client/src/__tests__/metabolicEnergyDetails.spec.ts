import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import MetabolicEnergyDetails from '@/components/trackdetails/MetabolicEnergyDetails.vue';
import type { MetabolicEnergy } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

const estimate: MetabolicEnergy = {
  activeKcal: 240,
  totalKcal: 320,
  coverage: 'COMPLETE',
  coveredSeconds: 3600,
  uncoveredSeconds: 0,
  bodyWeightKg: 75,
  modelVersion: '2026-09-27-v1',
  methodDescription: 'Seated passenger estimate. Aircraft energy is excluded.',
};

describe('Metabolic calorie details', () => {
  it('explains physically inconsistent water estimates', () => {
    const wrapper = mount(MetabolicEnergyDetails, {
      props: {
        energy: {
          ...estimate,
          activeKcal: undefined,
          totalKcal: undefined,
          coverage: 'UNAVAILABLE',
          exclusionReasons: ['INCONSISTENT_ENERGY_ESTIMATES'],
        },
      },
    });
    expect(wrapper.get('[data-test="calorie-unavailable"]').text()).toContain('unavailable');
    expect(wrapper.text()).toContain('estimated work is higher than their calorie estimate');
  });
  it('shows total calories and explains the server-selected model', () => {
    const wrapper = mount(MetabolicEnergyDetails, { props: { energy: estimate } });
    expect(wrapper.get('[data-test="total-calories"]').text()).toContain('320');
    expect(wrapper.text()).toContain('Aircraft energy is excluded');
    expect(wrapper.text()).toContain('Including resting energy');
  });
  it('explains time excluded from partial estimates', () => {
    const wrapper = mount(MetabolicEnergyDetails, {
      props: { energy: { ...estimate, coverage: 'PARTIAL', uncoveredSeconds: 600 } },
    });
    expect(wrapper.text()).toContain('10m 00s excluded');
  });
  it('distinguishes missing calculation from an unavailable estimate', () => {
    expect(mount(MetabolicEnergyDetails, { props: { pending: true } }).text()).toContain('pending');
    const wrapper = mount(MetabolicEnergyDetails, {
      props: { energy: { ...estimate, activeKcal: undefined, totalKcal: undefined, coverage: 'UNAVAILABLE' } },
    });
    expect(wrapper.text()).toContain('unavailable');
    expect(wrapper.find('[data-test="active-calories"]').exists()).toBe(false);
  });
  it('reports unknown duration even when known excluded seconds are zero', () => {
    const wrapper = mount(MetabolicEnergyDetails, {
      props: {
        energy: {
          ...estimate,
          coverage: 'PARTIAL',
          uncoveredSeconds: 0,
          unknownDuration: true,
          exclusionReasons: ['MISSING_TIMING'],
        },
      },
    });
    expect(wrapper.text()).toContain('unknown duration');
    expect(wrapper.text()).toContain('timestamps are missing or invalid');
  });
  it('explains recording gaps and sparse sampling separately from model limits', () => {
    const wrapper = mount(MetabolicEnergyDetails, {
      props: {
        energy: {
          ...estimate,
          coverage: 'UNAVAILABLE',
          activeKcal: undefined,
          uncoveredSeconds: 900,
          recordingGapSeconds: 600,
          sparseSamplingSeconds: 300,
          exclusionReasons: ['RECORDING_GAP', 'SPARSE_SAMPLING', 'GRADE_OUTSIDE_MODEL'],
        },
      },
    });
    expect(wrapper.text()).toContain('Recording gaps: 10m 00s');
    expect(wrapper.text()).toContain('Sparse sampling: 5m 00s');
    expect(wrapper.text()).toContain('−45% to +45%');
  });
});
