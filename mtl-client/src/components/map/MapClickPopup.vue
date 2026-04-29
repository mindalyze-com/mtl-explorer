<template>
  <div class="container">

    my popup component

    <button @click="closePopup">Close Popup</button>

    <table>
      <thead>
        <tr>
          <th></th>
          <th>Track Name</th>
          <th>Description</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="gpsTrack in gpsTracks"
            @mouseover="gpsTrack.id !== undefined && handleMouseOver(gpsTrack.id)"
            @mouseleave="gpsTrack.id !== undefined && handleMouseLeave(gpsTrack.id)"
        >
          <td><TrackShapePreview :trackId="gpsTrack.id!" :width="56" :height="40" /></td>
          <td><ActivityTypeBadge :type="gpsTrack.activityType" size="xs" /></td>
          <td>{{ gpsTrack.trackName }}</td>
        </tr>
      </tbody>
    </table>

  </div>
</template>

<script lang="ts">
import {defineComponent, inject, type PropType} from "vue";
import type {GpsTrack} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/GpsTrack';

import {ColorPalette} from "@/components/filter/ColorPalette";
import Card from 'primevue/card';
import Message from 'primevue/message';
import TrackShapePreview from '@/components/ui/TrackShapePreview.vue';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';

export default defineComponent({
  name: "MapClickPopup",
  components: { TrackShapePreview, ActivityTypeBadge },
  props: {
    popup: {
      type: Object as PropType<{ close: () => void }>,
      required: true,
    },
    gpsTracks: {
      type: Array as PropType<Array<GpsTrack>>,
    },
    nearbyTrackIds: {
      type: Array,
      required: true,
    },
    onTrackHover: {
      type: Function as PropType<(trackId: string | number) => void>,
      required: true,
    },
    onTrackLeave: {
      type: Function as PropType<(trackId: string | number) => void>,
      required: true,
    },
  },
  data() {
    return {};
  },
  mounted() {
  },
  methods: {
    closePopup() {
      this.popup.close();  // Close the popup programmatically
    },
    handleMouseOver(trackId: string | number) {
      this.onTrackHover(trackId);
    },
    handleMouseLeave(trackId: string | number) {
      this.onTrackLeave(trackId);
    },
  },
});
</script>

<style scoped>

.container {
  max-width: 100%;
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 300px;
  border-collapse: collapse;
}

th, td {
  padding: 0.5rem;
  text-align: left;
  border: 1px solid var(--chip-border);
}

/* Mobile responsive improvements */
@media screen and (max-width: 768px) {
  .container {
    font-size: var(--text-sm-size);
  }
  
  th, td {
    padding: 0.25rem;
    white-space: nowrap;
  }
}

</style>
