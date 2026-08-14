import axios, { AxiosError, type AxiosAdapter } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import router from '@/router';
import { apiClient } from '@/utils/apiClient';
import {
  clearToken,
  getAuthenticatedUsername,
  getToken,
  getUserSessionId,
  isAuthenticationFailureStatus,
  isAuthError,
  redirectToLoginAfterAuthFailure,
  setToken,
} from '@/utils/auth';

describe('auth token helpers', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('reads the username and session id from the JWT payload', () => {
    setToken(makeJwt({ sub: 'temp', user_session_id: 'session-123' }));

    expect(getAuthenticatedUsername()).toBe('temp');
    expect(getUserSessionId()).toBe('session-123');
  });

  it('returns null when the readable JWT is absent', () => {
    clearToken();

    expect(getAuthenticatedUsername()).toBeNull();
    expect(getUserSessionId()).toBeNull();
  });

  it('treats only 401 responses as authentication failures', () => {
    expect(isAuthenticationFailureStatus(401)).toBe(true);
    expect(isAuthenticationFailureStatus(403)).toBe(false);
    expect(isAuthError({ response: { status: 401 } })).toBe(true);
    expect(isAuthError({ response: { status: 403 } })).toBe(false);
  });

  it('keeps the session when a public request is forbidden', async () => {
    setToken(makeJwt({ exp: futureExpirationSeconds() }));
    const token = getToken();

    await expect(axios.get('/api/auth/demo-status', { adapter: rejectWithStatus(403) })).rejects.toMatchObject({
      response: { status: 403 },
    });

    expect(getToken()).toBe(token);
  });

  it('keeps the session when an authenticated request is forbidden', async () => {
    setToken(makeJwt({ exp: futureExpirationSeconds() }));
    const token = getToken();

    await expect(apiClient.get('/api/admin/test', { adapter: rejectWithStatus(403) })).rejects.toMatchObject({
      response: { status: 403 },
    });

    expect(getToken()).toBe(token);
  });

  it('keeps the session on a public route after a late authentication failure', async () => {
    setToken(makeJwt({ exp: futureExpirationSeconds() }));
    const token = getToken();
    await router.push('/about');
    const push = vi.spyOn(router, 'push');

    redirectToLoginAfterAuthFailure(true);

    expect(push).not.toHaveBeenCalled();
    expect(router.currentRoute.value.name).toBe('about');
    expect(getToken()).toBe(token);
    push.mockRestore();
  });

  it('redirects a protected route after an authentication failure', async () => {
    setToken(makeJwt({ exp: futureExpirationSeconds() }));
    await router.push('/');
    const push = vi.spyOn(router, 'push').mockResolvedValue();

    redirectToLoginAfterAuthFailure(true);

    expect(push).toHaveBeenCalledWith({ path: '/login', query: { reason: 'expired' } });
    expect(getToken()).toBeNull();
    push.mockRestore();
  });
});

function rejectWithStatus(status: number): AxiosAdapter {
  return async (config) => {
    throw new AxiosError(`Request failed with status code ${status}`, AxiosError.ERR_BAD_RESPONSE, config, undefined, {
      config,
      data: {},
      headers: {},
      status,
      statusText: status === 403 ? 'Forbidden' : 'Unauthorized',
    });
  };
}

function futureExpirationSeconds(): number {
  return Math.floor(Date.now() / 1000) + 60;
}

function makeJwt(payload: Record<string, unknown>): string {
  return `${base64Url({ alg: 'none', typ: 'JWT' })}.${base64Url(payload)}.signature`;
}

function base64Url(value: Record<string, unknown>): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
