<template>
  <BottomSheet
    :model-value="modelValue"
    title="Map style"
    icon="bi bi-map"
    :detents="[{ height: '78vh' }, { height: '95vh' }]"
    :no-backdrop="true"
    :z-index="5100"
    sheet-class="sheet--solid-over-map sheet--map-settings-detail"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="map-style-sheet">
      <p class="map-style-sheet__intro">Choose where the map comes from and how its background looks.</p>

      <section class="map-style-sheet__section" aria-labelledby="map-source-title">
        <div class="map-style-sheet__heading settings-section-heading">
          <h3 id="map-source-title">Map source</h3>
          <p>Automatic uses the local map archive when available. Remote always uses raster tiles.</p>
        </div>

        <div class="map-style-sheet__source-grid" role="radiogroup" aria-label="Map source">
          <button
            type="button"
            class="map-style-sheet__source"
            :class="{ 'map-style-sheet__source--selected': mapSourceMode === 'auto' }"
            role="radio"
            :aria-checked="mapSourceMode === 'auto'"
            @click="emit('update:map-source-mode', 'auto')"
          >
            <span class="map-style-sheet__source-icon"><i class="bi bi-signpost-split" aria-hidden="true"></i></span>
            <span class="map-style-sheet__source-copy settings-row__body">
              <strong>Automatic</strong>
              <small>Local when available</small>
            </span>
            <i v-if="mapSourceMode === 'auto'" class="bi bi-check-circle-fill map-style-sheet__check"></i>
          </button>

          <button
            type="button"
            class="map-style-sheet__source"
            :class="{ 'map-style-sheet__source--selected': mapSourceMode === 'remote' }"
            role="radio"
            :aria-checked="mapSourceMode === 'remote'"
            @click="emit('update:map-source-mode', 'remote')"
          >
            <span class="map-style-sheet__source-icon"><i class="bi bi-grid-3x3-gap" aria-hidden="true"></i></span>
            <span class="map-style-sheet__source-copy settings-row__body">
              <strong>Remote tiles</strong>
              <small>Always use the network</small>
            </span>
            <i v-if="mapSourceMode === 'remote'" class="bi bi-check-circle-fill map-style-sheet__check"></i>
          </button>
        </div>
      </section>

      <section class="map-style-sheet__section" aria-labelledby="map-theme-title">
        <div class="map-style-sheet__heading settings-section-heading">
          <h3 id="map-theme-title">Theme</h3>
          <p>{{ themes.length }} {{ themes.length === 1 ? 'theme' : 'themes' }} available for this source.</p>
        </div>

        <div class="map-style-sheet__theme-grid" role="radiogroup" aria-label="Map theme">
          <button
            v-for="theme in themes"
            :key="theme.code"
            type="button"
            class="map-style-sheet__theme"
            :class="{ 'map-style-sheet__theme--selected': selectedTheme === theme.code }"
            role="radio"
            :aria-checked="selectedTheme === theme.code"
            @click="emit('update:selected-theme', theme.code)"
          >
            <span class="map-style-sheet__swatch" :style="{ backgroundImage: `url(${theme.thumbnail})` }">
              <span
                v-if="theme.badgeLabel"
                class="map-style-sheet__badge"
                :class="theme.badgeTone ? `map-style-sheet__badge--${theme.badgeTone}` : undefined"
              >
                <i :class="theme.badgeTone === 'swiss' ? 'bi bi-geo-alt-fill' : 'bi bi-stars'"></i>
                {{ theme.badgeLabel }}
              </span>
              <span v-if="selectedTheme === theme.code" class="map-style-sheet__selected-mark" aria-hidden="true">
                <i class="bi bi-check-lg"></i>
              </span>
            </span>
            <span class="map-style-sheet__theme-name">{{ theme.name }}</span>
          </button>
        </div>
      </section>

      <section class="map-style-sheet__section" aria-labelledby="map-background-title">
        <div class="map-style-sheet__heading settings-section-heading">
          <h3 id="map-background-title">Background</h3>
          <p>Hide or dim the base map without changing your data layers.</p>
        </div>
        <div class="map-style-sheet__control-list">
          <LayerControl
            label="Base map"
            info="Street, topographic, or satellite background"
            icon="bi bi-map-fill"
            :enabled="basemap.enabled"
            :opacity="basemap.opacity"
            @update:enabled="emit('toggle-basemap')"
            @update:opacity="emit('change-basemap-opacity', $event)"
          />
        </div>
      </section>
    </div>

    <template #footer>
      <MapSettingsDetailFooter @done="emit('update:modelValue', false)" />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import BottomSheet from '@/components/ui/BottomSheet.vue';
import LayerControl from '@/components/map/LayerControl.vue';
import MapSettingsDetailFooter from '@/components/map/MapSettingsDetailFooter.vue';
import type { MapLayerState, MapSourceMode, MapThemeOption } from '@/components/map/mapSettingsPanelTypes';

defineOptions({ name: 'MapStyleSheet' });

defineProps<{
  basemap: MapLayerState;
  mapSourceMode: MapSourceMode;
  modelValue: boolean;
  selectedTheme: string;
  themes: MapThemeOption[];
}>();

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void;
  (event: 'update:map-source-mode', value: MapSourceMode): void;
  (event: 'update:selected-theme', value: string): void;
  (event: 'toggle-basemap'): void;
  (event: 'change-basemap-opacity', value: number): void;
}>();
</script>

<style scoped>
.map-style-sheet {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  box-sizing: border-box;
  flex-direction: column;
  gap: 1.5rem;
  padding: 0.35rem 1rem 1.5rem;
}

.map-style-sheet__intro {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.map-style-sheet__section {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.map-style-sheet__source-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  border-top: 1px solid var(--border-default);
  border-bottom: 1px solid var(--border-default);
}

.map-style-sheet__source {
  display: grid;
  min-height: 4.25rem;
  grid-template-columns: 2rem minmax(0, 1fr) 1.25rem;
  align-items: center;
  gap: 0.65rem;
  padding: 0.7rem 0.8rem;
  border: 0;
  border-right: 1px solid var(--border-subtle, var(--border-default));
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;
}

.map-style-sheet__source:last-child {
  border-right: 0;
}

.map-style-sheet__source:hover {
  background: var(--surface-hover);
}

.map-style-sheet__source--selected {
  background: color-mix(in srgb, var(--accent) 7%, transparent);
}

.map-style-sheet__source:focus-visible,
.map-style-sheet__theme:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.map-style-sheet__source-icon {
  display: inline-flex;
  width: 2rem;
  height: 2rem;
  align-items: center;
  justify-content: center;
  background: transparent;
  color: var(--text-muted);
  font-size: var(--text-base-size);
}

.map-style-sheet__source--selected .map-style-sheet__source-icon,
.map-style-sheet__check {
  color: var(--accent-text);
}

.map-style-sheet__theme-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.65rem;
}

.map-style-sheet__theme {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: stretch;
  gap: 0.45rem;
  padding: 0.35rem;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  cursor: pointer;
  transition: background 0.15s ease;
}

.map-style-sheet__theme:hover {
  background: var(--surface-hover);
}

.map-style-sheet__theme:hover .map-style-sheet__swatch {
  border-color: var(--border-hover);
}

.map-style-sheet__theme--selected .map-style-sheet__swatch {
  border-color: var(--accent);
  box-shadow: 0 0 0 1px var(--accent);
}

.map-style-sheet__swatch {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border: 1px solid var(--border-medium);
  border-radius: 0.45rem;
  background-color: var(--surface-elevated);
  background-position: center 60%;
  background-size: 155% auto;
}

.map-style-sheet__badge {
  position: absolute;
  top: 0.3rem;
  right: 0.35rem;
  display: inline-flex;
  max-width: calc(100% - 0.7rem);
  align-items: center;
  gap: 0.15rem;
  padding: 0.1rem 0.3rem;
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 999px;
  background: rgba(12, 19, 32, 0.82);
  color: var(--accent-contrast);
  font-size: 0.5rem;
  font-weight: 800;
  letter-spacing: 0.035em;
  line-height: 0.65rem;
  text-transform: uppercase;
  white-space: nowrap;
}

.map-style-sheet__badge--preferred {
  background: color-mix(in srgb, var(--primary-color) 86%, #162033);
}

.map-style-sheet__badge--swiss {
  background: color-mix(in srgb, var(--error) 82%, #721818);
}

.map-style-sheet__selected-mark {
  position: absolute;
  top: 0.3rem;
  left: 0.35rem;
  display: inline-flex;
  width: 1.4rem;
  height: 1.4rem;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 50%;
  background: var(--accent);
  color: var(--text-inverse);
  box-shadow: var(--shadow-sm);
  font-size: var(--text-xs-size);
}

.map-style-sheet__theme-name {
  min-height: calc(2 * var(--text-xs-lh));
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-xs-lh);
  text-align: center;
}

.map-style-sheet__control-list {
  border-top: 1px solid var(--border-default);
  border-bottom: 1px solid var(--border-default);
  background: transparent;
}

@media screen and (max-width: 600px) {
  .map-style-sheet {
    gap: 1.35rem;
    padding-inline: 0.75rem;
  }

  .map-style-sheet__source-grid {
    gap: 0;
  }

  .map-style-sheet__source {
    grid-template-columns: 1.75rem minmax(0, 1fr) 1rem;
    gap: 0.45rem;
    padding-inline: 0.6rem;
  }

  .map-style-sheet__source-icon {
    width: 1.75rem;
    height: 1.75rem;
  }

  .map-style-sheet__theme-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0.5rem;
  }
}

@media screen and (max-width: 440px) {
  .map-style-sheet__source-copy small {
    display: none;
  }
}
</style>
