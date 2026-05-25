import { beforeEach, describe, expect, it } from 'vitest';
import { clearToken, getAuthenticatedUsername, getUserSessionId, setToken } from '@/utils/auth';

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
});

function makeJwt(payload: Record<string, unknown>): string {
  return `${base64Url({ alg: 'none', typ: 'JWT' })}.${base64Url(payload)}.signature`;
}

function base64Url(value: Record<string, unknown>): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
