/**
 * Probe a list of well-known endpoints (backend, CDN-hosted basemap assets,
 * tiles) to detect networks that block parts of the page from loading.
 *
 * Extracted from App.vue. Runs once and writes a console.table summary; if any
 * probe fails it also emits a console.warn so users notice the issue when
 * inspecting devtools.
 */

interface Probe {
  label: string;
  url: string;
}

interface ProbeResult {
  label: string;
  status: string;
  ms: number;
}

const PROBES: ReadonlyArray<Probe> = [
  { label: 'Backend API', url: './api/info/build' },
  { label: 'Protomaps fonts (CDN)', url: 'https://protomaps.github.io/basemaps-assets/fonts/Noto%20Sans%20Regular/0-255.pbf' },
  { label: 'Protomaps sprites (CDN)', url: 'https://protomaps.github.io/basemaps-assets/sprites/v4/light.json' },
  { label: 'Mapterhorn elevation tiles', url: 'https://tiles.mapterhorn.com/0/0/0.webp' },
  { label: 'OSM raster tiles', url: 'https://tile.openstreetmap.org/0/0/0.png' },
];

export async function runConnectivityProbe(): Promise<void> {
  const results: ProbeResult[] = await Promise.all(
    PROBES.map(async ({ label, url }) => {
      const t0 = performance.now();
      try {
        const resp = await fetch(url, { method: 'HEAD', mode: 'no-cors', cache: 'no-store' });
        const ms = Math.round(performance.now() - t0);
        // no-cors returns opaque response (status 0) — that's still a successful network round-trip.
        const ok = resp.status === 0 || resp.ok;
        return { label, status: ok ? `OK (${resp.status})` : `HTTP ${resp.status}`, ms };
      } catch (err: unknown) {
        const ms = Math.round(performance.now() - t0);
        const reason = err instanceof TypeError ? err.message : String(err);
        return { label, status: `BLOCKED — ${reason}`, ms };
      }
    })
  );

  const blocked = results.filter((r) => r.status.startsWith('BLOCKED'));
  if (blocked.length > 0) {
    console.warn(
      `[MTL] Connectivity probe: ${blocked.length} resource(s) appear BLOCKED.\n` +
        'This is likely caused by a network filter, firewall, or content-based DPI rule.\n' +
        'The app may not render correctly (blank map, missing labels/icons).'
    );
  }
  console.table(results);
}
