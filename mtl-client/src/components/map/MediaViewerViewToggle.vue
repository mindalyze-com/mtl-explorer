<template>
  <details
    ref="menu"
    class="media-viewer-view-toggle media-viewer-panel-menu"
    @click.stop
    @keydown.esc.stop.prevent="close"
  >
    <summary aria-label="View options">
      <i class="bi bi-layout-sidebar" aria-hidden="true"></i>View<i class="bi bi-chevron-down" aria-hidden="true"></i>
    </summary>
    <div class="media-viewer-panel-options" role="group" aria-label="Visible photo panels" data-sheet-drag-exclude>
      <label
        ><input
          type="checkbox"
          aria-label="Details"
          :checked="modelValue"
          @change="emit('update:modelValue', ($event.target as HTMLInputElement).checked)"
        />Details</label
      >
      <label
        ><input
          type="checkbox"
          aria-label="Location map"
          :checked="mapVisible"
          @change="emit('update:mapVisible', ($event.target as HTMLInputElement).checked)"
        />Location map</label
      >
      <label
        ><input
          type="checkbox"
          aria-label="Photo strip"
          :checked="photosVisible"
          @change="emit('update:photosVisible', ($event.target as HTMLInputElement).checked)"
        />Photo strip</label
      >
      <button type="button" :aria-label="anyPanels ? 'Photo only' : 'Restore panels'" @click="togglePanels">
        {{ anyPanels ? 'Photo only' : 'Restore panels' }}
      </button>
    </div>
  </details>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
const props = withDefaults(defineProps<{ modelValue: boolean; mapVisible?: boolean; photosVisible?: boolean }>(), {
  mapVisible: true,
  photosVisible: true,
});
const emit = defineEmits<{
  'update:modelValue': [visible: boolean];
  'update:mapVisible': [visible: boolean];
  'update:photosVisible': [visible: boolean];
}>();
const menu = ref<HTMLDetailsElement | null>(null);
const anyPanels = computed(() => props.modelValue || props.mapVisible || props.photosVisible);
let previousPanels = [true, true, true];
function close() {
  if (menu.value) {
    menu.value.open = false;
    menu.value.querySelector('summary')?.focus();
  }
}
function togglePanels() {
  let values = previousPanels;
  if (anyPanels.value) {
    previousPanels = [props.modelValue, props.mapVisible, props.photosVisible];
    values = [false, false, false];
  }
  emit('update:modelValue', values[0]);
  emit('update:mapVisible', values[1]);
  emit('update:photosVisible', values[2]);
  close();
}
function onOutsidePointer(event: PointerEvent) {
  if (menu.value?.open && event.target instanceof Node && !menu.value.contains(event.target)) menu.value.open = false;
}
onMounted(() => document.addEventListener('pointerdown', onOutsidePointer));
onBeforeUnmount(() => document.removeEventListener('pointerdown', onOutsidePointer));
</script>

<style scoped>
.media-viewer-view-toggle {
  position: relative;
  flex-shrink: 0;
}

.media-viewer-view-toggle summary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  min-height: 2rem;
  padding: 0.25rem 0.55rem;
  box-sizing: border-box;
  border: 1px solid var(--mp-border-strong);
  border-radius: 8px;
  color: var(--mp-text);
  background: var(--mp-control-bg);
  font: inherit;
  font-size: var(--text-xs-size);
  white-space: nowrap;
  cursor: pointer;
}

.media-viewer-view-toggle[open] summary {
  color: var(--mp-text-strong);
  background: var(--mp-control-active);
}

.media-viewer-view-toggle summary:hover {
  background: var(--mp-control-hover);
}

.media-viewer-view-toggle summary:focus-visible {
  outline: 2px solid var(--mp-link);
  outline-offset: 2px;
}

.media-viewer-view-toggle summary::-webkit-details-marker {
  display: none;
}
.media-viewer-panel-options {
  position: absolute;
  right: 0;
  top: calc(100% + 6px);
  z-index: 8;
  width: 190px;
  padding: 0.5rem;
  border: 1px solid var(--mp-border-strong);
  border-radius: 10px;
  background: var(--mp-surface);
  color: var(--mp-text-strong);
  box-shadow: var(--mp-panel-shadow);
}
.media-viewer-panel-options label {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.6rem 0.5rem;
  cursor: pointer;
}
.media-viewer-panel-options input {
  accent-color: var(--mp-link);
}
.media-viewer-panel-options button {
  width: 100%;
  min-height: 36px;
  margin-top: 0.4rem;
  border: 1px solid var(--mp-border-strong);
  border-radius: 6px;
  background: var(--mp-control-bg);
  color: var(--mp-text);
  font: inherit;
  cursor: pointer;
}
@media (max-width: 768px) {
  .media-viewer-view-toggle summary,
  .media-viewer-panel-options label,
  .media-viewer-panel-options button {
    min-height: 44px;
  }
  .media-viewer-panel-options {
    left: 0;
    right: auto;
  }
}
</style>
