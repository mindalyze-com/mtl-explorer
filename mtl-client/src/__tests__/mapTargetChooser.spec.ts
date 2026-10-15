import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import TrackShapePreview from '@/components/ui/TrackShapePreview.vue';
import TrackChoiceRow from '@/components/map/TrackChoiceRow.vue';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';

vi.mock('@/components/ui/TrackShapePreview.vue', () => ({
  default: {
    props: ['trackId', 'width', 'height', 'padding', 'lineWidth', 'lineColor'],
    template: '<div />',
  },
}));
import MapTargetChooser from '@/components/map/MapTargetChooser.vue';
import { mapTargetKey, type MapTargetCandidate } from '@/components/map/mapInteractionDecision';
import { MAP_TARGET_OVERVIEW_ROW_BUDGET, rankMapCategoryCandidates } from '@/components/map/mapTargetCategories';
import type { MapInteractionState } from '@/components/map/mapInteractionCoordinator';
import { TrackProcessingStatusDtoStateEnum } from 'x8ing-mtl-api-typescript-fetch';

enableAutoUnmount(afterEach);
const track: MapTargetCandidate = {
  id: '7',
  kind: 'track',
  source: 'tracks',
  sourceLabel: 'Your tracks',
  label: 'Morning walk',
  detail: '12 October 2026',
  trackId: 7,
};
const photo: MapTargetCandidate = {
  id: 'cluster:4',
  kind: 'photo',
  source: 'media',
  sourceLabel: 'Photos',
  label: 'Photo cluster',
  count: 20,
  clusterId: 4,
};
const poi: MapTargetCandidate = {
  id: 'spring:1',
  kind: 'poi',
  source: 'protomaps',
  sourceLabel: 'Points of interest',
  label: 'Village spring',
  detail: 'Drinking water',
  coordinates: [8, 47],
  properties: {},
};
function trail(id: number, source = 'waymarked:hiking'): MapTargetCandidate {
  return {
    id: String(id),
    kind: 'overlay',
    source,
    sourceLabel: source === 'waymarked:cycling' ? 'Waymarked Trails · Cycling' : 'Waymarked Trails · Hiking',
    label: `Route ${id}`,
    route: { name: `Route ${id}`, type: 'Hiking', icon: 'bi-map' },
  };
}
function state(candidates: MapTargetCandidate[], selected: MapTargetCandidate | null = null): MapInteractionState {
  return {
    id: 1,
    context: {
      point: { x: 8, y: 47 },
      lngLat: { lng: 8, lat: 47 },
      inputKind: 'pointer',
      trackSetRevision: 1,
      mediaScope: 'all',
      eligibility: {},
    },
    candidates,
    sources: [],
    selected,
    presentation: 'chooser',
    processing: null,
  };
}
const tabSelector = (key: string) => `[data-test="map-category-tab"][data-category-key="${key}"]`;
const moreSelector = (key: string) => `[data-test="map-category-more"][data-category-key="${key}"]`;
const groupSelector = (key: string) => `[data-test="map-target-group"][data-category-key="${key}"]`;
const targetSelector = (candidate: MapTargetCandidate) => `[data-target-key='${mapTargetKey(candidate)}']`;

function overviewRows(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll(
    '[data-test="map-target-row"], [data-test="map-category-more"], [data-test="map-category-status"]'
  );
}

describe('map category chooser', () => {
  it('keeps fixed navigation and remembered pages in the chooser across selected-content transitions', async () => {
    const routes = Array.from({ length: 40 }, (_, index) => trail(index));
    const selected = routes[23];
    const current = state([...routes, photo], selected);
    const wrapper = mount(MapTargetChooser, {
      props: { state: current },
      attachTo: document.body,
    });
    const model = wrapper.vm;
    expect(wrapper.findAll('.map-target-navigation')).toHaveLength(1);
    expect(wrapper.get('.map-target-navigation').element.parentElement).toBe(wrapper.element);
    expect(
      wrapper.get('[data-test="map-target-results"]').element.contains(wrapper.get('.map-target-navigation').element)
    ).toBe(false);
    expect(wrapper.find('teleport').exists()).toBe(false);
    expect(wrapper.get('[data-test="map-category-all"]').attributes('aria-pressed')).toBe('true');
    await wrapper.get('[data-test="map-category-all"]').trigger('click');
    await wrapper.get(moreSelector('waymarked')).trigger('click');
    await wrapper.get(tabSelector('waymarked')).trigger('click');
    await wrapper.get('[aria-label="Next page"]').trigger('click');
    expect(wrapper.get('[data-test="map-category-page-range"]').text()).toBe('21–40 of 40');
    await wrapper.get(targetSelector(selected)).trigger('click');
    expect(wrapper.emitted('select')).toEqual([[mapTargetKey(selected)]]);
    await wrapper.setProps({ state: { ...current, presentation: 'target' } });
    await wrapper.setProps({ state: { ...current, presentation: 'chooser' } });
    expect(wrapper.get('[data-test="map-category-page-range"]').text()).toBe('21–40 of 40');
    expect(wrapper.props('state').selected).toEqual(selected);
    expect(wrapper.vm).toBe(model);
    await wrapper.get('[data-test="map-category-all"]').trigger('click');
    expect(wrapper.get(targetSelector(photo)).isVisible()).toBe(true);
    await wrapper.get(tabSelector('waymarked')).trigger('click');
    expect(wrapper.get('[data-test="map-category-page-range"]').text()).toBe('21–40 of 40');
    await wrapper.setProps({ state: { ...current, id: 2, candidates: [photo], selected: null } });
    expect(wrapper.get('[data-test="map-category-all"]').attributes('aria-pressed')).toBe('true');
    expect(wrapper.attributes('data-sheet-content-key')).toBe(JSON.stringify([2, null, 0]));
  });

  it('resets only the results scroll position when changing categories and pages', async () => {
    const wrapper = mount(MapTargetChooser, {
      props: { state: state([...Array.from({ length: 40 }, (_, index) => trail(index)), photo]) },
      attachTo: document.body,
    });
    const results = wrapper.get('[data-test="map-target-results"]').element as HTMLElement;
    const scrollTo = vi.fn();
    results.scrollTo = scrollTo;
    const outerScroll = vi.fn();
    (wrapper.element as HTMLElement).scrollIntoView = outerScroll;
    const focus = vi.spyOn(HTMLElement.prototype, 'focus');
    try {
      await wrapper.get(moreSelector('waymarked')).trigger('click');
      expect(scrollTo).toHaveBeenLastCalledWith({ top: 0 });
      expect(document.activeElement).toBe(wrapper.get(tabSelector('waymarked')).element);
      expect(focus).toHaveBeenLastCalledWith({ preventScroll: true });
      await wrapper.get('[aria-label="Next page"]').trigger('click');
      expect(scrollTo).toHaveBeenCalledTimes(2);
      expect(document.activeElement).toBe(wrapper.get('[data-test="map-target-row"]').element);
      expect(focus).toHaveBeenLastCalledWith({ preventScroll: true });
      await wrapper.get('[data-test="map-category-all"]').trigger('click');
      expect(scrollTo).toHaveBeenCalledTimes(3);
      expect(document.activeElement).toBe(wrapper.get('[data-test="map-category-all"]').element);
      expect(focus).toHaveBeenLastCalledWith({ preventScroll: true });
      expect(outerScroll).not.toHaveBeenCalled();
      expect(wrapper.get('[data-test="map-target-results"]').element).toBe(results);
    } finally {
      focus.mockRestore();
    }
  });

  it('selects every small-set target directly from All without a category step', async () => {
    const wrapper = mount(MapTargetChooser, { props: { state: state([track, photo, poi, trail(1)]) } });
    expect(wrapper.findAll('[data-test="map-target-row"]')).toHaveLength(4);
    expect(wrapper.find('[data-test="map-category-more"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="map-category-all"]').text()).toBe('All 4');
    expect(wrapper.get('[data-test="map-category-all"]').attributes('aria-pressed')).toBe('true');
    await wrapper.get(targetSelector(photo)).trigger('click');
    expect(wrapper.emitted('select')).toEqual([[mapTargetKey(photo)]]);
    expect(wrapper.findComponent({ name: 'BottomSheet' }).exists()).toBe(false);
  });

  it('hides completed empty categories while keeping available and failed categories usable', async () => {
    const current = state([photo]);
    current.sources = [
      { id: 'tracks', label: 'Your tracks', kind: 'tracks', status: 'complete' },
      { id: 'routes', label: 'Swiss routes', kind: 'overlay', status: 'failed' },
    ];
    const wrapper = mount(MapTargetChooser, { props: { state: current }, attachTo: document.body });
    expect(wrapper.get(tabSelector('photo')).get('.map-category-count').classes()).toContain(
      'map-category-count--available'
    );
    expect(wrapper.find(tabSelector('track')).exists()).toBe(false);
    expect(wrapper.find(groupSelector('track')).exists()).toBe(false);
    expect(wrapper.get(tabSelector('swiss')).get('.map-category-count').classes()).toContain(
      'map-category-count--failure'
    );
    expect(wrapper.get(tabSelector('swiss')).get('.map-category-count').text()).toBe('!');
    for (const key of ['photo', 'swiss']) expect(wrapper.get(tabSelector(key)).attributes('disabled')).toBeUndefined();
    await wrapper.get(tabSelector('swiss')).trigger('click');
    expect(wrapper.get(tabSelector('swiss')).attributes('aria-pressed')).toBe('true');
    expect(wrapper.get(groupSelector('swiss')).text()).toContain('unavailable');
  });

  it('labels photo collections explicitly and avoids repeating an existing photo-count title', () => {
    const cluster: MapTargetCandidate = { ...photo, label: '20 photos', detail: 'Photo cluster' };
    const single: MapTargetCandidate = { ...photo, id: 'single', label: 'Photo', count: 1, detail: 'At this location' };
    const wrapper = mount(MapTargetChooser, { props: { state: state([cluster, single]) } });
    expect(wrapper.get(groupSelector('photo')).get('.map-target-group__count').text()).toBe('2 collections');
    expect(wrapper.get('[data-test="map-category-all"]').get('.map-category-count').text()).toBe('2');
    expect(wrapper.get(targetSelector(cluster)).get('.map-target-row__context').text()).toBe('Photo cluster');
    expect(wrapper.get(targetSelector(cluster)).attributes('aria-label')).toBe('20 photos, Photo cluster');
    expect(wrapper.get(targetSelector(single)).get('.map-target-row__context').text()).toContain('1 photo');
  });

  it('shows cluster and captured map-view choices directly without summing overlapping photos', async () => {
    const cluster: MapTargetCandidate = {
      ...photo,
      id: 'cluster',
      label: 'This cluster',
      sourceLabel: 'Photos',
      detail: '',
      count: 5,
      collectionScope: 'primary',
    };
    const viewport: MapTargetCandidate = {
      ...photo,
      id: 'viewport',
      label: 'Map view at tap',
      sourceLabel: 'Photos',
      detail: '',
      count: 20,
      collectionScope: 'viewport',
    };
    const wrapper = mount(MapTargetChooser, { props: { state: state([track, cluster, viewport]) } });
    expect(wrapper.get(tabSelector('photo')).attributes('aria-label')).toBe('Photos, 2, 2 collections');
    const group = wrapper.get(groupSelector('photo'));
    expect(group.get('.map-target-group__count').text()).toBe('2 collections');
    expect(group.findAll('[data-test="map-target-row"]')).toHaveLength(2);
    expect(wrapper.get(targetSelector(cluster)).get('.map-target-row__context').text()).toBe('5 photos');
    expect(wrapper.get(targetSelector(viewport)).get('.map-target-row__context').text()).toBe('20 photos');
    expect(wrapper.get(targetSelector(viewport)).get('.map-target-row__icon').classes()).toContain('bi-map');
    await wrapper.get(targetSelector(viewport)).trigger('click');
    expect(wrapper.emitted('select')).toEqual([[mapTargetKey(viewport)]]);
  });

  it('reuses decorative track sketches only for GPS rows, without changing the row selection', async () => {
    const wrapper = mount(MapTargetChooser, { props: { state: state([track, photo, poi, trail(1)]) } });
    const previews = wrapper.findAllComponents(TrackShapePreview);
    expect(previews).toHaveLength(1);
    expect(previews[0].props()).toMatchObject({ trackId: 7, width: 48, height: 32, lineWidth: 1.75 });
    expect(previews[0].attributes('aria-hidden')).toBe('true');
    const row = wrapper.get(targetSelector(track));
    expect(row.element.firstElementChild).toBe(row.get('.track-choice-row__icon').element);
    expect(previews[0].element.nextElementSibling).toBe(row.get('.track-choice-row__chevron').element);
    expect(previews[0].props('lineColor')).toBe('currentColor');
    await wrapper.get(targetSelector(track)).trigger('click');
    expect(wrapper.emitted('select')).toEqual([[mapTargetKey(track)]]);
  });

  it('reuses the photo-picker GPS row with activity/date and positive photo facts in its accessible label', () => {
    const candidate: MapTargetCandidate = {
      ...track,
      kind: 'track',
      activityType: 'HIKING',
      description: 'Along the river',
      matchedMediaCount: 3,
      distanceMeters: 18.4,
    };
    const wrapper = mount(MapTargetChooser, { props: { state: state([candidate, photo], candidate) } });
    const row = wrapper.get(targetSelector(candidate));
    expect(wrapper.findAllComponents(TrackChoiceRow)).toHaveLength(1);
    expect(wrapper.getComponent(ActivityTypeBadge).props('type')).toBe('HIKING');
    expect(row.get('.track-choice-row__metadata').text()).toContain('Hiking');
    expect(row.get('.track-choice-row__metadata').text()).toContain('12 October 2026');
    expect(row.get('.track-choice-row__media').text()).toBe('3 photos');
    expect(row.attributes('aria-label')).toContain('Hiking');
    expect(row.attributes('aria-label')).toContain('3 photos');
    expect(row.attributes('aria-label')).toContain('Along the river');
    expect(row.attributes('aria-label')).toContain('Selected');
    expect(row.attributes('aria-pressed')).toBe('true');
    expect(row.text()).not.toContain('away');
    expect(row.attributes('aria-label')).not.toContain('18.4');
  });

  it('shows total distance and ascent only on GPS rows, keeping public routes basic', () => {
    const gps: MapTargetCandidate = {
      ...track,
      kind: 'track',
      recordedAt: Date.UTC(2026, 7, 18, 12),
      lengthMeters: 48_700,
      ascentMeters: 420,
      distanceMeters: 18.4,
    };
    const route = trail(9);
    if (route.kind !== 'overlay') throw new Error('Expected route fixture');
    const wrapper = mount(MapTargetChooser, { props: { state: state([gps, route, photo]) } });
    expect(wrapper.getComponent(TrackChoiceRow).props()).toMatchObject({
      recordedAt: gps.recordedAt,
      lengthMeters: 48_700,
      ascentMeters: 420,
    });
    for (const candidate of [gps]) {
      const row = wrapper.get(targetSelector(candidate));
      expect(row.get('[data-fact="length"]').attributes('aria-label')).toMatch(/^Distance: /);
      expect(row.get('[data-fact="ascent"]').attributes('aria-label')).toMatch(/^Ascent: /);
      expect(row.attributes('aria-label')).toContain('Distance:');
      expect(row.attributes('aria-label')).toContain('Ascent:');
      expect(row.text()).not.toContain('18.4');
    }
    expect(wrapper.get(targetSelector(route)).find('[data-fact]').exists()).toBe(false);
  });

  it.each([undefined, 0, Number.NaN])(
    'does not add a photo badge for an unknown, zero or invalid count %s',
    (matchedMediaCount) => {
      const candidate: MapTargetCandidate = { ...track, kind: 'track', activityType: 'WALKING', matchedMediaCount };
      const wrapper = mount(MapTargetChooser, { props: { state: state([candidate, photo]) } });
      const row = wrapper.get(targetSelector(candidate));
      expect(row.find('.track-choice-row__media').exists()).toBe(false);
      expect(row.text()).not.toContain('No matched photos');
      expect(row.attributes('aria-label')).toContain('Walking');
    }
  );

  it('caps dense previews while photos and places remain directly visible within the shared budget', async () => {
    const routes = Array.from({ length: 100 }, (_, index) => trail(index));
    const tracks = Array.from({ length: 100 }, (_, index) => ({ ...track, id: String(index), trackId: index }));
    const swiss = { ...trail(3, 'ch.astra.wanderland'), sourceLabel: 'SchweizMobil hiking' };
    const wrapper = mount(MapTargetChooser, { props: { state: state([...tracks, ...routes, swiss, photo, poi]) } });
    expect(overviewRows(wrapper).length).toBeLessThanOrEqual(MAP_TARGET_OVERVIEW_ROW_BUDGET);
    expect(wrapper.get(groupSelector('track')).findAll('[data-test="map-target-row"]')).toHaveLength(3);
    expect(wrapper.get(moreSelector('track')).text()).toContain('+97 more GPS tracks');
    expect(wrapper.get(groupSelector('waymarked')).findAll('[data-test="map-target-row"]')).toHaveLength(2);
    expect(wrapper.get(moreSelector('waymarked')).text()).toContain('+98 more Waymarked routes');
    expect(wrapper.get(targetSelector(photo)).text()).toContain('20 photos');
    expect(wrapper.get(targetSelector(poi)).text()).toContain('Village spring');
    expect(wrapper.get(targetSelector(swiss)).isVisible()).toBe(true);
    expect(wrapper.findAll('[data-test="map-category-tab"]')).toHaveLength(5);
    expect(wrapper.get(tabSelector('track')).text()).toBe('GPS tracks 100');
    expect(wrapper.get(tabSelector('waymarked')).text()).toBe('Waymarked 100');
    expect(wrapper.get(tabSelector('swiss')).text()).toBe('Swiss routes 1');
    await wrapper.get(targetSelector(photo)).trigger('click');
    expect(wrapper.emitted('select')).toEqual([[mapTargetKey(photo)]]);
  });

  it.each([
    ['track', track],
    ['track-point', { ...track, kind: 'track-point', pointIndex: 0 }],
    ['photo', photo],
    ['poi', poi],
    ['waymarked', trail(1)],
    ['swiss', trail(1, 'ch.astra.wanderland')],
    ['source:custom', trail(1, 'custom')],
  ] as const)('bounds %s previews and retains access to the full list', async (key, base) => {
    const candidates: MapTargetCandidate[] = Array.from({ length: 5 }, (_, index) => ({
      ...base,
      id: String(index),
    }));
    const wrapper = mount(MapTargetChooser, { props: { state: state(candidates) }, attachTo: document.body });
    const rows = wrapper.get(groupSelector(key)).findAll('[data-test="map-target-row"]');
    const previewCount = 3;
    expect(rows).toHaveLength(previewCount);
    expect(wrapper.get('[data-test="map-category-all"]').attributes('aria-pressed')).toBe('true');
    expect(wrapper.get(tabSelector(key)).attributes('aria-pressed')).toBe('false');
    expect(wrapper.get(moreSelector(key)).text()).toContain(`+${5 - previewCount} more`);
    await wrapper.get(moreSelector(key)).trigger('click');
    expect(wrapper.findAll('[data-test="map-target-row"]')).toHaveLength(5);
    await wrapper.get('[data-test="map-category-all"]').trigger('click');
    const focused = wrapper.get(groupSelector(key)).findAll('[data-test="map-target-row"]')[0]
      .element as HTMLButtonElement;
    focused.focus();
    await wrapper.setProps({ state: state([...candidates, { ...base, id: 'late' }]) });
    expect(
      wrapper
        .get(groupSelector(key))
        .findAll('[data-test="map-target-row"]')
        .map((row) => row.element)
    ).toEqual(rows.map((row) => row.element));
    expect(document.activeElement).toBe(focused);
    expect(wrapper.get(moreSelector(key)).text()).toContain(`+${6 - previewCount} more`);
  });

  it('shares the row budget across small categories and provides explicit access to omitted hits', async () => {
    const routes = Array.from({ length: 19 }, (_, index) => trail(index));
    const tracks = Array.from({ length: 9 }, (_, index) => ({ ...track, id: String(index), trackId: index }));
    const wrapper = mount(MapTargetChooser, { props: { state: state([...tracks, ...routes, photo, poi]) } });
    expect(overviewRows(wrapper)).toHaveLength(MAP_TARGET_OVERVIEW_ROW_BUDGET);
    for (const key of ['track', 'waymarked', 'photo', 'poi']) {
      expect(wrapper.get(groupSelector(key)).isVisible()).toBe(true);
      expect(wrapper.get(tabSelector(key)).isVisible()).toBe(true);
    }
    expect(wrapper.get(moreSelector('track')).text()).toMatch(/\+\d+ more GPS tracks/);
    expect(wrapper.get(moreSelector('waymarked')).text()).toMatch(/\+\d+ more Waymarked routes/);
    await wrapper.get(moreSelector('waymarked')).trigger('click');
    expect(wrapper.findAll('[data-test="map-target-row"]')).toHaveLength(19);
  });

  it('displays two text lines, track dates in metadata and full accessible labels', () => {
    const route = { ...trail(1), detail: 'Ref 81 · Regional network' };
    const wrapper = mount(MapTargetChooser, { props: { state: state([track, photo, poi, route]) } });
    const row = wrapper.get(targetSelector(track));
    expect(row.get('.track-choice-row__title').text()).toBe('Morning walk');
    expect(row.get('.track-choice-row__metadata [data-fact="date"]').text()).toBe('12 October 2026');
    expect(row.attributes('aria-label')).toContain('Your GPS tracks');
    expect(row.attributes('aria-label')).toContain('12 October 2026');
    expect(wrapper.find('.map-target-row__detail').exists()).toBe(false);
    expect(wrapper.get(targetSelector(poi)).get('.map-target-row__context').text()).toBe(
      'Points of interest · Drinking water'
    );
    expect(wrapper.get(targetSelector(route)).attributes('aria-label')).toContain(
      'Waymarked Trails · Hiking · Ref 81 · Regional network'
    );
  });

  it('groups enabled activities and Swiss layers while preserving the source on each result', async () => {
    const swiss = { ...trail(3, 'ch.astra.wanderland'), sourceLabel: 'SchweizMobil hiking' };
    const network = { ...trail(4, 'ch.swisstopo.swisstlm3d-wanderwege'), sourceLabel: 'Hiking trails (all)' };
    const wrapper = mount(MapTargetChooser, {
      props: { state: state([trail(1), trail(2, 'waymarked:cycling'), swiss, network]) },
    });
    expect(wrapper.findAll('[data-test="map-target-group"]')).toHaveLength(2);
    expect(wrapper.get(groupSelector('swiss')).text()).toContain('Swiss routes');
    await wrapper.get(tabSelector('waymarked')).trigger('click');
    expect(wrapper.findAll('[data-test="map-target-row"]').map((row) => row.text())).toEqual([
      expect.stringContaining('Waymarked Trails · Hiking'),
      expect.stringContaining('Waymarked Trails · Cycling'),
    ]);
    await wrapper.get(tabSelector('swiss')).trigger('click');
    expect(wrapper.findAll('[data-test="map-target-row"]').map((row) => row.text())).toEqual([
      expect.stringContaining('SchweizMobil hiking'),
      expect.stringContaining('Hiking trails (all)'),
    ]);
  });

  it('keeps every returned route accessible through pages and selects its original semantic identity', async () => {
    const routes = Array.from({ length: 103 }, (_, index) => trail(index));
    const wrapper = mount(MapTargetChooser, { props: { state: state([...routes, photo]) } });
    await wrapper.get(moreSelector('waymarked')).trigger('click');
    const seen: string[] = [];
    for (let page = 0; page < 6; page++) {
      const rows = wrapper.findAll('[data-test="map-target-row"]');
      expect(rows.length).toBeLessThanOrEqual(20);
      expect(wrapper.get(tabSelector('photo')).isVisible()).toBe(true);
      expect(wrapper.get('[data-test="map-category-all"]').isVisible()).toBe(true);
      seen.push(...rows.map((row) => row.attributes('data-target-key')));
      if (page < 5) await wrapper.get('[aria-label="Next page"]').trigger('click');
    }
    expect(seen).toEqual(routes.map(mapTargetKey));
    expect(wrapper.get('[data-test="map-category-page-range"]').text()).toBe('101–103 of 103');
    expect(wrapper.get('[aria-label="Next page"]').attributes('disabled')).toBeDefined();
    await wrapper.findAll('[data-test="map-target-row"]')[2].trigger('click');
    expect(wrapper.emitted('select')).toEqual([[mapTargetKey(routes[102])]]);
    await wrapper.get('[data-test="map-category-all"]').trigger('click');
    expect(wrapper.get(targetSelector(photo)).isVisible()).toBe(true);
    await wrapper.get(tabSelector('waymarked')).trigger('click');
    expect(wrapper.get('[data-test="map-category-page-range"]').text()).toBe('101–103 of 103');
  });

  it('distinguishes pending, completed empty, unavailable and cancelled categories', async () => {
    const current = state([photo]);
    current.sources = [
      { id: 'tracks', label: 'Your tracks', kind: 'tracks', status: 'pending' },
      { id: 'routes', label: 'Trail overlay', kind: 'overlay', status: 'failed' },
      { id: 'waymarked:hiking', label: 'Waymarked Trails · Hiking', kind: 'overlay', status: 'cancelled' },
    ];
    const wrapper = mount(MapTargetChooser, { props: { state: current } });
    expect(wrapper.get(groupSelector('track')).text()).toContain('Searching…');
    expect(wrapper.get(tabSelector('track')).text()).not.toContain('0');
    expect(wrapper.get(groupSelector('swiss')).text()).toContain('Trail overlay: unavailable');
    expect(wrapper.get(tabSelector('swiss')).text()).not.toContain('0');
    expect(wrapper.get(groupSelector('waymarked')).text()).toContain('lookup cancelled');
    await wrapper.setProps({ state: { ...current, sources: [{ ...current.sources[0], status: 'complete' }] } });
    expect(wrapper.find(groupSelector('track')).exists()).toBe(false);
    expect(wrapper.find(tabSelector('track')).exists()).toBe(false);
  });

  it('shows one compact skeleton per pending category while known targets remain usable', async () => {
    const current = state([track, photo, trail(1)]);
    current.sources = [
      { id: 'tracks', label: 'Your tracks', kind: 'tracks', status: 'pending' },
      { id: 'waymarked:hiking', label: 'Waymarked Trails · Hiking', kind: 'overlay', status: 'pending' },
      { id: 'waymarked:cycling', label: 'Waymarked Trails · Cycling', kind: 'overlay', status: 'pending' },
    ];
    const wrapper = mount(MapTargetChooser, { props: { state: current }, attachTo: document.body });
    expect(wrapper.findAll('[data-test="map-category-loading"]')).toHaveLength(2);
    expect(wrapper.get(tabSelector('track')).find('[data-test="map-category-spinner"]').exists()).toBe(true);
    expect(wrapper.get(tabSelector('track')).attributes('aria-label')).toContain('searching');
    expect(wrapper.get(tabSelector('waymarked')).find('[data-test="map-category-spinner"]').exists()).toBe(true);
    expect(wrapper.get(tabSelector('photo')).find('[data-test="map-category-spinner"]').exists()).toBe(false);
    expect(wrapper.get(groupSelector('waymarked')).findAll('[data-test="map-category-loading"]')).toHaveLength(1);
    expect(wrapper.get(groupSelector('waymarked')).get('[role="status"]').attributes('aria-label')).toContain(
      'Waymarked Trails · Cycling'
    );
    expect(wrapper.get(groupSelector('track')).get('h3 .bi').classes()).toContain('bi-signpost-split');
    expect(wrapper.get(groupSelector('photo')).get('h3 .bi').classes()).toContain('bi-images');
    expect(wrapper.get(groupSelector('waymarked')).get('h3 .bi').classes()).toContain('bi-signpost-split');
    const photoRow = wrapper.get<HTMLButtonElement>(targetSelector(photo)).element;
    photoRow.focus();
    await wrapper.get(targetSelector(track)).trigger('click');
    expect(wrapper.emitted('select')).toEqual([[mapTargetKey(track)]]);
    await wrapper.setProps({
      state: { ...current, sources: current.sources.map((source) => ({ ...source, status: 'complete' })) },
    });
    expect(wrapper.find('[data-test="map-category-loading"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="map-category-spinner"]').exists()).toBe(false);
    expect(wrapper.get(targetSelector(photo)).element).toBe(photoRow);
    expect(document.activeElement).toBe(photoRow);
    expect(wrapper.text()).not.toContain('loading');
    expect(wrapper.text()).not.toContain('Searching');
  });

  it('keeps pending zero counts provisional and replaces completed empty bodies with one final message', async () => {
    const current = state([]);
    current.sources = [
      { id: 'tracks', label: 'Your tracks', kind: 'tracks', status: 'pending' },
      { id: 'routes', label: 'Swiss routes', kind: 'overlay', status: 'pending' },
    ];
    const wrapper = mount(MapTargetChooser, { props: { state: current } });
    expect(wrapper.get('[data-test="map-category-all"]').text()).toBe('All …');
    expect(wrapper.get(tabSelector('track')).text()).toBe('GPS tracks …');
    expect(wrapper.get(tabSelector('swiss')).text()).toBe('Swiss routes …');
    expect(wrapper.findAll('[data-test="map-category-loading"]')).toHaveLength(2);
    expect(wrapper.find('[data-test="map-target-empty"]').exists()).toBe(false);
    await wrapper.setProps({
      state: { ...current, sources: current.sources.map((source) => ({ ...source, status: 'complete' })) },
    });
    expect(wrapper.find('[data-test="map-target-group"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="map-category-loading"]').exists()).toBe(false);
    expect(wrapper.findAll('[data-test="map-category-tab"]')).toHaveLength(0);
    expect(wrapper.get('[data-test="map-category-all"]').text()).toBe('All 0');
    expect(wrapper.get('[data-test="map-target-empty"]').text()).toBe('Nothing found here');
  });

  it.each([false, true])(
    'returns to All when the selected lookup completes empty (other results: %s)',
    async (hasPhoto) => {
      const current = state(hasPhoto ? [photo] : []);
      current.sources = [{ id: 'tracks', label: 'Your tracks', kind: 'tracks', status: 'pending' }];
      const wrapper = mount(MapTargetChooser, { props: { state: current }, attachTo: document.body });
      await wrapper.get(tabSelector('track')).trigger('click');
      expect(document.activeElement).toBe(wrapper.get(tabSelector('track')).element);

      await wrapper.setProps({ state: { ...current, sources: [{ ...current.sources[0], status: 'complete' }] } });
      await flushPromises();

      expect(wrapper.find(tabSelector('track')).exists()).toBe(false);
      expect(wrapper.find(groupSelector('track')).exists()).toBe(false);
      const all = wrapper.get('[data-test="map-category-all"]');
      expect(all.attributes('aria-pressed')).toBe('true');
      expect(document.activeElement).toBe(all.element);
      if (hasPhoto) expect(wrapper.get(targetSelector(photo)).isVisible()).toBe(true);
      else expect(wrapper.get('[data-test="map-target-empty"]').text()).toBe('Nothing found here');
    }
  );

  it('keeps grouped route categories until every lookup completes empty and restores them for later results', async () => {
    const current = state([photo]);
    current.sources = [
      { id: 'waymarked:hiking', label: 'Waymarked hiking', kind: 'overlay', status: 'complete' },
      { id: 'waymarked:cycling', label: 'Waymarked cycling', kind: 'overlay', status: 'pending' },
    ];
    const wrapper = mount(MapTargetChooser, { props: { state: current }, attachTo: document.body });
    const photoRow = wrapper.get(targetSelector(photo));
    (photoRow.element as HTMLButtonElement).focus();
    expect(wrapper.get(tabSelector('waymarked')).text()).toContain('…');

    const complete = {
      ...current,
      sources: current.sources.map((source) => ({ ...source, status: 'complete' as const })),
    };
    await wrapper.setProps({ state: complete });
    expect(wrapper.find(tabSelector('waymarked')).exists()).toBe(false);
    expect(document.activeElement).toBe(photoRow.element);

    await wrapper.setProps({ state: { ...complete, candidates: [photo, trail(1)] } });
    expect(wrapper.get(tabSelector('waymarked')).text()).toBe('Waymarked 1');
    expect(wrapper.get(targetSelector(trail(1))).isVisible()).toBe(true);
    expect(document.activeElement).toBe(photoRow.element);
  });

  it.each(['failed', 'timed-out', 'cancelled'] as const)(
    'keeps a %s lookup distinct from a completed empty result',
    (status) => {
      const current = state([]);
      current.sources = [
        { id: 'tracks', label: 'Your tracks', kind: 'tracks', status, message: 'Try another location' },
      ];
      const wrapper = mount(MapTargetChooser, { props: { state: current } });
      expect(wrapper.get('[data-test="map-target-empty"]').text()).toBe('Lookup incomplete');
      expect(wrapper.text()).not.toContain('Nothing found here');
      expect(wrapper.get('[data-test="map-category-failure"]').text()).toContain(
        status === 'cancelled' ? 'lookup cancelled' : 'unavailable'
      );
      expect(wrapper.get('[data-test="map-category-failure"]').text()).toContain('Try another location');
      expect(wrapper.get(tabSelector('track')).text()).toBe('GPS tracks !');
    }
  );

  it('keeps incomplete indexing distinct from a final empty lookup', () => {
    const current = state([]);
    current.sources = [{ id: 'tracks', label: 'Your tracks', kind: 'tracks', status: 'complete' }];
    current.processing = { state: TrackProcessingStatusDtoStateEnum.Running, failedGpsFiles: 0 } as never;
    const wrapper = mount(MapTargetChooser, { props: { state: current } });
    expect(wrapper.get('[data-test="map-target-empty"]').text()).toBe('Lookup incomplete');
    expect(wrapper.text()).toContain('Processing is still running');
    expect(wrapper.text()).not.toContain('Nothing found here');
  });

  it('keeps pending activities and zoom-in guidance discoverable inside their category', async () => {
    const current = state([trail(1), photo]);
    current.sources = [
      { id: 'waymarked:hiking', label: 'Waymarked Trails · Hiking', kind: 'overlay', status: 'complete' },
      { id: 'waymarked:cycling', label: 'Waymarked Trails · Cycling', kind: 'overlay', status: 'pending' },
      {
        id: 'waymarked:mtb',
        label: 'Waymarked Trails · Mountain biking',
        kind: 'overlay',
        status: 'failed',
        message: 'Zoom in to identify Waymarked Trails routes',
      },
    ];
    const wrapper = mount(MapTargetChooser, { props: { state: current } });
    expect(wrapper.get(tabSelector('waymarked')).get('.map-category-count').text()).toBe('1+');
    expect(wrapper.get(tabSelector('waymarked')).get('.map-category-warning').text()).toBe('!');
    await wrapper.get(tabSelector('waymarked')).trigger('click');
    expect(wrapper.get('[data-test="map-category-loading"]').attributes('aria-label')).toContain(
      'Waymarked Trails · Cycling'
    );
    expect(wrapper.text()).not.toContain(': loading');
    expect(wrapper.text()).toContain('Zoom in to identify Waymarked Trails routes');
  });

  it('shares one status row between pending and failed activities without exceeding a full overview budget', async () => {
    const routes = Array.from({ length: 19 }, (_, index) => trail(index));
    const tracks = Array.from({ length: 9 }, (_, index) => ({ ...track, id: String(index), trackId: index }));
    const swiss = { ...trail(30, 'ch.astra.wanderland'), sourceLabel: 'SchweizMobil hiking' };
    const current = state([...tracks, ...routes, photo, poi, swiss]);
    current.sources = [
      { id: 'waymarked:hiking', label: 'Waymarked Trails · Hiking', kind: 'overlay', status: 'pending' },
      { id: 'waymarked:cycling', label: 'Waymarked Trails · Cycling', kind: 'overlay', status: 'pending' },
      {
        id: 'waymarked:mtb',
        label: 'Waymarked Trails · Mountain biking',
        kind: 'overlay',
        status: 'failed',
        message: 'Zoom in to identify Waymarked Trails routes',
      },
    ];
    const wrapper = mount(MapTargetChooser, { props: { state: current }, attachTo: document.body });
    const group = wrapper.get(groupSelector('waymarked'));
    expect(overviewRows(wrapper)).toHaveLength(MAP_TARGET_OVERVIEW_ROW_BUDGET);
    expect(group.findAll('[data-test="map-category-status"]')).toHaveLength(1);
    const feedback = group.get('[data-test="map-category-status"]');
    expect(feedback.findAll('[data-test="map-category-loading"]')).toHaveLength(1);
    expect(feedback.get('[data-test="map-category-failure"]').text()).toContain(
      'Zoom in to identify Waymarked Trails routes'
    );
    expect(wrapper.get(targetSelector(photo)).isVisible()).toBe(true);
    const routeRow = group.get<HTMLButtonElement>('[data-test="map-target-row"]').element;
    routeRow.focus();
    await wrapper.setProps({
      state: {
        ...current,
        sources: current.sources.map((source) =>
          source.status === 'pending' ? { ...source, status: 'complete' } : source
        ),
      },
    });
    expect(wrapper.find('[data-test="map-category-loading"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="map-category-failure"]').text()).toContain(
      'Zoom in to identify Waymarked Trails routes'
    );
    expect(overviewRows(wrapper)).toHaveLength(MAP_TARGET_OVERVIEW_ROW_BUDGET);
    expect(document.activeElement).toBe(routeRow);
    expect(wrapper.text()).not.toContain('Nothing found here');
  });

  it('preserves category order, page, selected target and focused row when results arrive', async () => {
    const routes = Array.from({ length: 40 }, (_, index) => trail(index));
    const selected = routes[23];
    const wrapper = mount(MapTargetChooser, {
      props: { state: state([track, ...routes], selected) },
      attachTo: document.body,
    });
    await wrapper.get(moreSelector('waymarked')).trigger('click');
    await wrapper.get('[aria-label="Next page"]').trigger('click');
    const row = wrapper.findAll<HTMLButtonElement>('[data-test="map-target-row"]')[3].element;
    row.focus();
    expect(row.getAttribute('aria-pressed')).toBe('true');
    await wrapper.setProps({ state: state([track, ...routes, photo, { ...trail(40), distancePx: 0 }], selected) });
    expect(document.activeElement).toBe(row);
    expect(wrapper.get(targetSelector(selected)).element).toBe(row);
    expect(wrapper.get('[data-test="map-category-page-range"]').text()).toBe('21–40 of 41');
    expect(wrapper.findAll('[data-test="map-category-tab"]').map((tab) => tab.attributes('data-category-key'))).toEqual(
      ['track', 'waymarked', 'photo']
    );
    expect(wrapper.find('[data-test="map-target-back"]').exists()).toBe(false);
    await wrapper.get(targetSelector(selected)).trigger('click');
    expect(wrapper.emitted('select')).toEqual([[mapTargetKey(selected)]]);
  });

  it('keeps an overview target and its focus when a pending dense category arrives', async () => {
    const current = state([photo]);
    current.sources = [{ id: 'tracks', label: 'Your tracks', kind: 'tracks', status: 'pending' }];
    const wrapper = mount(MapTargetChooser, { props: { state: current }, attachTo: document.body });
    const row = wrapper.get<HTMLButtonElement>(targetSelector(photo)).element;
    row.focus();
    const tracks = Array.from({ length: 100 }, (_, index) => ({ ...track, id: String(index), trackId: index }));
    await wrapper.setProps({
      state: { ...current, candidates: [photo, ...tracks], sources: [{ ...current.sources[0], status: 'complete' }] },
    });
    expect(document.activeElement).toBe(row);
    expect(wrapper.get(targetSelector(photo)).element).toBe(row);
    expect(wrapper.get(groupSelector('track')).findAll('[data-test="map-target-row"]')).toHaveLength(3);
    expect(wrapper.get(moreSelector('track')).text()).toContain('+97 more GPS tracks');
    expect(
      wrapper.findAll('[data-test="map-target-group"]').map((group) => group.attributes('data-category-key'))
    ).toEqual(['track', 'photo']);
    expect(overviewRows(wrapper).length).toBeLessThanOrEqual(MAP_TARGET_OVERVIEW_ROW_BUDGET);
  });

  it('never replaces or reorders visible previews as nearer results arrive', async () => {
    const routes = Array.from({ length: 19 }, (_, index) => ({ ...trail(index), distancePx: index + 1 }));
    const wrapper = mount(MapTargetChooser, {
      props: { state: state([...routes, photo]) },
      attachTo: document.body,
    });
    const before = wrapper.findAll('[data-test="map-target-row"]').map((row) => row.element);
    const focused = before[1] as HTMLButtonElement;
    focused.focus();
    await wrapper.setProps({ state: state([...routes, photo, { ...trail(20), distancePx: 0 }]) });
    expect(wrapper.findAll('[data-test="map-target-row"]').map((row) => row.element)).toEqual(before);
    expect(document.activeElement).toBe(focused);
    await wrapper.get(tabSelector('waymarked')).trigger('click');
    expect(wrapper.findAll('[data-test="map-target-row"]').map((row) => row.attributes('data-target-key'))).toEqual(
      [...routes, trail(20)].map(mapTargetKey)
    );
  });

  it('reserves room for pending sources so a full overview stays within budget after late hits', async () => {
    const tracks = Array.from({ length: 9 }, (_, index) => ({ ...track, id: String(index), trackId: index }));
    const routes = Array.from({ length: 6 }, (_, index) => trail(index));
    const current = state([...tracks, ...routes, photo]);
    current.sources = [{ id: 'tracks', label: 'Your tracks', kind: 'tracks', status: 'pending' }];
    const wrapper = mount(MapTargetChooser, { props: { state: current } });
    const before = wrapper.findAll('[data-test="map-target-row"]').map((row) => row.element);
    expect(overviewRows(wrapper)).toHaveLength(MAP_TARGET_OVERVIEW_ROW_BUDGET);
    await wrapper.setProps({
      state: {
        ...current,
        candidates: [...tracks, ...routes, photo, { ...track, id: 'late', trackId: 99 }],
        sources: [{ ...current.sources[0], status: 'complete' }],
      },
    });
    expect(
      wrapper
        .findAll('[data-test="map-target-row"]')
        .map((row) => row.element)
        .filter((row) => before.includes(row))
    ).toEqual(before);
    expect(overviewRows(wrapper)).toHaveLength(9);
    expect(wrapper.get(moreSelector('track')).text()).toContain('+7 more GPS tracks');
  });

  it('ranks initial available distances and preserves supplied order for missing distances and localized dates', async () => {
    const distant = { ...poi, id: 'distant', distancePx: 9 };
    const near = { ...poi, id: 'near', distancePx: 2 };
    const missing = { ...poi, id: 'missing' };
    const invalid = { ...poi, id: 'invalid', distancePx: Number.NaN };
    const earlierSupplied = { ...track, id: 'earlier', detail: '03/04/2026' };
    const laterSupplied = { ...track, id: 'later', detail: '04/03/2026' };
    const wrapper = mount(MapTargetChooser, {
      props: { state: state([earlierSupplied, laterSupplied, missing, distant, invalid, near]) },
    });
    expect(
      wrapper
        .get(groupSelector('poi'))
        .findAll('[data-test="map-target-row"]')
        .map((row) => row.attributes('data-target-key'))
    ).toEqual([near, distant, missing].map(mapTargetKey));
    expect(
      wrapper
        .get(groupSelector('track'))
        .findAll('[data-test="map-target-row"]')
        .map((row) => row.attributes('data-target-key'))
    ).toEqual([earlierSupplied, laterSupplied].map(mapTargetKey));
    expect(wrapper.get(moreSelector('poi')).text()).toContain('+1 more places');
    await wrapper.get(moreSelector('poi')).trigger('click');
    expect(wrapper.findAll('[data-test="map-target-row"]').map((row) => row.attributes('data-target-key'))).toEqual(
      [near, distant, missing, invalid].map(mapTargetKey)
    );
  });

  it('breaks equal screen distances by canonical track recency and only appends newer late hits', async () => {
    const old = { ...track, id: 'old', distancePx: 2, recordedAt: Date.UTC(2026, 8, 1) };
    const recent = { ...track, id: 'recent', distancePx: 2, recordedAt: Date.UTC(2026, 9, 1) };
    const distant = { ...track, id: 'distant', distancePx: 8, recordedAt: Date.UTC(2026, 9, 11) };
    const undated = { ...track, id: 'undated', distancePx: 2 };
    const invalid = { ...track, id: 'invalid', distancePx: 2, recordedAt: Number.NaN };
    const noDistance = { ...track, id: 'no-distance', recordedAt: Date.UTC(2026, 9, 12) };
    const candidates = [old, undated, recent, distant, invalid, noDistance];
    const wrapper = mount(MapTargetChooser, { props: { state: state(candidates) }, attachTo: document.body });
    expect(wrapper.findAll('[data-test="map-target-row"]').map((row) => row.attributes('data-target-key'))).toEqual(
      [recent, old, undated].map(mapTargetKey)
    );
    const before = wrapper.findAll('[data-test="map-target-row"]').map((row) => row.element);
    const focused = before[1] as HTMLButtonElement;
    focused.focus();
    const late = { ...track, id: 'late', distancePx: 0, recordedAt: Date.UTC(2026, 9, 13) };
    await wrapper.setProps({ state: state([...candidates, late]) });
    const after = wrapper.findAll('[data-test="map-target-row"]');
    expect(after.map((row) => row.element)).toEqual(before);
    expect(wrapper.get(moreSelector('track')).text()).toContain('+4 more GPS tracks');
    expect(document.activeElement).toBe(focused);
    await wrapper.get(moreSelector('track')).trigger('click');
    expect(wrapper.findAll('[data-test="map-target-row"]').map((row) => row.attributes('data-target-key'))).toEqual(
      [recent, old, undated, invalid, distant, noDistance, late].map(mapTargetKey)
    );
    expect(
      rankMapCategoryCandidates([
        { ...old, distancePx: undefined },
        { ...undated, distancePx: undefined },
        { ...recent, distancePx: undefined },
        { ...invalid, distancePx: undefined },
      ]).map((candidate) => candidate.id)
    ).toEqual(['recent', 'old', 'undated', 'invalid']);
  });

  it('ranks GPS facts by physical proximity then recency while keeping that distance out of the row', async () => {
    const far: MapTargetCandidate = {
      ...track,
      kind: 'track',
      id: 'far',
      distanceMeters: 120,
      recordedAt: Date.UTC(2026, 9, 12),
    };
    const near: MapTargetCandidate = {
      ...track,
      kind: 'track',
      id: 'near',
      distanceMeters: 18,
      recordedAt: Date.UTC(2026, 8, 1),
    };
    const recentNear: MapTargetCandidate = {
      ...track,
      kind: 'track',
      id: 'recent-near',
      distanceMeters: 18,
      recordedAt: Date.UTC(2026, 9, 1),
    };
    const unknown: MapTargetCandidate = {
      ...track,
      kind: 'track',
      id: 'unknown',
      distancePx: 0,
      recordedAt: Date.UTC(2026, 9, 13),
    };
    const wrapper = mount(MapTargetChooser, { props: { state: state([far, unknown, near, recentNear]) } });
    expect(wrapper.findAll('[data-test="map-target-row"]').map((row) => row.attributes('data-target-key'))).toEqual(
      [recentNear, near, far].map(mapTargetKey)
    );
    await wrapper.get(moreSelector('track')).trigger('click');
    expect(wrapper.findAll('[data-test="map-target-row"]').map((row) => row.attributes('data-target-key'))).toEqual(
      [recentNear, near, far, unknown].map(mapTargetKey)
    );
    expect(wrapper.text()).not.toContain('away');
    expect(wrapper.get(targetSelector(far)).attributes('aria-label')).not.toContain('120');
  });

  it('returns focus to All and resets category, page and previews for a new interaction', async () => {
    const routes = Array.from({ length: 30 }, (_, index) => trail(index));
    const current = state([track, photo, ...routes]);
    const wrapper = mount(MapTargetChooser, { props: { state: current }, attachTo: document.body });
    await wrapper.get(moreSelector('waymarked')).trigger('click');
    await wrapper.get('[aria-label="Next page"]').trigger('click');
    expect(wrapper.attributes('data-sheet-content-key')).toBe(JSON.stringify([1, 'waymarked', 1]));
    await wrapper.get('[data-test="map-category-all"]').trigger('click');
    await flushPromises();
    expect(document.activeElement).toBe(wrapper.get('[data-test="map-category-all"]').element);
    await wrapper.get(tabSelector('waymarked')).trigger('click');
    await wrapper.setProps({ state: { ...current, id: 2, candidates: [poi] } });
    expect(wrapper.get('[data-test="map-category-all"]').attributes('aria-pressed')).toBe('true');
    expect(wrapper.get(targetSelector(poi)).text()).toContain('Village spring');
    expect(wrapper.find('[data-test="map-category-page-range"]').exists()).toBe(false);
    expect(wrapper.attributes('data-sheet-content-key')).toBe(JSON.stringify([2, null, 0]));
  });

  it('keeps unknown registered sources directly accessible without classifying them as Swiss routes', async () => {
    const candidate = { ...trail(9, 'custom-trails'), sourceLabel: 'Custom trails' };
    const wrapper = mount(MapTargetChooser, { props: { state: state([candidate, photo]) } });
    await wrapper.get(targetSelector(candidate)).trigger('click');
    expect(wrapper.emitted('select')).toEqual([[mapTargetKey(candidate)]]);
    expect(wrapper.get(tabSelector('source:custom-trails')).text()).toContain('Custom trails');
    expect(wrapper.find(tabSelector('swiss')).exists()).toBe(false);
  });

  it('shows incomplete track processing after the overview without making direct targets inaccessible', () => {
    const current = state([poi]);
    current.processing = { state: TrackProcessingStatusDtoStateEnum.Running, failedGpsFiles: 0 } as never;
    const wrapper = mount(MapTargetChooser, { props: { state: current } });
    expect(wrapper.text()).toContain('No matching tracks found yet. Processing is still running');
    expect(wrapper.get(targetSelector(poi)).text()).toContain('Village spring');
  });
});

describe('GPS row map previews', () => {
  const point: MapTargetCandidate = { ...track, id: 'point:7:0', kind: 'track-point', pointIndex: 0 };

  it.each([track, point])(
    'previews GPS row $kind on mouse hover without selecting it and clears on leave',
    async (candidate) => {
      const wrapper = mount(MapTargetChooser, { props: { state: state([candidate, photo]) } });
      const row = wrapper.get(targetSelector(candidate));
      await row.trigger('pointerenter', { pointerType: 'mouse' });
      expect(wrapper.emitted('preview-track')).toEqual([[7]]);
      expect(wrapper.emitted('select')).toBeUndefined();
      await row.trigger('pointerleave', { pointerType: 'mouse' });
      expect(wrapper.emitted('preview-track')).toEqual([[7], [null]]);
    }
  );

  it('supports pen hover and keyboard focus while non-GPS rows never request a track preview', async () => {
    const wrapper = mount(MapTargetChooser, { props: { state: state([track, photo]) } });
    const photoRow = wrapper.get(targetSelector(photo));
    await photoRow.trigger('pointerenter', { pointerType: 'mouse' });
    await photoRow.trigger('focus');
    expect(wrapper.emitted('preview-track')).toBeUndefined();
    await photoRow.trigger('blur');
    await photoRow.trigger('pointerleave');
    const trackRow = wrapper.get(targetSelector(track));
    await trackRow.trigger('pointerenter', { pointerType: 'pen' });
    await trackRow.trigger('pointerleave');
    await trackRow.trigger('focus');
    await trackRow.trigger('blur');
    expect(wrapper.emitted('preview-track')).toEqual([[7], [null], [7], [null]]);
    expect(wrapper.emitted('select')).toBeUndefined();
  });

  it('ignores touch hover and touch-driven focus while preserving explicit touch selection', async () => {
    const wrapper = mount(MapTargetChooser, { props: { state: state([track, photo]) } });
    const row = wrapper.get(targetSelector(track));
    await row.trigger('pointerenter', { pointerType: 'touch' });
    await row.trigger('pointerdown', { pointerType: 'touch' });
    await row.trigger('focus');
    expect(wrapper.emitted('preview-track')).toBeUndefined();
    await row.trigger('click');
    expect(wrapper.emitted('select')).toEqual([[mapTargetKey(track)]]);
    expect(wrapper.emitted('preview-track')).toBeUndefined();
  });

  it('allows keyboard focus after a touch interaction', async () => {
    const current = state([track, photo]);
    current.context.inputKind = 'touch';
    const wrapper = mount(MapTargetChooser, { props: { state: current } });
    const row = wrapper.get(targetSelector(track));
    await row.trigger('focus');
    expect(wrapper.emitted('preview-track')).toBeUndefined();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    await row.trigger('focus');
    expect(wrapper.emitted('preview-track')).toEqual([[7]]);
  });

  it.each(['hidden', 'new click', 'category change', 'unmount'])(
    'clears the temporary preview on %s',
    async (reason) => {
      const current = state([track, photo]);
      const onPreviewTrack = vi.fn();
      const wrapper = mount(MapTargetChooser, { props: { state: current, onPreviewTrack } });
      await wrapper.get(targetSelector(track)).trigger('pointerenter', { pointerType: 'mouse' });
      if (reason === 'hidden') await wrapper.setProps({ state: { ...current, presentation: 'target' } });
      if (reason === 'new click') await wrapper.setProps({ state: { ...current, id: 2 } });
      if (reason === 'category change') await wrapper.get(tabSelector('photo')).trigger('click');
      if (reason === 'unmount') wrapper.unmount();
      expect(onPreviewTrack.mock.calls).toEqual([[7], [null]]);
    }
  );

  it('clears the preview before explicit row selection and ignores events from hidden content', async () => {
    const current = state([track, photo]);
    const wrapper = mount(MapTargetChooser, { props: { state: current } });
    const row = wrapper.get(targetSelector(track));
    await row.trigger('pointerenter', { pointerType: 'mouse' });
    await row.trigger('click');
    expect(wrapper.emitted('preview-track')).toEqual([[7], [null]]);
    expect(wrapper.emitted('select')).toEqual([[mapTargetKey(track)]]);
    await wrapper.setProps({ state: { ...current, presentation: 'target' } });
    await row.trigger('pointerenter', { pointerType: 'mouse' });
    await row.trigger('focus');
    expect(wrapper.emitted('preview-track')).toEqual([[7], [null]]);
  });
});
