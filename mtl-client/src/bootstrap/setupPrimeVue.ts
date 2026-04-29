import type { App } from 'vue';

import PrimeVue from 'primevue/config';
import Lara from '@primeuix/themes/lara';
import { definePreset } from '@primeuix/themes';
import ToastService from 'primevue/toastservice';
import DialogService from 'primevue/dialogservice';
import Tooltip from 'primevue/tooltip';

// Components — registered globally for now. TODO(arch C.7): migrate to local
// per-component imports as files are converted to <script setup> so unused
// PrimeVue components can be tree-shaken from the bundle.
import Button from 'primevue/button';
import Slider from 'primevue/slider';
import InputText from 'primevue/inputtext';
import ProgressSpinner from 'primevue/progressspinner';
import DynamicDialog from 'primevue/dynamicdialog';
import Dialog from 'primevue/dialog';
import Popover from 'primevue/popover';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import ColumnGroup from 'primevue/columngroup';
import Row from 'primevue/row';
import Card from 'primevue/card';
import Panel from 'primevue/panel';
import Tabs from 'primevue/tabs';
import TabList from 'primevue/tablist';
import Tab from 'primevue/tab';
import TabPanels from 'primevue/tabpanels';
import TabPanel from 'primevue/tabpanel';
import DatePicker from 'primevue/datepicker';
import Select from 'primevue/select';
import Textarea from 'primevue/textarea';
import SelectButton from 'primevue/selectbutton';
import Accordion from 'primevue/accordion';
import AccordionPanel from 'primevue/accordionpanel';
import AccordionHeader from 'primevue/accordionheader';
import AccordionContent from 'primevue/accordioncontent';
import Message from 'primevue/message';
import Toast from 'primevue/toast';

const MyPreset = definePreset(Lara, {
  semantic: {
    primary: {
      50: '{indigo.50}',
      100: '{indigo.100}',
      200: '{indigo.200}',
      300: '{indigo.300}',
      400: '{indigo.400}',
      500: '{indigo.500}',
      600: '{indigo.600}',
      700: '{indigo.700}',
      800: '{indigo.800}',
      900: '{indigo.900}',
      950: '{indigo.950}',
    },
    // Map PrimeVue surface scale to slate for dark mode so component tokens
    // (--p-text-color, --p-surface-card, etc.) align with our design tokens.
    colorScheme: {
      dark: {
        surface: {
          0: '#ffffff',
          50: '{slate.50}',
          100: '{slate.100}',
          200: '{slate.200}',
          300: '{slate.300}',
          400: '{slate.400}',
          500: '{slate.500}',
          600: '{slate.600}',
          700: '{slate.700}',
          800: '{slate.800}',
          900: '{slate.900}',
          950: '{slate.950}',
        },
      },
    },
  },
});

/**
 * Install PrimeVue + theme preset + service plugins + the directive +
 * the (currently global) component registrations.
 *
 * Extracted from main.ts so the bootstrap file reads as orchestration only.
 *
 * Z-index ladder (must sit above BottomSheet at 5001 and Dialog at 5501).
 * Canonical values live in `assets/base.css` as --z-* design tokens; this
 * JS config is hardcoded because PrimeVue's zIndex API takes raw numbers.
 * Keep both in sync.
 */
export function installPrimeVue(app: App): void {
  app.use(PrimeVue, {
    theme: {
      preset: MyPreset,
      options: {
        // Matches the data-theme attribute set by useTheme composable
        darkModeSelector: '[data-theme="dark"]',
      },
    },
    zIndex: {
      modal: 5500, // Dialog mask           — base.css --z-modal-mask
      overlay: 5600, // Select / DatePicker / Popover / MultiSelect overlays — --z-popup
      menu: 5600, // TieredMenu, ContextMenu — --z-popup
      tooltip: 5700, // Tooltip               — --z-tooltip
    },
  });
  app.use(ToastService);
  app.use(DialogService);
  app.directive('tooltip', Tooltip);

  registerGlobalComponents(app);
}

function registerGlobalComponents(app: App): void {
  app.component('Button', Button);
  app.component('Slider', Slider);
  app.component('Toast', Toast);
  app.component('InputText', InputText);
  app.component('ProgressSpinner', ProgressSpinner);
  app.component('DynamicDialog', DynamicDialog);
  app.component('Dialog', Dialog);
  app.component('Popover', Popover);
  app.component('DataTable', DataTable);
  app.component('Column', Column);
  app.component('ColumnGroup', ColumnGroup);
  app.component('Row', Row);
  app.component('Tabs', Tabs);
  app.component('TabList', TabList);
  app.component('Tab', Tab);
  app.component('TabPanels', TabPanels);
  app.component('TabPanel', TabPanel);
  app.component('DatePicker', DatePicker);
  app.component('Select', Select);
  app.component('Textarea', Textarea);
  app.component('SelectButton', SelectButton);
  app.component('Card', Card);
  app.component('Panel', Panel);
  app.component('Accordion', Accordion);
  app.component('AccordionPanel', AccordionPanel);
  app.component('AccordionHeader', AccordionHeader);
  app.component('AccordionContent', AccordionContent);
  app.component('Message', Message);
}
