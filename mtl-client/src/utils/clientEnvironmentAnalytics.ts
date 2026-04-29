import { AnalyticsControllerApi, type ClientEnvironmentRequest } from 'x8ing-mtl-api-typescript-fetch';
import { getApiConfiguration } from '@/utils/openApiClient';
import { startupLog } from '@/utils/startupDiagnostics';

const DISPLAY_MODE_STANDALONE = 'standalone';
const DISPLAY_MODE_FULLSCREEN = 'fullscreen';
const DISPLAY_MODE_MINIMAL_UI = 'minimal-ui';
const DISPLAY_MODE_BROWSER = 'browser';

let submittedForCurrentBoot = false;

type NavigatorWithClientHints = Navigator & {
  deviceMemory?: number;
  connection?: {
    effectiveType?: string;
    downlink?: number;
    rtt?: number;
    saveData?: boolean;
  };
};

function getAnalyticsApi(): AnalyticsControllerApi {
  return new AnalyticsControllerApi(getApiConfiguration());
}

export function submitClientEnvironmentOnce(): void {
  if (submittedForCurrentBoot || typeof window === 'undefined' || typeof navigator === 'undefined') {
    return;
  }

  submittedForCurrentBoot = true;
  const payload = collectClientEnvironment();

  getAnalyticsApi().saveClientEnvironment({ clientEnvironmentRequest: payload })
    .then(() => startupLog('analytics', 'Client environment submitted'))
    .catch((error: unknown) => {
      submittedForCurrentBoot = false;
      console.warn('Client environment analytics submission failed', error);
    });
}

function collectClientEnvironment(): ClientEnvironmentRequest {
  const nav = navigator as NavigatorWithClientHints;
  const connection = nav.connection;

  return {
    userAgent: navigator.userAgent,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    browserLanguage: navigator.language,
    browserLanguages: Array.from(navigator.languages ?? []),
    screenWidth: window.screen.width,
    screenHeight: window.screen.height,
    availableScreenWidth: window.screen.availWidth,
    availableScreenHeight: window.screen.availHeight,
    viewportWidth: window.innerWidth,
    viewportHeight: window.innerHeight,
    devicePixelRatio: window.devicePixelRatio,
    colorDepth: window.screen.colorDepth,
    platform: navigator.platform,
    hardwareConcurrency: navigator.hardwareConcurrency,
    deviceMemoryGb: nav.deviceMemory,
    touchPoints: navigator.maxTouchPoints,
    appDisplayMode: detectDisplayMode(),
    online: navigator.onLine,
    additionalProperties: {
      cookieEnabled: navigator.cookieEnabled,
      doNotTrack: navigator.doNotTrack,
      connectionEffectiveType: connection?.effectiveType,
      connectionDownlinkMbps: connection?.downlink,
      connectionRttMs: connection?.rtt,
      connectionSaveData: connection?.saveData,
    },
  };
}

function detectDisplayMode(): string {
  if (window.matchMedia(`(display-mode: ${DISPLAY_MODE_STANDALONE})`).matches) {
    return DISPLAY_MODE_STANDALONE;
  }
  if (window.matchMedia(`(display-mode: ${DISPLAY_MODE_FULLSCREEN})`).matches) {
    return DISPLAY_MODE_FULLSCREEN;
  }
  if (window.matchMedia(`(display-mode: ${DISPLAY_MODE_MINIMAL_UI})`).matches) {
    return DISPLAY_MODE_MINIMAL_UI;
  }
  return DISPLAY_MODE_BROWSER;
}
