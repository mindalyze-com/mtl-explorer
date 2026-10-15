import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  buildSwissMapUrl,
  fetchSwissRouteDetails,
  parseSwissRouteDetails,
  type SwissRouteDetailsQuery,
} from '@/components/map/swissRouteDetails';

const fetchMock = vi.fn<typeof fetch>();
const location = { lng: 8.83, lat: 47.25 };
const query: SwissRouteDetailsQuery = { layerBodId: 'ch.astra.wanderland', featureId: '4.03', lngLat: location };

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('selected Swiss route details', () => {
  it.each([
    ['ch.astra.wanderland', '4.03', 'hiking-in-switzerland', '4', '3'],
    ['ch.astra.veloland', '3.02', 'cycling-in-switzerland', '3', '2'],
    ['ch.astra.mountainbikeland', '1.01', 'mountainbiking-in-switzerland', '1', '1'],
  ])(
    'keeps the exact published stage and full route links for %s',
    async (layerBodId, featureId, mode, route, stage) => {
      const fullRouteUrl = `https://schweizmobil.ch/en/${mode}/route-${route}`;
      const stageUrl = `${fullRouteUrl}/stage-${stage}`;
      fetchMock.mockResolvedValueOnce(
        htmlResponse(`<table>
      <tr><td>Route</td><td>Public example route</td></tr>
      <tr><td>Link</td><td><a href="${fullRouteUrl}">Route information</a></td></tr>
      <tr><td>Link</td><td><a href="${stageUrl}">Stage information</a></td></tr>
    </table>`)
      );
      const controller = new AbortController();
      const selected = { layerBodId, featureId, lngLat: location };
      const details = await fetchSwissRouteDetails(selected, controller.signal);
      expect(details).toEqual({
        stageLabel: `Stage ${stage}`,
        stageUrl,
        fullRouteUrl,
        swissMapUrl: buildSwissMapUrl(selected),
      });
      expect(fetchMock).toHaveBeenCalledExactlyOnceWith(
        `https://api3.geo.admin.ch/rest/services/ech/MapServer/${layerBodId}/${featureId}/htmlPopup?lang=en&sr=4326&coord=8.83%2C47.25`,
        { signal: controller.signal }
      );
    }
  );

  it('keeps an unnamed Swisstopo segment as a Swiss map link without inventing a route homepage', () => {
    const selected = { ...query, layerBodId: 'ch.swisstopo.swisstlm3d-wanderwege', featureId: 3810394 };
    const details = parseSwissRouteDetails('<table><tr><td>hikingtype</td><td>-</td></tr></table>', selected);
    expect(details).toEqual({ swissMapUrl: buildSwissMapUrl(selected) });
  });

  it('extracts a classification when the popup supplies one', () => {
    const details = parseSwissRouteDetails(
      '<table><tr><td>hikingtype</td><td> Mountain&nbsp;hiking trail </td></tr></table>',
      query
    );
    expect(details.classification).toBe('Mountain hiking trail');
  });

  it('decodes entities, recognizes a published German stage link, and preserves query parameters', () => {
    const stageUrl = 'https://schweizmobil.ch/de/wanderland/route-4/etappe-3?lang=de&view=stage';
    const details = parseSwissRouteDetails(
      `<a href="${stageUrl.replace('&', '&amp;')}">Etappe</a><a href="https://schweizmobil.ch/de/wanderland/route-4">Route</a>`,
      query
    );
    expect(details.stageUrl).toBe(stageUrl);
    expect(details.stageLabel).toBe('Stage 3');
  });

  it('returns only a full route when the popup has no published stage', () => {
    const details = parseSwissRouteDetails(
      '<a href="https://schweizmobil.ch/en/hiking-in-switzerland/route-4">Route</a>',
      query
    );
    expect(details.fullRouteUrl).toBe('https://schweizmobil.ch/en/hiking-in-switzerland/route-4');
    expect(details.stageUrl).toBeUndefined();
    expect(details.stageLabel).toBeUndefined();
  });

  it('rejects unsafe or unrelated anchor destinations and does not execute or fetch popup resources', async () => {
    const unsafe = [
      'javascript:alert(1)',
      'data:text/html,example',
      'http://schweizmobil.ch/en/route-4/stage-3',
      '//schweizmobil.ch/en/route-4/stage-3',
      'https://schweizmobil.ch.example.com/en/route-4/stage-3',
      'https://schweizmobil.ch@evil.example/en/route-4/stage-3',
      'https://user:password@schweizmobil.ch/en/route-4/stage-3',
      'https://schweizmobil.ch:8443/en/route-4/stage-3',
      'https://example.com/en/route-4/stage-3',
      'https://schweizmobil.ch/en/about',
    ];
    fetchMock.mockResolvedValueOnce(
      htmlResponse(
        `<script>window.popupExecuted = true; fetch('https://example.com/script')</script>
       <img src="https://example.com/image"><iframe src="https://example.com/frame"></iframe>
       ${unsafe.map((href) => `<a href="${href}">Untrusted</a>`).join('')}`
      )
    );
    expect(await fetchSwissRouteDetails(query, new AbortController().signal)).toEqual({
      swissMapUrl: buildSwissMapUrl(query),
    });
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(window).not.toHaveProperty('popupExecuted');
    expect(document.querySelector('img,iframe,script')).toBeNull();
  });

  it('builds the documented viewer link with the selected feature and original WGS84 click', () => {
    const url = new URL(buildSwissMapUrl(query));
    expect(url.origin).toBe('https://map.geo.admin.ch');
    expect(url.hash.startsWith('#/map?')).toBe(true);
    const params = new URLSearchParams(url.hash.slice('#/map?'.length));
    expect(params.get('layers')).toBe('ch.astra.wanderland@features=4.03');
    expect(params.get('swisssearch')).toBe('8.83,47.25');
    expect(params.get('swisssearch_autoselect')).toBe('true');
    expect(params.get('lang')).toBe('en');
    expect(params.get('topic')).toBe('ech');
  });

  it.each([
    { ...query, layerBodId: 'other-layer' },
    { ...query, featureId: '../htmlPopup' },
    { ...query, featureId: '4.03;other-layer@features=1' },
    { ...query, lngLat: { lng: Number.NaN, lat: 47 } },
  ])('rejects an invalid feature context before requesting remote data', async (invalid) => {
    await expect(fetchSwissRouteDetails(invalid, new AbortController().signal)).rejects.toBeInstanceOf(Error);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('keeps a source failure distinct from a feature with no published links', async () => {
    fetchMock.mockResolvedValueOnce(new Response('', { status: 503 }));
    await expect(fetchSwissRouteDetails(query, new AbortController().signal)).rejects.toThrow('503');
  });

  it('rejects a non-HTML API response', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('{"error":"unavailable"}', { headers: { 'content-type': 'application/json' } })
    );
    await expect(fetchSwissRouteDetails(query, new AbortController().signal)).rejects.toThrow('unsupported response');
  });

  it('does not start a request for an already cancelled selection', async () => {
    const controller = new AbortController();
    controller.abort();
    await expect(fetchSwissRouteDetails(query, controller.signal)).rejects.toMatchObject({ name: 'AbortError' });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('rejects delayed HTML after selection cancellation even when the transport ignores abort', async () => {
    let resolve!: (response: Response) => void;
    fetchMock.mockReturnValueOnce(
      new Promise((finish) => {
        resolve = finish;
      })
    );
    const controller = new AbortController();
    const result = fetchSwissRouteDetails(query, controller.signal);
    controller.abort();
    resolve(htmlResponse('<a href="https://schweizmobil.ch/en/hiking-in-switzerland/route-4/stage-3">Stage</a>'));
    await expect(result).rejects.toMatchObject({ name: 'AbortError' });
  });
});

function htmlResponse(html: string): Response {
  return new Response(html, { headers: { 'content-type': 'text/html; charset=UTF-8' } });
}
