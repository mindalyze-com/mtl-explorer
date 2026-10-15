import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const getUmamiConfig = vi.hoisted(() => vi.fn());
const getClientLoginRef = vi.hoisted(() => vi.fn());
const isAuthenticated = vi.hoisted(() => vi.fn());
vi.mock('@/utils/auth', () => ({ getClientLoginRef, isAuthenticated }));
vi.mock('x8ing-mtl-api-typescript-fetch', async (importOriginal) => ({
  ...(await importOriginal<typeof import('x8ing-mtl-api-typescript-fetch')>()),
  UmamiAnalyticsControllerApi: class {
    getUmamiConfig = getUmamiConfig;
  },
  Configuration: class {},
}));

const identify = vi.fn();
const clientLoginRef = '12345678123412341234123456789abc';
const activeConfig = {
  enabled: true,
  scriptUrl: 'https://stats.example.com/script.js',
  websiteId: '12345678-1234-1234-1234-123456789abc',
};

function tracker(): HTMLScriptElement | null {
  return document.querySelector('#mtl-umami-analytics');
}

function beforeSend(type: string, payload: Record<string, unknown> = {}): Record<string, unknown> | null {
  const callback = (window as unknown as Record<string, unknown>)[tracker()!.dataset.beforeSend!] as (
    type: string,
    payload: Record<string, unknown>
  ) => Record<string, unknown> | null;
  return callback(type, payload);
}

beforeEach(() => {
  vi.resetModules();
  getUmamiConfig.mockReset().mockResolvedValue(activeConfig);
  getClientLoginRef.mockReset().mockReturnValue(clientLoginRef);
  isAuthenticated.mockReset().mockReturnValue(true);
  identify.mockReset().mockResolvedValue(undefined);
  vi.stubGlobal('umami', { identify });
  vi.stubEnv('BASE_URL', '/mtl/');
  vi.spyOn(document, 'readyState', 'get').mockReturnValue('complete');
  vi.spyOn(console, 'warn').mockImplementation(() => {});
});

afterEach(() => {
  const callbackName = tracker()?.dataset.beforeSend;
  if (callbackName) Reflect.deleteProperty(window, callbackName);
  tracker()?.remove();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

describe('Umami analytics', () => {
  it.each([
    { label: 'disabled with no tracker settings', config: { enabled: false } },
    { label: 'disabled with stale tracker settings', config: { ...activeConfig, enabled: false } },
    { label: 'no settings', config: {} },
    { label: 'no configuration response', config: undefined },
    { label: 'null configuration response', config: null },
    { label: 'enabled without any tracker settings', config: { enabled: true } },
    { label: 'enabled without a script URL', config: { ...activeConfig, scriptUrl: undefined } },
    { label: 'enabled without a website ID', config: { ...activeConfig, websiteId: undefined } },
  ])('works with no Umami global when $label', async ({ config }) => {
    Reflect.deleteProperty(window, 'umami');
    getUmamiConfig.mockResolvedValue(config);
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await expect(analytics.startUmamiAnalytics()).resolves.toBeUndefined();
    window.dispatchEvent(new Event('load'));
    expect('umami' in window).toBe(false);
    expect('mtlUmamiBeforeSend' in window).toBe(false);
    expect(tracker()).toBeNull();
    expect(analytics.umamiAnalyticsEnabled.value).toBe(false);
    expect(isAuthenticated).not.toHaveBeenCalled();
    expect(getClientLoginRef).not.toHaveBeenCalled();
    expect(identify).not.toHaveBeenCalled();
  });

  it.each([false, undefined, null, 'true', 1])(
    'does not load external scripts unless enabled is exactly true: %s',
    async (enabled) => {
      getUmamiConfig.mockResolvedValue({ ...activeConfig, enabled });
      const analytics = await import('@/bootstrap/umamiAnalytics');
      await analytics.startUmamiAnalytics();
      expect(tracker()).toBeNull();
      expect(analytics.umamiAnalyticsEnabled.value).toBe(false);
      expect(getClientLoginRef).not.toHaveBeenCalled();
      expect(identify).not.toHaveBeenCalled();
    }
  );

  it('uses fresh public settings, inserts one asynchronous tracker, and configures privacy options', async () => {
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await Promise.all([analytics.startUmamiAnalytics(), analytics.startUmamiAnalytics()]);
    expect(getUmamiConfig).toHaveBeenCalledOnce();
    expect(getUmamiConfig).toHaveBeenCalledWith({ cache: 'no-store', signal: expect.any(AbortSignal) });
    expect(document.querySelectorAll('#mtl-umami-analytics')).toHaveLength(1);
    expect(tracker()?.src).toBe(activeConfig.scriptUrl);
    expect(tracker()?.async).toBe(true);
    expect(tracker()?.dataset).toMatchObject({
      websiteId: activeConfig.websiteId,
      domains: window.location.hostname,
      doNotTrack: 'true',
      excludeSearch: 'true',
      excludeHash: 'true',
    });
    expect(analytics.umamiAnalyticsEnabled.value).toBe(true);
  });

  it('uses default Umami identification without reading login references or sending a Distinct ID', async () => {
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await analytics.startUmamiAnalytics();
    tracker()!.dispatchEvent(new Event('load'));
    expect(tracker()?.dataset.distinctId).toBeUndefined();
    expect(beforeSend('event', { url: '/mtl/', id: 'stale-custom-identity' })).toEqual({ url: '/mtl/' });
    expect(getClientLoginRef).not.toHaveBeenCalled();
    expect(isAuthenticated).not.toHaveBeenCalled();
    expect(identify).not.toHaveBeenCalled();
  });

  it('waits until page load without holding up initialization', async () => {
    vi.spyOn(document, 'readyState', 'get').mockReturnValue('loading');
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await analytics.startUmamiAnalytics();
    expect(tracker()).toBeNull();
    window.dispatchEvent(new Event('load'));
    expect(tracker()).not.toBeNull();
    window.dispatchEvent(new Event('load'));
    expect(document.querySelectorAll('#mtl-umami-analytics')).toHaveLength(1);
  });

  it.each([
    { path: '/track/123', grouped: '/track/:id', data: { track_id: '123' } },
    { path: '/track/9007199254740993/', grouped: '/track/:id', data: { track_id: '9007199254740993' } },
    { path: '/plan/456', grouped: '/plan/:id', data: { plan_id: '456' } },
    { path: '/review/789', grouped: '/review/:id', data: { source_id: '789' } },
    { path: '/admin/data-status', grouped: '/admin/:section', data: { admin_section: 'data-status' } },
  ])('groups the first pageview for $path and retains its parameter as event data', async ({ path, grouped, data }) => {
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await analytics.startUmamiAnalytics();
    const payload = {
      website: activeConfig.websiteId,
      url: `${window.location.origin}/mtl${path}?secret=hidden#detail`,
    };
    expect(beforeSend('event', payload)).toEqual({
      website: activeConfig.websiteId,
      url: `/mtl${grouped}`,
      data,
    });
    expect(payload.url).toContain('?secret=hidden#detail');
    expect(identify).not.toHaveBeenCalled();
  });

  it('groups internal referrers and preserves custom event data without adding a second event', async () => {
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await analytics.startUmamiAnalytics();
    const payload = {
      name: 'track-opened',
      url: '/mtl/track/123',
      referrer: `${window.location.origin}/mtl/plan/456?secret=hidden#detail`,
      data: { source: 'map' },
    };
    expect(beforeSend('event', payload)).toEqual({
      ...payload,
      url: '/mtl/track/:id',
      referrer: '/mtl/plan/:id',
      data: { source: 'map', track_id: '123' },
    });
    expect(payload.data).toEqual({ source: 'map' });
    expect(identify).not.toHaveBeenCalled();
  });

  it.each(['identify', 'performance'])('does not add route parameters to %s data', async (type) => {
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await analytics.startUmamiAnalytics();
    expect(beforeSend(type, { url: '/mtl/track/123', data: { existing: true } })).toEqual({
      url: '/mtl/track/:id',
      data: { existing: true },
    });
  });

  it.each(['/mtl/', '/mtl/login', '/mtl/stats', '/mtl/plan', '/mtl/review', '/mtl/admin'])(
    'keeps the static route %s without route data',
    async (url) => {
      const analytics = await import('@/bootstrap/umamiAnalytics');
      await analytics.startUmamiAnalytics();
      expect(beforeSend('event', { url, referrer: '' })).toEqual({ url, referrer: '' });
    }
  );

  it.each(['/', '/custom/'])('uses the frontend mount path %s', async (base) => {
    vi.stubEnv('BASE_URL', base);
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await analytics.startUmamiAnalytics();
    expect(beforeSend('event', { url: `${base}track/123` })).toEqual({
      url: `${base}track/:id`,
      data: { track_id: '123' },
    });
  });

  it.each(['https://external.example/mtl/track/123', '/other/track/123', 'https://[invalid'])(
    'leaves external, unrelated or invalid URLs unchanged: %s',
    async (url) => {
      const analytics = await import('@/bootstrap/umamiAnalytics');
      await analytics.startUmamiAnalytics();
      expect(beforeSend('event', { url, referrer: url })).toEqual({ url, referrer: url });
    }
  );

  it('finishes initialization when an enabled tracker never loads or defines window.umami', async () => {
    Reflect.deleteProperty(window, 'umami');
    const analytics = await import('@/bootstrap/umamiAnalytics');
    // No load/error event: a stalled external request must not hold startup open.
    await expect(analytics.startUmamiAnalytics()).resolves.toBeUndefined();
    await expect(analytics.startUmamiAnalytics()).resolves.toBeUndefined();
    expect(tracker()?.async).toBe(true);
    expect(document.querySelectorAll('#mtl-umami-analytics')).toHaveLength(1);
    expect('umami' in window).toBe(false);
    expect(getClientLoginRef).not.toHaveBeenCalled();
    expect(identify).not.toHaveBeenCalled();
  });

  it('stops identification and payloads if Do Not Track becomes enabled before the tracker loads', async () => {
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await analytics.startUmamiAnalytics();
    vi.stubGlobal('doNotTrack', '1');
    tracker()!.dispatchEvent(new Event('load'));
    expect(beforeSend('event')).toBeNull();
    expect(identify).not.toHaveBeenCalled();
  });

  it.each(['1', 'yes', 1])('does not request the tracker when Do Not Track is enabled: %s', async (preference) => {
    const previous = Object.getOwnPropertyDescriptor(navigator, 'doNotTrack');
    Object.defineProperty(navigator, 'doNotTrack', { configurable: true, value: preference });
    try {
      const analytics = await import('@/bootstrap/umamiAnalytics');
      await analytics.startUmamiAnalytics();
      expect(tracker()).toBeNull();
      expect(analytics.umamiAnalyticsEnabled.value).toBe(true);
      expect(identify).not.toHaveBeenCalled();
    } finally {
      if (previous) Object.defineProperty(navigator, 'doNotTrack', previous);
      else Reflect.deleteProperty(navigator, 'doNotTrack');
    }
  });

  it.each([
    { ...activeConfig, websiteId: '' },
    { ...activeConfig, scriptUrl: '' },
    { ...activeConfig, scriptUrl: 'javascript:alert(1)' },
    { ...activeConfig, scriptUrl: 'http://stats.example.com/script.js' },
    { ...activeConfig, scriptUrl: 'invalid URL' },
  ])('leaves incomplete or invalid settings disabled: %j', async (config) => {
    getUmamiConfig.mockResolvedValue(config);
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await expect(analytics.startUmamiAnalytics()).resolves.toBeUndefined();
    expect(tracker()).toBeNull();
    expect(analytics.umamiAnalyticsEnabled.value).toBe(false);
  });

  it.each([
    new Error('Configuration endpoint returned 404'),
    new TypeError('Failed to fetch'),
    new DOMException('Configuration request timed out', 'TimeoutError'),
  ])('keeps startup usable without Umami when configuration fails: %s', async (error) => {
    Reflect.deleteProperty(window, 'umami');
    getUmamiConfig.mockRejectedValue(error);
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await expect(analytics.startUmamiAnalytics()).resolves.toBeUndefined();
    expect(tracker()).toBeNull();
    expect('mtlUmamiBeforeSend' in window).toBe(false);
    expect(analytics.umamiAnalyticsEnabled.value).toBe(false);
    expect(getClientLoginRef).not.toHaveBeenCalled();
    expect(console.warn).toHaveBeenCalled();
  });

  it('handles a blocked tracker without throwing or retrying', async () => {
    Reflect.deleteProperty(window, 'umami');
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await analytics.startUmamiAnalytics();
    expect(() => tracker()?.dispatchEvent(new Event('error'))).not.toThrow();
    await analytics.startUmamiAnalytics();
    expect(getUmamiConfig).toHaveBeenCalledOnce();
    expect('umami' in window).toBe(false);
    expect(getClientLoginRef).not.toHaveBeenCalled();
    expect(identify).not.toHaveBeenCalled();
    expect(console.warn).toHaveBeenCalledWith(
      'Umami analytics tracker could not be loaded.',
      expect.objectContaining({ severity: 'WARN' })
    );
  });
});
