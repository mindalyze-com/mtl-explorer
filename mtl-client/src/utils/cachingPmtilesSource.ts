import { PMTiles, type Source, type RangeResponse } from 'pmtiles';
import { isAbortLikeError } from '@/utils/errors';

export const MAP_ARCHIVE_STALE_EVENT = 'mtl-map-archive-stale';

const CONTENT_RANGE_SIZE_PREFIX = 'bytes */';
const FORCE_CACHE_DISABLED_URLS = new Set<string>();

/**
 * A PMTiles Source that uses `cache: 'force-cache'` on fetch() calls.
 *
 * Chrome does not reliably serve cached 206 Partial Content responses with the
 * default fetch cache policy ('default'). It revalidates via `If-Range` on every
 * request, defeating the `Cache-Control: immutable` header.
 *
 * `force-cache` tells the browser to serve from the HTTP cache (fresh or stale)
 * without revalidation, falling back to the network only on a complete cache miss.
 *
 * If the server-side PMTiles file changes (ETag mismatch), the library's own ETag
 * comparison detects this and sets `mustReload`, which flips to `cache: 'reload'`
 * to bypass stale cache entries.
 *
 * Some managed Chromium/Edge installations intermittently fail cached Range
 * reads with net::ERR_CACHE_OPERATION_NOT_SUPPORTED. If a force-cache request
 * fails before producing a usable response, retry without the HTTP cache and
 * keep using no-store for this archive for the rest of the page session.
 */
export class CachingFetchSource implements Source {
  private url: string;
  private mustReload = false;

  constructor(url: string) {
    this.url = url;
  }

  getKey(): string {
    return this.url;
  }

  async getBytes(offset: number, length: number, signal?: AbortSignal, etag?: string): Promise<RangeResponse> {
    const cacheMode = this.cacheMode();
    try {
      return await this.fetchRangeResponse(offset, length, cacheMode, signal, etag);
    } catch (error) {
      if (!shouldRetryWithoutForceCache(cacheMode, signal, error)) {
        throw error;
      }

      FORCE_CACHE_DISABLED_URLS.add(this.url);
      console.warn('PMTiles HTTP cache failed; retrying archive range requests without browser cache.', {
        url: this.url,
        message: describeError(error),
      });
      return this.fetchRangeResponse(offset, length, 'no-store', signal, etag);
    }
  }

  private cacheMode(): RequestCache {
    if (FORCE_CACHE_DISABLED_URLS.has(this.url)) {
      return 'no-store';
    }
    return this.mustReload ? 'reload' : 'force-cache';
  }

  private async fetchRangeResponse(
    offset: number,
    length: number,
    cache: RequestCache,
    signal?: AbortSignal,
    etag?: string
  ): Promise<RangeResponse> {
    const headers = byteRangeHeaders(offset, length);

    const resp = await fetch(this.url, {
      signal,
      cache,
      headers,
    });

    if (resp.status === 409) {
      notifyMapArchiveStale(this.url);
      throw new Error('Map archive changed; refreshing map config.');
    }

    // Handle edge case: archive smaller than initial probe size
    if (offset === 0 && resp.status === 416) {
      const contentRange = resp.headers.get('Content-Range');
      if (!contentRange || !contentRange.startsWith(CONTENT_RANGE_SIZE_PREFIX)) {
        throw new Error('Missing content-length on 416 response');
      }
      const actualLength = Number(contentRange.slice(CONTENT_RANGE_SIZE_PREFIX.length));
      if (!Number.isSafeInteger(actualLength) || actualLength <= 0) {
        throw new Error('Invalid content-length on 416 response');
      }
      const retryHeaders = byteRangeHeaders(0, actualLength);
      const retry = await fetch(this.url, {
        signal,
        cache: probeRetryCacheMode(cache),
        headers: retryHeaders,
      });
      if (retry.status === 409) {
        notifyMapArchiveStale(this.url);
        throw new Error('Map archive changed; refreshing map config.');
      }
      const retryEtag = getStrongEtag(retry);
      if (retry.status === 416 || (etag && retryEtag && retryEtag !== etag)) {
        this.mustReload = true;
        throw new Error('Server returned non-matching ETag. PMTiles file may have changed.');
      }
      if (retry.status >= 300) {
        throw new Error(`Bad response code: ${retry.status}`);
      }
      const a = await retry.arrayBuffer();
      return {
        data: a,
        etag: retryEtag || undefined,
        cacheControl: retry.headers.get('Cache-Control') || undefined,
        expires: retry.headers.get('Expires') || undefined,
      };
    }

    const newEtag = getStrongEtag(resp);

    // ETag mismatch or 416 after retry — server-side file changed
    if (resp.status === 416 || (etag && newEtag && newEtag !== etag)) {
      this.mustReload = true;
      throw new Error('Server returned non-matching ETag. PMTiles file may have changed.');
    }

    if (resp.status >= 300) {
      throw new Error(`Bad response code: ${resp.status}`);
    }

    // Detect servers that ignore Range and return the full file
    const contentLength = resp.headers.get('Content-Length');
    if (resp.status === 200 && (!contentLength || +contentLength > length)) {
      throw new Error(
        'Server returned no content-length header or content-length exceeding request. ' +
          'Check that your storage backend supports HTTP Byte Serving.'
      );
    }

    const a = await resp.arrayBuffer();
    return {
      data: a,
      etag: newEtag || undefined,
      cacheControl: resp.headers.get('Cache-Control') || undefined,
      expires: resp.headers.get('Expires') || undefined,
    };
  }
}

function byteRangeHeaders(offset: number, length: number): Headers {
  const headers = new Headers();
  headers.set('Range', `bytes=${offset}-${offset + length - 1}`);
  return headers;
}

function getStrongEtag(resp: Response): string | null {
  const etag = resp.headers.get('ETag');
  if (etag?.startsWith('W/')) return null; // weak etag not useful
  return etag;
}

function probeRetryCacheMode(cache: RequestCache): RequestCache {
  return cache === 'no-store' ? 'no-store' : 'reload';
}

function shouldRetryWithoutForceCache(cache: RequestCache, signal: AbortSignal | undefined, error: unknown): boolean {
  if (cache === 'no-store' || isAbortLikeError(error, signal)) {
    return false;
  }

  return error instanceof TypeError || isCacheOperationFailure(error);
}

function isCacheOperationFailure(error: unknown): boolean {
  const message = describeError(error);
  return message.includes('ERR_CACHE_');
}

function describeError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function notifyMapArchiveStale(url: string): void {
  window.dispatchEvent(new CustomEvent(MAP_ARCHIVE_STALE_EVENT, { detail: { url } }));
}

/**
 * Create a PMTiles instance backed by a caching fetch source.
 * Register the result on the Protocol with `protocol.add(pmtiles)` BEFORE
 * the MapLibre map starts requesting tiles.
 */
export function createCachingPMTiles(url: string): PMTiles {
  return new PMTiles(new CachingFetchSource(url));
}
