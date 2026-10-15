<template>
  <div v-if="description" class="track-description" :class="{ 'track-description--expanded': expanded }">
    <span ref="measurement" class="track-description__measurement" aria-hidden="true">{{ description }}</span>
    <p :id="descriptionId" class="track-description__text">{{ description }}</p>
    <button
      v-if="overflows"
      type="button"
      class="track-description__toggle"
      :aria-expanded="expanded"
      :aria-controls="descriptionId"
      :aria-label="expanded ? 'Show less track description' : 'Show full track description'"
      @keydown.enter.stop
      @keydown.space.stop
      @click.stop="expanded = !expanded"
    >
      {{ expanded ? 'Less' : 'More' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue';
import { observeElementResize } from '@/utils/observeElementResize';

const props = defineProps<{ text: string }>();
const description = computed(() => props.text.trim());
const descriptionId = `track-description-${useId()}`;
const measurement = ref<HTMLElement | null>(null);
const expanded = ref(false);
const overflows = ref(false);
const OVERFLOW_TOLERANCE_PX = 1;

function measureOverflow() {
  const element = measurement.value;
  if (!element?.clientWidth) return;
  // Measure the full row, so showing More cannot itself cause overflow.
  overflows.value = element.scrollWidth > element.clientWidth + OVERFLOW_TOLERANCE_PX;
  if (!overflows.value) expanded.value = false;
}

watch(
  measurement,
  (element, _previous, onCleanup) => {
    if (!element) return;
    measureOverflow();
    if (typeof ResizeObserver !== 'undefined') onCleanup(observeElementResize(element, measureOverflow));
  },
  { flush: 'post' }
);
watch(
  description,
  () => {
    expanded.value = false;
    measureOverflow();
  },
  { flush: 'post' }
);
onMounted(() => document.fonts?.addEventListener('loadingdone', measureOverflow));
onBeforeUnmount(() => document.fonts?.removeEventListener('loadingdone', measureOverflow));
</script>

<style scoped>
.track-description {
  position: relative;
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  min-width: 0;
  color: var(--track-description-color, var(--text-primary));
  font-size: var(--track-description-font-size, var(--text-sm-size));
  line-height: 1.5;
}
.track-description__measurement {
  position: absolute;
  inset-inline: 0;
  top: 0;
  overflow: hidden;
  visibility: hidden;
  white-space: nowrap;
  pointer-events: none;
}
.track-description__text {
  flex: 1 1 auto;
  min-width: 0;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.track-description--expanded .track-description__text {
  overflow: visible;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.track-description__toggle {
  flex: 0 0 auto;
  margin-block: -0.25rem;
  padding: 0.25rem;
  border: 0;
  border-radius: 4px;
  color: var(--accent-text);
  background: transparent;
  font: inherit;
  cursor: pointer;
}
.track-description__toggle:hover {
  background: var(--surface-hover);
  text-decoration: underline;
}
.track-description__toggle:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
</style>
