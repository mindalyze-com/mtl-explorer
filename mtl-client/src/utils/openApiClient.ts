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
import { Configuration } from 'x8ing-mtl-api-typescript-fetch';
import { getAuthHeaderValue } from '@/utils/auth';

const backendUrl: string = import.meta.env.VITE_BACKEND_URL ?? '';

export function getApiConfiguration(): Configuration {
  return new Configuration({
    basePath: backendUrl.replace(/\/$/, ''),
    headers: {
      Authorization: getAuthHeaderValue(),
    },
    credentials: 'include',
  });
}
