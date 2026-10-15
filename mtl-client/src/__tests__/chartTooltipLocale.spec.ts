import type Highcharts from 'highcharts';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { useLocale } from '@/composables/useLocale';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { buildChartOptions, type ChartThemeConfig } from '@/utils/chartTheme';

const { setLocale } = useLocale();
const { setMeasurementSystem } = useMeasurementSystem();

function tooltip(config: Partial<ChartThemeConfig> = {}) {
  const options = buildChartOptions({
    seriesName: 'Speed',
    seriesColor: '#6366f1',
    measurementDimension: 'speed',
    rangeTooltipLabel: 'bucket segment range',
    ...config,
  });
  const formatter = options.tooltip!.formatter as (this: Highcharts.Point) => string | false;
  return (point: Record<string, unknown>) => formatter.call(point as unknown as Highcharts.Point);
}

describe('chart tooltip locale', () => {
  beforeEach(() => {
    setLocale('en-GB');
    setMeasurementSystem('METRIC');
  });

  afterEach(() => {
    setLocale('');
    setMeasurementSystem('METRIC');
  });

  it('updates value, min, max and spread on the same formatter after a locale change', () => {
    const format = tooltip();
    const point = { x: 1000, y: 18.8, rangeLow: 17.1, rangeHigh: 19.9, series: { xAxis: { max: 2000 } } };
    for (const [locale, separator] of [
      ['en-GB', '.'],
      ['de-DE', ','],
      ['en-GB', '.'],
    ]) {
      setLocale(locale);
      const result = format(point);
      expect(result).toContain(`<b>18${separator}8\u202fkm/h</b>`);
      expect(result).toContain(`min 17${separator}1\u202fkm/h`);
      expect(result).toContain(`max 19${separator}9\u202fkm/h`);
      expect(result).toContain(`spread 2${separator}8\u202fkm/h`);
    }
  });

  it('localizes the distance header and converted US customary values with the same precision', () => {
    setMeasurementSystem('US_CUSTOMARY');
    const format = tooltip({ xMode: 'distance' });
    const point = { x: 10, y: 100, rangeLow: 80, rangeHigh: 120 };
    setLocale('de-DE');
    const result = format(point);
    expect(result).toContain('6,2\u202fmi');
    expect(result).toContain('<b>62,1\u202fmph</b>');
    expect(result).toContain('min 49,7\u202fmph');
    expect(result).toContain('max 74,6\u202fmph');
    expect(result).toContain('spread 24,9\u202fmph');
  });

  it('preserves zero precision and missing-point behavior', () => {
    setLocale('de-DE');
    const format = tooltip({ measurementDimension: 'elevation', decimals: 0 });
    expect(format({ x: 0, y: 61.4, series: {} })).toContain('<b>61\u202fm</b>');
    expect(format({ x: 0, y: null })).toBe(false);
  });
});
