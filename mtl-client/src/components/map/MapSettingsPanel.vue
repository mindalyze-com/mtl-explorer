<template>
  <div>
    <BottomSheet v-model="isOpen" title="Maps and data" icon="bi bi-map" :detents="[{ height: '55vh' }, { height: '92vh' }]" @closed="$emit('tool-closed')">
      <template #header-actions>
        <button class="msp-header-reset-btn" @click="$emit('reset-settings')" title="Reset all layers to defaults">
          <i class="bi bi-arrow-counterclockwise"></i>
          Reset
        </button>
      </template>
      <div class="msp-content">
        <!-- Base Map Theme -->
        <div class="msp-section-label">Base Map</div>
        <div class="msp-theme-grid">
          <div
              v-for="theme in themes"
              :key="theme.code"
              class="msp-theme-tile"
              :class="{ 'msp-theme-active': modelValue === theme.code }"
              @click="$emit('update:modelValue', theme.code)"
          >
            <div class="msp-theme-swatch" :style="{ backgroundImage: `url(${theme.thumbnail})` }">
              <span v-if="theme.featured" class="msp-theme-badge">★</span>
              <span v-if="modelValue === theme.code" class="msp-theme-selected">✓</span>
            </div>
            <span class="msp-theme-label">{{ theme.name }}</span>
          </div>
        </div>

        <!-- Background layer -->
        <div class="msp-section-label">Background</div>
        <LayerControl
          label="Base Map"
          info="The background map tiles — street map, topo, or satellite imagery"
          :enabled="layerStates.basemap.enabled"
          :opacity="layerStates.basemap.opacity"
          @update:enabled="$emit('toggle-layer', 'basemap')"
          @update:opacity="$emit('change-layer-opacity', 'basemap', $event)"
        />

        <!-- Data Layers -->
        <div class="msp-section-label">Data Layers</div>
        <LayerControl
          label="GPS Tracks"
          info="All your recorded activities drawn as colored lines on the map"
          :enabled="layerStates.tracks.enabled"
          :opacity="layerStates.tracks.opacity"
          @update:enabled="$emit('toggle-layer', 'tracks')"
          @update:opacity="$emit('change-layer-opacity', 'tracks', $event)"
        />
        <LayerControl
          label="Photos &amp; Media"
          info="Geotagged photos and media clustered by location"
          :enabled="layerStates.media.enabled"
          :opacity="layerStates.media.opacity"
          @update:enabled="$emit('toggle-layer', 'media')"
          @update:opacity="$emit('change-layer-opacity', 'media', $event)"
        />
        <LayerControl
          label="Track Points &amp; Direction"
          info="Individual GPS points with direction arrows — only visible when zoomed in close"
          :enabled="layerStates.trackpoints.enabled"
          :opacity="layerStates.trackpoints.opacity"
          @update:enabled="$emit('toggle-layer', 'trackpoints')"
          @update:opacity="$emit('change-layer-opacity', 'trackpoints', $event)"
        />
        <LayerControl
          label="Heatmap"
          info="Density overlay showing where you've been most frequently"
          :enabled="layerStates.heatmap.enabled"
          :opacity="layerStates.heatmap.opacity"
          @update:enabled="$emit('toggle-layer', 'heatmap')"
          @update:opacity="$emit('change-layer-opacity', 'heatmap', $event)"
        />

        <!-- Waymarked Trails (worldwide) -->
        <div class="msp-section-label">Waymarked Trails</div>
        <div class="msp-section-hint">Worldwide routes from OpenStreetMap · styled by importance level</div>
        <LayerControl
          label="Hiking (worldwide)"
          info="International, national, regional &amp; local hiking routes"
          :enabled="layerStates['wmt-hiking'].enabled"
          :opacity="layerStates['wmt-hiking'].opacity"
          @update:enabled="$emit('toggle-layer', 'wmt-hiking')"
          @update:opacity="$emit('change-layer-opacity', 'wmt-hiking', $event)"
        />
        <LayerControl
          label="Cycling (worldwide)"
          info="Cycling route networks — EuroVelo, national &amp; regional"
          :enabled="layerStates['wmt-cycling'].enabled"
          :opacity="layerStates['wmt-cycling'].opacity"
          @update:enabled="$emit('toggle-layer', 'wmt-cycling')"
          @update:opacity="$emit('change-layer-opacity', 'wmt-cycling', $event)"
        />
        <LayerControl
          label="MTB (worldwide)"
          info="Mountain bike trails and routes"
          :enabled="layerStates['wmt-mtb'].enabled"
          :opacity="layerStates['wmt-mtb'].opacity"
          @update:enabled="$emit('toggle-layer', 'wmt-mtb')"
          @update:opacity="$emit('change-layer-opacity', 'wmt-mtb', $event)"
        />

        <!-- Swiss Overlays -->
        <div class="msp-section-label">Swiss Overlays</div>
        <div class="msp-section-hint">Switzerland only — not available worldwide</div>
        <LayerControl
          label="Hiking Routes"
          :enabled="layerStates.wanderland.enabled"
          :opacity="layerStates.wanderland.opacity"
          @update:enabled="$emit('toggle-layer', 'wanderland')"
          @update:opacity="$emit('change-layer-opacity', 'wanderland', $event)"
        />
        <LayerControl
          label="Bike Routes"
          :enabled="layerStates.veloland.enabled"
          :opacity="layerStates.veloland.opacity"
          @update:enabled="$emit('toggle-layer', 'veloland')"
          @update:opacity="$emit('change-layer-opacity', 'veloland', $event)"
        />
        <LayerControl
          label="MTB Routes"
          :enabled="layerStates.mountainbikeland.enabled"
          :opacity="layerStates.mountainbikeland.opacity"
          @update:enabled="$emit('toggle-layer', 'mountainbikeland')"
          @update:opacity="$emit('change-layer-opacity', 'mountainbikeland', $event)"
        />
        <LayerControl
          label="Hiking Trails"
          info="All signposted hiking trails (yellow, red-white, blue-white)"
          :enabled="layerStates.wanderwege.enabled"
          :opacity="layerStates.wanderwege.opacity"
          @update:enabled="$emit('toggle-layer', 'wanderwege')"
          @update:opacity="$emit('change-layer-opacity', 'wanderwege', $event)"
        />

      </div>
    </BottomSheet>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import LayerControl from '@/components/map/LayerControl.vue';

defineProps({
  modelValue: { type: String, required: true },
  themes: { type: Array, required: true },
  layerStates: { type: Object, required: true },
});

const emit = defineEmits([
  'update:modelValue',
  'toggle-layer',
  'change-layer-opacity',
  'reset-settings',
  'tool-opened',
  'tool-closed',
]);

const isOpen = ref(false);

function toggle() {
  isOpen.value = !isOpen.value;
  if (isOpen.value) emit('tool-opened');
}

function close() {
  isOpen.value = false;
}

// Map.vue calls `mapSettingsTool.toggle()` / `.close()` via $refs
// (see closeAllToolsExcept). With <script setup>, members are private
// unless explicitly exposed.
defineExpose({ toggle, close });
</script>

<style scoped>
.msp-content {
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
  flex: 1 1 auto;
  min-height: 0;
  padding: 0 1rem 1.5rem;
}

.msp-section-label {
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-faint);
  margin: 0.6rem 0 0.35rem;
}
.msp-section-label:first-child {
  margin-top: 0;
}
.msp-section-hint {
  font-size: 0.68rem;
  color: var(--text-faint);
  opacity: 0.75;
  margin: -0.2rem 0 0.3rem;
  font-style: italic;
}

.msp-header-reset-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.72rem;
  font-weight: 500;
  padding: 0.2rem 0.65rem;
  border-radius: 999px;
  border: 1px solid var(--border-medium);
  background: transparent;
  color: var(--text-faint);
  cursor: pointer;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
  white-space: nowrap;
}
.msp-header-reset-btn:hover {
  background: var(--danger-bg, rgba(220,38,38,0.08));
  color: var(--danger-text, #dc2626);
  border-color: var(--danger-text, #dc2626);
}

/* ── Theme thumbnail grid ── */
.msp-theme-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.5rem;
}
@media (min-width: 640px) {
  .msp-theme-grid {
    grid-template-columns: repeat(auto-fit, minmax(8.75rem, 1fr));
    gap: 0.65rem;
  }
}
.msp-theme-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  border: 2px solid transparent;
  border-radius: 8px;
  padding: clamp(0.3rem, 0.45vw, 0.42rem);
  transition: border-color 0.15s;
  overflow: hidden;
}
.msp-theme-tile:hover {
  border-color: var(--border-hover);
}
.msp-theme-active {
  border-color: var(--primary-color);
}
.msp-theme-swatch {
  width: 100%;
  aspect-ratio: 16 / 10;
  border-radius: 4px;
  border: 1px solid var(--border-medium);
  background-size: 300% 300%;
  background-position: 100% 0%;
  position: relative;
}
@media (min-width: 640px) {
  .msp-theme-swatch {
    aspect-ratio: 16 / 9;
  }
}
.msp-theme-badge {
  position: absolute;
  top: 3px;
  right: 4px;
  font-size: 0.7rem;
  line-height: 1;
  background: var(--warning-bg);
  color: var(--warning-text);
  border-radius: 3px;
  padding: 1px 3px;
  pointer-events: none;
}
.msp-theme-selected {
  position: absolute;
  top: 3px;
  left: 4px;
  font-size: 0.7rem;
  line-height: 1;
  background: var(--primary-color);
  color: var(--text-primary);
  border-radius: 3px;
  padding: 1px 3px;
  pointer-events: none;
}
.msp-theme-label {
  font-size: 0.7rem;
  margin-top: 0.2rem;
  text-align: center;
  white-space: nowrap;
  color: var(--text-muted);
}
</style>
