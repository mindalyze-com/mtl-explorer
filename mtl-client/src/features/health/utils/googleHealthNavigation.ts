import type { RouteLocationRaw } from 'vue-router';

export const GOOGLE_HEALTH_CONNECTION_QUERY = 'connection';
export const GOOGLE_HEALTH_CONNECTION_ID = 'google-health';
export const GOOGLE_HEALTH_PROCESSING_ID = 'google-health-processing';
export const GOOGLE_HEALTH_REAUTHORIZATION_QUERY = 'googleHealthReauthorize';

export const GOOGLE_HEALTH_SETTINGS_ROUTE: RouteLocationRaw = {
  name: 'admin',
  params: { section: 'imports' },
  query: { [GOOGLE_HEALTH_CONNECTION_QUERY]: GOOGLE_HEALTH_CONNECTION_ID },
};

export const GOOGLE_HEALTH_REAUTHORIZATION_ROUTE: RouteLocationRaw = {
  name: 'admin',
  params: { section: 'imports' },
  query: {
    [GOOGLE_HEALTH_CONNECTION_QUERY]: GOOGLE_HEALTH_CONNECTION_ID,
    [GOOGLE_HEALTH_REAUTHORIZATION_QUERY]: 'open',
  },
};

export const GOOGLE_HEALTH_PROCESSING_ROUTE: RouteLocationRaw = {
  name: 'admin',
  params: { section: 'processing' },
  hash: `#${GOOGLE_HEALTH_PROCESSING_ID}`,
};
