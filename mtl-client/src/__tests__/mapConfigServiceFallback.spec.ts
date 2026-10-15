import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  readJsonStorage: vi.fn(),
}));

vi.mock('@/utils/apiClient', () => ({
  apiClient: { get: mocks.apiGet },
}));

vi.mock('@/utils/appStorage', () => ({
  STORAGE_KEYS: { mapConfigCache: 'map-config' },
  readJsonStorage: mocks.readJsonStorage,
  writeJsonStorage: vi.fn(),
}));

vi.mock('@/utils/startupDiagnostics', () => ({
  describeError: vi.fn(() => ({ message: 'unavailable' })),
  startStartupTimer: vi.fn(() => ({ success: vi.fn(), warn: vi.fn() })),
  startupLog: vi.fn(),
}));

import { clearMapConfigCache, fetchMapConfig } from '@/utils/mapConfigService';

describe('map config fallback status', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    clearMapConfigCache();
    mocks.apiGet.mockReset();
    mocks.readJsonStorage.mockReset();
    mocks.apiGet.mockRejectedValue(new Error('unavailable'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('waits for a twenty-second response without falling back and shares the request', async () => {
    const serverConfig = { tileMode: 'local', tileBaseUrl: '/tiles', remoteRasterStyles: {} };
    mocks.apiGet.mockImplementationOnce(
      () => new Promise((resolve) => setTimeout(() => resolve({ data: serverConfig }), 20_000))
    );

    const first = fetchMapConfig();
    const overlapping = fetchMapConfig();
    await vi.advanceTimersByTimeAsync(15_000);

    expect(mocks.apiGet).toHaveBeenCalledTimes(1);
    expect(mocks.apiGet).toHaveBeenCalledWith('api/map/config', { timeout: 60_000 });
    expect(mocks.readJsonStorage).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(5_000);
    await expect(Promise.all([first, overlapping])).resolves.toEqual([serverConfig, serverConfig]);
    expect(mocks.readJsonStorage).not.toHaveBeenCalled();
  });

  it('marks the built-in fallback as a visible recoverable failure', async () => {
    mocks.readJsonStorage.mockReturnValue(null);

    const config = await fetchMapConfig();

    expect(config).toMatchObject({ tileMode: 'remote', offline: true, configLoadFailed: true });
    expect(config.remoteRasterStyles.dark).toEqual(config.remoteRasterStyles.light);
    expect(config.remoteRasterStyles.dark.url).toBe('https://tile.openstreetmap.org/{z}/{x}/{y}.png');
  });

  it('marks a stored fallback as a visible recoverable failure', async () => {
    mocks.readJsonStorage.mockReturnValue({
      tileMode: 'local',
      tileBaseUrl: '/tiles',
      tilesetName: 'planet',
      lowzoomTilesetName: 'world-lowzoom',
      remoteRasterStyles: {},
    });

    const config = await fetchMapConfig();

    expect(config).toMatchObject({ tileMode: 'local', offline: true, configLoadFailed: true });
  });
});
