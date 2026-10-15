import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import type { SegmentCardiacCostSummary } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import SegmentCardiacCost from '@/components/measure/SegmentCardiacCost.vue';

const summary: SegmentCardiacCostSummary = {
  available: true,
  beatsPerKj: 10,
  wattsPerBpm: 250 / 150,
  estimatedHeartbeats: 1500,
  mechanicalEnergyKj: 150,
  coveredMovingSeconds: 600,
  movingSeconds: 750,
  coveragePercent: 80,
  minimumEnergyKj: 1,
};

describe('segment cardiac cost summary', () => {
  it('shows the server ratio, matched-time coverage and calculation details', () => {
    const wrapper = mount(SegmentCardiacCost, { props: { summary } });
    expect(wrapper.text()).toContain('10.0 beats/kJ');
    expect(wrapper.get('[data-test="watts-per-bpm"]').text()).toBe('1.67 W/bpm');
    expect(wrapper.get('[data-test="watts-per-bpm"]').attributes('title')).toContain('average heart rate while moving');
    expect(wrapper.get('[data-test="watts-per-bpm"]').attributes('title')).toContain(
      'Higher W/bpm is the same as lower beats/kJ'
    );
    expect(wrapper.get('[data-test="cardiac-cost-coverage"]').text()).toContain('80.0% paired coverage');
    expect(wrapper.text()).toContain('00:10:00 of 00:12:30 moving');
    expect(wrapper.text()).toContain('150.00 kJ');
    expect(wrapper.text()).toContain('including coasting');
    expect(wrapper.text()).toContain('not a fitness or efficiency score');
    wrapper.unmount();
  });

  it('explains insufficient energy without displaying a numeric ratio', () => {
    const wrapper = mount(SegmentCardiacCost, {
      props: {
        summary: {
          ...summary,
          available: false,
          beatsPerKj: undefined,
          unavailableReason: 'INSUFFICIENT_ENERGY' as SegmentCardiacCostSummary['unavailableReason'],
          mechanicalEnergyKj: 0.01,
        },
      },
    });
    expect(wrapper.text()).toContain('Unavailable: at least 1.0 kJ');
    expect(wrapper.find('strong').exists()).toBe(false);
    expect(wrapper.find('[data-test="watts-per-bpm"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('80.0% paired coverage');
    wrapper.unmount();
  });

  it('displays the server watts-per-bpm value without deriving it from the other unit', () => {
    const wrapper = mount(SegmentCardiacCost, { props: { summary: { ...summary, wattsPerBpm: 2.345 } } });
    expect(wrapper.get('[data-test="watts-per-bpm"]').text()).toBe('2.35 W/bpm');
    wrapper.unmount();
  });

  it('does not invent a watts-per-bpm value when the server omits it', () => {
    const wrapper = mount(SegmentCardiacCost, { props: { summary: { ...summary, wattsPerBpm: undefined } } });
    expect(wrapper.find('[data-test="watts-per-bpm"]').exists()).toBe(false);
    expect(wrapper.get('strong').text()).toBe('10.0 beats/kJ');
    wrapper.unmount();
  });

  it.each([
    ['NO_MATCHED_DATA', 'no overlapping heart-rate and energy coverage'],
    ['NO_MOVING_DATA', 'no usable moving intervals'],
    ['MISSING_TIMESTAMPS', 'segment timestamps are missing'],
    ['MOTORIZED_ACTIVITY', 'not applicable to motorized activities'],
  ])('explains %s', (unavailableReason, explanation) => {
    const wrapper = mount(SegmentCardiacCost, {
      props: {
        summary: {
          ...summary,
          available: false,
          beatsPerKj: undefined,
          unavailableReason: unavailableReason as SegmentCardiacCostSummary['unavailableReason'],
        },
      },
    });
    expect(wrapper.text()).toContain(explanation);
    wrapper.unmount();
  });

  it('distinguishes loading from errors and offers retry without triggering the track card', async () => {
    const wrapper = mount(SegmentCardiacCost, { props: { loading: true } });
    expect(wrapper.text()).toContain('Calculating');
    await wrapper.setProps({ loading: false, loadFailed: true });
    expect(wrapper.text()).toContain('Could not load');
    await wrapper.get('button').trigger('click');
    expect(wrapper.emitted('retry')).toHaveLength(1);
    wrapper.unmount();
  });
});
