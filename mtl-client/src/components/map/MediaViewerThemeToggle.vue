<template>
  <button
    type="button"
    class="media-viewer-theme-toggle"
    :aria-label="isMediaViewerDark ? 'Use light photo viewer' : 'Use dark photo viewer'"
    :title="isMediaViewerDark ? 'Use light photo viewer' : 'Use dark photo viewer'"
    @click.stop="toggleMediaViewerTheme"
  >
    <i :class="isMediaViewerDark ? 'bi bi-sun' : 'bi bi-moon-stars'" aria-hidden="true"></i>
  </button>
</template>

<script setup lang="ts">
import { useMediaViewerTheme } from '@/composables/useMediaViewerTheme';

const { isMediaViewerDark, toggleMediaViewerTheme } = useMediaViewerTheme();
</script>
