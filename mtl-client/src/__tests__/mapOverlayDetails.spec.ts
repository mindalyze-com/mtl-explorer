import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import MapOverlayDetails from '@/components/map/MapOverlayDetails.vue';
import type { MapOverlayRoute } from '@/components/map/composables/mapControllerRuntime';

function route(overrides: Partial<MapOverlayRoute> = {}): MapOverlayRoute {
  return { icon: '🥾', type: 'Hiking route', name: 'Mountain path', ...overrides };
}

describe('map overlay route details', () => {
  it('renders available route fields, source context, and a visible external details link', () => {
    const wrapper = mount(MapOverlayDetails, {
      props: {
        route: route({
          reference: '12',
          itinerary: 'Village – Mountain',
          network: 'Regional hiking network',
          detailsUrl: 'https://hiking.waymarkedtrails.org/#route?id=123',
        }),
        sourceLabel: 'Waymarked Trails · Hiking',
      },
    });
    expect(wrapper.get('h2').text()).toBe('Mountain path');
    expect(wrapper.get('.map-overlay-details__type').text()).toBe('Hiking route');
    expect(wrapper.findAll('dt').map((field) => field.text())).toEqual(['Reference', 'Itinerary', 'Network']);
    expect(wrapper.findAll('dd').map((field) => field.text())).toEqual([
      '12',
      'Village – Mountain',
      'Regional hiking network',
    ]);
    expect(wrapper.text()).toContain('Source: Waymarked Trails · Hiking');
    const link = wrapper.get('a');
    expect(link.text()).toBe('Open in Waymarked Trails');
    expect(link.attributes()).toMatchObject({
      href: 'https://hiking.waymarkedtrails.org/#route?id=123',
      target: '_blank',
      rel: 'noopener noreferrer',
    });
    expect(link.classes()).toContain('map-overlay-details__link--primary');
    expect(wrapper.find('button').exists()).toBe(false);
    expect(wrapper.text()).not.toMatch(/Download|3D replay/i);
    wrapper.unmount();
  });

  it('omits absent fields and uses the existing route number when reference is absent', () => {
    const wrapper = mount(MapOverlayDetails, {
      props: { route: route({ number: 0, itinerary: '   ' }), sourceLabel: 'Trail overlay' },
    });
    expect(wrapper.findAll('dt').map((field) => field.text())).toEqual(['Route number']);
    expect(wrapper.findAll('dd').map((field) => field.text())).toEqual(['0']);
    expect(wrapper.find('a').exists()).toBe(false);
    wrapper.unmount();
  });

  it('shows available Swiss stage and classification fields and opens stage details before the full route', () => {
    const wrapper = mount(MapOverlayDetails, {
      props: {
        route: route({
          name: 'ViaJacobi',
          number: '4',
          stageLabel: 'Stage 4.03',
          classification: 'Hiking trail',
          stageUrl: 'https://schweizmobil.ch/en/hiking-in-switzerland/route-04/stage-3',
          fullRouteUrl: 'https://www.schweizmobil.ch/en/hiking-in-switzerland/route-04',
        }),
        sourceLabel: 'Swiss Mobility',
      },
    });
    expect(wrapper.findAll('dt').map((field) => field.text())).toEqual(['Route number', 'Stage', 'Classification']);
    expect(wrapper.findAll('dd').map((field) => field.text())).toEqual(['4', 'Stage 4.03', 'Hiking trail']);
    const links = wrapper.findAll('a');
    expect(links.map((link) => link.text())).toEqual(['Stage details', 'Full route']);
    expect(links[0].classes()).toContain('map-overlay-details__link--primary');
    expect(links[1].classes()).not.toContain('map-overlay-details__link--primary');
    for (const link of links) expect(link.attributes()).toMatchObject({ target: '_blank', rel: 'noopener noreferrer' });
    expect(wrapper.find('button').exists()).toBe(false);
    expect(wrapper.text()).not.toMatch(/Download|3D replay/i);
    wrapper.unmount();
  });

  it('uses the first available valid action when stage details are missing or invalid', () => {
    const wrapper = mount(MapOverlayDetails, {
      props: {
        route: route({
          stageUrl: 'https://unrelated.example/stage',
          fullRouteUrl: 'https://schweizmobil.ch/en/hiking-in-switzerland/route-4',
          swissMapUrl: 'https://map.geo.admin.ch/',
        }),
        sourceLabel: 'Swiss Mobility',
      },
    });
    const links = wrapper.findAll('a');
    expect(links.map((link) => link.text())).toEqual(['Full route', 'Open in Swiss map']);
    expect(links[0].classes()).toContain('map-overlay-details__link--primary');
    expect(links[1].classes()).not.toContain('map-overlay-details__link--primary');
    wrapper.unmount();
  });

  it('opens an unnamed network segment in Swiss map and retains available Waymarked Trails details', () => {
    const wrapper = mount(MapOverlayDetails, {
      props: {
        route: route({
          name: 'Hiking trail',
          classification: 'Mountain hiking trail',
          swissMapUrl: 'https://map.geo.admin.ch/?layers=ch.swisstopo.swisstlm3d-wanderwege',
          detailsUrl: 'https://hiking.waymarkedtrails.org/#route?id=123',
        }),
        sourceLabel: 'Swiss hiking network',
      },
    });
    expect(wrapper.findAll('a').map((link) => link.text())).toEqual(['Open in Swiss map', 'Open in Waymarked Trails']);
    expect(wrapper.text()).toContain('Mountain hiking trail');
    expect(wrapper.text()).not.toContain('Stage details');
    expect(wrapper.text()).not.toContain('Full route');
    wrapper.unmount();
  });

  it.each([
    { stageUrl: 'https://schweizmobil.ch.example.org/stage-3' },
    { stageUrl: 'http://schweizmobil.ch/stage-3' },
    { stageUrl: 'https://user:secret@schweizmobil.ch/stage-3' },
    { fullRouteUrl: 'javascript:alert(1)' },
    { fullRouteUrl: 'https://hiking.waymarkedtrails.org/route-04' },
    { fullRouteUrl: 'https://user@www.schweizmobil.ch/route-04' },
    { swissMapUrl: 'https://other.map.geo.admin.ch/' },
    { swissMapUrl: 'https://map.geo.admin.ch.example.org/' },
    { swissMapUrl: 'http://map.geo.admin.ch/' },
    { swissMapUrl: 'https://user@map.geo.admin.ch/' },
    { swissMapUrl: '/map' },
  ])('blocks malformed or unrelated Swiss links: %o', (links) => {
    const wrapper = mount(MapOverlayDetails, {
      props: { route: route(links), sourceLabel: 'Swiss Mobility' },
    });
    expect(wrapper.find('a').exists()).toBe(false);
    wrapper.unmount();
  });

  it.each([
    'not a URL',
    '/route/123',
    'javascript:alert(1)',
    'data:text/html,<p>route</p>',
    'http://hiking.waymarkedtrails.org/route/123',
    'https://example.org/route/123',
    'https://waymarkedtrails.org.example.org/route/123',
    'https://notwaymarkedtrails.org/route/123',
    'https://user:secret@hiking.waymarkedtrails.org/route/123',
  ])('blocks an invalid or unrelated external URL: %s', (detailsUrl) => {
    const wrapper = mount(MapOverlayDetails, {
      props: { route: route({ detailsUrl }), sourceLabel: 'Trail overlay' },
    });
    expect(wrapper.find('a').exists()).toBe(false);
    wrapper.unmount();
  });

  it('renders route fields as text', () => {
    const wrapper = mount(MapOverlayDetails, {
      props: { route: route({ itinerary: '<img src=x onerror=alert(1)>' }), sourceLabel: 'Trail overlay' },
    });
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>');
    expect(wrapper.find('img').exists()).toBe(false);
    wrapper.unmount();
  });
});
