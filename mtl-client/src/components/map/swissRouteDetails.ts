export type SwissRouteDetailsQuery = {
  layerBodId: string;
  featureId: string | number;
  lngLat: { lng: number; lat: number };
};

export type SwissRouteDetails = {
  stageLabel?: string;
  stageUrl?: string;
  fullRouteUrl?: string;
  swissMapUrl?: string;
  classification?: string;
};

const SWISS_ROUTE_LAYERS = new Set([
  'ch.astra.wanderland',
  'ch.astra.veloland',
  'ch.astra.mountainbikeland',
  'ch.swisstopo.swisstlm3d-wanderwege',
]);
const SCHWEIZMOBIL_HOSTS = new Set(['schweizmobil.ch', 'www.schweizmobil.ch']);
const GEO_ADMIN_MAP_SERVER = 'https://api3.geo.admin.ch/rest/services/ech/MapServer';
const SWISS_MAP_VIEWER = 'https://map.geo.admin.ch/';
const POPUP_LANGUAGE = 'en';
const FEATURE_ID_PATTERN = /^[a-z\d_-][a-z\d_.-]*$/i;
const ROUTE_PATH_PATTERN = /^route-\d+$/i;
const STAGE_PATH_PATTERN = /^(?:stage|etappe|etape|tappa)-(\d+)$/i;
const CLASSIFICATION_FIELDS = new Set(['hikingtype', 'hiking type', 'wanderwegtyp', 'wanderwegart']);

function validateQuery(query: SwissRouteDetailsQuery): string {
  if (!SWISS_ROUTE_LAYERS.has(query.layerBodId)) throw new Error('Unsupported Swiss route layer');
  const featureId = String(query.featureId).trim();
  if (!FEATURE_ID_PATTERN.test(featureId)) throw new Error('Invalid Swiss route feature');
  const { lng, lat } = query.lngLat;
  if (!Number.isFinite(lng) || !Number.isFinite(lat) || Math.abs(lng) > 180 || Math.abs(lat) > 90) {
    throw new Error('Invalid Swiss route location');
  }
  return featureId;
}

/** Use the documented feature-selection and WGS84 location parameters. */
export function buildSwissMapUrl(query: SwissRouteDetailsQuery): string {
  const featureId = validateQuery(query);
  const params = new URLSearchParams({
    lang: POPUP_LANGUAGE,
    topic: 'ech',
    layers: `${query.layerBodId}@features=${featureId}`,
    swisssearch: `${query.lngLat.lng},${query.lngLat.lat}`,
    swisssearch_autoselect: 'true',
    featureInfo: 'default',
  });
  return `${SWISS_MAP_VIEWER}#/map?${params}`;
}

function publishedRouteUrl(href: string | null): URL | null {
  if (!href) return null;
  try {
    const url = new URL(href);
    if (url.protocol !== 'https:' || !SCHWEIZMOBIL_HOSTS.has(url.hostname) || url.username || url.password || url.port)
      return null;
    return url;
  } catch {
    return null;
  }
}

/** Parse inert content; never attach the returned markup or load its resources. */
export function parseSwissRouteDetails(html: string, query: SwissRouteDetailsQuery): SwissRouteDetails {
  const details: SwissRouteDetails = { swissMapUrl: buildSwissMapUrl(query) };
  const template = document.createElement('template');
  template.innerHTML = html;
  for (const anchor of template.content.querySelectorAll('a[href]')) {
    const url = publishedRouteUrl(anchor.getAttribute('href'));
    if (!url) continue;
    const segments = url.pathname.split('/').filter(Boolean);
    const routeIndex = segments.findIndex((segment) => ROUTE_PATH_PATTERN.test(segment));
    if (routeIndex === -1) continue;
    const stage = segments[routeIndex + 1]?.match(STAGE_PATH_PATTERN);
    if (stage && routeIndex + 2 === segments.length && !details.stageUrl) {
      details.stageUrl = url.href;
      const stageNumber = Number(stage[1]);
      if (Number.isSafeInteger(stageNumber) && stageNumber > 0) details.stageLabel = `Stage ${stageNumber}`;
    } else if (routeIndex + 1 === segments.length && !details.fullRouteUrl) {
      details.fullRouteUrl = url.href;
    }
  }
  for (const row of template.content.querySelectorAll('tr')) {
    const cells = row.querySelectorAll('td, th');
    const field = cells[0]?.textContent?.trim().replace(/\s+/g, ' ').toLowerCase();
    if (!field || !CLASSIFICATION_FIELDS.has(field)) continue;
    const classification = cells[1]?.textContent?.trim().replace(/\s+/g, ' ');
    if (classification && classification !== '-') details.classification = classification;
  }
  return details;
}

/** Enrich only the selected Swiss feature; published links are never fetched here. */
export async function fetchSwissRouteDetails(
  query: SwissRouteDetailsQuery,
  signal: AbortSignal
): Promise<SwissRouteDetails> {
  const featureId = validateQuery(query);
  signal.throwIfAborted();
  const params = new URLSearchParams({
    lang: POPUP_LANGUAGE,
    sr: '4326',
    coord: `${query.lngLat.lng},${query.lngLat.lat}`,
  });
  const response = await fetch(
    `${GEO_ADMIN_MAP_SERVER}/${encodeURIComponent(query.layerBodId)}/${encodeURIComponent(featureId)}/htmlPopup?${params}`,
    { signal }
  );
  if (!response.ok) throw new Error(`Swiss route details are unavailable (${response.status})`);
  const contentType = response.headers.get('content-type');
  if (contentType && !/^(?:text\/html|application\/xhtml\+xml)\b/i.test(contentType)) {
    throw new Error('Swiss route details returned an unsupported response');
  }
  const html = await response.text();
  signal.throwIfAborted();
  return parseSwissRouteDetails(html, query);
}
