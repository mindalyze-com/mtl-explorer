import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';

import { initializeStartupDiagnostics, startupLog } from '@/utils/startupDiagnostics';
import { installPrimeVue } from '@/bootstrap/setupPrimeVue';
import { installGlobalErrorHandlers } from '@/bootstrap/globalErrorHandlers';
import { startEarlyPrefetch } from '@/bootstrap/earlyPrefetch';
import { installHighchartsTheme } from '@/composables/useHighchartsTheme';
import { warmBackgroundCache } from '@/utils/backgroundCacheWarmer';

// ── Side-effect-only style imports (must come before component CSS) ──
import 'primeicons/primeicons.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
import 'highlight.js/styles/stackoverflow-light.min.css';
import './assets/main.css';

// ── Side-effect plugins ──
import HighchartsVue from 'highcharts-vue';
import hljs from 'highlight.js';
import sql from 'highlight.js/lib/languages/sql';
import pgsql from 'highlight.js/lib/languages/pgsql';
import hljsVuePlugin from '@highlightjs/vue-plugin';
hljs.registerLanguage('sql', sql);
hljs.registerLanguage('pgsql', pgsql);

// ── Eager-evaluated module side effects (must run before mount) ──
import '@/utils/auth';

// ── Disable pinch-zoom on iOS Safari ──
// iOS 10+ ignores user-scalable=no in the viewport meta for accessibility
// reasons. Blocking multi-touch touchmove on the document is the only reliable
// workaround. Single-finger panning (e.g. map drag, sheet drag) is unaffected.
document.addEventListener('touchmove', (e: TouchEvent) => {
  if (e.touches.length > 1) e.preventDefault();
}, { passive: false });
// Prevent double-tap zoom by eating the second tap if it follows within 300ms.
let _lastTap = 0;
document.addEventListener('touchend', (e: TouchEvent) => {
  const now = Date.now();
  if (now - _lastTap < 300) e.preventDefault();
  _lastTap = now;
}, { passive: false });

// ── Boot ──
initializeStartupDiagnostics();
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
// @ts-ignore -- highcharts-vue is missing proper Vue 3 plugin typings
app.use(HighchartsVue);
app.use(hljsVuePlugin);

app.mount('#app');
