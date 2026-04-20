import axios from "axios";
import router from "@/router";
import { USER_PREFS_KEYS, migrateLegacyKeys } from "@/utils/userPrefs";

const TOKEN_KEY = USER_PREFS_KEYS.jwt;

export function getToken(): string | null {
    migrateLegacyKeys();
    return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
    localStorage.setItem(TOKEN_KEY, token);
}

/**
 * Clear the JWT token from localStorage and wipe cached data.
 * Does NOT call the server logout endpoint — use {@link serverLogout} for that.
 *
 * This is safe to call from 401 interceptors where the JWT is already
 * invalid on the server and a fire-and-forget logout would race with
 * the next login (deleting the freshly-set HttpOnly cookie).
 */
export function clearToken() {
    localStorage.removeItem(TOKEN_KEY);
    wipeIndexedDB();
}

/**
 * Full logout: clears local state AND tells the server to delete the
 * HttpOnly JWT cookie.  Awaitable so callers can wait for the cookie
 * to be cleared before navigating to the login page.
 */
export async function serverLogout(): Promise<void> {
    localStorage.removeItem(TOKEN_KEY);
    const backendUrl = import.meta.env.VITE_BACKEND_URL ?? '';
    try {
        await fetch(`${backendUrl}api/auth/logout`, { method: 'POST', credentials: 'include' });
    } catch { /* best-effort */ }
    await wipeIndexedDB();
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

export function isAuthError(error: unknown): boolean {
    if (axios.isAxiosError(error) && error.response) {
        const status = error.response.status;
        return status === 401 || status === 403;
    }
    return false;
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
                clearToken();
                router.push({ path: '/login', query: { reason: 'expired' } }).catch(() => {});
            }
        }
        return Promise.reject(error);
    }
);
