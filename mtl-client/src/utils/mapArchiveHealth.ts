import { PMTiles, TileType } from 'pmtiles';
import { CachingFetchSource } from '@/utils/cachingPmtilesSource';

const ARCHIVE_PROBE_TIMEOUT_MS = 5000;

/** Validate a fresh archive header and root directory, independent of the browser's cached ranges. */
export async function probeVectorMapArchive(url: string, signal: AbortSignal): Promise<void> {
  const source = new CachingFetchSource(url, 'no-store');
  const requestSignal = AbortSignal.any([signal, AbortSignal.timeout(ARCHIVE_PROBE_TIMEOUT_MS)]);
  const archive = new PMTiles({
    getKey: () => source.getKey(),
    getBytes: (offset, length, _signal, etag) => source.getBytes(offset, length, requestSignal, etag),
  });
  const header = await archive.getHeader();
  if (header.tileType !== TileType.Mvt) throw new Error('Map archive does not contain vector tiles.');
}
