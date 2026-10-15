/** Download only the public terrain tiles needed by the MTL Explorer demo. */
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PMTiles, findTile, zxyToTileId } from '../../../mtl-client/node_modules/pmtiles/dist/esm/index.js';

const DOWNLOAD_WORKERS = 4;
const MAX_DIRECTORY_DEPTH = 4;
const REQUEST_TIMEOUT_MS = 30_000;
const configPath = join(dirname(fileURLToPath(import.meta.url)), 'demo-elevation-config.json');

async function main() {
  const output = process.argv[2];
  if (!output) throw new Error('Usage: node download_demo_elevation.mjs <output-directory>');
  const config = JSON.parse(await readFile(configPath, 'utf8'));
  const started = performance.now();
  const archive = new PMTiles(config.source);
  const header = await archive.getHeader();
  if (header.etag !== config.source_etag) {
    throw new Error('Mapterhorn source changed. Review the new terrain source and update demo-elevation-config.json.');
  }
  const [west, south, east, north] = config.track_bounds;
  const margin = (config.coverage_factor - 1) / 2;
  const dx = (east - west) * margin, dy = (north - south) * margin;
  const bounds = [west - dx, south - dy, east + dx, north + dy];
  const scale = 2 ** config.zoom * config.tile_size;
  const pixelX = lon => (lon + 180) / 360 * scale;
  const pixelY = lat => (1 - Math.asinh(Math.tan(lat * Math.PI / 180)) / Math.PI) / 2 * scale;
  // Include the neighbouring samples needed by interpolation on the coverage boundary.
  const range = [Math.floor((pixelX(bounds[0]) - 1) / config.tile_size),
    Math.floor((pixelY(bounds[3]) - 1) / config.tile_size),
    Math.floor((pixelX(bounds[2]) + 1) / config.tile_size),
    Math.floor((pixelY(bounds[1]) + 1) / config.tile_size)];
  await mkdir(output, { recursive: true });
  const tiles = {}, entries = new Map();
  for (let x = range[0]; x <= range[2]; x++) {
    for (let y = range[1]; y <= range[3]; y++) {
      const id = zxyToTileId(config.zoom, x, y);
      let offset = header.rootDirectoryOffset, length = header.rootDirectoryLength, entry;
      for (let depth = 0; depth < MAX_DIRECTORY_DEPTH; depth++) {
        const directory = await archive.cache.getDirectory(archive.source, offset, length, header);
        entry = findTile(directory, id);
        if (!entry || entry.runLength > 0) break;
        offset = header.leafDirectoryOffset + entry.offset;
        length = entry.length;
      }
      if (entry && entry.runLength === 0) throw new Error(`Unresolved terrain directory at ${x}/${y}`);
      tiles[`${x}/${y}`] = entry ? `${entry.offset}.webp` : null;
      if (entry) entries.set(entry.offset, entry.length);
    }
  }
  const pending = [...entries.entries()];
  let completed = 0, bytes = 0;
  const hashes = {};
  console.log(`Downloading Mapterhorn demo terrain: unique tiles=${pending.length}, zoom=${config.zoom}, bounds=${bounds}.`);
  await Promise.all(Array.from({ length: DOWNLOAD_WORKERS }, async () => {
    while (pending.length) {
      const [offset, length] = pending.shift();
      const filename = `${offset}.webp`;
      const response = await archive.source.getBytes(header.tileDataOffset + offset, length,
        AbortSignal.timeout(REQUEST_TIMEOUT_MS), header.etag);
      const data = Buffer.from(response.data);
      await writeFile(join(output, filename), data);
      hashes[filename] = createHash('sha256').update(data).digest('hex');
      bytes += data.length;
      if (++completed % 20 === 0 || completed === entries.size) {
        console.log(`Terrain download progress: ${completed}/${entries.size}, bytes=${bytes}.`);
      }
    }
  }));
  const metadata = { ...config, bounds, tile_range: range, tiles, hashes, source_bytes: bytes };
  await writeFile(join(output, 'manifest.json'), JSON.stringify(metadata, null, 2) + '\n');
  // Keep the upstream attribution snapshot alongside the raster preparation input.
  const attribution = await fetch('https://download.mapterhorn.com/attribution.json', {
    headers: { 'User-Agent': 'MTL-Explorer-demo-elevation' }, signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  if (!attribution.ok) throw new Error(`Terrain attribution request failed: HTTP ${attribution.status}`);
  await writeFile(join(output, 'attribution.json'), JSON.stringify(await attribution.json(), null, 2) + '\n');
  console.log(`Terrain download completed: bytes=${bytes}, duration=${((performance.now() - started) / 1000).toFixed(2)}s.`);
}

main().catch(error => { console.error(`Demo terrain download failed: ${error.name}: ${error.message}`); process.exitCode = 1; });
