import axios from 'axios';

const REDACTED = '<redacted>';
const MAX_DEPTH = 4;
const JWT_PATTERN = /\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g;
const BEARER_PATTERN = /\bBearer\s+[A-Za-z0-9._~+/=-]+/gi;
const URL_CREDENTIALS_PATTERN = /(https?:\/\/)[^\s/@?#]+@/gi;
const URL_QUERY_PATTERN = /((?:https?:\/\/|\.{0,2}\/)[^\s?]+)\?[^\s)"']*?(?=:\d+(?::\d+)?(?:[)\s"']|$)|[)\s"']|$)/g;
const SENSITIVE_ASSIGNMENT_PATTERN =
  /\b(authorization|cookie|password|passwd|token|secret|jwt|lat|lng|lon|longitude|latitude|coord|geometry|filename|trackId|mediaId|health)["']?\s*[=:]\s*(?:"[^"]*"|'[^']*'|[^&\s,;}]+)/gi;
const SENSITIVE_KEY_PARTS = [
  'authorization',
  'cookie',
  'password',
  'passwd',
  'token',
  'secret',
  'jwt',
  'lat',
  'lng',
  'lon',
  'coord',
  'filename',
  'fileName',
  'trackName',
  'trackDescription',
  'filterName',
  'search',
  'body',
  'data',
  'state',
  'health',
  'weight',
  'trackId',
  'mediaId',
  'planId',
];

export function sanitizeForLog(value: unknown, depth = 0, seen = new WeakSet<object>()): unknown {
  if (typeof value === 'string') {
    return sanitizeStringForLog(value);
  }
  if (value == null || typeof value !== 'object') {
    return value;
  }
  if (seen.has(value)) {
    return '[Circular]';
  }
  if (depth >= MAX_DEPTH) {
    return '[Object]';
  }
  seen.add(value);

  if (axios.isAxiosError(value)) {
    return {
      name: sanitizeForLog(value.name),
      message: sanitizeForLog(value.message),
      stack: sanitizeForLog(value.stack),
      code: sanitizeForLog(value.code),
      status: value.response?.status,
      statusText: sanitizeForLog(value.response?.statusText),
      method: sanitizeForLog(value.config?.method),
      url: sanitizeForLog(value.config?.url),
    };
  }

  if (value instanceof Error) {
    return {
      name: sanitizeForLog(value.name),
      message: sanitizeForLog(value.message),
      stack: sanitizeForLog(value.stack),
    };
  }

  if (Array.isArray(value)) {
    return value.map((entry) => sanitizeForLog(entry, depth + 1, seen));
  }

  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([key, entry]) => [
      key,
      isSensitiveKey(key) ? REDACTED : sanitizeForLog(entry, depth + 1, seen),
    ])
  );
}

function isSensitiveKey(key: string): boolean {
  const normalized = key.toLowerCase();
  return SENSITIVE_KEY_PARTS.some((part) => normalized.includes(part.toLowerCase()));
}

function sanitizeStringForLog(value: string): string {
  return value
    .replace(BEARER_PATTERN, `Bearer ${REDACTED}`)
    .replace(JWT_PATTERN, REDACTED)
    .replace(URL_CREDENTIALS_PATTERN, `$1${REDACTED}@`)
    .replace(URL_QUERY_PATTERN, `$1?${REDACTED}`)
    .replace(SENSITIVE_ASSIGNMENT_PATTERN, (_match, key: string) => `${key}=${REDACTED}`);
}
