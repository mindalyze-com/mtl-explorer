<template>
  <div>
    <BottomSheet v-model="showMenu" title="Filter" icon="bi bi-funnel" :detents="[{ height: '72vh' }, { height: '95vh' }]" @closed="onSheetClosed">
      <CustomFilter
        ref="customFilter"
        :palette="palette"
        :total-track-count="totalTrackCount"
        :visible-track-count="visibleTrackCount"
        @filterAppliedEvent="onClose"
        @filterChangedEvent="onFilterChanged"
        @start-geo-drawing="onStartGeoDrawing"
        @clear-geo-shape="onClearGeoShape"
      />
    </BottomSheet>
  </div>
</template>

<script lang="ts">
import {computed, defineComponent, inject} from "vue";
import {useFilterStore} from "@/stores/filterStore";
import CustomFilter from "@/components/filter/CustomFilter.vue";
import BottomSheet from "@/components/ui/BottomSheet.vue";
import type {ParamDefinition} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';

const EVENTS = {
  filterAppliedEvent: "filterAppliedEvent",
}

export default defineComponent({
  name: 'Filter',
  components: {CustomFilter, BottomSheet},
  props: ['tileLayer', 'palette', 'totalTrackCount', 'visibleTrackCount'],
  data(): {
    showMenu: boolean,
  } {
    return {
      showMenu: false,
    };
  },
  setup() {
    const filterStore = useFilterStore();
    return {
      toast: inject("toast"),
      filterStore,
      // `active` was previously a local data field updated via activeFilterCheck().
      // Now it's a reactive view onto the filterStore so any save through the
      // store (CustomFilter.vue) automatically updates the toolbar chip.
      active: computed(() => filterStore.isActive),
    };
  },
  mounted() {
    console.log("Filter (main) mounted.");
    // Hydrate the store so `active` reflects persisted state on first render.
    this.filterStore.ensureLoaded().catch((e) => {
      console.warn('Filter: store ensureLoaded failed', e);
    });
  },

  methods: {

    async toggle() {
      this.showMenu = !this.showMenu;
      if (this.showMenu) {
        this.$emit('tool-opened');
      }
    },

    close() {
      this.showMenu = false;
    },

    resetFilter() {
    },

    async onSheetClosed() {
      // Force a re-read in case legacy code paths mutated FilterService directly.
      // Once all writes funnel through the store this can be removed.
      await this.filterStore.refresh();
      if (!this.active) {
        this.$emit('tool-closed');
      }
    },

    onClose() {
      this.showMenu = false;
      this.$emit(EVENTS.filterAppliedEvent);
    },

    onFilterChanged() {
      this.$emit(EVENTS.filterAppliedEvent);
    },

    onStartGeoDrawing(paramDef: ParamDefinition) {
      this.$emit('start-geo-drawing', paramDef);
    },
    onClearGeoShape(paramDef: ParamDefinition) {
      this.$emit('clear-geo-shape', paramDef);
    },
    /** Called by Map.vue when drawing completes */
    onGeoDrawingComplete(paramDef: ParamDefinition, shape: any) {
      (this.$refs.customFilter as InstanceType<typeof CustomFilter>)?.onGeoDrawingComplete(paramDef, shape);
    },
    /** Returns all currently configured geo shapes for rendering on the map. */
    getGeoShapes() {
      return (this.$refs.customFilter as InstanceType<typeof CustomFilter>)?.getGeoShapes() ?? { circles: {}, rectangles: {}, polygons: {} };
    },
  },
});
</script>

<style scoped>
</style>
