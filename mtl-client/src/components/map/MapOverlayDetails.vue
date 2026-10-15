<template>
  <article class="map-overlay-details" aria-label="Route details">
    <header class="map-overlay-details__identity">
      <span class="map-overlay-details__icon" aria-hidden="true"><i :class="routeIcon"></i></span>
      <div class="map-overlay-details__heading">
        <h2>{{ route.name || 'Public route' }}</h2>
        <span v-if="route.type" class="map-overlay-details__type">{{ route.type }}</span>
      </div>
    </header>
    <dl v-if="rows.length" class="map-overlay-details__fields">
      <template v-for="row in rows" :key="row.label">
        <dt><i :class="row.icon" aria-hidden="true"></i>{{ row.label }}</dt>
        <dd>{{ row.value }}</dd>
      </template>
    </dl>
    <p v-if="sourceLabel" class="map-overlay-details__source">
      <i class="bi bi-map" aria-hidden="true"></i>Source: {{ sourceLabel }}
    </p>
    <div v-if="links.length" class="map-overlay-details__links">
      <a
        v-for="link in links"
        :key="link.label"
        :href="link.url"
        class="map-overlay-details__link"
        :class="{ 'map-overlay-details__link--primary': link.primary }"
        target="_blank"
        rel="noopener noreferrer"
      >
        {{ link.label }}
        <i class="bi bi-box-arrow-up-right" aria-hidden="true"></i>
      </a>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { MapOverlayRoute } from './composables/mapControllerRuntime';

defineOptions({ name: 'MapOverlayDetails' });
const props = defineProps<{ route: MapOverlayRoute; sourceLabel: string }>();
const WAYMARKED_TRAILS_HOST = 'waymarkedtrails.org';
const SWISS_MOBILITY_HOST = 'schweizmobil.ch';
const SWISS_MAP_HOST = 'map.geo.admin.ch';
const DETAILS_URL_PROTOCOL = 'https:';

const routeIcon = computed(() =>
  /^(bi\s+)?bi-[a-z0-9-]+$/.test(props.route.icon) ? `bi ${props.route.icon.replace(/^bi\s+/, '')}` : 'bi bi-signpost-2'
);
const rows = computed(() => {
  const reference = props.route.reference?.trim();
  return [
    { label: reference ? 'Reference' : 'Route number', value: reference || props.route.number, icon: 'bi bi-hash' },
    { label: 'Stage', value: props.route.stageLabel, icon: 'bi bi-signpost-2' },
    { label: 'Itinerary', value: props.route.itinerary, icon: 'bi bi-arrow-left-right' },
    { label: 'Network', value: props.route.network, icon: 'bi bi-diagram-3' },
    { label: 'Classification', value: props.route.classification, icon: 'bi bi-info-circle' },
  ]
    .map((row) => ({ ...row, value: row.value == null ? '' : String(row.value).trim() }))
    .filter((row) => row.value !== '');
});

function validatedUrl(value: string | null | undefined, host: string, allowSubdomains = false): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    if (
      url.protocol !== DETAILS_URL_PROTOCOL ||
      url.username ||
      url.password ||
      (url.hostname !== host && !(allowSubdomains && url.hostname.endsWith(`.${host}`)))
    )
      return null;
    return url.href;
  } catch {
    return null;
  }
}

const links = computed(() =>
  [
    {
      label: 'Stage details',
      url: validatedUrl(props.route.stageUrl, SWISS_MOBILITY_HOST, true),
    },
    { label: 'Full route', url: validatedUrl(props.route.fullRouteUrl, SWISS_MOBILITY_HOST, true) },
    { label: 'Open in Swiss map', url: validatedUrl(props.route.swissMapUrl, SWISS_MAP_HOST) },
    {
      label: 'Open in Waymarked Trails',
      url: validatedUrl(props.route.detailsUrl, WAYMARKED_TRAILS_HOST, true),
    },
  ]
    .filter((link): link is typeof link & { url: string } => link.url !== null)
    .map((link, index) => ({ ...link, primary: index === 0 }))
);
</script>

<style scoped>
.map-overlay-details {
  display: grid;
  gap: 1rem;
  min-width: 0;
}

.map-overlay-details__identity {
  display: flex;
  align-items: flex-start;
  gap: 0.85rem;
  padding: 0.35rem 0;
}

.map-overlay-details__icon {
  display: grid;
  place-items: center;
  flex: 0 0 3rem;
  width: 3rem;
  height: 3rem;
  border-radius: 0.8rem;
  background: var(--accent-bg);
  color: var(--accent-text);
  font-size: var(--text-xl-size);
}

.map-overlay-details__heading {
  display: grid;
  min-width: 0;
  gap: 0.45rem;
}

.map-overlay-details__heading h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-lg-size);
  line-height: var(--text-lg-lh);
  font-weight: var(--font-semibold);
  overflow-wrap: anywhere;
}

.map-overlay-details__type {
  justify-self: start;
  padding: 0.2rem 0.6rem;
  border-radius: 1rem;
  background: var(--surface-elevated);
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
}

.map-overlay-details__fields {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0.8rem 1rem;
  margin: 0;
  padding: 0.85rem;
  border: 1px solid var(--border-default);
  border-radius: 0.8rem;
  background: var(--surface-elevated);
  font-size: var(--text-sm-size);
}

.map-overlay-details__fields dt,
.map-overlay-details__source {
  color: var(--text-secondary);
}

.map-overlay-details__fields dt {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.map-overlay-details__fields dt i {
  flex: 0 0 1rem;
  color: var(--text-muted);
}

.map-overlay-details__fields dd {
  margin: 0;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}

.map-overlay-details__source {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  margin: 0;
  font-size: var(--text-xs-size);
}

.map-overlay-details__link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  min-height: 2.75rem;
  padding: 0.5rem 0.85rem;
  border: 1px solid var(--border-medium);
  border-radius: 0.65rem;
  background: var(--surface-card);
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: var(--font-semibold);
  text-decoration: none;
}

.map-overlay-details__link:hover {
  background: var(--surface-hover);
}

.map-overlay-details__links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem 1rem;
}

.map-overlay-details__link--primary {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-contrast);
}

.map-overlay-details__link--primary:hover {
  background: var(--accent-hover);
}

.map-overlay-details a:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 3px;
}
</style>
