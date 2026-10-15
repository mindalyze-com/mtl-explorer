<template>
  <button
    v-bind="attrs"
    type="button"
    class="media-thumbnail"
    :aria-busy="state === 'recovering' || undefined"
    @click="onClick"
  >
    <slot :image-props="imageProps" />
    <span v-if="state !== 'idle'" class="media-thumbnail__status" aria-hidden="true">
      {{ state === 'failed' ? 'No preview' : 'Loading…' }}
    </span>
  </button>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, useAttrs, watch } from 'vue';
import { fetchMediaImage } from '@/utils/fetchMediaImage';

defineOptions({ inheritAttrs: false });
const props = withDefaults(defineProps<{ src: string; enabled?: boolean }>(), {
  enabled: true,
});
const emit = defineEmits<{ click: [event: MouseEvent] }>();
const attrs = useAttrs();
const state = ref<'idle' | 'recovering' | 'failed'>('idle');
const displaySrc = ref(props.src);
let controller: AbortController | null = null;
let objectUrl: string | null = null;

const imageProps = computed(() => ({
  src: displaySrc.value,
  loading: 'lazy' as const,
  fetchpriority: 'low' as const,
  onError,
  onLoad: () => {
    state.value = 'idle';
  },
}));

function reset() {
  controller?.abort();
  controller = null;
  if (objectUrl) URL.revokeObjectURL(objectUrl);
  objectUrl = null;
  displaySrc.value = props.src;
  state.value = 'idle';
}

async function recover() {
  if (!props.enabled || controller) return;
  const request = new AbortController();
  controller = request;
  state.value = 'recovering';
  try {
    const blob = await fetchMediaImage(props.src, request.signal, 'thumbnail');
    if (request.signal.aborted || controller !== request) return;
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = URL.createObjectURL(blob);
    displaySrc.value = objectUrl;
  } catch {
    if (!request.signal.aborted && controller === request) state.value = 'failed';
  } finally {
    if (controller === request) controller = null;
  }
}

function onError() {
  if (!props.enabled || controller) return;
  // An invalid recovered image must offer a manual retry, not loop on its blob URL.
  if (objectUrl) state.value = 'failed';
  else void recover();
}

function onClick(event: MouseEvent) {
  emit('click', event);
  if (state.value === 'failed' && !event.defaultPrevented) void recover();
}

watch(() => [props.src, props.enabled], reset, { flush: 'sync' });
onBeforeUnmount(reset);
</script>

<style scoped>
.media-thumbnail {
  position: relative;
}

.media-thumbnail__status {
  position: absolute;
  inset: auto 0.25rem 0.25rem auto;
  display: grid;
  min-height: 2.75rem;
  padding: 0.25rem 0.5rem;
  place-items: center;
  border: 1px solid var(--mp-border, var(--border-medium));
  border-radius: 0.35rem;
  color: var(--mp-text-strong, var(--text-primary));
  background: var(--mp-surface, var(--surface-sheet-solid));
  font-size: var(--text-xs-size);
  line-height: 1.2;
  pointer-events: none;
}
</style>
