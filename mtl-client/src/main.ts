import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';

import { initializeStartupDiagnostics, startupLog } from '@/utils/startupDiagnostics';
import { installPrimeVue } from '@/bootstrap/setupPrimeVue';
import { installGlobalErrorHandlers } from '@/bootstrap/globalErrorHandlers';
import { startClientLogDelivery } from '@/utils/clientDiagnosticDelivery';
import { startEarlyPrefetch } from '@/bootstrap/earlyPrefetch';
import { startUmamiAnalytics } from '@/bootstrap/umamiAnalytics';
import { installHighchartsTheme } from '@/composables/useHighchartsTheme';
import { warmBackgroundCache } from '@/utils/backgroundCacheWarmer';
import { setWorkerUrl } from 'maplibre-gl';
import mapLibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';

// ── Side-effect-only style imports (must come before component CSS) ──
import 'primeicons/primeicons.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
import 'highlight.js/styles/stackoverflow-light.min.css';
import './assets/main.css';

// ── Side-effect plugins ──
import Highcharts from '@/bootstrap/highcharts';
import HighchartsVue from 'highcharts-vue';
import hljs from 'highlight.js/lib/core';
import sql from 'highlight.js/lib/languages/sql';
import pgsql from 'highlight.js/lib/languages/pgsql';
import hljsVuePlugin from '@highlightjs/vue-plugin';
import { registerMtlSqlHighlight } from '@/utils/mtlSqlHighlight';
hljs.registerLanguage('sql', sql);
hljs.registerLanguage('pgsql', pgsql);
registerMtlSqlHighlight(hljs);

// ── Eager-evaluated module side effects (must run before mount) ──
import '@/utils/auth';

// ── Boot ──
setWorkerUrl(mapLibreWorkerUrl);
initializeStartupDiagnostics();
startClientLogDelivery();
startupLog('boot', 'Vue bootstrap starting', { baseUrl: import.meta.env.BASE_URL });

installHighchartsTheme();
warmBackgroundCache();
startEarlyPrefetch();

const app = createApp(App);

// Pinia must be installed before any composable/component that calls `useStore()`.
// Installed early so even global error handlers and routed views can use stores.
app.use(createPinia());

installGlobalErrorHandlers(app);
app.use(router);
installPrimeVue(app);
app.use(HighchartsVue, { highcharts: Highcharts });
app.use(hljsVuePlugin);

app.mount('#app');
// Optional public-demo analytics. Private installations must work without a tracker.
void router.isReady().then(startUmamiAnalytics);
