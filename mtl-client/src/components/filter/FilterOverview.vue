<template>
  <div class="filter-overview">
    <section
      class="filter-overview__result status-rail"
      :class="{
        'filter-overview__result--paused': !enabled,
        'filter-overview__result--error': error,
      }"
      aria-labelledby="filter-result-title"
      aria-live="polite"
    >
      <div class="filter-overview__result-main">
        <div class="filter-overview__result-head">
          <div class="filter-overview__result-copy">
            <span class="settings-eyebrow filter-overview__eyebrow">Current result</span>
            <span
              v-if="activeIdentity"
              class="filter-overview__active-chip"
              data-test="active-filter-chip"
              :title="activeIdentity"
            >
              <i class="bi bi-funnel-fill" aria-hidden="true"></i>
              <span>{{ activeIdentity }}</span>
            </span>
            <h3 id="filter-result-title" class="filter-overview__result-value">
              <i v-if="loading" class="pi pi-spin pi-spinner" aria-hidden="true"></i>
              {{ resultText }}
            </h3>
            <p class="filter-overview__result-detail" :aria-hidden="resultDetail ? undefined : true">
              {{ resultDetail || '\u00a0' }}
            </p>
          </div>
        </div>

        <div class="filter-overview__result-actions">
          <button
            type="button"
            class="filter-overview__mode-toggle"
            role="switch"
            :aria-checked="enabled"
            aria-label="Apply filter"
            @click="emit('update:enabled', !enabled)"
          >
            <span class="filter-overview__mode-label">Apply filter</span>
            <span class="filter-overview__mode-track" aria-hidden="true">
              <span class="filter-overview__mode-thumb"></span>
            </span>
          </button>
          <div v-if="resetUndoAvailable" class="filter-overview__undo" role="status">
            <i class="bi bi-check-circle" aria-hidden="true"></i>
            <span>Filter reset.</span>
            <button type="button" @click="emit('undo-reset')">Undo</button>
          </div>
          <template v-else>
            <button
              v-if="resultActionLabel"
              type="button"
              class="filter-overview__primary"
              :disabled="resultActionDisabled"
              @click="emit('result-action')"
            >
              <i :class="resultActionIcon" aria-hidden="true"></i>
              {{ resultActionLabel }}
            </button>
            <button
              v-if="showSecondaryResultAction"
              type="button"
              class="filter-overview__secondary"
              @click="emit('secondary-result-action')"
            >
              {{ secondaryResultActionLabel }}
            </button>
          </template>
        </div>
      </div>

      <button
        type="button"
        class="filter-overview__scope-link"
        aria-label="How this result works. Read more"
        @click="emit('open-scope-help')"
      >
        <i class="bi bi-info-circle" aria-hidden="true"></i>
        <span class="filter-overview__scope-summary">
          <strong class="filter-overview__scope-title">How this result works</strong>
          <strong class="filter-overview__scope-title-mobile">Filters apply everywhere.</strong>
          <small>The current result is used throughout MTL Explorer.</small>
        </span>
        <span class="filter-overview__scope-read">
          <span class="filter-overview__scope-read-label">Read more</span>
          <span class="filter-overview__scope-read-label-narrow">More</span>
          <i class="bi bi-arrow-right" aria-hidden="true"></i>
        </span>
      </button>
    </section>

    <section class="filter-overview__configuration" aria-labelledby="filter-configuration-title">
      <div class="settings-section-heading filter-overview__section-heading">
        <div>
          <h3 id="filter-configuration-title">Configuration</h3>
          <p>Open a section to change it.</p>
        </div>
      </div>

      <div class="settings-list filter-overview__list">
        <button type="button" class="settings-row filter-overview-row" @click="emit('open-view')">
          <span class="settings-row__icon filter-overview-row__icon filter-overview-row__icon--view">
            <i class="bi bi-funnel" aria-hidden="true"></i>
          </span>
          <span class="settings-row__body filter-overview-row__body">
            <strong>Filter view</strong>
            <small>Choose how tracks are grouped and compared.</small>
          </span>
          <span class="settings-row__value filter-overview-row__value">{{ viewSummary }}</span>
          <i class="bi bi-chevron-right settings-row__chevron filter-overview-row__chevron" aria-hidden="true"></i>
        </button>

        <button type="button" class="settings-row filter-overview-row" @click="emit('open-criteria')">
          <span class="settings-row__icon filter-overview-row__icon filter-overview-row__icon--criteria">
            <i class="bi bi-sliders" aria-hidden="true"></i>
          </span>
          <span class="settings-row__body filter-overview-row__body">
            <strong>Criteria</strong>
            <small>Limit tracks by date, area, or selection.</small>
          </span>
          <span class="settings-row__value filter-overview-row__value">{{ criteriaSummary }}</span>
          <i class="bi bi-chevron-right settings-row__chevron filter-overview-row__chevron" aria-hidden="true"></i>
        </button>

        <button
          type="button"
          class="settings-row filter-overview-row"
          :class="{ 'filter-overview-row--disabled': !categoriesAvailable }"
          :disabled="!categoriesAvailable"
          @click="emit('open-categories')"
        >
          <span class="settings-row__icon filter-overview-row__icon filter-overview-row__icon--categories">
            <i class="bi bi-list-check" aria-hidden="true"></i>
          </span>
          <span class="settings-row__body filter-overview-row__body">
            <strong>Included categories</strong>
            <small>Choose which categories remain in the current result.</small>
          </span>
          <span class="settings-row__value filter-overview-row__value">{{ categoriesSummary }}</span>
          <i
            :class="categoriesAvailable ? 'bi bi-chevron-right' : 'bi bi-lock'"
            class="settings-row__chevron filter-overview-row__chevron"
            aria-hidden="true"
          ></i>
        </button>

        <button
          type="button"
          class="settings-row filter-overview-row"
          :class="{ 'filter-overview-row--disabled': !colorsAvailable }"
          :disabled="!colorsAvailable"
          @click="emit('open-colors')"
        >
          <span class="settings-row__icon filter-overview-row__icon filter-overview-row__icon--colors">
            <i class="bi bi-palette" aria-hidden="true"></i>
          </span>
          <span class="settings-row__body filter-overview-row__body">
            <strong>Map colors</strong>
            <small>Change map styling without changing matches.</small>
          </span>
          <span class="settings-row__value filter-overview-row__value filter-overview-row__value--colors">
            <span v-if="paletteColors.length" class="filter-overview-row__swatches" aria-hidden="true">
              <span
                v-for="(color, index) in paletteColors.slice(0, 5)"
                :key="`${color}-${index}`"
                :style="{ backgroundColor: color }"
              ></span>
            </span>
            <span>{{ colorsSummary }}</span>
          </span>
          <i
            :class="colorsAvailable ? 'bi bi-chevron-right' : 'bi bi-lock'"
            class="settings-row__chevron filter-overview-row__chevron"
            aria-hidden="true"
          ></i>
        </button>
      </div>
    </section>

    <div class="filter-overview__footer-actions">
      <button v-if="showReviewAction" type="button" class="filter-overview__review" @click="emit('review')">
        <i class="bi bi-table" aria-hidden="true"></i>
        Review tracks
      </button>
      <button type="button" class="filter-overview__reset" @click="emit('reset')">
        <i class="bi bi-arrow-counterclockwise" aria-hidden="true"></i>
        Reset filter
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'FilterOverview' });

withDefaults(
  defineProps<{
    enabled: boolean;
    loading?: boolean;
    error?: boolean;
    resultText: string;
    resultDetail?: string;
    resultActionLabel?: string;
    resultActionIcon?: string;
    resultActionDisabled?: boolean;
    showSecondaryResultAction?: boolean;
    secondaryResultActionLabel?: string;
    showReviewAction?: boolean;
    resetUndoAvailable?: boolean;
    activeIdentity?: string;
    viewSummary: string;
    criteriaSummary: string;
    categoriesSummary: string;
    categoriesAvailable: boolean;
    colorsSummary: string;
    colorsAvailable: boolean;
    paletteColors?: string[];
  }>(),
  {
    loading: false,
    error: false,
    resultDetail: '',
    resultActionLabel: '',
    resultActionIcon: 'bi bi-table',
    resultActionDisabled: false,
    showSecondaryResultAction: false,
    secondaryResultActionLabel: '',
    showReviewAction: false,
    resetUndoAvailable: false,
    activeIdentity: '',
    paletteColors: () => [],
  }
);

const emit = defineEmits<{
  (event: 'update:enabled', value: boolean): void;
  (event: 'result-action'): void;
  (event: 'secondary-result-action'): void;
  (event: 'open-view'): void;
  (event: 'open-criteria'): void;
  (event: 'open-categories'): void;
  (event: 'open-colors'): void;
  (event: 'open-scope-help'): void;
  (event: 'review'): void;
  (event: 'reset'): void;
  (event: 'undo-reset'): void;
}>();
</script>

<style scoped>
.filter-overview {
  display: flex;
  width: 100%;
  margin: 0 auto;
  padding: 0.5rem 1rem 1rem;
  box-sizing: border-box;
  flex-direction: column;
  gap: 1.35rem;
  color: var(--text-secondary);
}

.filter-overview__result {
  position: relative;
  display: grid;
  min-height: 10.5rem;
  box-sizing: border-box;
  grid-template-columns: minmax(0, 1fr) minmax(17rem, 0.82fr);
  gap: 1.15rem;
  padding: 0.8rem 0.8rem 0.8rem 1.05rem;
  border: 1px solid var(--border-subtle, var(--border-default));
  border-radius: 0.8rem;
  background: color-mix(in srgb, var(--surface-elevated) 78%, transparent);
}

.filter-overview__result--paused {
  background: color-mix(in srgb, var(--surface-elevated) 62%, transparent);
}

.filter-overview__result--paused::before {
  background: var(--border-medium);
}

.filter-overview__result--error::before {
  background: var(--error);
}

.filter-overview__result-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.filter-overview__result-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.filter-overview__result-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
}

.filter-overview__active-chip {
  display: inline-flex;
  max-width: 100%;
  min-height: 1.75rem;
  box-sizing: border-box;
  align-items: center;
  gap: 0.35rem;
  margin: 0.25rem 0 0.4rem;
  padding: 0.22rem 0.55rem;
  border: 1px solid var(--chip-border);
  border-radius: 999px;
  background: var(--chip-bg);
  color: var(--chip-text);
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-xs-lh);
}

.filter-overview__active-chip > i {
  flex: 0 0 auto;
  color: var(--warning);
}

.filter-overview__active-chip > span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-overview__result-value {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-lg-size);
  font-weight: var(--font-bold);
  line-height: var(--text-lg-lh);
}

.filter-overview__result-detail {
  min-height: var(--text-sm-lh);
  margin: 0.28rem 0 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.filter-overview__result-actions {
  display: flex;
  min-height: 2.65rem;
  align-items: center;
  gap: 0.4rem;
  margin-top: auto;
  padding-top: 1rem;
}

.filter-overview__primary,
.filter-overview__secondary,
.filter-overview__undo button,
.filter-overview__mode-toggle,
.filter-overview__review,
.filter-overview__reset {
  border: 0;
  font: inherit;
  font-size: var(--text-sm-size);
  font-weight: var(--font-semibold);
  cursor: pointer;
}

.filter-overview__primary,
.filter-overview__secondary {
  display: inline-flex;
  min-height: 2.5rem;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  padding: 0.48rem 0.7rem;
  border-radius: 0.55rem;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.filter-overview__primary {
  background: color-mix(in srgb, var(--accent) 11%, transparent);
  color: var(--accent-text);
}

.filter-overview__primary:hover:not(:disabled) {
  background: color-mix(in srgb, var(--accent) 17%, transparent);
}

.filter-overview__primary:disabled {
  cursor: default;
  opacity: 0.55;
}

.filter-overview__mode-toggle {
  display: inline-flex;
  min-height: 2.75rem;
  align-items: center;
  gap: 0.65rem;
  padding: 0.35rem 0.4rem;
  border-radius: 0.55rem;
  background: transparent;
  color: var(--text-primary);
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.filter-overview__mode-toggle:hover {
  background: var(--surface-hover);
}

.filter-overview__mode-toggle:focus-visible {
  outline: 2px solid var(--focus-ring, var(--accent));
  outline-offset: 2px;
}

.filter-overview__mode-label {
  white-space: nowrap;
}

.filter-overview__mode-track {
  position: relative;
  display: inline-flex;
  width: 2.5rem;
  height: 1.4rem;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--border-strong);
  box-shadow: inset 0 0 0 1px var(--border-default);
  transition:
    background 0.18s ease,
    box-shadow 0.18s ease;
}

.filter-overview__mode-thumb {
  position: absolute;
  top: 0.2rem;
  left: 0.2rem;
  width: 1rem;
  height: 1rem;
  border-radius: 50%;
  background: var(--accent-contrast);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.28);
  transition: transform 0.18s ease;
}

.filter-overview__mode-toggle[aria-checked='true'] .filter-overview__mode-track {
  background: var(--accent);
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, var(--accent) 70%, #000),
    0 0 0.65rem color-mix(in srgb, var(--accent) 24%, transparent);
}

.filter-overview__mode-toggle[aria-checked='true'] .filter-overview__mode-thumb {
  transform: translateX(1.1rem);
}

.filter-overview__secondary {
  background: transparent;
  color: var(--text-secondary);
}

.filter-overview__secondary:hover {
  background: var(--surface-hover);
}

.filter-overview__undo {
  display: inline-flex;
  min-height: 2.65rem;
  box-sizing: border-box;
  align-items: center;
  gap: 0.45rem;
  padding: 0.55rem 0.75rem;
  border-radius: 0.55rem;
  background: color-mix(in srgb, var(--success) 9%, transparent);
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
}

.filter-overview__undo > i {
  color: var(--success);
}

.filter-overview__undo button {
  padding: 0;
  background: transparent;
  color: var(--accent-text);
}

.filter-overview__configuration {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.filter-overview__section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 0 0.15rem;
}

.filter-overview__scope-link {
  display: grid;
  min-width: 0;
  grid-template-columns: 1.6rem minmax(0, 1fr);
  grid-template-rows: auto auto;
  align-content: center;
  align-items: start;
  gap: 0.4rem 0.65rem;
  margin: 1rem 0.2rem;
  padding: 0.9rem 1rem;
  border: 1px solid color-mix(in srgb, var(--accent) 22%, var(--border-default));
  border-radius: 0.7rem;
  background: color-mix(in srgb, var(--accent) 5%, var(--surface-elevated));
  color: var(--text-secondary);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}

.filter-overview__scope-link:hover {
  border-color: var(--border-hover, var(--border-default));
  background: var(--surface-hover);
}

.filter-overview__scope-link:focus-visible {
  outline: 2px solid var(--focus-ring, var(--accent));
  outline-offset: 2px;
}

.filter-overview__scope-link > i {
  grid-row: 1 / span 2;
  color: var(--accent-text);
  font-size: var(--text-base-size);
  line-height: var(--text-base-lh);
}

.filter-overview__scope-summary {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.18rem;
}

.filter-overview__scope-summary strong {
  color: var(--text-primary);
  font-weight: var(--font-semibold);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.filter-overview__scope-title-mobile {
  display: none;
}

.filter-overview__scope-summary small {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.filter-overview__scope-read {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--accent-text);
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-xs-lh);
}

.filter-overview__scope-read i {
  transition: transform 0.15s ease;
}

.filter-overview__scope-read-label-narrow {
  display: none;
}

.filter-overview__scope-link:hover .filter-overview__scope-read i {
  transform: translateX(0.12rem);
}

.filter-overview-row--disabled {
  cursor: default;
}

.filter-overview-row__icon--criteria {
  background: transparent;
  color: var(--text-muted);
}

.filter-overview-row__icon--categories {
  background: transparent;
  color: var(--text-muted);
}

.filter-overview-row__icon--colors {
  background: transparent;
  color: var(--text-muted);
}

.filter-overview-row--disabled .filter-overview-row__icon {
  background: transparent;
  color: var(--text-muted);
}

.filter-overview-row__value {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.45rem;
}

.filter-overview-row__value--colors > span:last-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-overview-row--disabled .filter-overview-row__value,
.filter-overview-row--disabled .filter-overview-row__body {
  opacity: 0.66;
}

.filter-overview-row__swatches {
  display: inline-flex;
  flex: 0 0 auto;
  flex-direction: row-reverse;
  padding-left: 0.35rem;
}

.filter-overview-row__swatches span {
  width: 0.85rem;
  height: 0.85rem;
  margin-left: -0.32rem;
  border: 1px solid var(--surface-elevated);
  border-radius: 50%;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.18);
}

.filter-overview-row--disabled .filter-overview-row__chevron {
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  transform: none;
}

.filter-overview__footer-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
}

.filter-overview__review,
.filter-overview__reset {
  align-self: flex-start;
  display: inline-flex;
  min-height: 2.5rem;
  align-items: center;
  gap: 0.45rem;
  padding: 0.45rem 0.15rem;
  border-radius: 0.4rem;
  background: transparent;
  color: var(--text-muted);
}

.filter-overview__review:hover,
.filter-overview__reset:hover {
  background: var(--surface-hover);
  color: var(--text-secondary);
}

.filter-overview__review {
  color: var(--text-secondary);
}

@media screen and (max-width: 760px) {
  .filter-overview__result {
    display: flex;
    flex-direction: column;
    gap: 0;
  }

  .filter-overview__scope-link {
    min-height: 2.75rem;
    grid-template-columns: auto minmax(0, 1fr) auto;
    grid-template-rows: auto;
    align-content: initial;
    align-items: center;
    gap: 0.45rem;
    margin: 0.7rem 0 0;
    padding: 0.5rem 0.65rem;
  }

  .filter-overview__scope-link > i {
    grid-row: 1;
    font-size: var(--text-sm-size);
    line-height: var(--text-sm-lh);
  }

  .filter-overview__scope-title {
    display: none;
  }

  .filter-overview__scope-title-mobile {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .filter-overview__scope-summary small {
    display: none;
  }

  .filter-overview__scope-read {
    white-space: nowrap;
  }
}

@media screen and (max-width: 600px) {
  .filter-overview {
    padding: 0.55rem 1rem calc(1.1rem + env(safe-area-inset-bottom));
    gap: 1.15rem;
  }

  .filter-overview__result {
    min-height: 0;
    padding: 0.75rem 0.65rem 0.75rem 0.9rem;
  }

  .filter-overview__result::before {
    top: 0.8rem;
    bottom: 0.8rem;
  }

  .filter-overview__result-detail {
    min-height: calc(2 * var(--text-sm-lh));
  }

  .filter-overview__result-head {
    gap: 0.65rem;
  }

  .filter-overview-row {
    min-height: 4.25rem;
    grid-template-columns: 1.45rem minmax(0, 1fr) auto;
    gap: 0.65rem;
    padding: 0.7rem 0.1rem;
  }

  .filter-overview-row__value {
    grid-column: 2;
    max-width: 100%;
    justify-content: flex-start;
    color: var(--text-secondary);
    font-size: var(--text-xs-size);
    line-height: var(--text-xs-lh);
    text-align: left;
  }

  .filter-overview-row__body small {
    display: none;
  }

  .filter-overview-row__chevron {
    grid-column: 3;
    grid-row: 1 / span 2;
  }

  .filter-overview-row__icon {
    width: 1.45rem;
    height: 1.45rem;
    grid-row: 1 / span 2;
  }
}

@media screen and (max-width: 360px) {
  .filter-overview__scope-link {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .filter-overview__scope-link > i {
    display: none;
  }

  .filter-overview__scope-read-label {
    display: none;
  }

  .filter-overview__scope-read-label-narrow {
    display: inline;
  }

  .filter-overview__scope-read i {
    display: none;
  }
}
</style>
