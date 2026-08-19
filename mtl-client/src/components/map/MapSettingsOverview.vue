<template>
  <div class="map-settings-overview">
    <section class="map-settings-overview__current status-rail" aria-labelledby="map-settings-current-title">
      <div class="map-settings-overview__current-copy">
        <span class="settings-eyebrow map-settings-overview__eyebrow">Current map</span>
        <h3 id="map-settings-current-title">{{ themeName }}</h3>
        <p>{{ sourceSummary }} · {{ terrainSummary }}</p>
      </div>
      <button
        type="button"
        class="map-settings-overview__preview"
        :style="{ backgroundImage: `url(${themeThumbnail})` }"
        aria-label="Open map style settings"
        title="Open map style settings"
        @click="emit('open-style')"
      >
        <span v-if="!basemapEnabled" class="map-settings-overview__preview-state">
          <i class="bi bi-eye-slash" aria-hidden="true"></i>
          Hidden
        </span>
      </button>
    </section>

    <section class="map-settings-overview__configuration" aria-labelledby="map-settings-configuration-title">
      <div class="settings-section-heading map-settings-overview__section-heading">
        <h3 id="map-settings-configuration-title">Configuration</h3>
        <p>Open a section to change it. Updates appear on the map immediately.</p>
      </div>

      <div class="settings-list map-settings-overview__list">
        <button type="button" class="settings-row map-settings-row" @click="emit('open-style')">
          <span class="settings-row__icon map-settings-row__icon"><i class="bi bi-map" aria-hidden="true"></i></span>
          <span class="settings-row__body map-settings-row__body">
            <strong>Map style</strong>
            <small>Choose the source, theme, and background.</small>
          </span>
          <span class="settings-row__value map-settings-row__value">{{ styleSummary }}</span>
          <i class="bi bi-chevron-right settings-row__chevron map-settings-row__chevron" aria-hidden="true"></i>
        </button>

        <button type="button" class="settings-row map-settings-row" @click="emit('open-terrain')">
          <span class="settings-row__icon map-settings-row__icon"
            ><i class="bi bi-badge-3d" aria-hidden="true"></i
          ></span>
          <span class="settings-row__body map-settings-row__body">
            <strong>Terrain</strong>
            <small>Switch between a flat map and 3D relief.</small>
          </span>
          <span class="settings-row__value map-settings-row__value">{{ terrainSummary }}</span>
          <i class="bi bi-chevron-right settings-row__chevron map-settings-row__chevron" aria-hidden="true"></i>
        </button>

        <button type="button" class="settings-row map-settings-row" @click="emit('open-data')">
          <span class="settings-row__icon map-settings-row__icon"><i class="bi bi-layers" aria-hidden="true"></i></span>
          <span class="settings-row__body map-settings-row__body">
            <strong>Your data</strong>
            <small>Show tracks, media, points, and the heatmap.</small>
          </span>
          <span class="settings-row__value map-settings-row__value">{{ dataSummary }}</span>
          <i class="bi bi-chevron-right settings-row__chevron map-settings-row__chevron" aria-hidden="true"></i>
        </button>

        <button type="button" class="settings-row map-settings-row" @click="emit('open-routes')">
          <span class="settings-row__icon map-settings-row__icon"
            ><i class="bi bi-signpost-2" aria-hidden="true"></i
          ></span>
          <span class="settings-row__body map-settings-row__body">
            <strong>Route overlays</strong>
            <small>Add hiking and cycling route networks.</small>
          </span>
          <span class="settings-row__value map-settings-row__value">{{ routesSummary }}</span>
          <i class="bi bi-chevron-right settings-row__chevron map-settings-row__chevron" aria-hidden="true"></i>
        </button>
      </div>
    </section>

    <button type="button" class="map-settings-overview__reset" @click="emit('reset')">
      <i class="bi bi-arrow-counterclockwise" aria-hidden="true"></i>
      Reset map settings
    </button>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'MapSettingsOverview' });

defineProps<{
  basemapEnabled: boolean;
  dataSummary: string;
  routesSummary: string;
  sourceSummary: string;
  styleSummary: string;
  terrainSummary: string;
  themeName: string;
  themeThumbnail: string;
}>();

const emit = defineEmits<{
  (event: 'open-style'): void;
  (event: 'open-terrain'): void;
  (event: 'open-data'): void;
  (event: 'open-routes'): void;
  (event: 'reset'): void;
}>();
</script>

<style scoped>
.map-settings-overview {
  --map-settings-preview-max-width: 22rem;

  display: flex;
  width: 100%;
  box-sizing: border-box;
  flex-direction: column;
  gap: 1.35rem;
  padding: 0.5rem 1rem calc(1rem + env(safe-area-inset-bottom));
  color: var(--text-secondary);
}

.map-settings-overview__current {
  position: relative;
  display: grid;
  min-height: 8.75rem;
  grid-template-columns: minmax(0, 1fr) minmax(9rem, 34%);
  align-items: center;
  gap: 1.25rem;
  padding: 0.8rem 0.25rem 0.8rem 1.05rem;
}

.map-settings-overview__current-copy {
  min-width: 0;
}

.map-settings-overview__current h3 {
  margin: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-size: var(--text-lg-size);
  font-weight: var(--font-bold);
  line-height: var(--text-lg-lh);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.map-settings-overview__current p {
  margin: 0.3rem 0 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.map-settings-overview__preview {
  position: relative;
  width: 100%;
  max-width: var(--map-settings-preview-max-width);
  aspect-ratio: 16 / 9;
  justify-self: end;
  overflow: hidden;
  padding: 0;
  border: 1px solid var(--border-medium);
  border-radius: 0.7rem;
  background-color: var(--surface-elevated);
  background-position: center 60%;
  background-size: 155% auto;
  box-shadow: var(--shadow-sm);
  color: inherit;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.map-settings-overview__preview:hover {
  border-color: var(--accent);
}

.map-settings-overview__preview:focus-visible {
  border-color: var(--accent);
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.map-settings-overview__preview-state {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  background: color-mix(in srgb, var(--surface-ground) 84%, transparent);
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: var(--font-bold);
}

.map-settings-overview__configuration {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.map-settings-overview__section-heading {
  padding: 0 0.15rem;
}

.map-settings-overview__reset {
  display: inline-flex;
  min-height: 2.5rem;
  align-self: flex-start;
  align-items: center;
  gap: 0.45rem;
  padding: 0.45rem 0.15rem;
  border: 0;
  border-radius: 0.4rem;
  background: transparent;
  color: var(--text-muted);
  font: inherit;
  font-size: var(--text-sm-size);
  font-weight: var(--font-semibold);
  cursor: pointer;
}

.map-settings-overview__reset:hover {
  background: var(--surface-hover);
  color: var(--text-secondary);
}

@media screen and (max-width: 600px) {
  .map-settings-overview {
    gap: 1.15rem;
    padding-inline: 1rem;
  }

  .map-settings-overview__current {
    min-height: 8rem;
    grid-template-columns: minmax(0, 1fr) 6.75rem;
    gap: 0.8rem;
    padding-left: 0.9rem;
  }

  .map-settings-overview__current h3 {
    white-space: normal;
  }

  .map-settings-overview__section-heading p {
    max-width: 18rem;
  }

  .map-settings-row {
    min-height: 4.25rem;
    grid-template-columns: 1.45rem minmax(0, 1fr) auto;
    gap: 0.65rem;
    padding: 0.7rem 0.1rem;
  }

  .map-settings-row__body small {
    display: none;
  }

  .map-settings-row__value {
    grid-column: 2;
    max-width: 100%;
    font-size: var(--text-xs-size);
    line-height: var(--text-xs-lh);
    text-align: left;
  }

  .map-settings-row__icon {
    width: 1.45rem;
    height: 1.45rem;
    grid-row: 1 / span 2;
  }

  .map-settings-row__chevron {
    grid-column: 3;
    grid-row: 1 / span 2;
  }
}

@media screen and (max-width: 380px) {
  .map-settings-overview__current {
    grid-template-columns: minmax(0, 1fr) 5.5rem;
  }
}
</style>
