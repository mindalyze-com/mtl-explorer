import { enableAutoUnmount, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import NearbyTrackList from '@/components/map/NearbyTrackList.vue';
import TrackChoiceRow from '@/components/map/TrackChoiceRow.vue';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';

enableAutoUnmount(afterEach);
const tracks = [
  {
    id: 42,
    name: 'Morning ride',
    displayName: 'Morning ride',
    description: 'Along the river',
    activityType: 'BICYCLE',
    date: '18/08/2026',
    distanceMeters: 18.4,
    matchedMediaCount: 3,
  },
  {
    id: 43,
    name: 'Evening ride',
    displayName: 'Evening ride',
    description: '',
    activityType: 'BICYCLE',
    date: '17/08/2026',
    distanceMeters: 1240,
    matchedMediaCount: 0,
  },
];
const mountList = (showMediaStatus = true) =>
  mount(NearbyTrackList, {
    props: { tracks, showMediaStatus },
    global: { stubs: { TrackShapePreview: true } },
  });

describe('NearbyTrackList', () => {
  it('reuses the compact GPS row with activity/date and matched photos, without proximity distance', () => {
    const wrapper = mountList();
    expect(wrapper.findAllComponents(TrackChoiceRow)).toHaveLength(2);
    expect(wrapper.findAllComponents(ActivityTypeBadge).map((badge) => badge.props('type'))).toEqual([
      'BICYCLE',
      'BICYCLE',
    ]);
    expect(wrapper.text()).toContain('3 photos');
    expect(wrapper.text()).toContain('No matched photos');
    expect(wrapper.text()).not.toContain('away');
    expect(wrapper.text()).not.toContain('18 m');
    expect(wrapper.text()).not.toContain('1.2 km');
    expect(wrapper.get('button').attributes('aria-label')).toContain('Bicycle');
    expect(wrapper.get('button').attributes('aria-label')).toContain('3 photos');
    expect(wrapper.get('button').attributes('aria-label')).toContain('Along the river');
    expect(wrapper.get('button').attributes('aria-label')).not.toContain('away');
    expect(wrapper.get('button').attributes('aria-pressed')).toBeUndefined();
    expect(wrapper.get('.track-choice-row__metadata').text()).toContain('18/08/2026');
    expect(wrapper.find('.mtl-track-pick__distance').exists()).toBe(false);
    const row = wrapper.get('button');
    expect(row.element.firstElementChild).toBe(row.get('.track-choice-row__icon').element);
    const sketch = row.get('.track-choice-row__shape');
    expect(sketch.element.previousElementSibling).toBe(row.get('.track-choice-row__media').element);
    expect(sketch.element.nextElementSibling).toBe(row.get('.track-choice-row__chevron').element);
  });

  it('keeps positive counts visible while the non-photo picker suppresses empty-count copy', () => {
    const wrapper = mountList(false);
    expect(wrapper.text()).toContain('3 photos');
    expect(wrapper.text()).not.toContain('No matched photos');
    expect(wrapper.text()).not.toContain('away');
  });

  it('does not turn unknown or invalid counts into a false zero-photo status', () => {
    const wrapper = mount(NearbyTrackList, {
      props: {
        tracks: [
          { ...tracks[0], matchedMediaCount: undefined },
          { ...tracks[1], matchedMediaCount: Number.NaN },
        ],
        showMediaStatus: true,
      },
      global: { stubs: { TrackShapePreview: true } },
    });
    expect(wrapper.find('.track-choice-row__media').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('No matched photos');
  });

  it('emits the original selected activity id through its native button', async () => {
    const wrapper = mountList();
    await wrapper.get('button').trigger('click');
    expect(wrapper.emitted('select')).toEqual([[42]]);
    await wrapper.findAll('button')[1].trigger('click');
    expect(wrapper.emitted('select')).toEqual([[42], [43]]);
  });

  it('forwards canonical track facts without confusing total length with proximity distance', () => {
    const wrapper = mount(NearbyTrackList, {
      props: {
        tracks: [{ ...tracks[0], recordedAt: Date.UTC(2026, 7, 18, 12), lengthMeters: 48_700, ascentMeters: 420 }],
      },
      global: { stubs: { TrackShapePreview: true } },
    });
    expect(wrapper.getComponent(TrackChoiceRow).props()).toMatchObject({
      recordedAt: Date.UTC(2026, 7, 18, 12),
      lengthMeters: 48_700,
      ascentMeters: 420,
    });
    expect(wrapper.find('[data-fact="length"]').exists()).toBe(true);
    expect(wrapper.find('[data-fact="ascent"]').exists()).toBe(true);
    expect(wrapper.text()).not.toContain('18.4');
  });
});
