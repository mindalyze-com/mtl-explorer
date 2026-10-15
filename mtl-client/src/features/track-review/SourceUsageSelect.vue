<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue';
import { ReviewDecisionRequestDecisionEnum as Decision } from 'x8ing-mtl-api-typescript-fetch';
import Button from 'primevue/button';
import { decisionLabel } from './api';
const props = withDefaults(
  defineProps<{ state?: string; decision?: string; disabled?: boolean; canInclude?: boolean; label?: string }>(),
  { state: undefined, decision: undefined, canInclude: true, label: 'Use recording' }
);
const emit = defineEmits<{ save: [decision: Decision] }>();
const choices = [
  { value: Decision.Include, label: 'Map and statistics', icon: 'bi bi-check2-circle' },
  { value: Decision.ExcludeStatistics, label: 'Map only', icon: 'bi bi-map' },
  { value: Decision.ExcludeMap, label: 'Statistics only', icon: 'bi bi-bar-chart' },
  { value: Decision.Exclude, label: 'Hidden', icon: 'bi bi-eye-slash' },
];
const name = useId();
const selected = ref<Decision | ''>('');
const saved = ref<Decision | ''>('');
const changed = computed(() => !!selected.value && selected.value !== saved.value);
watch(
  () => [props.state, props.decision],
  ([state, decision]) => {
    saved.value =
      choices.find((choice) => choice.value === decision)?.value ??
      (
        {
          INCLUDED: Decision.Include,
          EXCLUDED_MAP: Decision.ExcludeMap,
          EXCLUDED_STATISTICS: Decision.ExcludeStatistics,
          EXCLUDED: Decision.Exclude,
        } as Record<string, Decision>
      )[state ?? ''] ??
      '';
    selected.value = saved.value;
  },
  { immediate: true }
);
function save() {
  if (!selected.value || props.disabled || (selected.value === Decision.Include && !props.canInclude)) return;
  emit('save', selected.value);
}
</script>
<template>
  <form class="source-usage" @submit.prevent="save">
    <fieldset :disabled="disabled">
      <legend>{{ label }}</legend>
      <div class="source-usage__options">
        <label
          v-for="choice in choices"
          :key="choice.value"
          :title="decisionLabel(choice.value)"
          :class="{ 'source-usage__option--selected': selected === choice.value }"
        >
          <input
            v-model="selected"
            type="radio"
            :name="name"
            :value="choice.value"
            :disabled="choice.value === Decision.Include && !canInclude"
          />
          <i :class="choice.icon" aria-hidden="true"></i>
          <span>{{ choice.label }}</span>
        </label>
      </div>
    </fieldset>
    <Button
      type="submit"
      label="Save use settings"
      size="small"
      :severity="changed ? undefined : 'secondary'"
      :outlined="!changed"
      :disabled="disabled || !selected || (selected === Decision.Include && !canInclude)"
    />
  </form>
</template>
<style scoped>
.source-usage {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  align-items: end;
  margin: 0.75rem 0;
}
fieldset {
  flex: 1 1 100%;
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}
legend {
  margin-bottom: 0.4rem;
  font-size: 0.85rem;
  color: var(--text-secondary);
}
.source-usage__options {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(8.5rem, 1fr));
  gap: 0.4rem;
}
.source-usage__options label {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-height: 44px;
  padding: 0.45rem 0.7rem;
  border: 1px solid var(--border-medium);
  border-radius: 0.5rem;
  cursor: pointer;
  font-size: 0.9rem;
}
.source-usage__options input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}
.source-usage__options .source-usage__option--selected {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  font-weight: 600;
}
.source-usage__options label:has(input:focus-visible) {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.source-usage__options label:has(input:disabled) {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
