<template>
  <div class="section-hub">
    <section
      class="section-hub__intro"
      :class="{ 'section-hub__intro--single': !$slots.featured }"
      :aria-labelledby="`${hubId}-title`"
    >
      <div class="section-hub__intro-copy">
        <h3 :id="`${hubId}-title`">{{ title }}</h3>
        <p class="section-hub__description">
          {{ description }}
          <slot name="details" />
        </p>
        <p v-if="returnHint" class="section-hub__return-hint">{{ returnHint }}</p>
      </div>
      <slot name="featured" />
    </section>

    <section :aria-labelledby="`${hubId}-choices`">
      <div class="settings-section-heading section-hub__heading">
        <h3 :id="`${hubId}-choices`">{{ choicesTitle }}</h3>
        <p v-if="choicesDescription">{{ choicesDescription }}</p>
      </div>
      <div ref="listEl" class="settings-list section-hub__list">
        <button
          v-for="item in items"
          :key="item.value"
          type="button"
          class="settings-row section-hub__row"
          :data-section="item.value"
          :aria-label="item.actionLabel"
          :aria-describedby="`${hubId}-${item.value}-description`"
          @click="emit('open', item.value)"
        >
          <span class="settings-row__icon section-hub__icon"><i :class="item.icon" aria-hidden="true"></i></span>
          <span class="settings-row__body">
            <strong>{{ item.label }}</strong>
            <small :id="`${hubId}-${item.value}-description`">{{ item.description }}</small>
          </span>
          <span class="settings-row__value section-hub__action" aria-hidden="true">{{ item.actionLabel }}</span>
          <i class="bi bi-chevron-right settings-row__chevron" aria-hidden="true"></i>
        </button>
      </div>
    </section>

    <div v-if="$slots.actions" class="section-hub__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script lang="ts">
export interface SectionHubItem {
  value: string;
  label: string;
  description: string;
  icon: string;
  actionLabel: string;
}
</script>

<script setup lang="ts">
import { ref, useId } from 'vue';

withDefaults(
  defineProps<{
    title: string;
    description: string;
    items: readonly SectionHubItem[];
    returnHint?: string;
    choicesTitle?: string;
    choicesDescription?: string;
  }>(),
  {
    choicesTitle: 'Choose a view',
    choicesDescription: 'Open a section to continue.',
    returnHint: '',
  }
);

const emit = defineEmits<{ open: [value: string] }>();
const hubId = useId();
const listEl = ref<HTMLElement | null>(null);

function focusItem(value: string): void {
  const buttons = listEl.value?.querySelectorAll<HTMLButtonElement>('button[data-section]');
  Array.from(buttons ?? [])
    .find((button) => button.dataset.section === value)
    ?.focus({ preventScroll: true });
}

defineExpose({ focusItem });
</script>

<style scoped>
.section-hub {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding: 0.5rem 1rem 1.25rem;
  container: section-hub / inline-size;
}

.section-hub__intro {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  align-items: center;
  gap: 1.5rem;
  padding: 1rem 1.25rem;
  border: 1px solid var(--border-default);
  border-radius: 0.8rem;
  background: transparent;
}

.section-hub__intro h3 {
  margin: 0.4rem 0;
  color: var(--text-primary);
  font-size: var(--text-lg-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-lg-lh);
}

.section-hub__intro p {
  margin: 0.65rem 0 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.section-hub__intro--single {
  grid-template-columns: minmax(0, 1fr);
}

.section-hub__heading {
  margin: 0 0 1rem;
}

.section-hub__list {
  border: 1px solid var(--border-default);
  border-radius: 0.75rem;
}

.section-hub__row {
  grid-template-columns: 3rem minmax(0, 1fr) auto auto;
  gap: 1.2rem;
  min-height: 5.5rem;
  padding: 1rem;
}

.section-hub__icon {
  width: 3rem;
  height: 3rem;
  border-radius: 0.65rem;
  background: var(--surface-elevated);
  color: var(--accent-text);
  font-size: var(--text-2xl-size);
}

.section-hub__icon i {
  font-size: inherit;
}

.section-hub__action {
  font-weight: var(--font-medium);
}

@container section-hub (max-width: 40rem) {
  .section-hub__intro {
    grid-template-columns: minmax(0, 1fr);
    gap: 0.25rem;
    padding: 1rem;
  }

  .section-hub__return-hint {
    display: none;
  }

  .section-hub__row {
    grid-template-columns: 2.5rem minmax(0, 1fr) auto;
    gap: 0.65rem;
    padding: 0.8rem;
  }

  .section-hub__icon {
    width: 2.5rem;
    height: 2.5rem;
    font-size: var(--text-xl-size);
  }

  .section-hub__action {
    display: none;
  }
}

@container section-hub (max-width: 20rem) {
  .section-hub__intro {
    padding: 0.75rem;
  }

  .section-hub__intro-copy > p {
    display: none;
  }

  .section-hub__intro h3 {
    margin: 0;
    font-size: var(--text-lg-size);
    line-height: var(--text-lg-lh);
  }
}
</style>
