import * as maplibregl from 'maplibre-gl';
import { Protocol } from 'pmtiles';
// fetch() uses connect-src, which excludes data: URLs. Keep this PNG as a
// same-origin asset even when it falls below Vite's inline size threshold.
import seaLevelTileUrl from '@/assets/map/sea-level-dem.png?url&no-inline';

const PROTOCOL_NAME = 'mtl-terrain';
const GLOBAL_ARCHIVE_MAX_ZOOM = 12;
const GLOBAL_ARCHIVE_URL = 'https://download.mapterhorn.com/planet.pmtiles';
const DETAIL_TILE_BASE_URL = 'https://tiles.mapterhorn.com';
export const MAPTERHORN_TERRAIN_TILES_URL = `${PROTOCOL_NAME}://{z}/{x}/{y}`;

let registered = false;
let seaLevelTile: Promise<ArrayBuffer> | undefined;

function getSeaLevelTile(): Promise<ArrayBuffer> {
  seaLevelTile ??= fetch(seaLevelTileUrl).then((response) => {
    if (!response.ok) throw new Error(`Sea-level terrain tile failed: HTTP ${response.status}`);
    return response.arrayBuffer();
  });
  return seaLevelTile;
}

export function ensureMapterhornProtocol(): void {
  if (registered) return;
  const protocol = new Protocol();
  maplibregl.addProtocol(PROTOCOL_NAME, async (params, abortController) => {
    const path = params.url.slice(`${PROTOCOL_NAME}://`.length);
    const zoom = Number(path.split('/')[0]);
    if (zoom <= GLOBAL_ARCHIVE_MAX_ZOOM) {
      // Ocean tiles are omitted from the archive. Reading its directory avoids
      // the ZXY endpoint's 404s and supplies flat sea-level elevation instead.
      const response = await protocol.tilev4(
        { ...params, url: `pmtiles://${GLOBAL_ARCHIVE_URL}/${path}.webp` },
        abortController
      );
      const data = response.data ?? (await getSeaLevelTile()).slice(0);
      abortController.signal.throwIfAborted();
      return { ...response, data };
    }

    const url = `${DETAIL_TILE_BASE_URL}/${path}.webp`;
    const response = await fetch(url, {
      signal: abortController.signal,
    });
    // MapLibre uses HTTP 404 to keep looking for parent elevation tiles.
    // A plain Error loses that distinction and reports missing coverage as a render failure.
    if (!response.ok) {
      throw Object.assign(new Error(`Detailed terrain tile failed: HTTP ${response.status}`), {
        status: response.status,
      });
    }
    return {
      data: await response.arrayBuffer(),
      cacheControl: response.headers.get('Cache-Control'),
      expires: response.headers.get('Expires'),
    };
  });
  registered = true;
}
