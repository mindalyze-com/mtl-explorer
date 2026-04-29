import axios from "axios";
import router from "@/router";
import { USER_PREFS_KEYS, migrateLegacyKeys } from "@/utils/userPrefs";

const TOKEN_KEY = USER_PREFS_KEYS.jwt;
const JWT_PART_COUNT = 3;
const JWT_USER_SESSION_ID_CLAIM = 'user_session_id';
const JWT_ISSUED_AT_CLAIM = 'iat';
const JWT_EXPIRATION_CLAIM = 'exp';
const SECONDS_TO_MILLISECONDS = 1000;

type JwtPayload = Record<string, unknown>;

export function getToken(): string | null {
    migrateLegacyKeys();
    return localStorage.getItem(TOKEN_KEY);
}

export function getUserSessionId(): string | null {
    const payload = getJwtPayload();
    const userSessionId = payload?.[JWT_USER_SESSION_ID_CLAIM];
    return typeof userSessionId === 'string' && userSessionId.trim() ? userSessionId : null;
}

export function getTokenExpiresAt(): Date | null {
    return getJwtNumericDate(JWT_EXPIRATION_CLAIM);
}

export function getTokenIssuedAt(): Date | null {
    return getJwtNumericDate(JWT_ISSUED_AT_CLAIM);
}

export function setToken(token: string) {
    localStorage.setItem(TOKEN_KEY, token);
}

/**
 * Clear only client-readable auth credentials.
 * Does NOT wipe cached tracks, preferences, IndexedDB, Cache Storage, or service workers.
 * Does NOT call the server logout endpoint — use {@link logoutCredentialsOnly} when the
 * HttpOnly cookie should be cleared as well.
 *
 * This is safe to call from 401 interceptors where the JWT is already
 * invalid on the server and a fire-and-forget logout would race with
 * the next login (deleting the freshly-set HttpOnly cookie).
 */
export function clearToken() {
    localStorage.removeItem(TOKEN_KEY);
}

export function redirectToLoginAfterAuthFailure(hadCredential = !!getToken()) {
    clearToken();
    router.push({
        path: '/login',
        ...(hadCredential ? { query: { reason: 'expired' } } : {}),
    }).catch(() => {});
}

/**
 * Credentials-only logout: removes the local JWT and asks the server to clear
 * the HttpOnly cookie. Everything else remains in place for a quick login.
 */
export async function logoutCredentialsOnly(): Promise<void> {
    localStorage.removeItem(TOKEN_KEY);
    const backendUrl = import.meta.env.VITE_BACKEND_URL ?? '';
    try {
        await fetch(`${backendUrl}api/auth/logout`, { method: 'POST', credentials: 'include' });
    } catch { /* best-effort */ }
}

/**
 * Full local logout: asks the server to delete the HttpOnly JWT cookie, then
 * removes every browser-side app store we can access.
 */
export async function logoutAndForgetEverything(): Promise<void> {
    const backendUrl = import.meta.env.VITE_BACKEND_URL ?? '';
    try {
        await fetch(`${backendUrl}api/auth/logout`, { method: 'POST', credentials: 'include' });
    } catch { /* best-effort */ }

    try { localStorage.clear(); } catch { /* best-effort */ }
    try { sessionStorage.clear(); } catch { /* best-effort */ }
    clearReadableCookies();

    await Promise.all([
        wipeIndexedDB(),
        wipeCacheStorage(),
        unregisterServiceWorkers(),
    ]);
}

async function wipeIndexedDB() {
    if (typeof indexedDB !== 'undefined' && indexedDB.databases) {
        try {
            const dbs = await indexedDB.databases();
            await Promise.all(
                dbs.filter(db => db.name).map(db =>
                    new Promise<void>((resolve) => {
                        const req = indexedDB.deleteDatabase(db.name!);
                        req.onsuccess = () => resolve();
                        req.onerror = () => resolve(); // best-effort
                        req.onblocked = () => resolve();
                    })
                )
            );
        } catch { /* best-effort */ }
    }
}

async function wipeCacheStorage() {
    if (typeof caches === 'undefined') return;
    try {
        const cacheKeys = await caches.keys();
        await Promise.all(cacheKeys.map(key => caches.delete(key)));
    } catch { /* best-effort */ }
}

async function unregisterServiceWorkers() {
    if (!('serviceWorker' in navigator)) return;
    try {
        const registrations = await navigator.serviceWorker.getRegistrations();
        await Promise.all(registrations.map(registration => registration.unregister()));
    } catch { /* best-effort */ }
}

function clearReadableCookies() {
    if (typeof document === 'undefined') return;
    try {
        document.cookie.split(';').forEach(cookie => {
            const name = cookie.trim().split('=')[0];
            if (name) {
                document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 UTC;path=/`;
            }
        });
    } catch { /* best-effort */ }
}

export const lightLogout = logoutCredentialsOnly;
export const serverLogout = logoutAndForgetEverything;

export function isAuthenticated(): boolean {
    return !!getToken();
}

export function getAuthHeaderValue(): string {
    const token = getToken();
    if (token) {
        return `Bearer ${token}`;
    }
    return "";
}

function getJwtPayload(): JwtPayload | null {
    const token = getToken();
    if (!token) return null;

    const parts = token.split('.');
    if (parts.length !== JWT_PART_COUNT) return null;

    try {
        return JSON.parse(decodeBase64Url(parts[1])) as JwtPayload;
    } catch {
        return null;
    }
}

function getJwtNumericDate(claimName: string): Date | null {
    const payload = getJwtPayload();
    const timestampSeconds = payload?.[claimName];
    if (typeof timestampSeconds !== 'number' || !Number.isFinite(timestampSeconds)) {
        return null;
    }
    return new Date(timestampSeconds * SECONDS_TO_MILLISECONDS);
}

function decodeBase64Url(value: string): string {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const binary = atob(padded);
    const bytes = Array.from(binary, char => `%${char.charCodeAt(0).toString(16).padStart(2, '0')}`);
    return decodeURIComponent(bytes.join(''));
}

export function isAuthError(error: unknown): boolean {
    if (axios.isAxiosError(error) && error.response) {
        const status = error.response.status;
        return status === 401 || status === 403;
    }
    const status = (error as { response?: { status?: number } })?.response?.status;
    if (status === 401 || status === 403) {
        return true;
    }
    return false;
}

function requestHadAuthHeader(config: unknown): boolean {
    const headers = (config as { headers?: unknown } | undefined)?.headers;
    if (!headers) return false;
    const getHeader = (headers as { get?: (name: string) => unknown }).get;
    if (typeof getHeader === 'function') {
        return !!(getHeader.call(headers, 'Authorization') || getHeader.call(headers, 'authorization'));
    }
    const record = headers as Record<string, unknown>;
    return !!(record.Authorization || record.authorization);
}

// Global axios response interceptor.
//
// Most app traffic now goes through `apiClient` (utils/apiClient.ts) which
// has its own equivalent interceptor. This one is the safety net for the
// remaining bare-axios call sites:
//   - LoginView.vue (login bootstrap, before a token exists)
//   - serverAdminApi.getDemoStatus (public endpoint)
//   - any third-party lib that imports axios directly
// Keeps behavior identical to the apiClient interceptor on purpose.
axios.interceptors.response.use(
    (response) => {
        return response;
    },
    (error) => {
        if (!error.response && !axios.isCancel(error)) {
            console.error(
                "🚨 [Network Drop] Request failed without a server response! " +
                "This usually means the domain could not be resolved (DNS blocker), " +
                "the request was blocked by an extension, or the client is entirely offline.", 
                { url: error.config?.url, errorMessages: error.message }
            );
        }

        if (error.response && (error.response.status === 401 || error.response.status === 403)) {
            // Don't redirect if the failed request was the login call itself
            const url = error.config?.url || "";
            if (!url.includes("/api/auth/login")) {
                redirectToLoginAfterAuthFailure(requestHadAuthHeader(error.config) || !!getToken());
            }
        }
        return Promise.reject(error);
    }
);
