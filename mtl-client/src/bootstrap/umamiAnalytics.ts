/**
 * Optional analytics for public demo instances. Private installations keep this
 * disabled and need neither a tracker URL nor access to an Umami server.
 * App startup, authentication and navigation must never depend on the script,
 * window.umami, or a successful analytics request. Demo mode alone is not opt-in.
 */
import { readonly, ref } from 'vue';
import { UmamiAnalyticsControllerApi, Configuration } from 'x8ing-mtl-api-typescript-fetch';
import { backendBasePath } from '@/utils/apiBase';
import { getClientLoginRef, isAuthenticated } from '@/utils/auth';
import { warnSanitized } from '@/utils/safeLogging';

const CONFIG_TIMEOUT_MS = 5000;
const TRACKER_ELEMENT_ID = 'mtl-umami-analytics';
const BEFORE_SEND_FUNCTION_NAME = 'mtlUmamiBeforeSend';
type UmamiWindow = Window & {
  umami?: { identify?: (id: string) => void | Promise<unknown> };
  [BEFORE_SEND_FUNCTION_NAME]?: (type: string, payload: Record<string, unknown>) => Record<string, unknown> | null;
};
const enabled = ref(false);
export const umamiAnalyticsEnabled = readonly(enabled);
let startup: Promise<void> | undefined;

function isDoNotTrackEnabled(): boolean {
  const legacyWindow = window as Window & { doNotTrack?: string | number };
  const legacyNavigator = navigator as Navigator & { msDoNotTrack?: string | number };
  return [legacyWindow.doNotTrack, navigator.doNotTrack, legacyNavigator.msDoNotTrack].some((value) =>
    [1, '1', 'yes'].includes(value ?? '')
  );
}

function configureClientLoginIdentification(tracker: HTMLScriptElement): void {
  const analyticsWindow = window as UmamiWindow;
  let identifiedClientLoginRef: string | null = null;
  const currentClientLoginRef = () => (isAuthenticated() ? getClientLoginRef() : null);

  const identifyClientLogin = async (clientLoginRef: string | null): Promise<void> => {
    const umami = analyticsWindow.umami;
    // A private deployment or a blocked/missing script may never define this API.
    if (isDoNotTrackEnabled() || typeof umami?.identify !== 'function' || clientLoginRef === identifiedClientLoginRef)
      return;
    const previousClientLoginRef = identifiedClientLoginRef;
    // Set this before calling Umami: identify itself invokes the before-send hook.
    identifiedClientLoginRef = clientLoginRef;
    try {
      // An empty ID clears the tracker's previous identity after logout or expiry.
      await umami.identify(clientLoginRef ?? '');
    } catch (error) {
      if (identifiedClientLoginRef === clientLoginRef) identifiedClientLoginRef = previousClientLoginRef;
      warnSanitized('Umami client login identification unavailable; continuing without identification.', error, {
        topic1: 'application',
        topic2: 'umamiAnalytics',
        topic3: 'identifyClientLogin',
      });
    }
  };

  analyticsWindow[BEFORE_SEND_FUNCTION_NAME] = (type, payload) => {
    if (isDoNotTrackEnabled()) return null;
    const clientLoginRef = currentClientLoginRef();
    if (type !== 'identify') void identifyClientLogin(clientLoginRef);
    // The first automatic pageview precedes script load; every payload must use
    // the current client login reference, including login/logout changes without a page reload.
    return { ...payload, id: clientLoginRef ?? undefined };
  };
  tracker.dataset.beforeSend = BEFORE_SEND_FUNCTION_NAME;
  tracker.addEventListener('load', () => void identifyClientLogin(currentClientLoginRef()), { once: true });
}

async function initializeAnalytics(): Promise<void> {
  try {
    // Public, uncached configuration is the only opt-in source. Never persist it in browser storage.
    const api = new UmamiAnalyticsControllerApi(new Configuration({ basePath: backendBasePath, credentials: 'omit' }));
    const config = await api.getUmamiConfig({ cache: 'no-store', signal: AbortSignal.timeout(CONFIG_TIMEOUT_MS) });
    // Disabled or incomplete settings install no script, global hook or client login reference reader.
    if (config?.enabled !== true || !config.scriptUrl || !config.websiteId) return;

    const scriptUrl = new URL(config.scriptUrl);
    if (scriptUrl.protocol !== 'https:' || scriptUrl.username || scriptUrl.password) return;
    enabled.value = true;

    const loadTracker = () => {
      if (isDoNotTrackEnabled() || document.getElementById(TRACKER_ELEMENT_ID)) return;
      const tracker = document.createElement('script');
      tracker.id = TRACKER_ELEMENT_ID;
      tracker.async = true;
      tracker.src = scriptUrl.href;
      tracker.dataset.websiteId = config.websiteId;
      tracker.dataset.domains = window.location.hostname;
      tracker.dataset.doNotTrack = 'true';
      tracker.dataset.excludeSearch = 'true';
      tracker.dataset.excludeHash = 'true';
      configureClientLoginIdentification(tracker);
      tracker.addEventListener(
        'error',
        () =>
          warnSanitized('Umami analytics tracker could not be loaded.', undefined, {
            topic1: 'application',
            topic2: 'umamiAnalytics',
            topic3: 'loadTracker',
          }),
        {
          once: true,
        }
      );
      // Fire and forget: even a tracker request that never finishes cannot delay the app.
      document.head.appendChild(tracker);
    };

    if (document.readyState === 'complete') {
      loadTracker();
    } else {
      window.addEventListener('load', loadTracker, { once: true });
    }
  } catch (error) {
    warnSanitized('Umami analytics unavailable; continuing without tracking.', error, {
      topic1: 'application',
      topic2: 'umamiAnalytics',
      topic3: 'initializeAnalytics',
    });
  }
}

/** Starts once after the initial route is ready; app startup never waits for analytics. */
export function startUmamiAnalytics(): Promise<void> {
  startup ??= initializeAnalytics();
  return startup;
}
