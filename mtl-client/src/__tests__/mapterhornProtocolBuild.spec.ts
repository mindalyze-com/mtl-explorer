// @vitest-environment node
import { readFileSync } from 'node:fs';
import { fileURLToPath, URL } from 'node:url';
import { build } from 'vite';
import { describe, expect, it } from 'vitest';

describe('Mapterhorn terrain production assets', () => {
  it('emits a same-origin sea-level PNG that fetch can load under the application CSP', async () => {
    const result = await build({
      configFile: false,
      root: fileURLToPath(new URL('../../', import.meta.url)),
      base: '/mtl/',
      logLevel: 'silent',
      resolve: {
        alias: { '@': fileURLToPath(new URL('../', import.meta.url)) },
      },
      build: {
        write: false,
        rollupOptions: {
          input: fileURLToPath(new URL('../utils/mapterhornProtocol.ts', import.meta.url)),
          external: ['maplibre-gl', 'pmtiles'],
          preserveEntrySignatures: 'strict',
        },
      },
    });
    if (Array.isArray(result) || !('output' in result)) throw new Error('Expected one production bundle');

    const tile = result.output.find((item) => item.type === 'asset' && item.fileName.endsWith('.png'));
    expect(tile?.type).toBe('asset');
    if (tile?.type !== 'asset') throw new Error('Sea-level terrain PNG was not emitted');
    expect(Buffer.from(tile.source)).toEqual(readFileSync(new URL('../assets/map/sea-level-dem.png', import.meta.url)));
    const chunks = result.output
      .filter((item) => item.type === 'chunk')
      .map((chunk) => chunk.code)
      .join('\n');
    expect(chunks).toContain(`/mtl/${tile.fileName}`);
    expect(chunks).not.toContain('data:image/png');
  });
});
