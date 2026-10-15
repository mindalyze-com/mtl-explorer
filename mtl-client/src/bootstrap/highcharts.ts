// Keep the core and its modules on the same ESM instance in every build.
import Highcharts from 'highcharts/esm/highcharts.js';
import 'highcharts/esm/highcharts-more.js';
import 'highcharts/esm/modules/solid-gauge.js';
import 'highcharts/esm/modules/accessibility.js';
import 'highcharts/esm/modules/boost.js';

// Track sync requires full points. Dense health series opt into boost explicitly.
Highcharts.setOptions({ plotOptions: { series: { boostThreshold: 0 } } });

// Highcharts 13.1.1 still composes these Axis methods at runtime but omits their
// declarations. Remove this augmentation when upstream restores the types.
declare module 'highcharts' {
  interface Axis {
    addPlotBand(options: AxisPlotBandsOptions): PlotLineOrBand | undefined;
    addPlotLine(options: AxisPlotLinesOptions): PlotLineOrBand | undefined;
    removePlotBand(id: string): void;
    removePlotLine(id: string): void;
  }
}

export default Highcharts;
