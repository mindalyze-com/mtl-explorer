import { PMTiles, type Source, type RangeResponse } from 'pmtiles';

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
 */
class CachingFetchSource implements Source {
  private url: string;
  private mustReload = false;

  constructor(url: string) {
    this.url = url;
  }

  getKey(): string {
    return this.url;
  }

  async getBytes(
    offset: number,
    length: number,
    signal?: AbortSignal,
    etag?: string,
  ): Promise<RangeResponse> {
    const headers = new Headers();
    headers.set('Range', `bytes=${offset}-${offset + length - 1}`);

    const resp = await fetch(this.url, {
      signal,
      cache: this.mustReload ? 'reload' : 'force-cache',
      headers,
    });

    // Handle edge case: archive smaller than initial probe size
    if (offset === 0 && resp.status === 416) {
      const contentRange = resp.headers.get('Content-Range');
      if (!contentRange || !contentRange.startsWith('bytes */')) {
        throw new Error('Missing content-length on 416 response');
      }
      const actualLength = +contentRange.substr(8);
      headers.set('Range', `bytes=0-${actualLength - 1}`);
      const retry = await fetch(this.url, {
        signal,
        cache: 'reload',
        headers,
      });
      const a = await retry.arrayBuffer();
      return {
        data: a,
        etag: getStrongEtag(retry) || undefined,
        cacheControl: retry.headers.get('Cache-Control') || undefined,
        expires: retry.headers.get('Expires') || undefined,
      };
    }

    let newEtag = getStrongEtag(resp);

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
        'Check that your storage backend supports HTTP Byte Serving.',
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

function getStrongEtag(resp: Response): string | null {
  const etag = resp.headers.get('ETag');
  if (etag?.startsWith('W/')) return null; // weak etag not useful
  return etag;
}

/**
 * Create a PMTiles instance backed by a caching fetch source.
 * Register the result on the Protocol with `protocol.add(pmtiles)` BEFORE
 * the MapLibre map starts requesting tiles.
 */
export function createCachingPMTiles(url: string): PMTiles {
  return new PMTiles(new CachingFetchSource(url));
}
