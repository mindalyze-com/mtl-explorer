<template>
  <div>
    <div class="tool-container">
      <Button
        :icon="active ? 'bi bi-info-circle-fill' : 'bi bi-info-circle'"
        :severity="active ? 'danger' : 'primary'"
        @click="onToggleTool"
      />
      <!-- using a badge could be an option to show the filtered tracks number -->

      <Dialog v-model:visible="showMenu" :onclose="onClose" maximizable modal header="Info" class="tool-dialog">
        <div class="info-dialog-inner">
          <div v-if="tracksCount">
            There are <b>{{ tracksCount }}</b> tracks on the map.
          </div>
          <div>
            <ColorPaletteLegend :palette="palette" />
          </div>
          <div class="map-attribution single-line">
            <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OSM</a> |
            <a href="https://www.swisstopo.admin.ch" target="_blank" rel="noopener">swisstopo</a> |
            <a href="https://leafletjs.com" target="_blank" rel="noopener">Leaflet</a>
          </div>
        </div>
      </Dialog>
    </div>
  </div>
</template>

<script setup lang="ts">
import { inject, onMounted, ref } from 'vue';
import ColorPaletteLegend from '@/components/map/ColorPaletteLegend.vue';
import { ColorPalette } from '@/components/filter/ColorPalette';

const EVENTS = {
  filterAppliedEvent: 'filterAppliedEvent',
} as const;

defineProps<{
  palette: ColorPalette;
  tracksCount?: number;
}>();

const emit = defineEmits<{
  (event: 'filterAppliedEvent'): void;
}>();

const toast = inject('toast');
const active = ref(false);
const showMenu = ref(false);

onMounted(() => {
  console.log('Filter (main) mounted.');
  activeFilterCheck();
});

async function onToggleTool() {
  showMenu.value = !showMenu.value;
}

function resetFilter() {}

function onClose() {
  showMenu.value = false;
  activeFilterCheck();
  emit(EVENTS.filterAppliedEvent);
}

async function activeFilterCheck() {
  // check if filter needs to be marked
}

void toast;
void resetFilter;
</script>

<style scoped>
.tool-container {
  position: relative;
  display: inline-block;
}

.info-dialog-inner {
  position: relative;
  min-height: 120px; /* ensure some space so attribution does not overlap */
  padding-bottom: 1.2rem; /* space for attribution */
}

.map-attribution {
  position: absolute;
  right: 0.4rem;
  bottom: 0.25rem;
  font-size: 0.55rem;
  line-height: 0.7rem;
  opacity: 0.7;
  display: flex;
  gap: 0.25rem;
  flex-wrap: nowrap;
  align-items: center;
  background: rgba(255, 255, 255, 0.6);
  padding: 0.15rem 0.35rem;
  border-radius: 3px;
}

.map-attribution.single-line {
  white-space: nowrap;
}

.map-attribution a {
  color: var(--primary-color, #3f51b5);
  text-decoration: none;
}

.map-attribution a:hover {
  text-decoration: underline;
}

.attribution-separator {
  margin: 0.5rem 0 0.4rem 0;
  border: none;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
}

.attr-line {
  display: inline;
}
</style>
