/**
 * Builds a fresh `Configuration` object for the OpenAPI-generated controllers
 * (TracksControllerApi, FilterControllerApi, ConfigControllerApi, etc.).
 *
 * IMPORTANT: this MUST be called per-request rather than cached, because the
 * Authorization header is captured at construction time. The hand-rolled
 * axios client (`apiClient.ts`) avoids this by stamping the header on every
 * request via an interceptor — the generated client predates that pattern,
 * so we keep building a new Configuration each call.
 */
import { Configuration, type Middleware } from 'x8ing-mtl-api-typescript-fetch';
import { getAuthHeaderValue, getToken, redirectToLoginAfterAuthFailure } from '@/utils/auth';
import { backendBasePath } from '@/utils/apiBase';

const AUTH_FAILURE_STATUSES = new Set([401, 403]);

const authFailureMiddleware: Middleware = {
  async post({ response }) {
    if (AUTH_FAILURE_STATUSES.has(response.status)) {
      redirectToLoginAfterAuthFailure(!!getToken());
    }
    return response;
  },
};

export function getApiConfiguration(): Configuration {
  const authHeader = getAuthHeaderValue();
  return new Configuration({
    basePath: backendBasePath,
    headers: authHeader ? { Authorization: authHeader } : {},
    credentials: 'include',
    middleware: [authFailureMiddleware],
  });
}
