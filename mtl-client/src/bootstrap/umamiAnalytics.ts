/**
 * Optional analytics for public demo instances. Private installations keep this
 * disabled and need neither a tracker URL nor access to an Umami server.
 * App startup, authentication and navigation must never depend on the script,
 * window.umami, or a successful analytics request. Demo mode alone is not opt-in.
 */
import { readonly, ref } from 'vue';
import { UmamiAnalyticsControllerApi, Configuration } from 'x8ing-mtl-api-typescript-fetch';
import { backendBasePath } from '@/utils/apiBase';
import { warnSanitized } from '@/utils/safeLogging';

const CONFIG_TIMEOUT_MS = 5000;
const TRACKER_ELEMENT_ID = 'mtl-umami-analytics';
const BEFORE_SEND_FUNCTION_NAME = 'mtlUmamiBeforeSend';
const PAGE_ID_FIELDS = { track: 'track_id', plan: 'plan_id', review: 'source_id' } as const;
type UmamiWindow = Window & {
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

function analyticsPage(value: unknown): { url: string; data?: Record<string, string> } | null {
  if (typeof value !== 'string' || !value) return null;
  try {
    const url = new URL(value, window.location.origin);
    const base = import.meta.env.BASE_URL.replace(/\/$/, '');
    if (url.origin !== window.location.origin || !url.pathname.startsWith(`${base}/`)) return null;
    const path = url.pathname.slice(base.length);
    const entity = /^\/(track|plan|review)\/(\d+)\/?$/.exec(path);
    if (entity) {
      const route = entity[1] as keyof typeof PAGE_ID_FIELDS;
      return { url: `${base}/${route}/:id`, data: { [PAGE_ID_FIELDS[route]]: entity[2] } };
    }
    const admin = /^\/admin\/([a-z][a-z-]*)\/?$/.exec(path);
    if (admin) return { url: `${base}/admin/:section`, data: { admin_section: admin[1] } };
    return { url: url.pathname };
  } catch {
    return null;
  }
}

function configurePageviews(tracker: HTMLScriptElement): void {
  const analyticsWindow = window as UmamiWindow;
  analyticsWindow[BEFORE_SEND_FUNCTION_NAME] = (type, payload) => {
    if (isDoNotTrackEnabled()) return null;
    const anonymousPayload = { ...payload };
    delete anonymousPayload.id;
    const page = analyticsPage(payload.url);
    const referrer = analyticsPage(payload.referrer);
    const data = payload.data && typeof payload.data === 'object' && !Array.isArray(payload.data) ? payload.data : {};
    return {
      ...anonymousPayload,
      ...(page && { url: page.url }),
      ...(referrer && { referrer: referrer.url }),
      // Keep parameter details on this event, without another pageview or session data update.
      ...(type === 'event' && page?.data && { data: { ...data, ...page.data } }),
    };
  };
  tracker.dataset.beforeSend = BEFORE_SEND_FUNCTION_NAME;
}

async function initializeAnalytics(): Promise<void> {
  try {
    // Public, uncached configuration is the only opt-in source. Never persist it in browser storage.
    const api = new UmamiAnalyticsControllerApi(new Configuration({ basePath: backendBasePath, credentials: 'omit' }));
    const config = await api.getUmamiConfig({ cache: 'no-store', signal: AbortSignal.timeout(CONFIG_TIMEOUT_MS) });
    // Disabled or incomplete settings install no script or analytics hook.
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
      configurePageviews(tracker);
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
