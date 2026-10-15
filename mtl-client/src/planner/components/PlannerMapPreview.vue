<template>
  <figure class="planner-map-preview">
    <div class="planner-map-preview__image">
      <img
        :src="mapPreview"
        width="1280"
        height="1044"
        alt="Map near Valongo with recorded tracks to the west and marked hiking routes in the hills to the east."
      />
    </div>
  </figure>
</template>

<script setup lang="ts">
import mapPreview from '@/assets/planner/map-context.webp';
</script>

<style scoped>
.planner-map-preview {
  min-width: 0;
  margin: 0;
}

.planner-map-preview__image {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: 0.65rem;
  background: var(--surface-hover);
}

.planner-map-preview img {
  display: block;
  width: 100%;
  height: auto;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}
</style>
