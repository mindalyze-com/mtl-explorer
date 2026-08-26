import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { GpsTrackDataPoint } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

const maplibreMock = vi.hoisted(() => {
  class MockPopup {
    static instances: MockPopup[] = [];

    options: Record<string, unknown>;
    setLngLat = vi.fn(() => this);
    setDOMContent = vi.fn(() => this);
    addTo = vi.fn(() => this);

    constructor(options: Record<string, unknown>) {
      this.options = options;
      MockPopup.instances.push(this);
    }
  }

  return { MockPopup };
});

vi.mock('maplibre-gl', () => ({
  Popup: maplibreMock.MockPopup,
}));

import { createTrackPointPopup, TRACK_POINT_POPUP_MAX_WIDTH } from '@/components/map/trackPointPopup';
import { buildArchiveTrackPointPopupRows, useTrackPointLayer } from '@/components/map/composables/useTrackPointLayer';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';

const measurementPreference = useMeasurementSystem();

describe('track point popup', () => {
  beforeEach(() => {
    maplibreMock.MockPopup.instances.length = 0;
    measurementPreference.setMeasurementSystem('METRIC');
  });

  it('creates the shared popup structure and treats row content as text', () => {
    const map = { id: 'map' };
    const popup = createTrackPointPopup({
      map: map as never,
      lngLat: [8.5, 47.3],
      title: 'Track <unsafe>',
      rows: [
        { label: 'Point', value: '5 / 10' },
        { label: '<img src=x>', value: '<script>unsafe()</script>' },
      ],
      closeOnClick: false,
    });

    const instance = maplibreMock.MockPopup.instances[0];
    const content = instance.setDOMContent.mock.calls[0][0] as HTMLDivElement;

    expect(popup).toBe(instance);
    expect(instance.options).toEqual({
      closeButton: true,
      closeOnClick: false,
      maxWidth: TRACK_POINT_POPUP_MAX_WIDTH,
      className: 'mtl-point-popup-container',
    });
    expect(instance.setLngLat).toHaveBeenCalledWith([8.5, 47.3]);
    expect(instance.addTo).toHaveBeenCalledWith(map);
    expect(content.className).toBe('mtl-point-popup');
    expect(content.querySelector('.mtl-point-popup-header')?.textContent).toBe('Track <unsafe>');
    expect(Array.from(content.querySelectorAll('.mtl-pp-label')).map((cell) => cell.textContent)).toEqual([
      'Point',
      '<img src=x>',
    ]);
    expect(Array.from(content.querySelectorAll('.mtl-pp-value')).map((cell) => cell.textContent)).toEqual([
      '5 / 10',
      '<script>unsafe()</script>',
    ]);
    expect(content.querySelector('img')).toBeNull();
    expect(content.querySelector('script')).toBeNull();
  });

  it('builds archive rows without optional energy metrics', () => {
    const rows = buildArchiveTrackPointPopupRows(
      [8.5, 47.3],
      {
        pointIndex: 4,
        pointIndexMax: 9,
        pointLongLat: { type: 'Point', coordinates: [8.6, 47.4] },
        pointAltitude: 612.34,
      } as GpsTrackDataPoint,
      {
        speedInKmhMovingWindow: 12.34,
        durationSinceStart: 3661,
      } as GpsTrackDataPoint
    );

    expect(rows).toHaveLength(14);
    expect(rows.slice(0, 4)).toEqual([
      { label: 'Point', value: '5 / 10' },
      { label: 'Time', value: '—' },
      { label: 'Lat / Lng', value: '47.400000 / 8.600000' },
      { label: 'Altitude', value: '612.3 m' },
    ]);
    expect(rows).toContainEqual({ label: 'Speed', value: '12.3 km/h' });
    expect(rows).toContainEqual({ label: 'Duration', value: '1h 1m 1s' });
    expect(rows.some((row) => row.label.startsWith('Est.'))).toBe(false);
  });

  it('adds archive energy metrics when they are available', () => {
    const rows = buildArchiveTrackPointPopupRows(
      { lng: 8.5, lat: 47.3 },
      { pointIndex: 0, pointIndexMax: 0 } as GpsTrackDataPoint,
      {
        energyTotalWh: 12.345,
        energyCumulativeWh: 98.76,
        powerWatts: 245.6,
      } as GpsTrackDataPoint
    );

    expect(rows.slice(-3)).toEqual([
      { label: 'Est. energy (seg)', value: '12.35 Wh' },
      { label: 'Est. energy (cum)', value: '98.8 Wh' },
      { label: 'Est. power', value: '246 W' },
    ]);
  });

  it('formats archive distance, elevation, and speed in US customary units', () => {
    measurementPreference.setMeasurementSystem('US_CUSTOMARY');

    const rows = buildArchiveTrackPointPopupRows(
      [8.5, 47.3],
      {
        pointIndex: 4,
        pointIndexMax: 9,
        pointAltitude: 612.34,
      } as GpsTrackDataPoint,
      {
        distanceInMeterSinceStart: 1609.344,
        speedInKmhMovingWindow: 16.09344,
      } as GpsTrackDataPoint
    );

    expect(rows).toContainEqual({ label: 'Altitude', value: '2,009.0 ft' });
    expect(rows).toContainEqual({ label: 'Dist from start', value: '1.00 mi' });
    expect(rows).toContainEqual({ label: 'Speed', value: '10.0 mph' });
  });

  it('shows missing numeric values as unavailable instead of zero', () => {
    const rows = buildArchiveTrackPointPopupRows(
      [8.5, 47.3],
      {
        pointIndex: 0,
        pointIndexMax: 0,
        pointAltitude: null,
      } as GpsTrackDataPoint,
      {
        speedInKmhMovingWindow: null,
        distanceInMeterSinceStart: null,
        distanceInMeterBetweenPoints: null,
        durationBetweenPointsInSec: null,
        ascentInMeterSinceStart: null,
        descentInMeterSinceStart: null,
        slopePercentageInMovingWindow: null,
        elevationGainPerHourMovingWindow: null,
        elevationLossPerHourMovingWindow: null,
      } as GpsTrackDataPoint
    );

    for (const label of [
      'Altitude',
      'Speed',
      'Dist from start',
      'Dist prev point',
      'Time prev point',
      'Ascent',
      'Descent',
      'Slope',
      'Elev gain/h',
      'Elev loss/h',
    ]) {
      expect(rows).toContainEqual({ label, value: '—' });
    }
  });

  it('refreshes an open popup after the measurement system changes', () => {
    const popup = new maplibreMock.MockPopup({});
    const methods = useTrackPointLayer();
    const context = {
      lngLat: [8.5, 47.3] as [number, number],
      trackId: 7,
      point: { pointAltitude: 1609.344 } as GpsTrackDataPoint,
      canonical: { distanceInMeterSinceStart: 1609.344 } as GpsTrackDataPoint,
    };

    measurementPreference.setMeasurementSystem('US_CUSTOMARY');
    methods.refreshTrackPointPopupMeasurementLabels.call({
      trackPointsPopup: popup,
      trackPointsPopupContext: context,
    } as never);

    const content = popup.setDOMContent.mock.calls.at(-1)?.[0] as HTMLDivElement;
    expect(content.textContent).toContain('5,280.0 ft');
    expect(content.textContent).toContain('1.00 mi');
  });
});
