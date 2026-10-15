import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import MapPoiDetails from '@/components/map/MapPoiDetails.vue';
import { mapPoiPresentation, type MapPoiCandidate } from '@/components/map/mapPoiPresentation';

function place(overrides: Partial<MapPoiCandidate> = {}): MapPoiCandidate {
  return {
    id: 'park',
    kind: 'poi',
    source: 'protomaps',
    sourceLabel: 'Points of interest',
    label: 'Parque de Real',
    detail: 'park',
    coordinates: [-8.65, 41.17],
    properties: { kind: 'park' },
    ...overrides,
  };
}

describe('map place details', () => {
  it('gives a sparse park its map category icon and readable identity without repeating the sheet title', () => {
    expect(mapPoiPresentation(place())).toEqual({
      typeLabel: 'Park',
      categoryLabel: 'Parks and nature',
      icon: 'bi bi-tree-fill',
      color: '#618345',
    });
    const wrapper = mount(MapPoiDetails, { props: { candidate: place() } });
    expect(wrapper.get('article').attributes('aria-label')).toBe('Place details');
    expect(wrapper.get('.map-poi-details__type').text()).toBe('Park');
    expect(wrapper.get('.map-poi-details__category').text()).toBe('Parks and nature');
    expect(wrapper.get('.map-poi-details__icon i').classes()).toContain('bi-tree-fill');
    expect(wrapper.get('.map-poi-details__source').text()).toBe('Source: Points of interest');
    expect(wrapper.text()).not.toContain('Parque de Real');
    expect(wrapper.find('dl').exists()).toBe(false);
    expect(wrapper.find('a').exists()).toBe(false);
    expect(wrapper.find('button').exists()).toBe(false);
    wrapper.unmount();
  });

  it('recognizes the readable transport detail when the kind property is missing', () => {
    const candidate = place({ detail: 'bus stop', properties: {} });
    expect(mapPoiPresentation(candidate)).toMatchObject({
      typeLabel: 'Bus stop',
      categoryLabel: 'Public transport',
      icon: 'bi bi-bus-front-fill',
    });
    const wrapper = mount(MapPoiDetails, { props: { candidate } });
    expect(wrapper.text()).toContain('Bus stop');
    expect(wrapper.text()).toContain('Public transport');
    wrapper.unmount();
  });

  it('uses the mapped kind before the fallback detail and avoids a duplicate category label', () => {
    const candidate = place({ detail: 'park', properties: { kind: 'drinking_water' } });
    const wrapper = mount(MapPoiDetails, { props: { candidate } });
    expect(wrapper.get('.map-poi-details__type').text()).toBe('Drinking water');
    expect(wrapper.find('.map-poi-details__category').exists()).toBe(false);
    expect(wrapper.get('.map-poi-details__icon i').classes()).toContain('bi-droplet-fill');
    wrapper.unmount();
  });

  it.each(['viewpoint;telescope', 'viewpoint · telescope'])('shows compound types as readable text: %s', (detail) => {
    expect(mapPoiPresentation(place({ detail, properties: {} }))).toMatchObject({
      typeLabel: 'Viewpoint · Telescope',
      icon: 'bi bi-binoculars-fill',
      categoryLabel: 'Viewpoints and towers',
    });
  });

  it('keeps an unknown type readable and falls back to a neutral place icon without inventing a category', () => {
    const presentation = mapPoiPresentation(place({ properties: { kind: 'local_meeting_place' } }));
    expect(presentation).toEqual({
      typeLabel: 'Local meeting place',
      icon: 'bi bi-geo-alt',
      color: 'var(--text-muted)',
      categoryLabel: null,
    });
  });

  it('uses a concise place fallback when no type or category is available', () => {
    const wrapper = mount(MapPoiDetails, {
      props: { candidate: place({ detail: undefined, sourceLabel: undefined, properties: {} }) },
    });
    expect(wrapper.get('.map-poi-details__type').text()).toBe('Place');
    expect(wrapper.find('.map-poi-details__category').exists()).toBe(false);
    expect(wrapper.get('.map-poi-details__source').text()).toBe('Source: Map places');
    wrapper.unmount();
  });

  it.each([0, '0', 42, '42', -12.5])('shows an available elevation including zero: %s', (elevation) => {
    const wrapper = mount(MapPoiDetails, { props: { candidate: place({ properties: { elevation } }) } });
    expect(wrapper.get('.map-poi-details__metric dt').text()).toBe('Elevation');
    expect(wrapper.get('.map-poi-details__metric dd').text()).toBe(`${Number(elevation)} m`);
    wrapper.unmount();
  });

  it.each([undefined, null, '', ' ', 'unknown', Infinity, NaN, false, ['42']])(
    'omits an absent or invalid elevation: %s',
    (elevation) => {
      const wrapper = mount(MapPoiDetails, { props: { candidate: place({ properties: { elevation } }) } });
      expect(wrapper.find('.map-poi-details__metric').exists()).toBe(false);
      expect(wrapper.text()).not.toContain('Elevation');
      wrapper.unmount();
    }
  );

  it('renders type and source values as text', () => {
    const wrapper = mount(MapPoiDetails, {
      props: {
        candidate: place({
          properties: { kind: '<img src=x onerror=alert(1)>' },
          sourceLabel: '<script>alert(1)</script>',
        }),
      },
    });
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>');
    expect(wrapper.text()).toContain('<script>alert(1)</script>');
    expect(wrapper.find('img').exists()).toBe(false);
    expect(wrapper.find('script').exists()).toBe(false);
    wrapper.unmount();
  });
});
