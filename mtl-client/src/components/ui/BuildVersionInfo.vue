<template>
  <div class="build-version">
    <div class="build-version__summary">
      <code>{{ version }}</code>
      <template v-if="fullRevision">
        <span aria-hidden="true">·</span>
        <button
          type="button"
          class="build-version__revision"
          :aria-label="expanded ? 'Hide full Git revision' : 'Show full Git revision'"
          :aria-expanded="expanded"
          :aria-controls="expanded ? revisionId : undefined"
          aria-haspopup="dialog"
          @pointerdown="revisionPointerStart = { x: $event.clientX, y: $event.clientY }"
          @click="toggleRevision"
        >
          <code>{{ shortRevision }}</code>
        </button>
      </template>
    </div>
    <Popover
      v-if="fullRevision"
      :id="revisionId"
      ref="revisionPopover"
      append-to="body"
      aria-label="Full Git revision"
      @show="expanded = true"
      @hide="expanded = false"
    >
      <div class="build-version__details">
        <span>Git revision</span>
        <code tabindex="0" autofocus>{{ fullRevision }}</code>
      </div>
    </Popover>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue';
import Popover from 'primevue/popover';

const props = defineProps<{ version: string; revision?: string | null }>();
const SHORT_REVISION_LENGTH = 8;
const SELECTION_DRAG_THRESHOLD_PX = 3;
const revisionId = useId();
const fullRevision = computed(() => props.revision?.trim() || '');
const shortRevision = computed(() => {
  const match = fullRevision.value.match(/^([a-f0-9]{40}|[a-f0-9]{64})(-dirty)?$/i);
  return match ? match[1].slice(0, SHORT_REVISION_LENGTH) + (match[2] ?? '') : fullRevision.value;
});
const expanded = ref(false);
const revisionPopover = ref<InstanceType<typeof Popover> | null>(null);
let revisionPointerStart: { x: number; y: number } | null = null;

function toggleRevision(event: MouseEvent) {
  const pointerStart = revisionPointerStart;
  revisionPointerStart = null;
  const selection = window.getSelection();
  if (
    event.detail > 0 &&
    pointerStart &&
    Math.hypot(event.clientX - pointerStart.x, event.clientY - pointerStart.y) > SELECTION_DRAG_THRESHOLD_PX &&
    selection &&
    !selection.isCollapsed &&
    event.currentTarget instanceof Node &&
    selection.containsNode(event.currentTarget, true)
  )
    return;
  revisionPopover.value?.toggle(event);
}

watch([() => props.version, fullRevision], () => {
  revisionPopover.value?.hide();
  expanded.value = false;
});
</script>

<style scoped>
.build-version {
  min-width: 0;
}

.build-version__summary {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.15rem 0.35rem;
}

.build-version code,
.build-version__details code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: inherit;
  overflow-wrap: anywhere;
  -webkit-user-select: text;
  user-select: text;
}

.build-version button {
  min-height: 28px;
  padding: 0.15rem 0.2rem;
  border: 0;
  border-radius: 0.25rem;
  background: transparent;
  color: var(--text-muted);
  font: inherit;
  cursor: pointer;
}

.build-version button:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}

.build-version button:focus-visible {
  outline: 2px solid var(--accent-text);
  outline-offset: 2px;
}

.build-version__revision {
  white-space: nowrap;
  -webkit-user-select: text;
  user-select: text;
  text-decoration: underline dotted;
  text-underline-offset: 0.2em;
}

.build-version__details {
  display: grid;
  max-width: min(32rem, calc(100vw - 4rem));
  gap: 0.15rem;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  text-align: left;
}

.build-version__details > span {
  font-family: var(--font-family-ui);
  font-size: var(--text-xs-size);
  color: var(--text-muted);
}
</style>
