export const HTTP_MISDIRECTED_REQUEST = 421;

const READ_ONLY_POST_PATHS = new Set([
  '/api/tracks/get-simplified',
  '/api/tracks/get-track-overview',
  '/api/tracks/get-track-statistics',
  '/api/media/trends',
  '/api/media/trends/items',
]);

/** Only replay reads; do not retry login, imports, edits or other mutations. */
export function isRetryableReadRequest(method: string | undefined, url: string): boolean {
  const path = new URL(url, 'http://localhost').pathname.replace(/^\/mtl(?=\/api\/)/, '');
  if (!path.startsWith('/api/')) return false;
  const normalizedMethod = (method ?? 'GET').toUpperCase();
  return (
    normalizedMethod === 'GET' ||
    normalizedMethod === 'HEAD' ||
    (normalizedMethod === 'POST' && READ_ONLY_POST_PATHS.has(path))
  );
}
