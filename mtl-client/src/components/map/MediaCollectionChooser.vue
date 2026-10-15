<template>
  <section class="media-collection-chooser" aria-live="polite">
    <p class="media-collection-chooser__intro">Choose which photo collection to open.</p>

    <div class="media-collection-chooser__options">
      <button
        ref="primaryButton"
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
          <strong>Map view at tap</strong>
          <span>Positioned photos in the map view when you tapped.</span>
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
          <strong>Nearby GPS tracks</strong>
          <span>Open linked photos, or track details when no photos are matched.</span>
        </span>
        <span class="media-collection-option__count">
          {{ tracksLoading ? 'Loading…' : activityCountLabel }}
        </span>
        <i class="bi bi-chevron-right media-collection-option__chevron" aria-hidden="true"></i>
      </button>
    </div>
    <div v-if="tracksError" class="media-collection-error" role="status" data-test="media-collection-error">
      <span>{{ tracksError }}</span>
      <button type="button" :disabled="tracksLoading" data-test="media-collection-retry" @click="retryActivities">
        Retry GPS tracks
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import type { MediaOverlaySelection } from '@/layers/MediaOverlay';

defineOptions({ name: 'MediaCollectionChooser' });

const props = withDefaults(
  defineProps<{
    selection: MediaOverlaySelection;
    trackCount?: number;
    tracksLoading?: boolean;
    tracksError?: string | null;
  }>(),
  {
    trackCount: 0,
    tracksLoading: false,
    tracksError: null,
  }
);

const emit = defineEmits<{
  'choose-primary': [];
  'choose-viewport': [];
  'open-activities': [];
  'retry-activities': [];
}>();
const primaryButton = ref<HTMLButtonElement | null>(null);

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
  if (!viewportCount.value) return false;
  const primaryIds = new Set(props.selection.mediaIds);
  if (primaryIds.size !== props.selection.totalMediaCount) return true;
  const viewportIds = new Set(props.selection.viewportMediaPoints.map((point) => point.id));
  return primaryIds.size !== viewportIds.size || [...primaryIds].some((id) => !viewportIds.has(id));
});
const activityCountLabel = computed(
  () => `${props.trackCount.toLocaleString()} ${props.trackCount === 1 ? 'track' : 'tracks'}`
);

async function retryActivities(): Promise<void> {
  emit('retry-activities');
  await nextTick();
  primaryButton.value?.focus();
}

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
  background: var(--surface-sheet-solid);
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
.media-collection-error {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.75rem;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
}
.media-collection-error button {
  min-height: 2.75rem;
  padding: 0.4rem 0.6rem;
  color: var(--text-primary);
  background: var(--surface-sheet-solid);
  border: 1px solid var(--border-default);
  border-radius: 0.5rem;
  cursor: pointer;
}
.media-collection-error button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

@media (max-width: 600px) {
  .media-collection-chooser {
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
