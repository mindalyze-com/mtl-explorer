export type RedirectUriNotice = {
  tone: 'info' | 'warning' | 'error';
  text: string;
};

const IPV4_PATTERN = /^\d{1,3}(?:\.\d{1,3}){3}$/;
const LOOPBACK_IPV6 = new Set(['::1', '0:0:0:0:0:0:0:1']);

export function buildGoogleHealthRedirectUri(
  backendBaseUrl: string,
  browserOrigin: string,
  callbackPath: string
): string {
  const base = new URL(backendBaseUrl, ensureOriginSlash(browserOrigin));
  const basePath = base.pathname.replace(/\/+$/, '');
  const normalizedCallbackPath = callbackPath.startsWith('/') ? callbackPath : `/${callbackPath}`;
  base.pathname = `${basePath}${normalizedCallbackPath}`;
  base.search = '';
  base.hash = '';
  return base.toString();
}

export function assessGoogleHealthRedirectUri(value: string): RedirectUriNotice[] {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return [{ tone: 'error', text: 'The OAuth redirect URI is not a valid absolute URL.' }];
  }

  const host = url.hostname.toLowerCase();
  const loopback = isLoopbackHost(host);
  const notices: RedirectUriNotice[] = [];

  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    notices.push({ tone: 'error', text: 'Google accepts only HTTP or HTTPS redirect URIs.' });
  } else if (url.protocol === 'http:' && !loopback) {
    notices.push({
      tone: 'error',
      text: 'Google requires HTTPS unless the host is localhost or a loopback address.',
    });
  }

  if (isRawIpAddress(host) && !loopback) {
    notices.push({
      tone: 'error',
      text: 'Google does not accept a raw LAN or public IP address. Use an HTTPS hostname or access the server locally.',
    });
  }

  if (loopback) {
    notices.push({
      tone: 'info',
      text: 'A localhost redirect works only when this browser can reach MTL Explorer on the same device.',
    });
  }

  return notices;
}

export function validateGoogleHealthManualRedirectUrl(value: string, expectedRedirectUri: string): string {
  const trimmedValue = value.trim();
  if (!trimmedValue) return 'Paste the complete address from the browser.';

  let url: URL;
  let expectedUrl: URL;
  try {
    url = new URL(trimmedValue);
    expectedUrl = new URL(expectedRedirectUri);
  } catch {
    return 'The pasted address is not a valid URL.';
  }

  if (
    url.origin !== expectedUrl.origin ||
    url.pathname !== expectedUrl.pathname ||
    url.username ||
    url.password ||
    url.hash
  ) {
    return `Paste the complete ${expectedUrl.origin}${expectedUrl.pathname} address returned by Google.`;
  }

  const parameterNames = new Set<string>();
  for (const [name] of url.searchParams) {
    if (parameterNames.has(name)) return 'The pasted address contains duplicate parameters.';
    parameterNames.add(name);
  }

  if (!url.searchParams.get('state')) {
    return 'The pasted address is missing the Google authorization state.';
  }

  const hasCode = Boolean(url.searchParams.get('code'));
  const hasError = Boolean(url.searchParams.get('error'));
  if (hasCode === hasError) {
    return 'The pasted address has no valid Google authorization result.';
  }

  return '';
}

function ensureOriginSlash(origin: string): string {
  return origin.endsWith('/') ? origin : `${origin}/`;
}

function isLoopbackHost(host: string): boolean {
  if (host === 'localhost' || LOOPBACK_IPV6.has(stripIpv6Brackets(host))) return true;
  if (!isValidIpv4Address(host)) return false;
  return Number(host.split('.')[0]) === 127;
}

function isRawIpAddress(host: string): boolean {
  return stripIpv6Brackets(host).includes(':') || isValidIpv4Address(host);
}

function stripIpv6Brackets(host: string): string {
  return host.replace(/^\[/, '').replace(/\]$/, '');
}

function isValidIpv4Address(host: string): boolean {
  if (!IPV4_PATTERN.test(host)) return false;
  return host.split('.').every((part) => Number(part) >= 0 && Number(part) <= 255);
}
