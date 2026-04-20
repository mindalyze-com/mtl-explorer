import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios';
import router from '@/router';
import { clearToken, getAuthHeaderValue } from '@/utils/auth';

/**
 * Single shared axios instance for all backend calls.
 *
 * Replaces the per-call boilerplate that used to look like:
 *   axios.get(backendUrl + 'api/...', {
 *     headers: { Authorization: getAuthHeaderValue() },
 *     withCredentials: true,
 *   })
 *
 * Why an instance (not the global `axios`)?
 *   - Centralizes baseURL so call sites pass relative paths.
 *   - The request interceptor stamps a fresh Authorization header on every
 *     call. Per-call headers were being captured at module import time in
 *     a few places, which would silently break after token refresh.
 *   - The response interceptor handles 401/403 the same way the global
 *     one does (clear token + redirect to /login). Shared behavior, one
 *     place to change.
 *
 * The global axios instance still has its own interceptor (in `utils/auth.ts`)
 * because some code paths (e.g. login, third-party libs that import axios
 * directly) bypass this client. Both interceptors do the same thing.
 */

const backendUrl: string = import.meta.env.VITE_BACKEND_URL ?? '';

export const apiClient: AxiosInstance = axios.create({
  baseURL: backendUrl,
  withCredentials: true,
});

apiClient.interceptors.request.use((config) => {
  // Stamp on every request so token rotation is picked up automatically.
  const auth = getAuthHeaderValue();
  if (auth) {
    config.headers = config.headers ?? {};
    (config.headers as Record<string, string>).Authorization = auth;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (!error.response && !axios.isCancel(error)) {
      console.error(
        '🚨 [Network Drop] apiClient request failed without a server response',
        { url: error.config?.url, message: error.message }
      );
    }
    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
      const url: string = error.config?.url ?? '';
      // Don't redirect if the failed request was the login call itself
      if (!url.includes('/api/auth/login')) {
        clearToken();
        router.push({ path: '/login', query: { reason: 'expired' } }).catch(() => {});
      }
    }
    return Promise.reject(error);
  }
);

/**
 * Convenience wrapper used by code that needs to share an AbortSignal but
 * doesn't want to import AxiosRequestConfig.
 */
export type RequestOptions = Pick<AxiosRequestConfig, 'signal' | 'timeout' | 'headers' | 'params' | 'responseType'>;
