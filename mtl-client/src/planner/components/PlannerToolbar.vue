<template>
  <div class="planner-toolbar">
    <!-- Custom profile dropdown with per-category icons -->
    <div class="planner-toolbar__dropdown" ref="dropdownEl">
      <button
        type="button"
        class="planner-toolbar__profile-btn"
        :disabled="!profiles.length"
        :aria-expanded="dropdownOpen"
        aria-haspopup="listbox"
        @click="dropdownOpen = !dropdownOpen"
      >
        <i :class="[profileIcon, 'planner-toolbar__profile-icon']" />
        <span class="planner-toolbar__profile-label">{{ profileLabelFor(selectedProfile) }}</span>
        <i class="bi bi-chevron-down planner-toolbar__chevron" />
      </button>
      <ul v-if="dropdownOpen" class="planner-toolbar__dropdown-list" role="listbox">
        <li
          v-for="p in profiles"
          :key="p"
          class="planner-toolbar__dropdown-item"
          :class="{ 'is-active': p === selectedProfile }"
          role="option"
          :aria-selected="p === selectedProfile"
          @click="selectProfile(p)"
        >
          <i :class="[profileIconFor(p)]" />
          <span>{{ profileLabelFor(p) }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

const props = defineProps<{
  profiles: string[];
  profile: string;
}>();

const emit = defineEmits<{
  (e: 'profile-changed', p: string): void;
}>();

const selectedProfile = ref(props.profile);
const dropdownOpen = ref(false);
const dropdownEl = ref<HTMLElement | null>(null);

watch(
  () => props.profile,
  (p) => { selectedProfile.value = p; }
);

const PROFILE_ICONS: Record<string, string> = {
  trekking: 'bi bi-signpost-split',
  fastbike: 'bi bi-bicycle',
  'hiking-mountain': 'bi bi-compass',
  'car-eco': 'bi bi-car-front',
};

const PROFILE_LABELS: Record<string, string> = {
  trekking: 'Hiking',
  fastbike: 'Road Bike',
  'hiking-mountain': 'Mountain Hiking',
  'car-eco': 'Car',
};

function profileLabelFor(p: string): string {
  return PROFILE_LABELS[p] ?? p;
}

function profileIconFor(p: string): string {
  return PROFILE_ICONS[p] ?? 'bi bi-signpost-split';
}

const profileIcon = computed(() => profileIconFor(selectedProfile.value));

function selectProfile(p: string) {
  selectedProfile.value = p;
  dropdownOpen.value = false;
  emit('profile-changed', p);
}

function onDocClick(e: MouseEvent) {
  if (dropdownEl.value && !dropdownEl.value.contains(e.target as Node)) {
    dropdownOpen.value = false;
  }
}

onMounted(() => document.addEventListener('click', onDocClick, true));
onUnmounted(() => document.removeEventListener('click', onDocClick, true));
</script>

<style scoped>
.planner-toolbar {
  display: inline-flex;
  align-items: center;
}

/* ── Profile dropdown ─────────────────────────────────────────── */
.planner-toolbar__dropdown {
  position: relative;
  flex-shrink: 0;
}
.planner-toolbar__profile-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  height: 2.4rem;
  padding: 0 0.75rem;
  border: 1px solid var(--accent);
  border-radius: 8px;
  background: var(--accent-bg);
  cursor: pointer;
  font-size: var(--text-sm-size);
  font-weight: 600;
  color: var(--accent-text);
  text-transform: capitalize;
  white-space: nowrap;
  transition: background 0.12s, border-color 0.12s;
}
.planner-toolbar__profile-btn:hover {
  background: var(--accent-subtle);
}
.planner-toolbar__profile-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.planner-toolbar__profile-icon {
  color: var(--accent-text);
  font-size: var(--text-base-size);
  flex-shrink: 0;
}
.planner-toolbar__profile-label {
  flex: 1 1 auto;
}
.planner-toolbar__chevron {
  font-size: 10px;
  color: var(--accent-muted);
  flex-shrink: 0;
}
.planner-toolbar__dropdown-list {
  position: absolute;
  top: calc(100% + 5px);
  left: 0;
  z-index: 60;
  min-width: 100%;
  list-style: none;
  margin: 0;
  padding: 0.3rem;
  border: 1px solid var(--border-default);
  border-radius: 14px;
  background: var(--surface-glass-heavy);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}
.planner-toolbar__dropdown-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.7rem;
  border-radius: 10px;
  font-size: var(--text-sm-size);
  font-weight: 500;
  color: var(--text-secondary);
  text-transform: capitalize;
  cursor: pointer;
  transition: background 0.1s;
  white-space: nowrap;
}
.planner-toolbar__dropdown-item:hover {
  background: var(--accent-bg);
  color: var(--accent-text);
}
.planner-toolbar__dropdown-item.is-active {
  color: var(--accent-text);
  font-weight: 700;
  background: var(--accent-subtle);
}
.planner-toolbar__dropdown-item i {
  font-size: var(--text-base-size);
  flex-shrink: 0;
  width: 1.1rem;
  text-align: center;
}

/* ── end ─────────────────────────────────────────────────────── */
</style>
