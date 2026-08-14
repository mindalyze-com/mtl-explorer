import { beforeEach, describe, expect, it, vi } from 'vitest';

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
    clearMapConfigCache();
    mocks.apiGet.mockReset();
    mocks.readJsonStorage.mockReset();
    mocks.apiGet.mockRejectedValue(new Error('unavailable'));
  });

  it('marks the built-in fallback as a visible recoverable failure', async () => {
    mocks.readJsonStorage.mockReturnValue(null);

    const config = await fetchMapConfig();

    expect(config).toMatchObject({ tileMode: 'remote', offline: true, configLoadFailed: true });
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
