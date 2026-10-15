import { Configuration } from 'x8ing-mtl-api-typescript-fetch';
import { readStorage, STORAGE_KEYS } from '@/utils/appStorage';
import { backendBasePath } from '@/utils/apiBase';

// Deliberately independent of router, stores, auth redirects and reporting interceptors.
export interface DiagnosticSession {
  token: string;
  account: string;
  loginRef: string | null;
  expiresAt: number;
}

export const DIAGNOSTIC_AUTH_CHANGED = 'mtl:diagnostic-auth-changed';

export function getDiagnosticSession(): DiagnosticSession | null {
  const token = readStorage(STORAGE_KEYS.jwt);
  if (!token) return null;
  try {
    const encoded = token.split('.')[1]!;
    const bytes = Uint8Array.from(atob(encoded.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
    const payload = JSON.parse(new TextDecoder().decode(bytes));
    if (
      typeof payload.sub !== 'string' ||
      !payload.sub ||
      typeof payload.exp !== 'number' ||
      !Number.isFinite(payload.exp)
    )
      return null;
    return {
      token,
      account: payload.sub,
      loginRef: typeof payload.client_login_ref === 'string' ? payload.client_login_ref : null,
      expiresAt: payload.exp * 1000,
    };
  } catch {
    return null;
  }
}

export function getDiagnosticApiConfiguration(session = getDiagnosticSession()): Configuration | null {
  if (!session || session.expiresAt <= Date.now()) return null;
  return new Configuration({
    basePath: backendBasePath,
    headers: { Authorization: `Bearer ${session.token}` },
    credentials: 'include',
    middleware: [],
  });
}
