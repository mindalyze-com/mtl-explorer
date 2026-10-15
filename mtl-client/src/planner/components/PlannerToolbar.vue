<template>
  <PrimeSelect
    :model-value="profile"
    :options="profileOptions"
    option-label="label"
    option-value="value"
    :aria-label="label || 'Routing profile'"
    :disabled="!profiles.length"
    class="planner-profile-select"
    @update:model-value="emit('profile-changed', $event)"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import PrimeSelect from 'primevue/select';

const props = defineProps<{ profiles: string[]; profile: string; label?: string }>();
const emit = defineEmits<{ 'profile-changed': [profile: string] }>();
const PROFILE_LABELS: Record<string, string> = {
  trekking: 'Touring bike',
  fastbike: 'Road bike',
  mtb: 'Mountain bike',
  'hiking-mountain': 'Hiking',
  'car-eco': 'Car',
};
const profileOptions = computed(() =>
  props.profiles.map((value) => ({ value, label: PROFILE_LABELS[value] ?? value }))
);
</script>

<style scoped>
.planner-profile-select {
  min-width: 0;
  min-height: 2.75rem;
  align-items: center;
}
</style>
