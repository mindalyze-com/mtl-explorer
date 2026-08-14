<template>
  <Map3DRenderer
    v-if="mapMode === '3d'"
    @mode-close-requested="onModeCloseRequested"
    @load-failed="emit('load-failed')"
  />
  <Map2DRenderer
    v-else
    :from-login="props.fromLogin"
    v-bind="$attrs"
    @tracks-loaded="emit('tracks-loaded')"
    @load-failed="emit('load-failed')"
    @syncing="emit('syncing', $event)"
  />
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia';
import Map2DRenderer from '@/components/map/Map2DRenderer.vue';
import Map3DRenderer from '@/components/map/Map3DRenderer.vue';
import { useMapStateStore } from '@/stores/mapStateStore';
import type { MapControllerEmit, MapControllerProps } from '@/components/map/composables/mapControllerRuntime';

defineOptions({
  name: 'Map',
  inheritAttrs: false,
});

const props = withDefaults(defineProps<MapControllerProps>(), { fromLogin: false });
const emit = defineEmits<MapControllerEmit>();

const mapStateStore = useMapStateStore();
mapStateStore.resetSessionState();

const { mapMode } = storeToRefs(mapStateStore);

function onModeCloseRequested() {
  mapStateStore.exit3DReplay();
}
</script>
