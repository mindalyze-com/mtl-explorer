import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import SegmentTimeDifference from '@/components/measure/SegmentTimeDifference.vue';

describe('segment time comparison wording', () => {
  it.each([
    [-3, '3 sec faster', 'faster'],
    [7, '7 sec slower', 'slower'],
    [-105.4, '105.40 sec faster', 'faster'],
    [0, 'Same time', 'neutral'],
    [-0.001, 'Same time', 'neutral'],
    [null, 'Unavailable', 'neutral'],
  ] as const)('describes server difference %s without ambiguous signs', (seconds, text, tone) => {
    const wrapper = mount(SegmentTimeDifference, { props: { seconds } });
    expect(wrapper.text()).toBe(text);
    expect(wrapper.classes()).toContain(`segment-time-difference--${tone}`);
    wrapper.unmount();
  });
  it('distinguishes the reference and pending comparisons', async () => {
    const wrapper = mount(SegmentTimeDifference, { props: { seconds: 0, reference: true } });
    expect(wrapper.text()).toBe('Reference');
    await wrapper.setProps({ reference: false, seconds: undefined, loading: true });
    expect(wrapper.text()).toBe('Loading…');
    wrapper.unmount();
  });
});
