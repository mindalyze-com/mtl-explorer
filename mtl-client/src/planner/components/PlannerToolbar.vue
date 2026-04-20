<template>
  <div class="planner-toolbar">
    <select
      class="planner-toolbar__select"
      v-model="selectedProfile"
      :disabled="!profiles.length"
      aria-label="Routing profile"
      @change="emitProfileChanged"
    >
      <option v-for="p in profiles" :key="p" :value="p">{{ p }}</option>
    </select>

    <div class="planner-toolbar__group" role="group" aria-label="Edit history">
      <button
        type="button"
        class="planner-toolbar__btn planner-toolbar__btn--primary planner-toolbar__btn--compact-primary"
        :disabled="!canUndo"
        @click="$emit('undo')"
        title="Undo"
        aria-label="Undo"
      >
        <i class="bi bi-arrow-counterclockwise" />
      </button>
      <button
        type="button"
        class="planner-toolbar__btn"
        :disabled="!canRedo"
        @click="$emit('redo')"
        title="Redo"
        aria-label="Redo"
      >
        <i class="bi bi-arrow-clockwise" />
      </button>
      <button
        type="button"
        class="planner-toolbar__btn planner-toolbar__btn--primary planner-toolbar__btn--compact-primary"
        :disabled="!hasWaypoints"
        @click="$emit('clear')"
        title="Clear route"
        aria-label="Clear route"
      >
        <i class="bi bi-trash" />
      </button>
    </div>

    <button
      type="button"
      class="planner-toolbar__btn planner-toolbar__btn--primary planner-toolbar__save"
      :disabled="!hasRoute"
      @click="$emit('save')"
      title="Save plan"
    >
      <i class="bi bi-save" /> <span>Save</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

const props = defineProps<{
  profiles: string[];
  profile: string;
  canUndo: boolean;
  canRedo: boolean;
  hasWaypoints: boolean;
  hasRoute: boolean;
}>();

const emit = defineEmits<{
  (e: 'profile-changed', p: string): void;
  (e: 'undo'): void;
  (e: 'redo'): void;
  (e: 'clear'): void;
  (e: 'save'): void;
}>();

const selectedProfile = ref(props.profile);
watch(() => props.profile, (p) => { selectedProfile.value = p; });

function emitProfileChanged() {
  emit('profile-changed', selectedProfile.value);
}
</script>

<style scoped>
.planner-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
  padding: 0.65rem 0.7rem;
  background: linear-gradient(180deg, var(--surface-glass-heavy), var(--surface-glass));
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
  border-radius: 14px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-medium);
}
.planner-toolbar__select {
  flex: 1 1 8rem;
  min-width: 6rem;
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--border-medium);
  background: color-mix(in srgb, var(--surface-glass-heavy) 84%, transparent);
  color: var(--text-primary);
  border-radius: 10px;
  font-size: 0.9rem;
  min-height: 38px;
  text-transform: capitalize;
}
.planner-toolbar__group {
  display: inline-flex;
  gap: 0.25rem;
}
.planner-toolbar__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  min-width: 38px;
  min-height: 38px;
  padding: 0 0.65rem;
  border: 1px solid var(--border-medium);
  background: color-mix(in srgb, var(--surface-glass-heavy) 84%, transparent);
  border-radius: 10px;
  cursor: pointer;
  font-size: 0.95rem;
  color: var(--text-secondary);
  font-weight: 600;
  transition: background 0.12s ease, border-color 0.12s ease, color 0.12s ease;
}
.planner-toolbar__btn:hover:not(:disabled) {
  background: var(--surface-hover);
  border-color: var(--border-hover);
  color: var(--text-primary);
}
.planner-toolbar__btn:disabled { opacity: 0.45; cursor: not-allowed; }
.planner-toolbar__btn--danger:not(:disabled):hover {
  color: var(--error);
  border-color: color-mix(in srgb, var(--error) 35%, var(--border-medium));
  background: var(--error-bg);
}
.planner-toolbar__btn--primary {
  background: var(--accent);
  color: var(--text-inverse);
  border-color: var(--accent);
  font-weight: 600;
  letter-spacing: 0.02em;
  box-shadow: 0 10px 24px color-mix(in srgb, var(--accent) 24%, transparent);
}
.planner-toolbar__btn--primary:hover:not(:disabled) {
  background: var(--accent-hover);
  border-color: var(--accent-hover);
  color: var(--text-inverse);
}
.planner-toolbar__btn--compact-primary {
  box-shadow: 0 6px 16px color-mix(in srgb, var(--accent) 18%, transparent);
}
.planner-toolbar__save { margin-left: auto; padding: 0 0.9rem; }

@media (max-width: 480px) {
  .planner-toolbar { gap: 0.4rem; }
  .planner-toolbar__select { flex-basis: 100%; order: -1; }
  .planner-toolbar__save { margin-left: auto; }
}
</style>
