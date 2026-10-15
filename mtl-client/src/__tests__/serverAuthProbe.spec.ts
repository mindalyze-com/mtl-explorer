import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import axios from 'axios';

const { apiGet, timer } = vi.hoisted(() => ({
  apiGet: vi.fn(),
  timer: { success: vi.fn(), warn: vi.fn(), error: vi.fn() },
}));

vi.mock('@/utils/apiClient', () => ({ apiClient: { get: apiGet } }));
vi.mock('@/utils/startupDiagnostics', () => ({ startStartupTimer: () => timer }));

import { checkServerAuth, getDemoStatus, triggerGarminExport } from '@/utils/serverAdminApi';

describe('startup auth probe', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.resetAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('accepts a twenty-second response with a sixty-second request budget', async () => {
    apiGet.mockImplementationOnce(() => new Promise((resolve) => setTimeout(() => resolve({ data: '' }), 20_000)));

    const pending = checkServerAuth();
    await vi.advanceTimersByTimeAsync(20_000);

    await expect(pending).resolves.toBe('ok');
    expect(apiGet).toHaveBeenCalledWith('api/info/build', { responseType: 'text', timeout: 60_000 });
  });

  it('reports invalid credentials immediately', async () => {
    apiGet.mockRejectedValueOnce({ isAxiosError: true, response: { status: 401 } });

    await expect(checkServerAuth()).resolves.toBe('auth-error');
    expect(timer.success).toHaveBeenCalledWith('Auth probe requires sign-in', { status: 401 });
    expect(timer.warn).not.toHaveBeenCalled();
  });

  it('reports an actual network failure immediately', async () => {
    const failure = new Error('Connection refused');
    apiGet.mockRejectedValueOnce(failure);

    await expect(checkServerAuth()).resolves.toBe('network-error');
    expect(timer.warn).toHaveBeenCalledWith('Auth probe failed due to network issue', failure);
  });

  it('retains the original Garmin export failure as the user-facing error cause', async () => {
    const failure = new Error('Synthetic transport failure');
    apiGet.mockRejectedValueOnce(failure);
    const result = await triggerGarminExport().catch((error: unknown) => error);
    expect(result).toBeInstanceOf(Error);
    expect((result as Error).message).toBe('Failed to trigger Garmin export: Synthetic transport failure');
    expect((result as Error).cause).toBe(failure);
  });

  it('preserves tolerant demo probes while allowing write controls to require a confirmed response', async () => {
    const failure = new Error('Synthetic transport failure');
    vi.spyOn(axios, 'get').mockRejectedValue(failure);
    await expect(getDemoStatus()).resolves.toEqual({ demoMode: false });
    await expect(getDemoStatus({ throwOnFailure: true })).rejects.toBe(failure);
    expect(axios.get).toHaveBeenLastCalledWith(expect.stringContaining('api/auth/demo-status'), { timeout: 60_000 });
  });

  it('rejects missing installation status in a strict probe', async () => {
    vi.spyOn(axios, 'get')
      .mockResolvedValueOnce({ data: null })
      .mockResolvedValueOnce({ data: { demoMode: true } });
    await expect(getDemoStatus({ throwOnFailure: true })).rejects.toThrow('Installation status is unavailable.');
    await expect(getDemoStatus({ throwOnFailure: true })).resolves.toEqual({ demoMode: true });
  });
});
