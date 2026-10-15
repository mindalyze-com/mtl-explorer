import { afterEach, describe, expect, it, vi } from 'vitest';
import { runConnectivityProbe } from '@/composables/useConnectivityProbe';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('startup connectivity probe', () => {
  it('uses the public backend endpoint before sign-in and probes external assets without credentials', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ status: 200, ok: true });
    vi.stubGlobal('fetch', fetchMock);
    const table = vi.spyOn(console, 'table').mockImplementation(() => {});
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    await runConnectivityProbe();
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('api/auth/demo-status'), {
      method: 'HEAD',
      mode: 'cors',
      cache: 'no-store',
    });
    expect(fetchMock.mock.calls.some(([url]) => url.includes('info/build'))).toBe(false);
    expect(fetchMock).toHaveBeenCalledWith('https://tiles.mapterhorn.com/0/0/0.webp', {
      method: 'HEAD',
      mode: 'no-cors',
      cache: 'no-store',
    });
    expect(table).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({ status: 'OK (200)' })]));
    expect(warn).not.toHaveBeenCalled();
  });

  it('reports a backend HTTP failure instead of treating it as an opaque success', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 503, ok: false }));
    const table = vi.spyOn(console, 'table').mockImplementation(() => {});
    await runConnectivityProbe();
    expect(table).toHaveBeenCalledWith(
      expect.arrayContaining([expect.objectContaining({ label: 'Backend API', status: 'HTTP 503' })])
    );
  });
});
