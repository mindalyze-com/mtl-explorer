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
import {
  getAuthHeaderValue,
  getToken,
  isAuthenticationFailureStatus,
  redirectToLoginAfterAuthFailure,
} from '@/utils/auth';
import { backendBasePath } from '@/utils/apiBase';
import { HTTP_MISDIRECTED_REQUEST, isRetryableReadRequest } from '@/utils/misdirectedRequests';

export function getApiConfiguration(): Configuration {
  const requestToken = getToken();
  const authHeader = getAuthHeaderValue();
  const authFailureMiddleware: Middleware = {
    async post({ response }) {
      if (isAuthenticationFailureStatus(response.status)) {
        redirectToLoginAfterAuthFailure(!!requestToken, requestToken);
      }
      return response;
    },
  };
  return new Configuration({
    basePath: backendBasePath,
    headers: authHeader ? { Authorization: authHeader } : {},
    credentials: 'include',
    middleware: [authFailureMiddleware],
    fetchApi: async (url, init) => {
      const response = await fetch(url, init);
      // Generated calls use URL strings and replayable JSON bodies. Leave
      // arbitrary Request objects (which can contain consumed streams) alone.
      const requestUrl = typeof url === 'string' ? url : url instanceof URL ? url.href : null;
      if (
        response.status === HTTP_MISDIRECTED_REQUEST &&
        requestUrl &&
        isRetryableReadRequest(init?.method, requestUrl) &&
        !init?.signal?.aborted &&
        requestToken === getToken()
      ) {
        // One fresh read attempt. Connection selection belongs to the browser;
        // a persistent proxy failure still propagates to the normal diagnostics.
        return fetch(url, { ...init, cache: 'no-store' });
      }
      return response;
    },
  });
}

export { getDiagnosticApiConfiguration } from '@/utils/diagnosticApiConfiguration';
