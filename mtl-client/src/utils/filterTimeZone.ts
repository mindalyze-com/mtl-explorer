import type { FilterParamsRequest } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterParamsRequest';

const DEFAULT_TIME_ZONE = 'UTC';

/** Calendar filters follow the same browser timezone as date controls and track dates. */
export function withBrowserTimeZone(params?: FilterParamsRequest | null): FilterParamsRequest {
  return {
    ...params,
    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || DEFAULT_TIME_ZONE,
  };
}
