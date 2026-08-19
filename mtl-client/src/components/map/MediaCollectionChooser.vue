<template>
  <section class="media-collection-chooser" aria-live="polite">
    <p class="media-collection-chooser__intro">Choose which photo collection to open.</p>

    <div class="media-collection-chooser__options">
      <button
        type="button"
        class="media-collection-option"
        data-test="media-collection-primary"
        @click="emit('choose-primary')"
      >
        <span class="media-collection-option__icon"><i :class="primaryIcon" aria-hidden="true"></i></span>
        <span class="media-collection-option__body">
          <strong>{{ primaryLabel }}</strong>
          <span>{{ primaryDescription }}</span>
        </span>
        <span class="media-collection-option__count">{{ countLabel(selection.totalMediaCount) }}</span>
        <i class="bi bi-chevron-right media-collection-option__chevron" aria-hidden="true"></i>
      </button>

      <button
        v-if="showViewportOption"
        type="button"
        class="media-collection-option"
        data-test="media-collection-viewport"
        @click="emit('choose-viewport')"
      >
        <span class="media-collection-option__icon"><i class="bi bi-map" aria-hidden="true"></i></span>
        <span class="media-collection-option__body">
          <strong>Current map view</strong>
          <span>All positioned photos visible on the map at this moment.</span>
        </span>
        <span class="media-collection-option__count">{{ countLabel(viewportCount) }}</span>
        <i class="bi bi-chevron-right media-collection-option__chevron" aria-hidden="true"></i>
      </button>

      <button
        v-if="tracksLoading || trackCount > 0"
        type="button"
        class="media-collection-option"
        data-test="media-collection-activities"
        :disabled="tracksLoading"
        @click="emit('open-activities')"
      >
        <span class="media-collection-option__icon"><i class="bi bi-signpost-split" aria-hidden="true"></i></span>
        <span class="media-collection-option__body">
          <strong>Photos along a GPS track</strong>
          <span>Open photos linked to a GPS activity that passes this location.</span>
        </span>
        <span class="media-collection-option__count">
          {{ tracksLoading ? 'Loading…' : activityCountLabel }}
        </span>
        <i class="bi bi-chevron-right media-collection-option__chevron" aria-hidden="true"></i>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { MediaOverlaySelection } from '@/layers/MediaOverlay';

defineOptions({ name: 'MediaCollectionChooser' });

const props = withDefaults(
  defineProps<{
    selection: MediaOverlaySelection;
    trackCount?: number;
    tracksLoading?: boolean;
  }>(),
  {
    trackCount: 0,
    tracksLoading: false,
  }
);

const emit = defineEmits<{
  'choose-primary': [];
  'choose-viewport': [];
  'open-activities': [];
}>();

const viewportCount = computed(() => props.selection.viewportMediaPoints.length);
const primaryLabel = computed(() => {
  if (props.selection.kind === 'cluster') return 'This cluster';
  return props.selection.totalMediaCount === 1 ? 'This photo' : 'This location';
});
const primaryDescription = computed(() => {
  if (props.selection.kind === 'cluster') return 'Photos represented by this marker at the current zoom.';
  if (props.selection.totalMediaCount === 1) return 'Open only the selected photo.';
  return 'Photos grouped at the clicked marker.';
});
const primaryIcon = computed(() => (props.selection.kind === 'cluster' ? 'bi bi-collection-fill' : 'bi bi-image'));
const showViewportOption = computed(() => {
  if (props.selection.kind === 'cluster') return viewportCount.value > 0;
  const primaryIds = props.selection.mediaIds;
  const viewportIds = props.selection.viewportMediaPoints.map((point) => point.id);
  return primaryIds.length !== viewportIds.length || primaryIds.some((id, index) => id !== viewportIds[index]);
});
const activityCountLabel = computed(
  () => `${props.trackCount.toLocaleString()} ${props.trackCount === 1 ? 'activity' : 'activities'}`
);

function countLabel(count: number): string {
  return `${count.toLocaleString()} ${count === 1 ? 'photo' : 'photos'}`;
}
</script>

<style scoped>
.media-collection-chooser {
  --media-collection-bottom-buffer: 0.75rem;

  box-sizing: border-box;
  padding: 0.25rem var(--dlg-padding)
    max(
      calc(var(--dlg-padding) + var(--media-collection-bottom-buffer)),
      calc(env(safe-area-inset-bottom) + var(--media-collection-bottom-buffer))
    );
}

.media-collection-chooser__intro {
  margin: 0 0 0.85rem;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
}

.media-collection-chooser__options {
  display: grid;
  gap: 0.65rem;
  padding: 0;
  margin: 0;
  list-style: none;
}

.media-collection-option {
  display: grid;
  grid-template-columns: 2.5rem minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  min-height: 4.5rem;
  padding: 0.75rem;
  color: var(--text-primary);
  text-align: left;
  background: var(--surface-card);
  border: 1px solid var(--border-default);
  border-radius: 10px;
  cursor: pointer;
}

.media-collection-option:hover {
  border-color: var(--accent);
  background: var(--surface-elevated);
}

.media-collection-option:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.media-collection-option:disabled {
  cursor: progress;
  opacity: 0.7;
}

.media-collection-option__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  color: var(--accent);
  background: var(--accent-bg);
  border-radius: 50%;
  font-size: 1.1rem;
}

.media-collection-option__body {
  display: grid;
  gap: 0.2rem;
  min-width: 0;
}

.media-collection-option__body strong {
  overflow: hidden;
  font-size: var(--text-sm-size);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-collection-option__body span {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  line-height: 1.35;
}

.media-collection-option__count {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: 700;
  white-space: nowrap;
}

.media-collection-option__chevron {
  color: var(--text-muted);
}

@media (max-width: 600px) {
  .media-collection-chooser {
    min-height: min(26rem, calc(82dvh - 3rem));
    padding-inline: 0.75rem;
  }

  .media-collection-option {
    grid-template-columns: 2.5rem minmax(0, 1fr) auto;
    gap: 0.55rem;
    min-height: 4.75rem;
    padding: 0.65rem;
  }

  .media-collection-option__count {
    grid-column: 2;
    justify-self: start;
  }

  .media-collection-option > .media-collection-option__chevron {
    grid-column: 3;
    grid-row: 1 / span 2;
  }
}
</style>
