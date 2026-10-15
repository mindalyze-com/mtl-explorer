<template>
  <button
    type="button"
    class="media-viewer-theme-toggle"
    :aria-label="isMediaViewerDark ? 'Use light photo viewer' : 'Use dark photo viewer'"
    :title="isMediaViewerDark ? 'Use light photo viewer' : 'Use dark photo viewer'"
    @click.stop="toggleTheme"
  >
    <i :class="isMediaViewerDark ? 'bi bi-sun' : 'bi bi-moon-stars'" aria-hidden="true"></i>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useMediaViewerTheme, type MediaViewerTheme } from '@/composables/useMediaViewerTheme';

const props = defineProps<{ theme?: MediaViewerTheme }>();
const emit = defineEmits<{ 'update:theme': [theme: MediaViewerTheme] }>();
const { mediaViewerTheme, toggleMediaViewerTheme } = useMediaViewerTheme();
const isMediaViewerDark = computed(() => (props.theme ?? mediaViewerTheme.value) === 'dark');

function toggleTheme() {
  if (props.theme === undefined) {
    toggleMediaViewerTheme();
    return;
  }
  emit('update:theme', props.theme === 'dark' ? 'light' : 'dark');
}
</script>
