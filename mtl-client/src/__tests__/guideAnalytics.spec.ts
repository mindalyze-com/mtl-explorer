import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { flushPromises } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const source = readFileSync('../documentation/tools/website/guide-analytics.js', 'utf8');
const fetchConfig = vi.fn();
const config = {
  enabled: true,
  scriptUrl: 'https://stats.example.com/script.js',
  websiteId: '12345678-1234-1234-1234-123456789abc',
};
function tracker(): HTMLScriptElement | null {
  return document.querySelector('#mtl-umami-analytics');
}

function runGuide(): void {
  runInNewContext(source, {
    window,
    navigator,
    document,
    fetch: fetchConfig,
    URL,
    AbortSignal,
    atob,
    Uint8Array,
    TextDecoder,
    Date,
  });
}

beforeEach(() => {
  tracker()?.remove();
  localStorage.clear();
  localStorage.setItem('mtl.jwt', 'unused-synthetic-login-token');
  const loader = document.createElement('script');
  loader.src = 'https://mtl.test/mtl/guide/guide-analytics.js';
  vi.spyOn(document, 'currentScript', 'get').mockReturnValue(loader);
  vi.spyOn(document, 'readyState', 'get').mockReturnValue('complete');
  vi.spyOn(Storage.prototype, 'getItem');
  vi.spyOn(Storage.prototype, 'setItem');
  fetchConfig.mockReset().mockResolvedValue({ ok: true, json: async () => config });
});

afterEach(() => {
  tracker()?.remove();
  localStorage.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('installed guide analytics', () => {
  it('uses the app setting and default Umami identification without accessing browser storage', async () => {
    runGuide();
    await flushPromises();
    expect(fetchConfig).toHaveBeenCalledExactlyOnceWith('https://mtl.test/mtl/api/analytics/umami-config', {
      credentials: 'omit',
      cache: 'no-store',
      signal: expect.any(AbortSignal),
    });
    expect(tracker()?.src).toBe(config.scriptUrl);
    expect(tracker()?.async).toBe(true);
    expect(tracker()?.dataset).toMatchObject({
      websiteId: config.websiteId,
      domains: window.location.hostname,
      doNotTrack: 'true',
      excludeSearch: 'true',
      excludeHash: 'true',
    });
    expect(localStorage.getItem).not.toHaveBeenCalled();
    expect(tracker()?.dataset.distinctId).toBeUndefined();
    expect(localStorage.setItem).not.toHaveBeenCalled();
    runGuide();
    await flushPromises();
    expect(document.querySelectorAll('#mtl-umami-analytics')).toHaveLength(1);
  });

  it.each([
    undefined,
    null,
    {},
    { enabled: false },
    { ...config, enabled: false },
    { ...config, enabled: 'true' },
    { ...config, scriptUrl: '' },
    { ...config, websiteId: '' },
  ])('loads no tracker and reads no login token for disabled or incomplete settings: %j', async (value) => {
    fetchConfig.mockResolvedValue({ ok: true, json: async () => value });
    runGuide();
    await flushPromises();
    expect(tracker()).toBeNull();
    expect(localStorage.getItem).not.toHaveBeenCalled();
  });

  it.each([
    'http://stats.example.com/script.js',
    'javascript:alert(1)',
    'https://user:password@stats.example.com/script.js',
    'https://stats.example.com/script.js?secret=hidden',
    'https://stats.example.com/script.js#fragment',
    'invalid URL',
  ])('does not load an invalid tracker URL: %s', async (scriptUrl) => {
    fetchConfig.mockResolvedValue({ ok: true, json: async () => ({ ...config, scriptUrl }) });
    runGuide();
    await flushPromises();
    expect(tracker()).toBeNull();
    expect(localStorage.getItem).not.toHaveBeenCalled();
  });

  it('uses default tracking even when browser storage is blocked', async () => {
    vi.mocked(localStorage.getItem).mockImplementation(() => {
      throw new Error('Storage blocked');
    });
    runGuide();
    await flushPromises();
    expect(tracker()).not.toBeNull();
    expect(tracker()?.dataset.distinctId).toBeUndefined();
  });

  it.each(['reject', 'http error', 'invalid json'])('ignores unavailable configuration: %s', async (mode) => {
    if (mode === 'reject') fetchConfig.mockRejectedValue(new TypeError('Offline'));
    else
      fetchConfig.mockResolvedValue({
        ok: mode !== 'http error',
        json: async () => {
          throw new SyntaxError('Not JSON');
        },
      });
    runGuide();
    await flushPromises();
    expect(tracker()).toBeNull();
    expect(localStorage.getItem).not.toHaveBeenCalled();
  });

  it('waits for page load and never waits for a stalled configuration request', async () => {
    vi.spyOn(document, 'readyState', 'get').mockReturnValue('loading');
    fetchConfig.mockReturnValue(new Promise(() => {}));
    expect(runGuide()).toBeUndefined();
    expect(fetchConfig).not.toHaveBeenCalled();
    window.dispatchEvent(new Event('load'));
    await flushPromises();
    expect(fetchConfig).toHaveBeenCalledOnce();
    expect(tracker()).toBeNull();
  });

  it.each(['1', 'yes', 1])('loads no tracker or login reference with Do Not Track: %s', async (flag) => {
    vi.stubGlobal('doNotTrack', flag);
    runGuide();
    await flushPromises();
    expect(fetchConfig).not.toHaveBeenCalled();
    expect(tracker()).toBeNull();
    expect(localStorage.getItem).not.toHaveBeenCalled();
  });

  it('rechecks Do Not Track after receiving configuration', async () => {
    runGuide();
    vi.stubGlobal('doNotTrack', '1');
    await flushPromises();
    expect(fetchConfig).toHaveBeenCalledOnce();
    expect(tracker()).toBeNull();
    expect(localStorage.getItem).not.toHaveBeenCalled();
  });
});
