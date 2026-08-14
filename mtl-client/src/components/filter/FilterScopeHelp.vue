<template>
  <article class="filter-scope-help" data-test="filter-scope-help">
    <header class="filter-scope-help__intro">
      <div class="filter-scope-help__meta">
        <span v-if="firstVisit" class="filter-scope-help__important">
          <i class="bi bi-exclamation-circle" aria-hidden="true"></i>
          Important
        </span>
        <span class="filter-scope-help__eyebrow">Filter behavior</span>
      </div>
      <h3>Your filter shapes everything</h3>
      <p>
        Filter view, criteria, and included categories define the current result. MTL Explorer uses only those tracks
        throughout the app.
      </p>
    </header>

    <section class="filter-scope-help__section" aria-labelledby="filter-scope-every-view">
      <div class="filter-scope-help__section-head">
        <span class="filter-scope-help__section-icon"><i class="bi bi-grid" aria-hidden="true"></i></span>
        <div>
          <h4 id="filter-scope-every-view">Every view adapts</h4>
          <p>Tracks outside the result behave as if they did not exist. Nothing is deleted.</p>
        </div>
      </div>
      <div class="filter-scope-help__view-list" aria-label="Views affected by the filter">
        <span>Map</span>
        <span>Track lists</span>
        <span>Statistics</span>
        <span>Trends</span>
        <span>Milestones</span>
        <span>Heatmap</span>
      </div>
    </section>

    <section
      class="filter-scope-help__section filter-scope-help__section--example"
      aria-labelledby="filter-scope-example"
    >
      <div class="filter-scope-help__section-head">
        <span class="filter-scope-help__section-icon"><i class="bi bi-person-walking" aria-hidden="true"></i></span>
        <div>
          <h4 id="filter-scope-example">Example: walking only</h4>
          <p>
            Select an activity filter and include only the on-foot category. MTL Explorer then behaves as if only those
            tracks existed, so statistics, milestones, and trends become walking-only. Every filter works this way.
          </p>
        </div>
      </div>
      <div class="filter-scope-help__flow" aria-label="Walking-only filter example">
        <span>Activity filter</span>
        <i class="bi bi-arrow-right" aria-hidden="true"></i>
        <span>On foot only</span>
        <i class="bi bi-arrow-right" aria-hidden="true"></i>
        <strong>Walking-only Explorer</strong>
      </div>
    </section>

    <section class="filter-scope-help__section" aria-labelledby="filter-scope-colors">
      <div class="filter-scope-help__section-head">
        <span class="filter-scope-help__section-icon"><i class="bi bi-palette" aria-hidden="true"></i></span>
        <div>
          <h4 id="filter-scope-colors">Map colors reveal patterns</h4>
          <p>
            Color matching tracks by activity type, weekday, or another grouping. Advanced users can define virtually
            any grouping with a custom SQL filter.
          </p>
        </div>
      </div>
      <div class="filter-scope-help__color-examples" aria-label="Map coloring examples">
        <span><i class="bi bi-bicycle" aria-hidden="true"></i> Activity type</span>
        <span class="filter-scope-help__weekday">
          <span aria-hidden="true"> <i></i><i></i><i></i><i></i><i></i><i></i><i></i> </span>
          Weekday
        </span>
        <span><i class="bi bi-code-slash" aria-hidden="true"></i> Custom SQL</span>
      </div>
      <p class="filter-scope-help__note">Coloring changes how matching tracks are drawn, not which tracks count.</p>
    </section>

    <button type="button" class="filter-scope-help__done" @click="emit('done')">
      {{ firstVisit ? 'Got it' : 'Back to Filter' }}
    </button>
  </article>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    firstVisit?: boolean;
  }>(),
  {
    firstVisit: false,
  }
);

const emit = defineEmits<{
  (event: 'done'): void;
}>();

defineOptions({ name: 'FilterScopeHelp' });
</script>

<style scoped>
.filter-scope-help {
  display: flex;
  width: min(100%, 46rem);
  min-height: 0;
  box-sizing: border-box;
  flex: 1 1 auto;
  flex-direction: column;
  align-self: center;
  gap: 1rem;
  overflow-y: auto;
  padding: 0.6rem 1rem calc(1.25rem + env(safe-area-inset-bottom));
  color: var(--text-secondary);
}

.filter-scope-help__intro {
  padding: 0.35rem 0.15rem 0.5rem;
}

.filter-scope-help__meta {
  display: flex;
  min-height: 1.4rem;
  align-items: center;
  gap: 0.45rem;
  margin-bottom: 0.3rem;
}

.filter-scope-help__eyebrow {
  color: var(--accent-text);
  font-size: var(--text-xs-size);
  font-weight: var(--font-bold);
  letter-spacing: 0.065em;
  text-transform: uppercase;
}

.filter-scope-help__important {
  display: inline-flex;
  min-height: 1.4rem;
  align-items: center;
  gap: 0.28rem;
  padding: 0.12rem 0.42rem;
  border-radius: 999px;
  background: var(--warning-bg);
  color: var(--warning-text);
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-xs-lh);
}

.filter-scope-help h3,
.filter-scope-help h4,
.filter-scope-help p {
  margin: 0;
}

.filter-scope-help h3 {
  color: var(--text-primary);
  font-size: var(--text-xl-size, 1.45rem);
  line-height: var(--text-xl-lh, 1.85rem);
}

.filter-scope-help__intro p {
  max-width: 42rem;
  margin-top: 0.35rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.filter-scope-help__section {
  display: grid;
  gap: 0.8rem;
  padding: 1rem;
  border: 1px solid var(--border-subtle, var(--border-default));
  border-radius: 0.8rem;
  background: var(--surface-glass-subtle, var(--surface-elevated));
}

.filter-scope-help__section--example {
  border-color: color-mix(in srgb, var(--accent) 24%, var(--border-default));
  background: color-mix(in srgb, var(--accent) 5%, var(--surface-elevated));
}

.filter-scope-help__section-head {
  display: grid;
  grid-template-columns: 2rem minmax(0, 1fr);
  gap: 0.7rem;
}

.filter-scope-help__section-icon {
  display: inline-flex;
  width: 2rem;
  height: 2rem;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--accent-subtle);
  color: var(--accent-text);
}

.filter-scope-help h4 {
  color: var(--text-primary);
  font-size: var(--text-base-size);
  line-height: var(--text-base-lh);
}

.filter-scope-help__section-head p,
.filter-scope-help__note {
  margin-top: 0.2rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.filter-scope-help__view-list,
.filter-scope-help__color-examples {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  padding-left: 2.7rem;
}

.filter-scope-help__view-list span,
.filter-scope-help__color-examples > span,
.filter-scope-help__flow span,
.filter-scope-help__flow strong {
  display: inline-flex;
  min-height: 1.7rem;
  box-sizing: border-box;
  align-items: center;
  gap: 0.35rem;
  padding: 0.25rem 0.55rem;
  border-radius: 999px;
  background: var(--surface-hover);
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.filter-scope-help__flow {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  padding-left: 2.7rem;
  color: var(--text-muted);
}

.filter-scope-help__flow strong {
  background: var(--accent-subtle);
  color: var(--accent-text);
}

.filter-scope-help__weekday > span {
  display: inline-flex;
}

.filter-scope-help__weekday i {
  display: inline-block;
  width: 0.38rem;
  height: 0.7rem;
  border-radius: 0.15rem;
  background: var(--accent);
}

.filter-scope-help__weekday i:nth-child(2),
.filter-scope-help__weekday i:nth-child(6) {
  opacity: 0.72;
}

.filter-scope-help__weekday i:nth-child(3),
.filter-scope-help__weekday i:nth-child(5) {
  opacity: 0.5;
}

.filter-scope-help__weekday i:nth-child(4) {
  opacity: 0.32;
}

.filter-scope-help__note {
  padding-left: 2.7rem;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.filter-scope-help__done {
  align-self: flex-end;
  min-height: 2.5rem;
  padding: 0.5rem 0.9rem;
  border: 0;
  border-radius: 0.55rem;
  background: var(--accent);
  color: var(--accent-contrast);
  font: inherit;
  font-size: var(--text-sm-size);
  font-weight: var(--font-semibold);
  cursor: pointer;
}

.filter-scope-help__done:focus-visible {
  outline: 2px solid var(--focus-ring, var(--accent));
  outline-offset: 2px;
}

@media (max-width: 40rem) {
  .filter-scope-help {
    gap: 0.8rem;
    padding: 0.35rem 1rem calc(1.1rem + env(safe-area-inset-bottom));
  }

  .filter-scope-help__section {
    padding: 0.85rem;
  }

  .filter-scope-help__view-list,
  .filter-scope-help__color-examples,
  .filter-scope-help__flow,
  .filter-scope-help__note {
    padding-left: 0;
  }

  .filter-scope-help__flow {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .filter-scope-help__done {
    min-height: 2.75rem;
  }
}
</style>
