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
  vi.spyOn(document, 'readyState', 'get').mockReturnValue('complete');
  vi.spyOn(console, 'warn').mockImplementation(() => {});
});

afterEach(() => {
  const callbackName = tracker()?.dataset.beforeSend;
  if (callbackName) Reflect.deleteProperty(window, callbackName);
  tracker()?.remove();
  vi.unstubAllGlobals();
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

  it('identifies the current client login reference once after the tracker loads', async () => {
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await analytics.startUmamiAnalytics();
    expect(identify).not.toHaveBeenCalled();
    tracker()!.dispatchEvent(new Event('load'));
    expect(identify).toHaveBeenCalledExactlyOnceWith(clientLoginRef);
    beforeSend('event');
    tracker()!.dispatchEvent(new Event('load'));
    expect(identify).toHaveBeenCalledOnce();
  });

  it('identifies the first automatic pageview before load without recursive identify calls', async () => {
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await analytics.startUmamiAnalytics();
    identify.mockImplementation(() => {
      expect(beforeSend('identify')).toEqual({ id: clientLoginRef });
      return Promise.resolve();
    });
    expect(beforeSend('event', { website: activeConfig.websiteId, url: '/mtl/' })).toEqual({
      website: activeConfig.websiteId,
      url: '/mtl/',
      id: clientLoginRef,
    });
    tracker()!.dispatchEvent(new Event('load'));
    expect(identify).toHaveBeenCalledExactlyOnceWith(clientLoginRef);
  });

  it('uses the latest client login reference when login changes during tracker loading', async () => {
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await analytics.startUmamiAnalytics();
    getClientLoginRef.mockReturnValue('replacement-login');
    tracker()!.dispatchEvent(new Event('load'));
    expect(identify).toHaveBeenCalledExactlyOnceWith('replacement-login');
  });

  it.each(['missing', 'expired', 'no client login reference claim'])(
    'leaves a %s client login unidentified',
    async (state) => {
      if (state === 'no client login reference claim') getClientLoginRef.mockReturnValue(null);
      else isAuthenticated.mockReturnValue(false);
      const analytics = await import('@/bootstrap/umamiAnalytics');
      await analytics.startUmamiAnalytics();
      tracker()!.dispatchEvent(new Event('load'));
      expect(beforeSend('event', { id: 'stale-login', url: '/mtl/login' })).toEqual({
        id: undefined,
        url: '/mtl/login',
      });
      expect(identify).not.toHaveBeenCalled();
    }
  );

  it('follows login, logout, another login and expiry on subsequent pageviews', async () => {
    isAuthenticated.mockReturnValue(false);
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await analytics.startUmamiAnalytics();
    tracker()!.dispatchEvent(new Event('load'));
    expect(identify).not.toHaveBeenCalled();

    isAuthenticated.mockReturnValue(true);
    expect(beforeSend('event')?.id).toBe(clientLoginRef);
    beforeSend('event');
    expect(identify).toHaveBeenCalledExactlyOnceWith(clientLoginRef);

    isAuthenticated.mockReturnValue(false);
    expect(beforeSend('event', { id: clientLoginRef })?.id).toBeUndefined();
    beforeSend('event');
    expect(identify.mock.calls).toEqual([[clientLoginRef], ['']]);

    isAuthenticated.mockReturnValue(true);
    getClientLoginRef.mockReturnValue('new-login');
    expect(beforeSend('event')?.id).toBe('new-login');
    expect(identify.mock.calls).toEqual([[clientLoginRef], [''], ['new-login']]);

    isAuthenticated.mockReturnValue(false);
    expect(beforeSend('event', { id: 'new-login' })?.id).toBeUndefined();
    expect(identify.mock.calls).toEqual([[clientLoginRef], [''], ['new-login'], ['']]);
  });

  it.each([undefined, null, {}, { identify: undefined }, { identify: 'unavailable' }])(
    'tolerates a loaded script without a usable identify API: %j',
    async (umami) => {
      vi.stubGlobal('umami', umami);
      if (umami === undefined) Reflect.deleteProperty(window, 'umami');
      const analytics = await import('@/bootstrap/umamiAnalytics');
      await analytics.startUmamiAnalytics();
      expect(() => tracker()!.dispatchEvent(new Event('load'))).not.toThrow();
      expect(beforeSend('event')?.id).toBe(clientLoginRef);
      expect(identify).not.toHaveBeenCalled();
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

  it('never waits for an identify request that does not settle', async () => {
    identify.mockReturnValue(new Promise(() => {}));
    const analytics = await import('@/bootstrap/umamiAnalytics');
    await analytics.startUmamiAnalytics();
    tracker()!.dispatchEvent(new Event('load'));
    expect(beforeSend('event')?.id).toBe(clientLoginRef);
    expect(beforeSend('event', { url: '/mtl/about' })).toEqual({ url: '/mtl/about', id: clientLoginRef });
    await expect(analytics.startUmamiAnalytics()).resolves.toBeUndefined();
    expect(identify).toHaveBeenCalledExactlyOnceWith(clientLoginRef);
  });

  it.each(['throw', 'reject'])(
    'handles identify %s without blocking pageviews and retries on the next event',
    async (mode) => {
      const error = new Error('Tracker unavailable');
      identify.mockImplementationOnce(() => {
        if (mode === 'throw') throw error;
        return Promise.reject(error);
      });
      const analytics = await import('@/bootstrap/umamiAnalytics');
      await analytics.startUmamiAnalytics();
      expect(beforeSend('event')?.id).toBe(clientLoginRef);
      await Promise.resolve();
      expect(console.warn).toHaveBeenCalled();
      expect(beforeSend('event')?.id).toBe(clientLoginRef);
      expect(identify).toHaveBeenCalledTimes(2);
    }
  );

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
