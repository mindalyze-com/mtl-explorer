const DEFAULT_BASE_URL = '/';
const ABSOLUTE_URL_PATTERN = /^[a-z][a-z\d+\-.]*:\/\//i;

function ensureTrailingSlash(url: string): string {
  return url.endsWith('/') ? url : `${url}/`;
}

function normalizeAppBaseUrl(appBaseUrl: string | null | undefined): string {
  const fallback = appBaseUrl?.trim() || DEFAULT_BASE_URL;
  const withLeadingSlash = fallback.startsWith('/') ? fallback : `/${fallback}`;
  return ensureTrailingSlash(withLeadingSlash);
}

export function normalizeBackendBaseUrl(
  configuredUrl: string | null | undefined,
  appBaseUrl: string | null | undefined = import.meta.env.BASE_URL
): string {
  const appBase = normalizeAppBaseUrl(appBaseUrl);
  const raw = configuredUrl?.trim();

  if (!raw || raw === '.' || raw === './') {
    return appBase;
  }

  if (raw.startsWith('./')) {
    return ensureTrailingSlash(`${appBase}${raw.slice(2)}`);
  }

  const withLeadingSlash = ABSOLUTE_URL_PATTERN.test(raw) || raw.startsWith('/') ? raw : `/${raw}`;
  return ensureTrailingSlash(withLeadingSlash);
}

export const backendBaseUrl = normalizeBackendBaseUrl(import.meta.env.VITE_BACKEND_URL, import.meta.env.BASE_URL);

export const backendBasePath = backendBaseUrl === DEFAULT_BASE_URL ? '' : backendBaseUrl.replace(/\/$/, '');

export function apiUrl(path: string): string {
  return `${backendBaseUrl}${path.replace(/^\/+/, '')}`;
}
