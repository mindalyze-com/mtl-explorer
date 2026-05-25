import axios from 'axios';

const REDACTED = '<redacted>';
const MAX_DEPTH = 4;
const JWT_PATTERN = /\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g;
const BEARER_PATTERN = /\bBearer\s+[A-Za-z0-9._~+/=-]+/gi;
const SENSITIVE_ASSIGNMENT_PATTERN =
  /\b(authorization|cookie|password|passwd|token|secret|jwt|lat|lng|lon|longitude|latitude|coord|geometry|filename|fileName)=([^&\s]+)/gi;
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
      name: value.name,
      message: value.message,
      code: value.code,
      status: value.response?.status,
      statusText: value.response?.statusText,
      method: value.config?.method,
      url: sanitizeForLog(value.config?.url),
    };
  }

  if (value instanceof Error) {
    return {
      name: value.name,
      message: value.message,
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

export function logSanitizedError(message: string, error: unknown, context?: unknown): void {
  if (context === undefined) {
    console.error(message, sanitizeForLog(error));
    return;
  }
  console.error(message, sanitizeForLog(error), sanitizeForLog(context));
}

export function warnSanitized(message: string, error: unknown, context?: unknown): void {
  if (context === undefined) {
    console.warn(message, sanitizeForLog(error));
    return;
  }
  console.warn(message, sanitizeForLog(error), sanitizeForLog(context));
}

function isSensitiveKey(key: string): boolean {
  const normalized = key.toLowerCase();
  return SENSITIVE_KEY_PARTS.some((part) => normalized.includes(part.toLowerCase()));
}

function sanitizeStringForLog(value: string): string {
  return value
    .replace(BEARER_PATTERN, `Bearer ${REDACTED}`)
    .replace(JWT_PATTERN, REDACTED)
    .replace(SENSITIVE_ASSIGNMENT_PATTERN, (_match, key: string) => `${key}=${REDACTED}`);
}
