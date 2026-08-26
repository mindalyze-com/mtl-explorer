import { describe, expect, it, vi } from 'vitest';
import {
  basemapOpacityFactor,
  scaledOpacityValue,
  useMapLayerSettings,
} from '@/components/map/composables/useMapLayerSettings';
import { useTerrainMode } from '@/components/map/composables/useTerrainMode';

describe('single MapLibre renderer', () => {
  it('moves the application map camera directly', () => {
    const jumpTo = vi.fn();
    const triggerRepaint = vi.fn();
    const methods = useTerrainMode({ mapSettingsStore: {} as never });
    const view = { center: [8.5, 47.5] as [number, number], zoom: 12, bearing: 0, pitch: 0 };

    methods.jumpMapCamera.call({ overlayMap: { jumpTo, triggerRepaint } } as never, view);

    expect(jumpTo).toHaveBeenCalledWith(view);
    expect(triggerRepaint).toHaveBeenCalledOnce();
  });

  it('scales constant and expression opacity values', () => {
    expect(basemapOpacityFactor(true, 50)).toBe(0.5);
    expect(basemapOpacityFactor(true, 120)).toBe(1);
    expect(basemapOpacityFactor(false, 100)).toBe(0.08);
    expect(scaledOpacityValue(0.6, 0.5)).toBe(0.3);
    expect(scaledOpacityValue(['interpolate', ['linear'], ['zoom'], 5, 0.4, 10, 1], 0.5)).toEqual([
      'interpolate',
      ['linear'],
      ['zoom'],
      5,
      0.2,
      10,
      0.5,
    ]);
    expect(scaledOpacityValue(['step', ['zoom'], 0.2, 8, 0.6, 12, 1], 0.5)).toEqual([
      'step',
      ['zoom'],
      0.1,
      8,
      0.3,
      12,
      0.5,
    ]);
  });

  it('dims only style layers captured before application layers are added', () => {
    const setPaintProperty = vi.fn();
    const layers = [
      { id: 'base-fill', type: 'fill' },
      { id: 'base-label', type: 'symbol' },
    ];
    const map = {
      getStyle: () => ({ layers }),
      getPaintProperty: (layerId: string, propertyName: string) =>
        layerId === 'base-fill' && propertyName === 'fill-opacity' ? 0.6 : undefined,
      getLayer: (layerId: string) => ({ id: layerId }),
      setPaintProperty,
    };
    const methods = useMapLayerSettings({ filterStore: {} as never, mapSettingsStore: {} as never });
    const context = {
      overlayMap: map,
      basemapEnabled: true,
      layerOpacities: { basemap: 50 },
    };

    methods.captureBasemapLayers.call(context as never);
    layers.push({ id: 'tracks-layer', type: 'line' });
    methods.applyBasemapAppearance.call(context as never);

    expect(setPaintProperty).toHaveBeenCalledWith('base-fill', 'fill-opacity', 0.3);
    expect(setPaintProperty).toHaveBeenCalledWith('base-label', 'icon-opacity', 0.5);
    expect(setPaintProperty).toHaveBeenCalledWith('base-label', 'text-opacity', 0.5);
    expect(setPaintProperty).not.toHaveBeenCalledWith('tracks-layer', expect.anything(), expect.anything());
  });
});
