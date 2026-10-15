<template>
  <div class="planner-entry">
    <section v-if="guide" class="planner-guide" aria-label="Planning with your map">
      <div class="planner-guide__copy">
        <h3>Your map is your starting point</h3>
        <p>Use familiar tracks and public route networks to decide where to go next.</p>
        <ol>
          <li><strong>Find your area.</strong> Move and zoom the map to where you want to plan.</li>
          <li>
            <strong>Show trails.</strong> In Map, open Route overlays to show hiking and cycling routes. Your tracks
            stay visible while you plan.
          </li>
          <li>
            <strong>Draw your route.</strong> Choose a travel mode, then tap the map to add points. Drag a point to move
            it, or select it, choose Move to…, and tap the new spot. Check distance and elevation as you go.
          </li>
          <li>
            <strong>Mark places to visit.</strong> Choose Points of interest and tap the map. Add a name and an optional
            note. Drag a place to move it, or choose Move POI.
          </li>
          <li>
            <strong>Save.</strong> Save the route and places as one plan. Use Tools to reverse or clear the route, and
            Undo to take back a change. Open it again later with Continue a saved plan, or export the route as GPX.
          </li>
        </ol>
        <p class="planner-guide__note">
          Trails on the map are for reference. The route follows your points and travel mode.
        </p>
        <a :href="guideTopicUrl('planning')" target="_blank" rel="opener">Full planning guide</a>
      </div>
      <PlannerMapPreview />
    </section>
    <SectionHub
      v-else
      ref="sectionHub"
      class="planner-hub"
      title="Plan on your map"
      description="Draw a route and mark places to visit. Save both as one plan."
      choices-title="What you can do"
      choices-description=""
      :items="items"
      @open="openSection"
    >
      <template #details>
        <span class="planner-hub__guide-summary">
          <button type="button" @click="emit('open', 'guide')">More info</button>
        </span>
      </template>
    </SectionHub>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { guideTopicUrl } from '@/utils/documentation';
import SectionHub, { type SectionHubItem } from '@/components/ui/SectionHub.vue';
import PlannerMapPreview from '@/planner/components/PlannerMapPreview.vue';

export type PlannerSection = 'draw' | 'guide' | 'load';

const props = withDefaults(defineProps<{ hasDraft: boolean; guide?: boolean }>(), { guide: false });
const emit = defineEmits<{ open: [section: PlannerSection] }>();
const sectionHub = ref<InstanceType<typeof SectionHub> | null>(null);
const items = computed<SectionHubItem[]>(() => [
  {
    value: 'draw',
    label: props.hasDraft ? 'Continue your current plan' : 'Start a new plan',
    description: props.hasDraft
      ? 'Return to your route and points of interest.'
      : 'Add a route, points of interest, or both.',
    icon: 'bi bi-plus-circle',
    actionLabel: props.hasDraft ? 'Continue your current plan' : 'Start a new plan',
  },
  {
    value: 'load',
    label: 'Continue a saved plan',
    description: 'Continue editing a plan, or export its route as GPX.',
    icon: 'bi bi-folder2-open',
    actionLabel: 'Continue a saved plan',
  },
]);

function openSection(value: string): void {
  if (value === 'draw' || value === 'guide' || value === 'load') emit('open', value);
}

defineExpose({ focusItem: (value: string) => sectionHub.value?.focusItem(value) });
</script>

<style scoped>
.planner-entry {
  container: planner-entry / inline-size;
}

.planner-hub {
  gap: 0.75rem;
  padding-bottom: 0.75rem;
}

.planner-hub :deep(.section-hub__intro) {
  padding: 0.75rem 1rem;
}

.planner-hub :deep(.section-hub__heading) {
  margin-bottom: 0.75rem;
}

.planner-hub :deep(.section-hub__list) {
  display: grid;
  gap: 0.65rem;
  overflow: visible;
  border: 0;
  background: transparent;
}

.planner-hub :deep(.section-hub__row) {
  grid-template-columns: 2.75rem minmax(0, 1fr) auto;
  min-height: 4.5rem;
  gap: 1rem;
  padding: 0.75rem 1rem;
  border: 1px solid var(--border-default);
  border-radius: 0.65rem;
}

.planner-hub__guide-summary {
  margin-left: 0.25rem;
}

.planner-hub__guide-summary button {
  min-height: 2.25rem;
  margin: -0.45rem 0;
  padding: 0 0.2rem;
  border: 0;
  border-radius: 0.2rem;
  background: transparent;
  color: var(--accent-text);
  font: inherit;
  font-weight: var(--font-semibold);
  cursor: pointer;
}

.planner-hub__guide-summary button:hover {
  text-decoration: underline;
}

.planner-hub__guide-summary button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.planner-hub :deep(.section-hub__icon) {
  width: 2.75rem;
  height: 2.75rem;
  border-radius: 50%;
  background: var(--accent-subtle);
  font-size: var(--text-xl-size);
}

.planner-hub :deep(.section-hub__action) {
  display: none;
}

.planner-guide {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  align-items: center;
  gap: 2rem;
  padding: 1rem 1.5rem 1.5rem;
}

.planner-guide h3 {
  margin: 0.5rem 0;
  font-size: var(--text-xl-size);
  line-height: var(--text-xl-lh);
}

.planner-guide p,
.planner-guide li {
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.planner-guide ol {
  display: grid;
  gap: 1rem;
  margin: 1.25rem 0;
  padding-left: 1.25rem;
}

.planner-guide .planner-guide__note {
  color: var(--text-muted);
}

@container planner-entry (max-width: 40rem) {
  :deep(.planner-map-preview) {
    display: none;
  }

  .planner-hub :deep(.section-hub__intro) {
    grid-template-columns: minmax(0, 1fr);
    gap: 1rem;
  }

  .planner-hub :deep(.section-hub__row) {
    gap: 0.65rem;
    padding: 0.75rem;
  }

  .planner-guide {
    grid-template-columns: minmax(0, 1fr);
    gap: 1rem;
    padding: 0.5rem 1rem 1rem;
  }
}
</style>
