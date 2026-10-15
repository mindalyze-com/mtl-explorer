<template>
  <button
    type="button"
    class="track-choice-row"
    :class="{ 'track-choice-row--selected': selected }"
    :aria-pressed="selected"
    :aria-label="accessibleLabel"
  >
    <i class="bi bi-signpost-split track-choice-row__icon" aria-hidden="true"></i>
    <span class="track-choice-row__content">
      <strong class="track-choice-row__title">{{ label }}</strong>
      <span v-if="descriptionText" class="track-choice-row__description" :title="descriptionText">
        {{ descriptionText }}
      </span>
      <span class="track-choice-row__metadata">
        <ActivityTypeBadge v-if="activityType" :type="activityType" size="xs" class="track-choice-row__activity" />
        <TrackChoiceFacts :facts="facts" />
        <span v-if="!activityType && !facts.length && !descriptionText">{{ context }}</span>
      </span>
    </span>
    <span
      v-if="mediaLabel"
      class="track-choice-row__media"
      :class="{ 'track-choice-row__media--empty': matchedMediaCount === 0 }"
    >
      <i v-if="positiveMediaCount" class="bi bi-camera-fill" aria-hidden="true"></i>
      {{ mediaLabel }}
    </span>
    <i v-if="selected" class="bi bi-check2 track-choice-row__selected" aria-hidden="true"></i>
    <TrackShapePreview
      :track-id="trackId"
      :width="48"
      :height="32"
      :padding="3"
      :line-width="1.75"
      line-color="currentColor"
      class="track-choice-row__shape"
      aria-hidden="true"
    />
    <i class="bi bi-chevron-right track-choice-row__chevron" aria-hidden="true"></i>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import ActivityTypeBadge, { activityLabel } from '@/components/ui/ActivityTypeBadge.vue';
import TrackShapePreview from '@/components/ui/TrackShapePreview.vue';
import TrackChoiceFacts from './TrackChoiceFacts.vue';
import { formatTrackChoiceFacts } from './trackChoiceFactFormatting';

const props = withDefaults(
  defineProps<{
    trackId: number;
    label: string;
    date?: string;
    recordedAt?: number;
    lengthMeters?: number;
    ascentMeters?: number;
    activityType?: string;
    description?: string;
    context?: string;
    matchedMediaCount?: number;
    showEmptyMedia?: boolean;
    selected?: boolean;
  }>(),
  {
    date: '',
    recordedAt: undefined,
    lengthMeters: undefined,
    ascentMeters: undefined,
    activityType: '',
    description: '',
    context: '',
    matchedMediaCount: undefined,
    showEmptyMedia: false,
    selected: undefined,
  }
);

const facts = computed(() =>
  formatTrackChoiceFacts({
    recordedAt: props.recordedAt,
    fallbackDate: props.date,
    lengthMeters: props.lengthMeters,
    ascentMeters: props.ascentMeters,
  })
);

const descriptionText = computed(() => props.description.replace(/\s+/g, ' ').trim());

const positiveMediaCount = computed(() =>
  Number.isFinite(props.matchedMediaCount) && props.matchedMediaCount! > 0 ? props.matchedMediaCount! : 0
);
const mediaLabel = computed(() => {
  if (positiveMediaCount.value)
    return `${positiveMediaCount.value.toLocaleString()} ${positiveMediaCount.value === 1 ? 'photo' : 'photos'}`;
  return props.showEmptyMedia && props.matchedMediaCount === 0 ? 'No matched photos' : '';
});
const accessibleLabel = computed(() =>
  [
    props.label,
    props.context,
    props.activityType && activityLabel(props.activityType),
    ...facts.value.map((fact) => `${fact.label}: ${fact.value}`),
    descriptionText.value,
    mediaLabel.value,
    props.selected ? 'Selected' : '',
  ]
    .filter(Boolean)
    .join(', ')
);
</script>

<style scoped>
.track-choice-row {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  width: 100%;
  min-height: 3.25rem;
  padding: 0.55rem 0.65rem;
  color: var(--text-primary);
  text-align: left;
  background: var(--surface-sheet-solid);
  border: 1px solid var(--border-default);
  border-radius: 0.75rem;
  cursor: pointer;
}
.track-choice-row:hover,
.track-choice-row--selected {
  background: var(--surface-elevated);
  border-color: var(--accent);
}
.track-choice-row:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.track-choice-row__icon,
.track-choice-row__chevron,
.track-choice-row__selected {
  flex: 0 0 auto;
  color: var(--text-muted);
}
.track-choice-row .track-choice-row__shape {
  display: none;
  color: color-mix(in srgb, var(--info) 75%, var(--text-muted));
}
.track-choice-row__shape :deep(svg) {
  opacity: 1;
  animation: none;
}
@container track-choice-results (min-width: 40rem) {
  .track-choice-row .track-choice-row__shape {
    display: inline-flex;
  }
}
.track-choice-row__content {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  flex: 1 1 auto;
  gap: 0.15rem;
  min-width: 0;
}
.track-choice-row__title {
  overflow: hidden;
  font-size: var(--text-sm-size);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.track-choice-row__metadata {
  --track-choice-badge-padding: 0.1rem 0.35rem;
  --track-choice-badge-gap: 0.25rem;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}
.track-choice-row__activity {
  flex: 0 1 auto;
  min-width: 0;
  max-width: 50%;
}
.track-choice-row .track-choice-row__activity {
  gap: var(--track-choice-badge-gap);
  padding: var(--track-choice-badge-padding);
  border-radius: 1rem;
  font-size: inherit;
  line-height: inherit;
}
.track-choice-row .track-choice-row__activity :deep(.activity-badge__icon) {
  font-size: inherit;
}
.track-choice-row__activity :deep(.activity-badge__label) {
  overflow: hidden;
  text-overflow: ellipsis;
}
.track-choice-row__description {
  min-width: 0;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.track-choice-row__media {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0.3rem;
  color: var(--accent);
  font-size: var(--text-xs-size);
  font-weight: 600;
  white-space: nowrap;
}
.track-choice-row__media--empty {
  color: var(--text-muted);
  font-weight: 400;
}
</style>
