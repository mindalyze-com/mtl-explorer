import { mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { createPinia } from 'pinia';
import { describe, expect, it } from 'vitest';

import LayerControl from '@/components/map/LayerControl.vue';
import MapSettingsOverview from '@/components/map/MapSettingsOverview.vue';
import MapSettingsPanel from '@/components/map/MapSettingsPanel.vue';
import MapStyleSheet from '@/components/map/MapStyleSheet.vue';
import { DATA_LAYER_IDS, ROUTE_LAYER_IDS } from '@/components/map/mapSettingsPanelTypes';

const BottomSheetStub = defineComponent({
  name: 'BottomSheet',
  template: '<section><header><slot name="title" /></header><slot /><footer><slot name="footer" /></footer></section>',
});

const overviewProps = {
  basemapEnabled: true,
  dataSummary: '2 of 4 shown',
  routesSummary: 'None shown',
  sourceSummary: 'Automatic source',
  styleSummary: 'OSM Topo Contrast',
  terrainSummary: '2D map',
  themeName: 'OSM Topo Contrast',
  themeThumbnail: '/theme.webp',
};

describe('Map settings UI', () => {
  it('opens each focused settings area from the overview rows', async () => {
    const wrapper = mount(MapSettingsOverview, { props: overviewProps });

    expect(wrapper.get('#map-settings-current-title').text()).toBe('OSM Topo Contrast');
    expect(wrapper.get('.map-settings-overview__current-copy p').text()).toBe('Automatic source · 2D map');
    await wrapper.get('button.map-settings-overview__preview').trigger('click');

    const rows = wrapper.findAll('.map-settings-row');
    expect(rows.map((row) => row.text())).toEqual([
      expect.stringContaining('Map style'),
      expect.stringContaining('Terrain'),
      expect.stringContaining('Points of interest'),
      expect.stringContaining('Your data'),
      expect.stringContaining('Route overlays'),
    ]);

    for (const row of rows) await row.trigger('click');
    await wrapper.get('.map-settings-overview__reset').trigger('click');

    expect(wrapper.emitted('open-style')).toHaveLength(2);
    expect(wrapper.emitted('open-terrain')).toHaveLength(1);
    expect(wrapper.emitted('open-pois')).toHaveLength(1);
    expect(wrapper.emitted('open-data')).toHaveLength(1);
    expect(wrapper.emitted('open-routes')).toHaveLength(1);
    expect(wrapper.emitted('reset')).toHaveLength(1);
  });

  it('shows when the background map is hidden', () => {
    const wrapper = mount(MapSettingsOverview, {
      props: { ...overviewProps, basemapEnabled: false, styleSummary: 'OSM Topo Contrast · hidden' },
    });

    expect(wrapper.get('.map-settings-overview__preview-state').text()).toContain('Hidden');
    expect(wrapper.findAll('.map-settings-row')[0].text()).toContain('OSM Topo Contrast · hidden');
  });

  it('keeps the current map summary in the body as settings change', async () => {
    const layerStates = Object.fromEntries(
      ['basemap', 'terrain', ...DATA_LAYER_IDS, ...ROUTE_LAYER_IDS].map((id) => [
        id,
        { enabled: id === 'basemap', opacity: 100 },
      ])
    );
    const wrapper = mount(MapSettingsPanel, {
      props: {
        layerStates,
        mapSourceMode: 'auto',
        modelValue: 'contrast',
        terrainExaggeration: 1.5,
        themes: [{ code: 'contrast', name: 'OSM Topo Contrast', thumbnail: '/theme.webp' }],
      },
      global: { plugins: [createPinia()], stubs: { BottomSheet: BottomSheetStub } },
    });

    expect(wrapper.get('.map-settings-header h2').text()).toBe('Map');
    expect(wrapper.find('.map-settings-header p').exists()).toBe(false);
    expect(wrapper.get('#map-settings-current-title').text()).toBe('OSM Topo Contrast');
    expect(wrapper.get('.map-settings-overview__current-copy p').text()).toBe('Automatic source · 2D map');

    await wrapper.setProps({
      mapSourceMode: 'remote',
      layerStates: {
        ...layerStates,
        basemap: { enabled: false, opacity: 100 },
        terrain: { enabled: true, opacity: 100 },
      },
    });
    expect(wrapper.get('.map-settings-overview__current-copy p').text()).toBe('Remote tiles · 3D · 1.5× relief');
    expect(wrapper.get('.map-settings-overview__preview-state').text()).toContain('Hidden');
  });

  it('uses an accessible layer switch and only shows opacity while enabled', async () => {
    const wrapper = mount(LayerControl, {
      props: { label: 'GPS tracks', info: 'Recorded tracks', enabled: false, opacity: 65 },
    });

    const toggle = wrapper.get('.lc-row');
    expect(toggle.attributes('aria-pressed')).toBe('false');
    expect(toggle.attributes('aria-label')).toBe('Show GPS tracks');
    expect(wrapper.find('.lc-slider-area').exists()).toBe(false);

    await toggle.trigger('click');
    expect(wrapper.emitted('update:enabled')).toEqual([[true]]);

    await wrapper.setProps({ enabled: true });
    expect(wrapper.get('.lc-row').attributes('aria-label')).toBe('Hide GPS tracks');
    expect(wrapper.get('.lc-slider-heading output').text()).toBe('65%');
  });

  it('changes map source and theme directly from the style sheet', async () => {
    const wrapper = mount(MapStyleSheet, {
      props: {
        basemap: { enabled: true, opacity: 100 },
        mapSourceMode: 'auto',
        modelValue: true,
        selectedTheme: 'contrast',
        themes: [
          { code: 'contrast', name: 'Contrast', thumbnail: '/contrast.webp' },
          { code: 'dark', name: 'Dark', thumbnail: '/dark.webp' },
        ],
      },
      global: { stubs: { BottomSheet: BottomSheetStub } },
    });

    await wrapper.findAll('.map-style-sheet__source')[1].trigger('click');
    await wrapper.findAll('.map-style-sheet__theme')[1].trigger('click');

    expect(wrapper.emitted('update:map-source-mode')).toEqual([['remote']]);
    expect(wrapper.emitted('update:selected-theme')).toEqual([['dark']]);
  });
});
