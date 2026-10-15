import axios, { AxiosError, type AxiosAdapter } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import router from '@/router';
import { apiClient } from '@/utils/apiClient';
import { getApiConfiguration } from '@/utils/openApiClient';
import {
  clearToken,
  getAuthenticatedUsername,
  getToken,
  getClientLoginRef,
  isAuthenticationFailureStatus,
  isAuthError,
  redirectToLoginAfterAuthFailure,
  setToken,
} from '@/utils/auth';

describe('auth token helpers', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('reads the username and client login reference from the JWT payload', () => {
    setToken(makeJwt({ sub: 'temp', client_login_ref: 'login-123' }));

    expect(getAuthenticatedUsername()).toBe('temp');
    expect(getClientLoginRef()).toBe('login-123');
  });

  it('returns null when the readable JWT is absent', () => {
    clearToken();

    expect(getAuthenticatedUsername()).toBeNull();
    expect(getClientLoginRef()).toBeNull();
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

  it('ignores a late generated-client 401 after a newer sign-in on a protected route', async () => {
    setToken(makeJwt({ exp: futureExpirationSeconds(), client_login_ref: 'old-session' }));
    await router.push('/');
    const configuration = getApiConfiguration();
    setToken(makeJwt({ exp: futureExpirationSeconds(), client_login_ref: 'new-session' }));
    const token = getToken();
    const push = vi.spyOn(router, 'push').mockResolvedValue();
    try {
      await configuration.middleware[0]!.post!({
        response: new Response(null, { status: 401 }),
        url: '/api/test',
        init: {},
        fetch: vi.fn(),
      });
      expect(getToken()).toBe(token);
      expect(push).not.toHaveBeenCalled();
    } finally {
      push.mockRestore();
    }
  });

  it('ignores a late axios 401 after a newer sign-in on a protected route', async () => {
    setToken(makeJwt({ exp: futureExpirationSeconds(), client_login_ref: 'old-session' }));
    await router.push('/');
    const failureAdapter = rejectWithStatus(401);
    let rejectOld!: () => void;
    const pending = apiClient.get('/api/test', {
      adapter: (config) =>
        new Promise((_resolve, reject) => {
          rejectOld = () => {
            failureAdapter(config).catch(reject);
          };
        }),
    });
    const rejected = expect(pending).rejects.toMatchObject({ response: { status: 401 } });
    await Promise.resolve();
    await Promise.resolve();
    setToken(makeJwt({ exp: futureExpirationSeconds(), client_login_ref: 'new-session' }));
    const token = getToken();
    rejectOld();
    await rejected;
    expect(getToken()).toBe(token);
  });

  it('still redirects after a generated-client 401 for the current session', async () => {
    setToken(makeJwt({ exp: futureExpirationSeconds() }));
    await router.push('/');
    const configuration = getApiConfiguration();
    const push = vi.spyOn(router, 'push').mockResolvedValue();
    try {
      await configuration.middleware[0]!.post!({
        response: new Response(null, { status: 401 }),
        url: '/api/test',
        init: {},
        fetch: vi.fn(),
      });
      expect(getToken()).toBeNull();
      expect(push).toHaveBeenCalledWith({ path: '/login', query: { reason: 'expired' } });
    } finally {
      push.mockRestore();
    }
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
